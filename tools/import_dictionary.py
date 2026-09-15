#!/usr/bin/env python3
"""Build a traceable offline dictionary from the user-provided IELTS book and OEWN JSON."""
import argparse, collections, hashlib, json, re, sqlite3, tempfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
SCHEMA = '''
PRAGMA user_version=2;
CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL);
CREATE TABLE book(id TEXT PRIMARY KEY,title TEXT NOT NULL,source_sha256 TEXT NOT NULL,entry_count INTEGER NOT NULL);
CREATE TABLE word(id TEXT PRIMARY KEY,headword TEXT NOT NULL,lookup TEXT NOT NULL,status TEXT NOT NULL,pronunciation TEXT NOT NULL,forms_json TEXT NOT NULL DEFAULT '[]');
CREATE TABLE book_entry(book_id TEXT NOT NULL,position INTEGER NOT NULL,word_id TEXT NOT NULL,chapter INTEGER NOT NULL,source_line INTEGER NOT NULL,original TEXT NOT NULL,gloss_zh TEXT NOT NULL,source_pronunciation TEXT NOT NULL,PRIMARY KEY(book_id,position),FOREIGN KEY(word_id) REFERENCES word(id));
CREATE TABLE sense(word_id TEXT NOT NULL,id TEXT NOT NULL,pos TEXT NOT NULL,definition_en TEXT NOT NULL,examples_json TEXT NOT NULL,synonyms_json TEXT NOT NULL,frequency INTEGER,source_order INTEGER NOT NULL DEFAULT 0,example_sources_json TEXT NOT NULL DEFAULT '{}',PRIMARY KEY(word_id,id),FOREIGN KEY(word_id) REFERENCES word(id));
CREATE TABLE generation(word_id TEXT PRIMARY KEY,sense_id TEXT NOT NULL,prompt_sha256 TEXT NOT NULL,input_sha256 TEXT NOT NULL,model TEXT NOT NULL,generated_at TEXT NOT NULL,status TEXT NOT NULL CHECK(status IN ('generated','reviewed')),payload_json TEXT NOT NULL,FOREIGN KEY(word_id) REFERENCES word(id));
CREATE INDEX entry_word ON book_entry(word_id);
CREATE INDEX word_lookup ON word(lookup);
CREATE INDEX sense_word ON sense(word_id);
'''
BOOK_ID = 'ielts-word-list'
ALIASES = {'easy-going':'easygoing', 'co-operate':'cooperate', 'co-operation':'cooperation', 'co-operative':'cooperative', 'air-hostess':'air hostess'}

def parse_book(path):
    rows, chapter = [], None
    for number, raw in enumerate(path.read_text(encoding='utf-8-sig').splitlines(), 1):
        line = raw.strip()
        match = re.fullmatch(r'Word List\s+(\d+)', line)
        if match: chapter = int(match.group(1)); continue
        if chapter is None or not line: continue
        match = re.match(r"^([A-Za-z][A-Za-z'’ -]*)(.*)$", line)
        if not match: raise ValueError('Unparsed source line %s: %s' % (number, line))
        head = match.group(1).strip()
        # With no phonetic delimiter, do not swallow a part-of-speech marker.
        head = re.sub(r'\s+(?:n|v|a|ad|adj|adv|vt|vi|prep|pron|conj|excl)$', '', head)
        remainder = line[len(head):].lstrip('* ').strip()
        phon = re.match(r'^(/[^/]+/|\[[^]]+\]|\{[^}]+\})', remainder)
        pron = phon.group(0) if phon else ''
        gloss = remainder[len(pron):].strip()
        rows.append({'position':len(rows)+1,'chapter':chapter,'source_line':number,'headword':head,'key':re.sub(r'\s+',' ',head.lower().replace('’',"'")),'source_pronunciation':pron,'gloss_zh':gloss,'original':line})
    if not rows: raise ValueError('Book has no entries')
    return rows

def lookup(key, entries):
    if key in entries: return key, 'exact'
    candidates = [ALIASES.get(key), key.replace('-', ' '), key.replace('-', '')]
    for c in candidates:
        if c and c in entries: return c, 'variant'
    return key, 'missing'

