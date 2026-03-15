package com.amanoform.resources;

import com.amanoform.resources.ec2.EC2InstanceManualProvisioningHandler;
import com.amanoform.resources.s3.S3BucketManualProvisioningHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry and factory for manual resource provisioning handlers.
 *
 * <p>Maps resource type identifiers to their corresponding handler
 * implementations. In Python, this was a dictionary literal and a
 * function. In Java, it is a class named ResourceHandlerRegistryFactory
 * with a static initializer block, a private constructor, and a
 * static lookup method. The Registry pattern meets the Factory pattern
 * in a class that is both and neither.</p>
 *
 * <p>To add support for a new resource type, instantiate its handler
 * in the static initializer block below and add a mapping entry.
 * Then spend 45 minutes writing the Javadoc.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
public final class ResourceHandlerRegistryFactory {

    private static final Map<String, ResourceHandler> HANDLER_REGISTRY = new HashMap<>();

    static {
        HANDLER_REGISTRY.put("af_ec2_instance", new EC2InstanceManualProvisioningHandler());
        HANDLER_REGISTRY.put("af_s3_bucket", new S3BucketManualProvisioningHandler());
    }

    /** Prevent instantiation of registry factory. */
    private ResourceHandlerRegistryFactory() {
        throw new UnsupportedOperationException(
            "ResourceHandlerRegistryFactory is a static utility class. "
            + "Instantiation is not supported, expected, or desired."
        );
    }

    /**
     * Look up the manual workflow handler for a given resource type.
     *
     * @param resourceType the resource type identifier (e.g., "af_ec2_instance")
     * @return the corresponding handler, or {@code null} if no handler is registered
     */
    public static ResourceHandler getHandler(String resourceType) {
        return HANDLER_REGISTRY.get(resourceType);
    }
}
