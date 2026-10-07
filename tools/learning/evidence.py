"""Prepare a local, redacted tutor handoff; never execute source or call a model."""

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "src" / "main" / "resources" / "dhrlang" / "learn" / "exercises.json"
MAX_BYTES = 2 * 1024 * 1024
STATUSES = {
    "SUCCESS", "COMPILE_ERROR", "RUNTIME_ERROR", "INVALID_REQUEST",
    "TIME_LIMIT", "OUTPUT_LIMIT", "WORKER_ERROR",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def fields(value, names):
    require(type(value) is dict and set(value) == set(names.split()),
            "Object fields must be exactly: " + names)


def text(value, maximum=4096):
    require(type(value) is str and 0 < len(value) <= maximum, "Invalid bounded text")


def integer(value, minimum, maximum):
    require(type(value) is int and minimum <= value <= maximum, "Invalid exact integer")


def digest(value):
    require(type(value) is str and re.fullmatch(r"[0-9a-f]{64}", value), "Invalid SHA-256")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "Duplicate JSON key")
        result[key] = value
    return result


def reject_constant(value):
    raise ValueError("Non-finite JSON value: " + value)


def read_json(path):
    with Path(path).open("rb") as stream:
        data = stream.read(MAX_BYTES + 1)
    require(len(data) <= MAX_BYTES, "JSON exceeds 2 MiB")
    try:
        value = json.loads(data.decode("utf-8"), object_pairs_hook=unique_object,
                           parse_constant=reject_constant)
        pending = [(value, 0)]
        while pending:
            item, depth = pending.pop()
            require(depth <= 64, "JSON nesting exceeds 64 levels")
            if type(item) is dict:
                pending.extend((child, depth + 1) for child in item.values())
            elif type(item) is list:
                pending.extend((child, depth + 1) for child in item)
        return value
    except RecursionError as error:
        raise ValueError("JSON nesting is too deep") from error


def hash_text(value):
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def validate_report(report, exercise):
    fields(report, "schemaVersion compilerVersion profile exercise sourceSha256 passed total cases transfer")
    integer(report["schemaVersion"], 1, 1)
    text(report["compilerVersion"], 128)
    require(re.fullmatch(r"[0-9][A-Za-z0-9.+_-]*", report["compilerVersion"]),
            "Invalid compiler version metadata")
    require(report["profile"] == "jvm-bytecode-v1", "Unsupported execution profile")
    require(report["exercise"] == exercise["id"], "Exercise identity mismatch")
    digest(report["sourceSha256"])
    require(report["transfer"] == exercise["transfer"], "Transfer task differs from the catalog")
    cases = report["cases"]
    require(type(cases) is list and len(cases) == len(exercise["cases"]), "Case count mismatch")
    integer(report["total"], len(cases), len(cases))
    integer(report["passed"], 0, len(cases))
    count = 0
    for sample, reference in zip(cases, exercise["cases"]):
        fields(sample, "name input passed status expected actual stderr diagnostics message")
        require(all(type(sample[key]) is str and sample[key] == reference[key]
                    for key in ("name", "input", "expected")),
                "Case identity, input or expected output differs from the catalog")
        require(type(sample["passed"]) is bool, "Expected a boolean pass flag")
        require(type(sample["status"]) is str and sample["status"] in STATUSES, "Unknown worker status")
        for key in ("actual", "stderr"):
            require(type(sample[key]) is str and len(sample[key]) <= 4096, "Output exceeds its budget")
        text(sample["message"])
        require(sample["passed"] == (sample["status"] == "SUCCESS"
                and sample["actual"] == sample["expected"] and sample["stderr"] == ""),
                "Pass flag contradicts execution evidence")
        count += sample["passed"]
        diagnostics = sample["diagnostics"]
        require(type(diagnostics) is list and len(diagnostics) <= 50, "Invalid diagnostic budget")
        for diagnostic in diagnostics:
            fields(diagnostic, "severity code message line column")
            text(diagnostic["severity"], 32)
            require(diagnostic["code"] is None or
                    (type(diagnostic["code"]) is str and len(diagnostic["code"]) <= 128),
                    "Invalid diagnostic code")
            text(diagnostic["message"])
            integer(diagnostic["line"], 0, 131072)
            integer(diagnostic["column"], 0, 131072)
    require(count == report["passed"], "Pass total contradicts case evidence")


