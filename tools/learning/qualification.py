"""Offline arithmetic for consented pilot records, not qualification certification."""

import argparse
import json
import re
import sys
from pathlib import Path


FIELDS = {
    "id", "group", "consented", "supportedSetup", "firstCorrectRunSeconds",
    "capstoneCompleted", "transferAttempted", "transferOutcome", "aiOff",
}
OUTCOMES = {"passed", "failed", "missing"}
MAX_BYTES = 2 * 1024 * 1024


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key: " + key)
        result[key] = value
    return result


def reject_constant(value):
    raise ValueError("Invalid JSON constant: " + value)


def read_records(path):
    with Path(path).open("rb") as stream:
        raw = stream.read(MAX_BYTES + 1)
    if len(raw) > MAX_BYTES:
        raise ValueError("Pilot record exceeds 2 MiB")
    return json.loads(raw.decode("utf-8"), object_pairs_hook=unique_object,
                      parse_constant=reject_constant)


def validate(data):
    if type(data) is not dict or set(data) != {"schemaVersion", "participants"}:
        raise ValueError("Expected schemaVersion and participants only")
    if type(data["schemaVersion"]) is not int or data["schemaVersion"] != 1:
        raise ValueError("Expected schemaVersion 1")
    participants = data["participants"]
    if type(participants) is not list or len(participants) > 10000:
        raise ValueError("Expected at most 10000 participant records")
    seen = set()
    for record in participants:
        if type(record) is not dict or set(record) != FIELDS:
            raise ValueError("Participant fields do not match the pilot schema")
        identifier = record["id"]
        if type(identifier) is not str or not re.fullmatch(r"P-[0-9]{4,8}", identifier):
            raise ValueError("Use a pseudonymous P-0001-style identifier")
        if identifier in seen:
            raise ValueError("Duplicate participant identifier")
        seen.add(identifier)
        if record["group"] not in ("dhrlang", "baseline"):
            raise ValueError("Expected dhrlang or baseline group")
        for field in ("consented", "supportedSetup", "transferAttempted", "aiOff"):
            if type(record[field]) is not bool:
                raise ValueError(field + " must be a JSON boolean")
        if not record["consented"]:
            raise ValueError("Do not process records without consent")
        seconds = record["firstCorrectRunSeconds"]
        if seconds is not None and (type(seconds) is not int or seconds < 0):
            raise ValueError("Time must be non-negative integer seconds or null")
        if record["capstoneCompleted"] is not None and type(record["capstoneCompleted"]) is not bool:
            raise ValueError("capstoneCompleted must be boolean or null")
        outcome = record["transferOutcome"]
        if type(outcome) is not str or outcome not in OUTCOMES:
            raise ValueError("Expected passed, failed or missing transfer outcome")
        if record["transferAttempted"] and record["capstoneCompleted"] is not True:
            raise ValueError("Transfer attempts require capstone completion")
        if outcome != "missing" and not record["transferAttempted"]:
            raise ValueError("Transfer result requires an attempt")
        if record["aiOff"] and not record["transferAttempted"]:
            raise ValueError("AI-off observation requires a transfer attempt")
    return participants


def ratio(numerator, denominator, minimum_percent):
    return {
        "numerator": numerator,
        "denominator": denominator,
        "minimumPercent": minimum_percent,
        "met": denominator > 0 and numerator * 100 >= denominator * minimum_percent,
    }


def cohort(records):
    completers = [p for p in records if p["capstoneCompleted"] is True]
    timely = sum(p["supportedSetup"] and p["firstCorrectRunSeconds"] is not None
                 and p["firstCorrectRunSeconds"] <= 600 for p in records)
    transfer = sum(p["transferOutcome"] == "passed" and p["aiOff"] for p in completers)
    return {
        "enrolled": len(records),
        "missingFirstRun": sum(p["firstCorrectRunSeconds"] is None for p in records),
        "missingCapstone": sum(p["capstoneCompleted"] is None for p in records),
        "unsupportedSetups": sum(not p["supportedSetup"] for p in records),
        "missingOrUnattemptedTransfer": sum(p["transferOutcome"] == "missing" for p in completers),
        "nonAiOffTransferAttempts": sum(p["transferAttempted"] and not p["aiOff"] for p in completers),
        "setupWithin600Seconds": ratio(timely, len(records), 80),
        "capstoneCompletion": ratio(len(completers), len(records), 60),
        "aiOffTransfer": ratio(transfer, len(completers), 70),
    }


def summarize(data):
    records = validate(data)
    dhrlang = cohort([p for p in records if p["group"] == "dhrlang"])
    baseline = cohort([p for p in records if p["group"] == "baseline"])
    enough = dhrlang["enrolled"] >= 20
    return {
        "schemaVersion": 1,
        "kind": "SELF_REPORTED_PILOT_ARITHMETIC",
        "minimumDhrlangEnrollment": {"required": 20, "observed": dhrlang["enrolled"], "met": enough},
        "dhrlang": dhrlang,
        "baseline": baseline,
        "numericalLearnerThresholdsMet": enough and all(dhrlang[key]["met"] for key in (
            "setupWithin600Seconds", "capstoneCompletion", "aiOffTransfer")),
        "recordAuthenticityVerified": False,
        "participantIndependenceVerified": False,
        "baselineComparabilityVerified": False,
        "releaseAuthorized": False,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--records", required=True, help="Explicit local consented pilot JSON")
    parser.add_argument("--output", required=True, help="New aggregate JSON file; never overwritten")
    args = parser.parse_args(argv)
    try:
        result = summarize(read_records(args.records))
        encoded = json.dumps(result, indent=2) + "\n"
        with Path(args.output).open("x", encoding="utf-8") as stream:
            stream.write(encoded)
        print("Aggregate written; arithmetic is not independent qualification or release approval.")
        return 0
    except (OSError, ValueError, TypeError, RecursionError) as error:
        print("Qualification error: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
