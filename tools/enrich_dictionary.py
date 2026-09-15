#!/usr/bin/env python3
"""Stable sense-key enrichment: historical counts, original examples, inflections."""
import argparse, hashlib, json, re, sqlite3, zipfile, shutil, tempfile
from pathlib import Path

def enrich(db_path, wordnet_json, princeton_zip, report_path):
    z = zipfile.ZipFile(princeton_zip)
    index = {s[0]: (s[1], int(s[3])) for s in (line.split() for line in z.read('wordnet/index.sense').decode().splitlines())}
    originals, exceptions, lexemes = {}, {}, {}
    for pos, name in [('1','noun'),('2','verb'),('3','adj'),('5','adj'),('4','adv')]:
        for line in z.read('wordnet/data.' + name).decode().splitlines():
            if line and line[0].isdigit(): originals[(pos,line[:8])] = re.findall(r'"([^"]+)"',line.split('|',1)[-1])
        for line in z.read('wordnet/' + name + '.exc').decode().splitlines():
            parts = line.split()
            for lemma in parts[1:]: exceptions.setdefault(lemma.replace('_',' '),set()).add(parts[0].replace('_',' '))
    for path in sorted(wordnet_json.glob('entries-*.json')): lexemes.update(json.loads(path.read_text()))
    temporary = db_path.with_suffix(".enriching")
    shutil.copyfile(str(db_path), str(temporary))
    db = sqlite3.connect(str(temporary))
    cols = {r[1] for r in db.execute('PRAGMA table_info(sense)')}
    for name, typ in [('frequency','INTEGER'),('source_order','INTEGER NOT NULL DEFAULT 0'),('example_sources_json',"TEXT NOT NULL DEFAULT '{}'")]:
        if name not in cols: db.execute('ALTER TABLE sense ADD COLUMN '+name+' '+typ)
    if 'forms_json' not in {r[1] for r in db.execute('PRAGMA table_info(word)')}:
        db.execute("ALTER TABLE word ADD COLUMN forms_json TEXT NOT NULL DEFAULT '[]'")
    added, ranked = 0, 0
    for wid, head, lookup in db.execute('SELECT id,headword,lookup FROM word').fetchall():
        forms = {head,lookup} | exceptions.get(lookup,set())
        records = lexemes.get(lookup,{})
        if 'n' in records or 'v' in records:
            forms.update([lookup+'s',lookup+'es'])
            if lookup.endswith('y') and len(lookup)>1 and lookup[-2] not in 'aeiou': forms.add(lookup[:-1]+'ies')
        if 'v' in records:
            forms.update([lookup+'ed',lookup+'ing'])
            if lookup.endswith('e'): forms.update([lookup+'d',lookup[:-1]+'ing'])
            if lookup.endswith('y'): forms.add(lookup[:-1]+'ied')
        db.execute('UPDATE word SET forms_json=? WHERE id=?',(json.dumps(sorted(forms)),wid))
        order = 0
        for lexeme in records.values():
            for ref in lexeme.get('sense',[]):
                order += 1
                row = db.execute('SELECT examples_json,example_sources_json FROM sense WHERE word_id=? AND id=?',(wid,ref['synset'])).fetchone()
                if row is None: continue
                examples = json.loads(row[0]); sources = {e:'Open English Wordnet 2025' for e in examples}; sources.update(json.loads(row[1]))
                key = ref['id'].replace(' ','_'); previous = index.get(key)
                frequency = previous[1] if previous else None
                if frequency: ranked += 1
                if previous:
                    for sentence in originals.get((key.split('%')[1][0],previous[0]),[]):
                        if sentence not in examples:
                            examples.append(sentence); added += 1; sources[sentence] = 'Princeton WordNet 3.0'
                db.execute('UPDATE sense SET frequency=?,source_order=?,examples_json=?,example_sources_json=? WHERE word_id=? AND id=?',
                    (frequency,order,json.dumps(examples,ensure_ascii=False),json.dumps(sources,ensure_ascii=False),wid,ref['synset']))
    metadata = {'sense_frequency_source':'Princeton WordNet 3.0 historical tagged counts; https://wordnet.princeton.edu/documentation/cntlist5wn',
        'princeton_archive_sha256':hashlib.sha256(princeton_zip.read_bytes()).hexdigest(),'schema_version':'2'}
    for key,value in metadata.items(): db.execute('INSERT OR REPLACE INTO metadata VALUES(?,?)',(key,value))
    db.execute('PRAGMA user_version=2'); db.commit()
    counts = {}
    for wid, examples in db.execute('SELECT word_id,examples_json FROM sense'): counts[wid] = counts.get(wid,0)+len(json.loads(examples))
    report = {'added_original_examples':added,'senses_with_positive_frequency':ranked,'total_examples':sum(counts.values()),'words_with_multiple_examples':sum(n>=2 for n in counts.values()),**metadata}
    assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
    db.close()
    temporary.replace(db_path)
    report['database_sha256'] = hashlib.sha256(db_path.read_bytes()).hexdigest()
    db_path.with_suffix('.sha256').write_text(hashlib.sha256(db_path.read_bytes()).hexdigest()+'\n')
    (db_path.parent/'PRINCETON-WORDNET-LICENSE.txt').write_bytes(z.read('wordnet/LICENSE'))
    report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    return report

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--db',type=Path,default=Path('app/src/main/assets/content.db'));p.add_argument('--wordnet-json',type=Path,required=True);p.add_argument('--princeton-zip',type=Path,required=True);p.add_argument('--report',type=Path,default=Path('docs/example-coverage.json'));a=p.parse_args()
    print(json.dumps(enrich(a.db,a.wordnet_json,a.princeton_zip,a.report),indent=2))