def tutor_bundle(report, exercise, source, compiler_sha256, level):
    integer(level, 1, 3)
    digest(compiler_sha256)
    validate_report(report, exercise)
    require(hash_text(source) == report["sourceSha256"], "Submission does not match this historical report")
    failures = []
    for index, sample in enumerate(report["cases"]):
        if sample["passed"]:
            continue
        failures.append({
            "reference": "/cases/" + str(index),
            "status": sample["status"],
            "outputMatches": sample["actual"] == sample["expected"],
            "stderrPresent": sample["stderr"] != "",
            "diagnosticPositions": [
                {"reference": "/cases/" + str(index) + "/diagnostics/" + str(number),
                 "line": diagnostic["line"], "column": diagnostic["column"]}
                for number, diagnostic in enumerate(sample["diagnostics"])
            ],
        })
    # A lesson hint is not an explanation for infrastructure or compilation failures.
    hint_available = any(item["status"] == "SUCCESS" and not item["outputMatches"]
                         and not item["stderrPresent"] for item in failures)
    return {
        "schemaVersion": 1,
        "mode": "OFFLINE_TUTOR_HANDOFF",
        "historicalEvidenceOnly": True,
        "reportAuthenticityVerified": False,
        "compilerVersion": report["compilerVersion"],
        "compilerArtifactSha256": compiler_sha256,
        "compilerArtifactIdentityVerified": False,
        "sourceSha256": report["sourceSha256"],
        "profile": report["profile"],
        "exercise": exercise["id"],
        "passed": report["passed"],
        "total": report["total"],
        "failures": failures,
        "guidance": {
            "level": level,
            "catalogHint": exercise["hints"][level - 1] if hint_available else None,
            "rootCauseEstablished": False,
            "nextAction": "Inspect cited execution evidence; recheck any edited program locally."
            if failures else "Predict and test a new case; this report does not prove mastery.",
        },
        "clientPolicy": {
            "tools": [],
            "externalWritesAllowed": False,
            "sourceIncluded": False,
            "reportTextIncluded": False,
            "maximumModelCalls": 1,
            "maximumOutputTokens": 512,
            "maximumWallClockMs": 10000,
            "enforcement": "Export guidance only; external client must enforce its own budgets and allowlist.",
            "verification": "A model response cannot award a pass. Run learn check after any edit.",
        },
    }


def write_new(path, value):
    encoded = (json.dumps(value, indent=2, ensure_ascii=True, allow_nan=False) + "\n").encode("utf-8")
    require(len(encoded) <= MAX_BYTES, "Output exceeds 2 MiB")
    with Path(path).open("xb") as stream:
        stream.write(encoded)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", required=True)
    parser.add_argument("--submission", required=True)
    parser.add_argument("--compiler-sha256", required=True,
                        help="Recorded artifact hash; cannot authenticate a historical compiler")
    parser.add_argument("--level", type=int, choices=(1, 2, 3), default=1)
    parser.add_argument("--output", required=True)
    args = parser.parse_args(argv)
    try:
        report = read_json(args.report)
        require(type(report) is dict, "Expected a grading report object")
        catalog = read_json(CATALOG)
        matches = [item for item in catalog["exercises"] if item["id"] == report.get("exercise")]
        require(len(matches) == 1, "Unknown exercise")
        with Path(args.submission).open("rb") as stream:
            raw = stream.read(512 * 1024 + 1)
        require(len(raw) <= 512 * 1024, "Submission exceeds 512 KiB")
        source = raw.decode("utf-8")
        require(len(source.encode("utf-16-le")) // 2 <= 131072,
                "Submission exceeds source character budget")
        result = tutor_bundle(report, matches[0], source, args.compiler_sha256, args.level)
        write_new(args.output, result)
        print("Created " + args.output + "; no model call, source execution or qualification performed.")
        return 0
    except (OSError, ValueError, TypeError, KeyError) as error:
        print("Evidence error: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
