import json
import sqlite3
import tempfile
import unittest
import zipfile
from pathlib import Path
from tools.import_dictionary import SCHEMA
from tools.enrich_dictionary import enrich

class EnrichmentTest(unittest.TestCase):
    def test_stable_key_counts_original_examples_and_idempotence(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); db_path = root/'content.db'
            db = sqlite3.connect(str(db_path)); db.executescript(SCHEMA)
            db.execute("INSERT INTO word(id,headword,lookup,status,pronunciation) VALUES('w','run','run','exact','')")
            db.execute("INSERT INTO sense(word_id,id,pos,definition_en,examples_json,synonyms_json) VALUES('w','oewn-123','v','move quickly',?, '[]')", (json.dumps(['The engine runs.']),))
            db.commit(); db.close()
            (root/'entries-r.json').write_text(json.dumps({'run':{'v':{'sense':[{'id':'run%2:38:00::','synset':'oewn-123'}]}}}))
            archive = root/'wn.zip'
            with zipfile.ZipFile(str(archive),'w') as z:
                z.writestr('wordnet/index.sense','run%2:38:00:: 00000001 1 9\n')
                z.writestr('wordnet/LICENSE','Fixture license')
                for pos in ['noun','verb','adj','adv']:
                    z.writestr('wordnet/data.'+pos,'00000001 | move quickly; "They run daily."\n' if pos=='verb' else '')
                    z.writestr('wordnet/'+pos+'.exc','ran run\n' if pos=='verb' else '')
            report = root/'report.json'
            self.assertEqual(enrich(db_path,root,archive,report)['added_original_examples'],1)
            self.assertEqual(enrich(db_path,root,archive,report)['added_original_examples'],0)
            with sqlite3.connect(str(db_path)) as db:
                frequency, examples, sources = db.execute('SELECT frequency,examples_json,example_sources_json FROM sense').fetchone()
                self.assertEqual(frequency,9)
                self.assertEqual(json.loads(examples),['The engine runs.','They run daily.'])
                self.assertEqual(json.loads(sources)['They run daily.'],'Princeton WordNet 3.0')
                self.assertIn('ran',json.loads(db.execute('SELECT forms_json FROM word').fetchone()[0]))
