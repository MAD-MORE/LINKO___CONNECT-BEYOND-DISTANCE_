#!/usr/bin/env python3
"""Fast Kotlin structural preflight for LINKO CI."""
from __future__ import annotations

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app" / "src" / "main" / "java"
PRIVATE_DECL = re.compile(r"^\s+private\s+(?:fun|val|var|class|object|interface|typealias)\b")
EXPR_FUN = re.compile(r"\bfun\s+[A-Za-z_][A-Za-z0-9_]*\s*\([^)]*\)\s*(?::[^=\{\n]+)?=\s*")
FUN_DECL = re.compile(r"\bfun\s+[A-Za-z_][A-Za-z0-9_]*\s*\(")


def sanitize(line: str, state: dict[str, bool]) -> str:
    out: list[str] = []
    i = 0
    in_block = state.get("block", False)
    in_string = state.get("string", False)
    triple = state.get("triple", False)
    while i < len(line):
        if in_block:
            end = line.find("*/", i)
            if end < 0:
                i = len(line)
                break
            in_block = False
            i = end + 2
            continue
        if triple:
            end = line.find('"""', i)
            if end < 0:
                i = len(line)
                break
            triple = False
            in_string = False
            i = end + 3
            continue
        if in_string:
            if line[i] == "\\":
                i += 2
                continue
            if line[i] == '"':
                in_string = False
            i += 1
            continue
        if line.startswith("//", i):
            break
        if line.startswith("/*", i):
            in_block = True
            i += 2
            continue
        if line.startswith('"""', i):
            triple = True
            in_string = True
            i += 3
            continue
        if line[i] == '"':
            in_string = True
            i += 1
            continue
        if line[i] == "'":
            j = i + 1
            while j < len(line):
                if line[j] == "\\":
                    j += 2
                    continue
                if line[j] == "'":
                    j += 1
                    break
                j += 1
            i = j
            out.append(" ")
            continue
        out.append(line[i])
        i += 1
    state["block"] = in_block
    state["string"] = in_string
    state["triple"] = triple
    return "".join(out)


def delta(clean: str) -> int:
    return clean.count("{") - clean.count("}")


def check_expression_body(lines: list[str], start: int) -> int | None:
    state: dict[str, bool] = {}
    header = sanitize(lines[start], state)
    match = EXPR_FUN.search(header)
    if not match:
        return None
    body = header[match.end():].strip()
    if "{" not in body and not body.startswith(("try", "run", "with", "runCatching")):
        return None
    depth = delta(body)
    for idx in range(start + 1, len(lines)):
        clean = sanitize(lines[idx], {})
        if re.search(r"\breturn\b", clean):
            return idx + 1
        depth += delta(clean)
        if depth <= 0:
            break
    return None


def check_file(path: Path) -> list[str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    state: dict[str, bool] = {}
    cleans = [sanitize(line, state) for line in lines]
    errors: list[str] = []
    depth = 0
    function_body_depths: list[int] = []

    for number, clean in enumerate(cleans, 1):
        before = depth

        # A private declaration is invalid when it is nested in a function,
        # but perfectly valid as a class member. Track function-body depth
        # separately from ordinary class/object braces.
        function_body_depths[:] = [d for d in function_body_depths if before >= d]
        if PRIVATE_DECL.match(lines[number - 1]) and function_body_depths:
            errors.append(f"{path.relative_to(ROOT)}:{number}: private declaration nested inside a function")

        if EXPR_FUN.search(clean):
            bad = check_expression_body(lines, number - 1)
            if bad is not None:
                errors.append(f"{path.relative_to(ROOT)}:{bad}: return inside expression-body function declared near line {number}")

        depth += delta(clean)
        if depth < 0:
            errors.append(f"{path.relative_to(ROOT)}:{number}: unexpected closing brace")
            depth = 0

        if FUN_DECL.search(clean) and "{" in clean:
            # Only keep an open function body. One-line functions that close
            # on the same line do not create a nested scope.
            if depth > before:
                function_body_depths.append(before + 1)

    if depth != 0:
        errors.append(f"{path.relative_to(ROOT)}: unbalanced braces (depth {depth})")
    if state.get("block") or state.get("string") or state.get("triple"):
        errors.append(f"{path.relative_to(ROOT)}: unterminated comment/string literal")
    return errors


def main() -> int:
    files = sorted(SOURCE.rglob("*.kt"))
    failures = [error for path in files for error in check_file(path)]
    if failures:
        print("Kotlin preflight FAILED")
        print("\n".join(f"- {item}" for item in failures))
        return 1
    print(f"Kotlin preflight PASSED: {len(files)} Kotlin files checked")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
