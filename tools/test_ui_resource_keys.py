"""Regression tests for readable resource names and historical hash keys."""

import csv
from pathlib import Path
import unittest

from ui_resource_keys import resource_key_errors


class UiResourceKeyTests(unittest.TestCase):
    def test_readable_english_words_are_allowed(self):
        for key in (
            "common_close", "cast_proxy_port_unavailable", "library_decade_label",
            "decade", "theme_color_facade", "facade_description",
            "lyrics_beaded_frame", "artwork_defaced_label", "common_efface",
            "player_acceded_status", "common_accede", "text_deface",
            "text_effaced", "common_bedded", "common_beefed", "common_cabbed",
            "common_baffed", "common_faffed", "common_decaff",
        ):
            with self.subTest(key=key):
                self.assertEqual(resource_key_errors(key, "example.xml"), [])

    def test_hash_components_are_rejected_in_each_position(self):
        for key in (
            "common_12ab34", "common_12ab34_label", "a12b34_label",
            "common_123456", "common_abcdef", "abcdef_label",
            "common_abcdefabcdef", "common_0123456789abcdef0123456789abcdef",
        ):
            with self.subTest(key=key):
                self.assertTrue(any("hash-like" in error
                                    for error in resource_key_errors(key, "example.xml")))

    def test_word_exception_does_not_hide_another_hash(self):
        for key in (
            "library_decade_12ab34", "library_12ab34_decade",
            "library_facade_abcdef_label", "library_decade_facade_abcdef",
        ):
            with self.subTest(key=key):
                self.assertTrue(any("hash-like" in error
                                    for error in resource_key_errors(key, "example.xml")))

    def test_all_historical_hash_keys_stay_rejected(self):
        audit_path = Path(__file__).resolve().parents[1] / "docs/ui-string-key-renames.tsv"
        with audit_path.open(encoding="utf-8", newline="") as audit:
            rows = list(csv.DictReader(audit, delimiter="\t"))
        self.assertTrue(rows)
        for row in rows:
            with self.subTest(key=row["old_key"]):
                self.assertTrue(any("hash-like" in error for error in
                                    resource_key_errors(row["old_key"], row["resource_file"])))
                self.assertEqual(resource_key_errors(row["new_key"], row["resource_file"]), [])

    def test_invalid_snake_case_stays_rejected(self):
        for key in (
            "", "Common_close", "commonClose", "_common_close", "common_close_",
            "common__close", "common-close", "common.close", "1_common_close",
        ):
            with self.subTest(key=key):
                self.assertTrue(any("invalid resource key" in error
                                    for error in resource_key_errors(key, "example.xml")))

    def test_error_preserves_key_and_resource_location(self):
        key = "common_12ab34"
        self.assertEqual(resource_key_errors(key, "values/common.xml"), [
            "values/common.xml: hash-like resource key common_12ab34; name its UI purpose",
        ])


if __name__ == "__main__":
    unittest.main()
