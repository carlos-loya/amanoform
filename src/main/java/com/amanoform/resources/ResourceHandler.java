package com.amanoform.resources;

import com.amanoform.provider.AWSManagementConsoleSessionProvider;

import java.util.List;
import java.util.Map;

/**
 * Interface defining the contract for manual resource provisioning handlers.
 *
 * <p>Each implementation encapsulates the browser automation workflow required
 * to provision a specific AWS resource type through the AWS Management Console.
 * Think of it as a very detailed set of instructions for clicking through the
 * console — because that's exactly what it is.</p>
 *
 * <p>In Python, this was a Protocol with three methods. In Java, it is an
 * interface with three methods, a package declaration, two import statements,
 * and this Javadoc block. The methods are functionally identical. The ceremony
 * is not.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
public interface ResourceHandler {

    /**
     * Provision the resource by navigating the AWS Management Console.
     *
     * <p>The implementation should open the appropriate console page, fill out
     * the required form fields, click the appropriate buttons, and extract
     * any output identifiers (such as instance IDs or ARNs) from the
     * resulting success page.</p>
     *
     * @param provider the authenticated browser session provider
     * @param attributes resource attributes from the configuration file
     * @return a map of output attributes (e.g., instance_id, arn, status)
     */
    Map<String, Object> create(AWSManagementConsoleSessionProvider provider,
                                Map<String, Object> attributes);

    /**
     * Terminate the resource through the AWS Management Console.
     *
     * <p>The implementation should navigate to the resource, locate the
     * appropriate termination or deletion controls, and confirm the
     * destructive action.</p>
     *
     * @param provider the authenticated browser session provider
     * @param resourceData the resource's current state data, including identifiers
     */
    void destroy(AWSManagementConsoleSessionProvider provider,
                 Map<String, Object> resourceData);

    /**
     * Compare existing state with desired configuration to detect drift.
     *
     * <p>In a perfect world, we'd take a screenshot of the console and use
     * computer vision to detect drift. For now, we compare attributes.</p>
     *
     * @param existing current state from the state file
     * @param desired desired state from the configuration
     * @return a list of attribute names that have drifted
     */
    List<String> detectDrift(Map<String, Object> existing, Map<String, Object> desired);
}
