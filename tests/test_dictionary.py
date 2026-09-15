import sqlite3
import hashlib
from tools.import_dictionary import parse_book, lookup
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

class TestDictionary(unittest.TestCase):
    def setUp(self):
        self.db_path = ROOT / "app/src/main/assets/content.db"
        self.book_path = ROOT / "sources/ielts-word-list.txt"
        self.report_path = ROOT / "docs/dictionary-report.json"

    def test_source_rows_and_checksum_match_assets(self):
        rows = parse_book(self.book_path)
        self.assertEqual(len(rows), 3611)
        self.assertEqual(len(set(row['key'] for row in rows)), 3610)
        with sqlite3.connect(str(self.db_path)) as conn:
            actual = conn.execute('SELECT position, source_line, original FROM book_entry ORDER BY position').fetchall()
        self.assertEqual(actual, [(row['position'], row['source_line'], row['original']) for row in rows])
        self.assertEqual(hashlib.sha256(self.db_path.read_bytes()).hexdigest(), self.db_path.with_suffix('.sha256').read_text().strip())

    def test_lookup_does_not_guess_unknown_words(self):
        self.assertEqual(lookup('not-a-real-word', {'real': {}})[1], 'missing')
        self.assertEqual(lookup('easy-going', {'easygoing': {}}), ('easygoing', 'variant'))

    def test_assets_exist(self):
        self.assertTrue(self.db_path.exists(), "content.db must exist in app/src/main/assets")
        self.assertTrue(self.book_path.exists(), "ielts-word-list.txt must exist in sources")
        self.assertTrue(self.report_path.exists(), "dictionary-report.json must exist in docs")

    def test_database_integrity_and_schema(self):
        conn = sqlite3.connect(str(self.db_path))
        conn.execute("PRAGMA foreign_keys = ON;")
        integrity = conn.execute("PRAGMA integrity_check;").fetchone()[0]
        self.assertEqual(integrity, "ok", "Database integrity check failed")

        fk_violations = conn.execute("PRAGMA foreign_key_check;").fetchall()
        self.assertEqual(len(fk_violations), 0, f"Foreign key violations found: {fk_violations}")

        tables = set(r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table';").fetchall())
        expected = {"metadata", "book", "word", "book_entry", "sense", "generation"}
        self.assertTrue(expected.issubset(tables), f"Missing tables: {expected - tables}")

        # Check metadata
        meta = dict(conn.execute("SELECT key, value FROM metadata;").fetchall())
        self.assertIn("dictionary_license", meta)
        self.assertEqual(meta["dictionary_license"], "CC BY 4.0")
        self.assertIn("book_sha256", meta)

        # Check book entry count
        book_row = conn.execute("SELECT id, entry_count FROM book WHERE id='ielts-word-list';").fetchone()
        self.assertIsNotNone(book_row)
        self.assertEqual(book_row[1], 3611)

        # Check word and sense counts
        word_count = conn.execute("SELECT count(*) FROM word;").fetchone()[0]
        self.assertEqual(word_count, 3610)

        matched_count = conn.execute("SELECT count(*) FROM word WHERE status != 'missing';").fetchone()[0]
        self.assertEqual(matched_count, 3563)

        conn.close()

if __name__ == "__main__":
    unittest.main()
