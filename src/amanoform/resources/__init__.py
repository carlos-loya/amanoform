"""Amanoform resource handlers.

Each resource type corresponds to a set of manual browser interactions
required to provision that resource through the AWS Console.

To add support for a new resource type, create a module in this package
that implements the ResourceHandler protocol.
"""

from amanoform.resources.base import ResourceHandler
from amanoform.resources.ec2 import EC2InstanceHandler
from amanoform.resources.rds import RDSInstanceHandler

_HANDLERS: dict[str, ResourceHandler] = {
    "af_ec2_instance": EC2InstanceHandler(),
    "af_rds_instance": RDSInstanceHandler(),
}


def get_resource_handler(resource_type: str) -> ResourceHandler | None:
    """Look up the manual workflow handler for a given resource type."""
    return _HANDLERS.get(resource_type)
