"""Base protocol for Amanoform resource handlers.

Each handler encapsulates the browser automation workflow for a specific
AWS resource type. Think of it as a very detailed set of instructions
for clicking through the console — because that's exactly what it is.
"""

from typing import Protocol

from amanoform.provider import AWSConsoleProvider


class ResourceHandler(Protocol):
    """Protocol that all manual resource handlers must implement."""

    def create(self, provider: AWSConsoleProvider, attrs: dict) -> dict:
        """Provision the resource by navigating the AWS Console.

        Args:
            provider: The authenticated browser session.
            attrs: Resource attributes from the configuration file.

        Returns:
            A dict of output attributes (e.g., instance_id, arn).
        """
        ...

    def destroy(self, provider: AWSConsoleProvider, resource_data: dict) -> None:
        """Terminate the resource through the AWS Console.

        Args:
            provider: The authenticated browser session.
            resource_data: The resource's state data, including identifiers.
        """
        ...

    def detect_drift(self, existing: dict, desired: dict) -> list[str]:
        """Compare existing state with desired configuration.

        Args:
            existing: Current state from the state file.
            desired: Desired state from the configuration.

        Returns:
            A list of attribute names that have drifted.
        """
        ...
