import datetime
import json
import re
import unittest
from typing import List, Dict, Any, Optional

SEMESTER_START = datetime.date(2026, 9, 14)
HOLIDAYS = {"2026-09-25", "2026-10-01", "2026-10-02", "2026-10-03", "2027-01-01"}
INTERVALS = [1, 3, 7, 14, 30, 60]

def parse_date(date_str: str) -> datetime.date:
    parts = list(map(int, date_str.split("-")))
    return datetime.date(parts[0], parts[1], parts[2])

def week_of(date_str: str) -> int:
    d = parse_date(date_str)
    return (d - SEMESTER_START).days // 7 + 1

def date_of(week: int, weekday: int) -> str:
    days = (week - 1) * 7 + (weekday - 1)
    return (SEMESTER_START + datetime.timedelta(days=days)).isoformat()

def weekday_of(date_str: str) -> int:
    return parse_date(date_str).isoweekday()

def times_for_date(date_str: str) -> str:
    md = date_str[5:]
    return "summer" if "05-01" <= md < "10-01" else "winter"

def is_badminton(name: str, weekday: int, sections: List[int]) -> bool:
    norm_name = re.sub(r"\s+", "", name).replace("（", "(").replace("）", ")")
    return norm_name == "羽毛球" and weekday == 3 and sorted(sections) == [3, 4]

def merge_consecutive_sections(sections: List[int]) -> List[tuple]:
    sorted_s = sorted(set(sections))
    if not sorted_s:
        return []
    ranges = []
    start = sorted_s[0]
    end = start
    for n in sorted_s[1:]:
        if n == end + 1:
            end = n
        else:
            ranges.append((start, end))
            start = end = n
    ranges.append((start, end))
    return ranges

class TestCoreParity(unittest.TestCase):
    def test_semester_bounds_and_dates(self):
        self.assertEqual(week_of("2026-09-14"), 1)
        self.assertEqual(weekday_of("2026-09-14"), 1)
        self.assertEqual(date_of(1, 1), "2026-09-14")
        self.assertEqual(date_of(1, 7), "2026-09-20")
        self.assertEqual(week_of("2026-09-20"), 1)

        self.assertEqual(date_of(2, 1), "2026-09-21")
        self.assertEqual(week_of("2026-09-21"), 2)

        self.assertEqual(date_of(18, 7), "2027-01-17")
        self.assertEqual(week_of("2027-01-17"), 18)

    def test_holidays_presence(self):
        for h in ["2026-09-25", "2026-10-01", "2026-10-02", "2026-10-03", "2027-01-01"]:
            self.assertIn(h, HOLIDAYS)
        self.assertNotIn("2026-09-24", HOLIDAYS)
        self.assertNotIn("2026-10-04", HOLIDAYS)

    def test_times_season_switch(self):
        self.assertEqual(times_for_date("2026-09-30"), "summer")
        self.assertEqual(times_for_date("2026-10-01"), "winter")
        self.assertEqual(times_for_date("2026-12-31"), "winter")

    def test_badminton_specification(self):
        self.assertTrue(is_badminton("羽毛球", 3, [3, 4]))
        self.assertTrue(is_badminton("  羽毛球  ", 3, [4, 3]))
        self.assertFalse(is_badminton("羽毛球（高阶）", 3, [3, 4]))
        self.assertFalse(is_badminton("羽毛球", 4, [3, 4]))
        self.assertFalse(is_badminton("羽毛球", 3, [1, 2]))

    def test_consecutive_merging(self):
        ranges = merge_consecutive_sections([1, 2, 3, 5, 6])
        self.assertEqual(ranges, [(1, 3), (5, 6)])

    def test_srs_intervals(self):
        stage = -1
        # Step 1: known
        stage = min(5, stage + 1)
        self.assertEqual(INTERVALS[stage], 1)
        # Step 2: known
        stage = min(5, stage + 1)
        self.assertEqual(INTERVALS[stage], 3)
        # Step 3: known
        stage = min(5, stage + 1)
        self.assertEqual(INTERVALS[stage], 7)
        # Step 4: known
        stage = min(5, stage + 1)
        self.assertEqual(INTERVALS[stage], 14)
        # Step 5: known
        stage = min(5, stage + 1)
        self.assertEqual(INTERVALS[stage], 30)
        # Step 6: known
        stage = min(5, stage + 1)
        self.assertEqual(INTERVALS[stage], 60)
        # Step 7: max cap at 60
        stage = min(5, stage + 1)
        self.assertEqual(INTERVALS[stage], 60)

if __name__ == "__main__":
    unittest.main()
