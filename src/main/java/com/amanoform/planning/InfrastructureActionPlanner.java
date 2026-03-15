package com.amanoform.planning;

import com.amanoform.resources.ResourceHandler;
import com.amanoform.resources.ResourceHandlerRegistryFactory;
import com.amanoform.state.AmanoformInfrastructureStateManager;
import com.amanoform.util.ConsoleOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compares desired infrastructure configuration against current state
 * and produces a list of planned actions.
 *
 * <p>This class encapsulates the core planning logic that determines which
 * resources need to be created, updated, or destroyed through manual
 * browser interaction with the AWS Management Console.</p>
 *
 * <p>In the Python implementation, this was a single function called
 * {@code _build_actions}. In Java, it is a class with a static method,
 * because functions cannot exist outside of classes. This is considered
 * a feature of the language.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
public class InfrastructureActionPlanner {

    /** Prevent instantiation. */
    private InfrastructureActionPlanner() {}

    /**
     * Build the list of infrastructure actions by comparing configuration
     * against the current state file.
     *
     * @param config the parsed Amanoform configuration
     * @param state the current infrastructure state manager
     * @param targets optional set of resource keys to filter by
     * @return a list of planned actions to execute
     */
    @SuppressWarnings("unchecked")
    public static List<PlannedAction> buildActions(
            Map<String, Object> config,
            AmanoformInfrastructureStateManager state,
            Set<String> targets) {

        List<PlannedAction> actions = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        Map<String, Map<String, Map<String, Object>>> resources =
                (Map<String, Map<String, Map<String, Object>>>) config.getOrDefault(
                        "resource", Collections.emptyMap());

        for (Map.Entry<String, Map<String, Map<String, Object>>> typeEntry : resources.entrySet()) {
            String resourceType = typeEntry.getKey();
            ResourceHandler handler = ResourceHandlerRegistryFactory.getHandler(resourceType);

            if (handler == null) {
                ConsoleOutput.printRed("  Error: No manual workflow defined for resource type \""
                        + resourceType + "\". Cannot proceed.");
                continue;
            }

            for (Map.Entry<String, Map<String, Object>> resourceEntry : typeEntry.getValue().entrySet()) {
                String name = resourceEntry.getKey();
                Map<String, Object> attrs = resourceEntry.getValue();
                String resourceKey = resourceType + "." + name;
                seenKeys.add(resourceKey);

                if (!targets.isEmpty() && !targets.contains(resourceKey)) {
                    continue;
                }

                Map<String, Object> existing = state.getResource(resourceKey);

                if (existing == null) {
                    actions.add(new PlannedAction("create", resourceKey, resourceType, attrs));
                } else {
                    List<String> changes = handler.detectDrift(existing, attrs);
                    if (!changes.isEmpty()) {
                        actions.add(new PlannedAction("update", resourceKey, resourceType, attrs));
                    }
                }
            }
        }

        // Resources in state but no longer in config should be destroyed
        for (String resourceKey : state.listResources()) {
            if (seenKeys.contains(resourceKey)) {
                continue;
            }
            if (!targets.isEmpty() && !targets.contains(resourceKey)) {
                continue;
            }

            String resourceType = resourceKey.split("\\.")[0];
            Map<String, Object> resourceData = state.getResource(resourceKey);
            actions.add(new PlannedAction("destroy", resourceKey, resourceType, resourceData));
        }

        return actions;
    }
}
