import tempfile
import unittest
from pathlib import Path

from tools.batch_generation import check_tips_file, validate_payload, count_words

ROOT = Path(__file__).resolve().parents[1]

class TestBatchGeneration(unittest.TestCase):
    def test_empty_tips_raises(self):
        with tempfile.NamedTemporaryFile("w+", delete=False) as f:
            temp_path = Path(f.name)
        try:
            with self.assertRaises(ValueError) as ctx:
                check_tips_file(temp_path)
            self.assertIn("empty (0 bytes)", str(ctx.exception))
        finally:
            if temp_path.exists():
                temp_path.unlink()

    def test_missing_tips_raises(self):
        non_existent = ROOT / "non_existent_tips_file.txt"
        with self.assertRaises(ValueError) as ctx:
            check_tips_file(non_existent)
        self.assertIn("does not exist", str(ctx.exception))

    def test_validate_payload_success(self):
        sample = {
            "concrete_image": "A sturdy stone embankment holding back turbulent storm floodwaters from village houses.",
            "synonyms_comparison": "1. mitigate: softens the impact without total removal.\n2. alleviate: eases suffering or pain.\n3. lessen: general reduction of quantity or severity.",
            "register_and_contexts": "Formal / Academic context; municipal disaster mitigation planning.",
            "collocations": "mitigate the risk, mitigate damage, mitigate climate impact",
            "associations": "climate change, flood barriers, emergency response",
            "integrated_example": (
                "When severe rainfall triggered flash floods across the rural valley, community leaders worked through "
                "the night to reinforce the reservoir walls. Their timely intervention helped mitigate the damage to "
                "local schools and homes. Engineers later presented a comprehensive review explaining why proper drainage "
                "infrastructure remains critical for long-term regional resilience and flood protection."
            ),
        }
        valid, reason = validate_payload(sample, "mitigate")
        self.assertTrue(valid, f"Validation should succeed, but got: {reason}")

    def test_validate_payload_missing_field(self):
        sample = {
            "concrete_image": "A barrier",
            "synonyms_comparison": "1. a 2. b 3. c",
        }
        valid, reason = validate_payload(sample, "mitigate")
        self.assertFalse(valid)
        self.assertIn("Missing or empty", reason)

    def test_validate_payload_few_synonyms(self):
        sample = {
            "concrete_image": "A barrier",
            "synonyms_comparison": "similar to ease",
            "register_and_contexts": "formal",
            "collocations": "mitigate risk",
            "associations": "risk",
            "integrated_example": "word " * 60,
        }
        valid, reason = validate_payload(sample, "mitigate")
        self.assertFalse(valid)
        self.assertIn("synonyms", reason)

    def test_word_count_bounds(self):
        sample = {
            "concrete_image": "A barrier",
            "synonyms_comparison": "1. apple 2. banana 3. cherry comparison details",
            "register_and_contexts": "formal",
            "collocations": "mitigate risk, mitigate impact",
            "associations": "risk, harm, disaster",
            "integrated_example": "Too short example sentence.",
        }
        valid, reason = validate_payload(sample, "mitigate")
        self.assertFalse(valid)
        self.assertIn("word count", reason)

if __name__ == "__main__":
    unittest.main()
