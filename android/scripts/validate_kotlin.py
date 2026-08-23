#!/usr/bin/env python3
"""Fast Kotlin structural preflight for LINKO CI.

This is intentionally a guard, not a replacement for kotlinc. It catches
common edit/merge regressions before the full Android build:
- unbalanced braces while ignoring strings/comments
- `return` inside expression-body functions (`fun x() = ...`)
- top-level-only `private` declarations accidentally nested in a function
"""
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app" / "src" / "main" / "java"

PRIVATE_DECL = re.compile(r"^\s+private\s+(?:fun|val|var|class|object|interface|typealias)\b")
EXPR_FUN = re.compile(r"\bfun\s+[A-Za-z_][A-Za-z0-9_]*\s*\([^)]*\)\s*(?::[^=\{\n]+)?=\s*")


def sanitize(line: str, state: dict[str, bool]) -> str:
    out = []
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
            out.append(" ")
            i = j
            continue
        out.append(line[i])
        i += 1
    state["block"] = in_block
    state["string"] = in_string
    state["triple"] = triple
    return "".join(out)


def check_file(path: Path) -> list[str]:
    errors: list[str] = []
    state: dict[str, bool] = {}
    depth = 0
    lines = path.read_text(encoding="utf-8").splitlines()

    for number, raw in enumerate(lines, 1):
        clean = sanitize(raw, state)
        stripped = clean.strip()

        if stripped and depth == 0 and not raw.startswith((" ", "\t")):
            pass
        if PRIVATE_DECL.match(raw) and depth > 0:
            errors.append(f"{path.relative_to(ROOT)}:{number}: private declaration appears nested inside a block")

        if EXPR_FUN.search(clean):
            for look in range(number, min(number + 25, len(lines))):
                next_clean = sanitize(lines[look], {})
                if re.search(r"\breturn\b", next_clean):
                    errors.append(f"{path.relative_to(ROOT)}:{look + 1}: return inside expression-body function declared near line {number}")
                    break
                if "=" in next_clean and look > number and ("fun " in next_clean or "class " in next_clean):
                    break

        depth += clean.count("{") - clean.count("}")
        if depth < 0:
            errors.append(f"{path.relative_to(ROOT)}:{number}: unexpected closing brace")
            depth = 0

    if depth != 0:
        errors.append(f"{path.relative_to(ROOT)}: unbalanced braces (depth {depth})")
    if state.get("block") or state.get("string") or state.get("triple"):
        errors.append(f"{path.relative_to(ROOT)}: unterminated comment/string literal")
    return errors


def main() -> int:
    files = sorted(SOURCE.rglob("*.kt"))
    failures: list[str] = []
    for path in files:
        failures.extend(check_file(path))
    if failures:
        print("Kotlin preflight FAILED")
        print("\n".join(f"- {item}" for item in failures))
        return 1
    print(f"Kotlin preflight PASSED: {len(files)} Kotlin files checked")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
