"""Naming checks shared by the localization validator and its unit tests."""

import re


RESOURCE_KEY_PATTERN = re.compile(r"[a-z][a-z0-9]*(?:_[a-z0-9]+)*")
HASH_COMPONENT_PATTERN = re.compile(r"[0-9a-f]{6,}")

# These English words also happen to consist entirely of hexadecimal letters.
# Keep explicit word exceptions: requiring a digit would miss all-letter hashes.
READABLE_HEX_WORDS = frozenset({
    "accede", "acceded", "baffed", "beaded", "bedded", "beefed", "cabbed",
    "decade", "decaff", "deface", "defaced", "efface", "effaced", "facade",
    "faffed",
})


def resource_key_errors(key: str, location: str) -> list[str]:
    errors = []
    if not RESOURCE_KEY_PATTERN.fullmatch(key):
        errors.append(f"{location}: invalid resource key {key}; use readable snake_case")
    if any(HASH_COMPONENT_PATTERN.fullmatch(part) and part not in READABLE_HEX_WORDS
           for part in key.split("_")):
        errors.append(f"{location}: hash-like resource key {key}; name its UI purpose")
    return errors