def build(book_path, source, output, report_path):
    rows = parse_book(book_path)
    lexemes, synsets = {}, {}
    for path in sorted(source.glob('*.json')):
        (lexemes if path.name.startswith('entries-') else synsets).update(json.loads(path.read_text()))
    if not lexemes or not synsets: raise ValueError('Complete OEWN JSON directory is required')
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix('.building')
    if temporary.exists(): temporary.unlink()
    db = sqlite3.connect(str(temporary)); db.execute('PRAGMA foreign_keys=ON'); db.executescript(SCHEMA)
    digest = hashlib.sha256(book_path.read_bytes()).hexdigest()
    db.execute('INSERT INTO book VALUES(?,?,?,?)',(BOOK_ID,'IELTS Word List',digest,len(rows)))
    for k,v in {'schema_version':'2','dictionary':'Open English Wordnet 2025','dictionary_license':'CC BY 4.0','dictionary_url':'https://en-word.net/downloads','attribution':'Open English Wordnet Community, derived from Princeton WordNet','book_permission':'User supplied and explicitly authorized use; original transcription attribution retained in sources/ielts-word-list.txt','book_sha256':digest}.items(): db.execute('INSERT INTO metadata VALUES(?,?)',(k,v))
    seen, unmatched, variants = {}, [], []
    for r in rows:
        key = r['key']; wid = 'word-' + hashlib.sha256(key.encode()).hexdigest()[:20]
        r['word_id'] = wid
        if key not in seen:
            target,status = lookup(key, lexemes); seen[key] = {'id':wid,'status':status,'positions':[]}
            senses, pronunciation = {}, ''
            for pos, lexeme in lexemes.get(target,{}).items():
                pr = lexeme.get('pronunciation',[])
                if not pronunciation and pr: pronunciation=pr[0].get('value','')
                for ref in lexeme.get('sense',[]):
                    syn=synsets[ref['synset']]
                    if not syn.get('definition'): continue
                    examples=[x if isinstance(x,str) else x.get('text','') for x in syn.get('example',[])]
                    members=[x if isinstance(x,str) else x[0] for x in syn.get('members',[])]
                    senses[ref['synset']]=(wid,ref['synset'],pos,'; '.join(syn['definition']),json.dumps(examples,ensure_ascii=False),json.dumps([m for m in members if m != target],ensure_ascii=False))
            if not senses: status='missing';seen[key]['status']=status
            db.execute('INSERT INTO word(id,headword,lookup,status,pronunciation) VALUES(?,?,?,?,?)',(wid,r['headword'],target,status,pronunciation))
            db.executemany('INSERT INTO sense(word_id,id,pos,definition_en,examples_json,synonyms_json) VALUES(?,?,?,?,?,?)',senses.values())
            if status=='missing':unmatched.append({'word':r['headword'],'source_line':r['source_line'],'word_id':wid})
            if status=='variant':variants.append({'word':r['headword'],'lookup':target,'word_id':wid})
        seen[key]['positions'].append(r['position'])
        db.execute('INSERT INTO book_entry VALUES(?,?,?,?,?,?,?,?)',(BOOK_ID,r['position'],wid,r['chapter'],r['source_line'],r['original'],r['gloss_zh'],r['source_pronunciation']))
    db.commit()
    assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
    assert not db.execute('PRAGMA foreign_key_check').fetchall()
    report={'source_lines':len(book_path.read_text().splitlines()),'book_entries':len(rows),'unique_headwords':len(seen),'chapters':len(set(r['chapter'] for r in rows)),'matched_unique':sum(v['status']!='missing' for v in seen.values()),'unmatched':unmatched,'variants':variants,'duplicates':{k:v['positions'] for k,v in seen.items() if len(v['positions'])>1},'sense_count':db.execute('SELECT count(*) FROM sense').fetchone()[0],'source_sha256':digest}
    db.close();temporary.replace(output)
    report['database_sha256']=hashlib.sha256(output.read_bytes()).hexdigest()
    report_path.parent.mkdir(parents=True,exist_ok=True);report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    output.with_suffix('.sha256').write_text(report['database_sha256']+'\n')
    return report

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--book',type=Path,default=ROOT/'sources/ielts-word-list.txt');parser.add_argument('--wordnet',type=Path,required=True);parser.add_argument('--output',type=Path,default=ROOT/'app/src/main/assets/content.db');parser.add_argument('--report',type=Path,default=ROOT/'docs/dictionary-report.json');args=parser.parse_args()
    report=build(args.book,args.wordnet,args.output,args.report)
    print(json.dumps({k:v for k,v in report.items() if k not in ['unmatched','duplicates','variants']},ensure_ascii=False));print('Unmatched:',len(report['unmatched']),'Duplicates:',len(report['duplicates']))
