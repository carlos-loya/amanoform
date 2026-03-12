"""Configuration parser for Amanoform .af files.

Amanoform uses a simplified HCL-inspired configuration format.
This is intentionally not HCL — we believe in doing things by hand,
and that includes writing our own parser.
"""

import re
from pathlib import Path


def load_config(path: str) -> dict:
    """Load and parse an Amanoform configuration file.

    Args:
        path: Path to the .af configuration file.

    Returns:
        Parsed configuration as a nested dictionary.
    """
    config_path = Path(path)
    if not config_path.exists():
        raise FileNotFoundError(
            'Configuration file "{path}" not found.\n'
            "Have you created your Amanoform configuration? "
            "See documentation for examples.".format(path=path)
        )

    text = config_path.read_text()
    return _parse(text)


def _parse(text: str) -> dict:
    """Parse the Amanoform configuration format.

    Supports blocks like:
        provider "aws" { ... }
        resource "af_ec2_instance" "name" { ... }
    """
    result = {}
    text = _strip_comments(text)
    pos = 0

    while pos < len(text):
        # Skip whitespace
        match = re.match(r"\s+", text[pos:])
        if match:
            pos += match.end()
            continue

        if pos >= len(text):
            break

        # Match block: keyword "label1" ["label2"] { ... }
        block_match = re.match(
            r'(\w+)\s+"([^"]+)"(?:\s+"([^"]+)")?\s*\{', text[pos:]
        )
        if block_match:
            keyword = block_match.group(1)
            label1 = block_match.group(2)
            label2 = block_match.group(3)
            pos += block_match.end()

            body, end_pos = _extract_block(text, pos)
            pos = end_pos

            attrs = _parse_attributes(body)

            if keyword not in result:
                result[keyword] = {}

            if label2:
                if label1 not in result[keyword]:
                    result[keyword][label1] = {}
                result[keyword][label1][label2] = attrs
            else:
                result[keyword][label1] = attrs

            continue

        # If we can't parse anything, skip a character
        pos += 1

    return result


def _strip_comments(text: str) -> str:
    """Remove single-line comments (# and //)."""
    lines = []
    for line in text.split("\n"):
        # Remove comments (but not inside strings)
        in_string = False
        result_chars = []
        i = 0
        while i < len(line):
            c = line[i]
            if c == '"' and (i == 0 or line[i - 1] != "\\"):
                in_string = not in_string
                result_chars.append(c)
            elif not in_string and c == "#":
                break
            elif not in_string and c == "/" and i + 1 < len(line) and line[i + 1] == "/":
                break
            else:
                result_chars.append(c)
            i += 1
        lines.append("".join(result_chars))
    return "\n".join(lines)


def _extract_block(text: str, pos: int) -> tuple[str, int]:
    """Extract content between matched braces, starting after the opening brace."""
    depth = 1
    start = pos
    while pos < len(text) and depth > 0:
        if text[pos] == "{":
            depth += 1
        elif text[pos] == "}":
            depth -= 1
        pos += 1
    return text[start : pos - 1], pos


def _parse_attributes(body: str) -> dict:
    """Parse key = value pairs from a block body."""
    attrs = {}
    for line in body.split("\n"):
        line = line.strip()
        if not line:
            continue

        match = re.match(r"(\w+)\s*=\s*(.+)", line)
        if match:
            key = match.group(1)
            value = _parse_value(match.group(2).strip())
            attrs[key] = value

    return attrs


def _parse_value(value: str) -> str | int | float | bool:
    """Parse a configuration value."""
    # String
    if value.startswith('"') and value.endswith('"'):
        return value[1:-1]

    # Boolean
    if value == "true":
        return True
    if value == "false":
        return False

    # Integer
    try:
        return int(value)
    except ValueError:
        pass

    # Float
    try:
        return float(value)
    except ValueError:
        pass

    return value
