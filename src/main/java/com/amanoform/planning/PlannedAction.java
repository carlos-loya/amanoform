package com.amanoform.planning;

import java.util.Map;

/**
 * Represents a single infrastructure action to be performed through
 * the AWS Management Console.
 *
 * <p>In Python, this was a tuple. In Java, it is a record with a
 * package declaration, an import statement, a Javadoc block, and
 * four explicitly typed fields. Progress.</p>
 *
 * @param actionType the type of action: "create", "update", or "destroy"
 * @param resourceKey the fully qualified resource identifier (e.g., "af_ec2_instance.web")
 * @param resourceType the resource type (e.g., "af_ec2_instance")
 * @param attributes the resource attributes from config or state
 */
public record PlannedAction(
    String actionType,
    String resourceKey,
    String resourceType,
    Map<String, Object> attributes
) {}
