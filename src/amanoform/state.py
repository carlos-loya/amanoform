"""Amanoform state management.

Tracks the current state of manually provisioned infrastructure.
State is persisted to a local JSON file, as is tradition.

The state file includes browser session metadata and screenshot
checksums for drift detection via visual inspection.
"""

import json
from pathlib import Path

STATE_FILE = "amanoform.state"


class State:
    """Manages the Amanoform infrastructure state file."""

    def __init__(self, data: dict | None = None):
        self._data = data or {
            "version": 1,
            "serial": 0,
            "provider": "aws-console-manual",
            "resources": {},
        }

    @classmethod
    def load(cls) -> "State":
        """Load state from the state file, or create a fresh state."""
        path = Path(STATE_FILE)
        if path.exists():
            data = json.loads(path.read_text())
            return cls(data)
        return cls()

    def save(self) -> None:
        """Persist state to disk."""
        self._data["serial"] += 1
        path = Path(STATE_FILE)
        path.write_text(json.dumps(self._data, indent=2) + "\n")

    def get_resource(self, key: str) -> dict | None:
        """Retrieve a resource's state by its key (e.g., 'af_ec2_instance.web')."""
        return self._data["resources"].get(key)

    def set_resource(self, key: str, attrs: dict) -> None:
        """Store or update a resource's state."""
        self._data["resources"][key] = attrs

    def remove_resource(self, key: str) -> None:
        """Remove a resource from state."""
        self._data["resources"].pop(key, None)

    def list_resources(self) -> list[str]:
        """List all tracked resource keys."""
        return list(self._data["resources"].keys())
