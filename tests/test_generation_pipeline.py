import hashlib
import json
import shutil
import sqlite3
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import patch
from tools.batch_generation import run_batch

ROOT = Path(__file__).resolve().parents[1]

class GenerationPipelineTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.db = Path(self.tmp.name) / 'content.db'
        shutil.copyfile(str(ROOT / 'app/src/main/assets/content.db'), str(self.db))
        self.tips = ROOT / 'sources/5steps_tips.txt'
    def tearDown(self):
        self.tmp.cleanup()
    def test_default_run_is_offline(self):
        before = self.db.read_bytes()
        with patch('urllib.request.urlopen', side_effect=AssertionError('network called')):
            report = run_batch(self.tips, self.db, limit=2)
        self.assertTrue(report['dry_run'])
        self.assertEqual(before, self.db.read_bytes())
    def test_authentication_failure_stops_immediately(self):
        error = urllib.error.HTTPError('http://localhost', 401, 'Unauthorized', {}, None)
        with patch('urllib.request.urlopen', side_effect=error) as request:
            with self.assertRaises(PermissionError):
                run_batch(self.tips, self.db, dry_run=False, api_key_env='', limit=2)
        self.assertEqual(request.call_count, 1)
        self.assertEqual(sqlite3.connect(str(self.db)).execute('select count(*) from generation').fetchone()[0], 0)
    def test_generated_content_commits_and_resumes(self):
        class Response:
            def __enter__(inner): return inner
            def __exit__(inner, *args): pass
            def read(inner): return inner.body
        def respond(req, **kwargs):
            body = json.loads(req.data.decode())
            self.assertIn('integrated_example_mapping', body['messages'][0]['content'])
            word = body['messages'][1]['content'].splitlines()[0].split(': ', 1)[1]
            payload = dict(concrete_image='A vivid scene.', synonyms_comparison='1. reduce: lower force\n2. lessen: smaller amount\n3. alleviate: ease pain',
                register_and_contexts='Formal reports and academic discussion.', collocations='Neutral: common use, frequent use.',
                associations='A story connects rain, river and wall.', integrated_example=word + ' ' + 'example ' * 55,
                integrated_example_mapping='Image, spectrum, register, prosody and associations are combined.')
            result = Response()
            result.body = json.dumps({'choices': [{'message': {'content': json.dumps(payload)}}]}).encode()
            return result
        with patch('urllib.request.urlopen', side_effect=respond) as request:
            result = run_batch(self.tips, self.db, dry_run=False, api_key_env='', limit=1)
        self.assertEqual(result['succeeded'], 1)
        self.assertEqual(self.db.with_suffix('.sha256').read_text().strip(), hashlib.sha256(self.db.read_bytes()).hexdigest())
        full = run_batch(self.tips, self.db, limit=0, resume=False)
        resumed = run_batch(self.tips, self.db, limit=0)
        self.assertEqual(full['total_queued'] - 1, resumed['total_queued'])
