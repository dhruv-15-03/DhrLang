import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("qualification.py")
spec = importlib.util.spec_from_file_location("qualification", SCRIPT)
qualification = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qualification)


def participant(number, **changes):
    result = {
        "id": "P-" + str(number).zfill(4), "group": "dhrlang", "consented": True,
        "supportedSetup": True, "firstCorrectRunSeconds": 600,
        "capstoneCompleted": True, "transferAttempted": True,
        "transferOutcome": "passed", "aiOff": True,
    }
    result.update(changes)
    return result


def data(records):
    return {"schemaVersion": 1, "participants": records}


class QualificationTests(unittest.TestCase):
    def test_exact_boundary_preserves_all_enrolled_and_completer_denominators(self):
        records = [participant(i) for i in range(1, 21)]
        for p in records[12:]:
            p.update(capstoneCompleted=None, transferAttempted=False,
                     transferOutcome="missing", aiOff=False)
        for p in records[16:]:
            p["firstCorrectRunSeconds"] = None
        for p in records[9:12]:
            p["transferOutcome"] = "missing"
        result = qualification.summarize(data(records))
        self.assertTrue(result["numericalLearnerThresholdsMet"])
        self.assertEqual(16, result["dhrlang"]["setupWithin600Seconds"]["numerator"])
        self.assertEqual(20, result["dhrlang"]["capstoneCompletion"]["denominator"])
        self.assertEqual(12, result["dhrlang"]["aiOffTransfer"]["denominator"])
        self.assertEqual(9, result["dhrlang"]["aiOffTransfer"]["numerator"])
        records[8]["transferOutcome"] = "failed"
        self.assertFalse(qualification.summarize(data(records))["numericalLearnerThresholdsMet"])

    def test_late_unsupported_and_missing_setups_remain_in_denominator(self):
        records = [participant(1, firstCorrectRunSeconds=601),
                   participant(2, firstCorrectRunSeconds=None),
                   participant(3, supportedSetup=False), participant(4)]
        result = qualification.summarize(data(records))["dhrlang"]
        self.assertEqual(1, result["setupWithin600Seconds"]["numerator"])
        self.assertEqual(4, result["setupWithin600Seconds"]["denominator"])

    def test_setup_and_capstone_cannot_pass_one_below_required_counts(self):
        records = [participant(i) for i in range(1, 21)]
        for p in records[15:]:
            p["firstCorrectRunSeconds"] = None
        result = qualification.summarize(data(records))
        self.assertFalse(result["dhrlang"]["setupWithin600Seconds"]["met"])
        self.assertFalse(result["numericalLearnerThresholdsMet"])
        for p in records:
            p["firstCorrectRunSeconds"] = 600
        for p in records[11:]:
            p.update(capstoneCompleted=False, transferAttempted=False,
                     transferOutcome="missing", aiOff=False)
        result = qualification.summarize(data(records))
        self.assertFalse(result["dhrlang"]["capstoneCompletion"]["met"])
        self.assertFalse(result["numericalLearnerThresholdsMet"])

    def test_empty_and_small_cohorts_never_meet_enrollment(self):
        for records in ([], [participant(i) for i in range(1, 20)]):
            self.assertFalse(qualification.summarize(data(records))["numericalLearnerThresholdsMet"])
        self.assertFalse(qualification.summarize(data([]))["dhrlang"]["aiOffTransfer"]["met"])

    def test_ai_assisted_and_unattempted_transfer_do_not_count_as_success(self):
        records = [participant(1, aiOff=False),
                   participant(2, transferAttempted=False, transferOutcome="missing", aiOff=False)]
        transfer = qualification.summarize(data(records))["dhrlang"]["aiOffTransfer"]
        self.assertEqual(0, transfer["numerator"])
        self.assertEqual(2, transfer["denominator"])

    def test_baseline_is_separate_and_cannot_satisfy_dhrlang_enrollment(self):
        records = [participant(i, group="baseline") for i in range(1, 21)]
        result = qualification.summarize(data(records))
        self.assertEqual(20, result["baseline"]["enrolled"])
        self.assertEqual(0, result["dhrlang"]["enrolled"])
        self.assertFalse(result["numericalLearnerThresholdsMet"])
        self.assertFalse(result["baselineComparabilityVerified"])

    def test_totals_never_certify_participants_or_release_and_omit_ids(self):
        result = qualification.summarize(data([participant(i) for i in range(1, 21)]))
        self.assertTrue(result["numericalLearnerThresholdsMet"])
        for key in ("recordAuthenticityVerified", "participantIndependenceVerified", "releaseAuthorized"):
            self.assertFalse(result[key])
        self.assertNotIn("P-0001", json.dumps(result))

    def test_rejects_duplicates_nonconsent_inconsistent_outcomes_and_extra_fields(self):
        invalid = [
            data([participant(1), participant(1)]),
            data([participant(1, consented=False)]),
            data([participant(1, transferAttempted=False)]),
            data([participant(1, capstoneCompleted=None)]),
            data([participant(1, email="private@example.invalid")]),
            data([participant(1, transferOutcome=[])]),
        ]
        for value in invalid:
            with self.subTest(value=value), self.assertRaises((ValueError, TypeError)):
                qualification.summarize(value)

    def test_requires_exact_time_and_boolean_types(self):
        for seconds in (True, 599.9, "600", -1):
            with self.subTest(seconds=seconds), self.assertRaises(ValueError):
                qualification.summarize(data([participant(1, firstCorrectRunSeconds=seconds)]))
        for key in ("consented", "supportedSetup", "capstoneCompleted", "transferAttempted", "aiOff"):
            with self.subTest(key=key), self.assertRaises(ValueError):
                qualification.summarize(data([participant(1, **{key: 1})]))
        value = data([])
        value["schemaVersion"] = True
        with self.assertRaises(ValueError):
            qualification.summarize(value)

    def test_cli_creates_only_new_output_and_rejects_invalid_json(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            records = root / "records.json"
            output = root / "aggregate.json"
            records.write_text(json.dumps(data([participant(1)])), encoding="utf-8")
            command = [sys.executable, str(SCRIPT), "--records", str(records), "--output", str(output)]
            first = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(0, first.returncode, first.stderr)
            original = output.read_bytes()
            second = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(2, second.returncode)
            self.assertEqual(original, output.read_bytes())
            output.unlink()
            for raw in ('{"schemaVersion":1,"schemaVersion":1,"participants":[]}',
                        '{"schemaVersion":1,"participants":NaN}', '{} trailing'):
                records.write_text(raw, encoding="utf-8")
                failure = subprocess.run(command, capture_output=True, text=True)
                self.assertEqual(2, failure.returncode, failure.stderr)
                self.assertFalse(output.exists())

    def test_reader_caps_input_and_participant_count(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "oversized.json"
            path.write_bytes(b" " * (qualification.MAX_BYTES + 1))
            with self.assertRaises(ValueError):
                qualification.read_records(path)
        with self.assertRaises(ValueError):
            qualification.summarize(data([participant(1)] * 10001))

    def test_portable_script_needs_no_repository_and_false_threshold_is_not_cli_error(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            standalone = root / "qualification.py"
            standalone.write_bytes(SCRIPT.read_bytes())
            records = root / "outcomes.json"
            output = root / "aggregate.json"
            records.write_text(json.dumps(data([])), encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(standalone), "--records", str(records), "--output", str(output)],
                capture_output=True, text=True, cwd=root)
            self.assertEqual(0, result.returncode, result.stderr)
            aggregate = json.loads(output.read_text(encoding="utf-8"))
            self.assertFalse(aggregate["numericalLearnerThresholdsMet"])
            self.assertFalse(aggregate["releaseAuthorized"])
