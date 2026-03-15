package com.amanoform.cli;

import com.amanoform.AmanoformApplication;
import com.amanoform.config.AmanoformConfigurationParser;
import com.amanoform.planning.InfrastructureActionPlanner;
import com.amanoform.planning.PlannedAction;
import com.amanoform.provider.AWSManagementConsoleSessionProvider;
import com.amanoform.resources.ResourceHandler;
import com.amanoform.resources.ResourceHandlerRegistryFactory;
import com.amanoform.state.AmanoformInfrastructureStateManager;
import com.amanoform.util.ConsoleOutput;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * Subcommand: {@code amanoform apply}
 *
 * <p>Builds or changes infrastructure by manually operating the AWS
 * Management Console. Amanoform will open a browser session, navigate
 * to the appropriate AWS Console pages, and perform the required click
 * sequences to provision your infrastructure.</p>
 *
 * <p>This is the longest class in the Amanoform codebase, weighing in
 * at approximately 200 lines. The equivalent Python function was about
 * 100 lines. The Java version includes explicit type declarations for
 * every variable, a Scanner for user confirmation (because Java doesn't
 * have a one-liner for "ask yes/no"), and try-catch blocks around every
 * operation that might throw a checked exception.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
@Command(
    name = "apply",
    description = "Build or change infrastructure by manually operating the AWS Console.%n%n"
            + "Amanoform will open a browser session, navigate to the appropriate%n"
            + "AWS Console pages, and perform the required click sequences to%n"
            + "provision your infrastructure. Resources removed from config will%n"
            + "be terminated through the console."
)
public class ApplyCommand implements Runnable {

    @Option(names = {"-c", "--config"}, defaultValue = "main.af",
            description = "Path to Amanoform configuration file.")
    private String config;

    @Option(names = {"--auto-approve"},
            description = "Skip interactive approval of the plan.")
    private boolean autoApprove;

    @Option(names = {"-t", "--target"},
            description = "Target specific resources (e.g., af_ec2_instance.web).")
    private String[] targets;

    @Option(names = {"--headless"}, defaultValue = "true", negatable = true,
            description = "Run browser in headless mode (default: true).")
    private boolean headless;

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        System.out.println();
        ConsoleOutput.printBold("Amanoform v" + AmanoformApplication.VERSION);
        System.out.println();

        Map<String, Object> cfg = AmanoformConfigurationParser.loadConfig(config);
        AmanoformInfrastructureStateManager state = AmanoformInfrastructureStateManager.load();

        Map<String, Object> providerCfg = (Map<String, Object>) ((Map<String, Object>) cfg
                .getOrDefault("provider", Map.of()))
                .getOrDefault("aws", Map.of());
        String region = providerCfg.getOrDefault("region", "us-east-1").toString();

        Set<String> targetSet = new HashSet<>();
        if (targets != null) {
            for (String target : targets) {
                targetSet.add(target);
            }
        }

        List<PlannedAction> actions = InfrastructureActionPlanner.buildActions(cfg, state, targetSet);

        if (actions.isEmpty()) {
            ConsoleOutput.printGreen("No changes. Infrastructure is up to date.");
            System.out.println();
            return;
        }

        ConsoleOutput.print("Amanoform will perform the following actions:");
        System.out.println();

        for (PlannedAction action : actions) {
            ConsoleOutput.printAction(action.actionType(), action.resourceKey(), null);
        }

        int[] counts = countActions(actions);
        ConsoleOutput.printPlanSummary(counts[0], counts[1], counts[2]);

        if (!autoApprove) {
            System.out.print("Do you want to perform these actions? (y/N): ");
            Scanner scanner = new Scanner(System.in);
            String response = scanner.nextLine().trim().toLowerCase();
            if (!response.equals("y") && !response.equals("yes")) {
                System.out.println();
                ConsoleOutput.printYellow("Apply cancelled.");
                System.out.println();
                return;
            }
        }

        System.out.println();
        ConsoleOutput.printBold("Opening browser session to AWS Console...");
        System.out.println();

        AWSManagementConsoleSessionProvider provider =
                new AWSManagementConsoleSessionProvider(region, headless);
        provider.login();

        Map<String, Integer> succeeded = new HashMap<>();
        succeeded.put("create", 0);
        succeeded.put("update", 0);
        succeeded.put("destroy", 0);
        List<String> failed = new ArrayList<>();

        for (PlannedAction action : actions) {
            ResourceHandler handler = ResourceHandlerRegistryFactory.getHandler(action.resourceType());

            if (handler == null) {
                ConsoleOutput.printRed("  Error: No handler for " + action.resourceType()
                        + ", skipping " + action.resourceKey() + ".");
                failed.add(action.resourceKey());
                continue;
            }

            try {
                switch (action.actionType()) {
                    case "create" -> {
                        ConsoleOutput.printGreen("  Creating " + action.resourceKey()
                                + "... (navigating to console)");
                        Map<String, Object> result = handler.create(provider, action.attributes());
                        Map<String, Object> merged = new HashMap<>(action.attributes());
                        merged.putAll(result);
                        state.setResource(action.resourceKey(), merged);
                        state.save();
                        ConsoleOutput.printGreen("  Created " + action.resourceKey() + ": " + result);
                    }

                    case "update" -> {
                        ConsoleOutput.printYellow("  Updating " + action.resourceKey()
                                + "... (navigating to console)");
                        Map<String, Object> existing = state.getResource(action.resourceKey());
                        handler.destroy(provider, existing);
                        Map<String, Object> result = handler.create(provider, action.attributes());
                        Map<String, Object> merged = new HashMap<>(action.attributes());
                        merged.putAll(result);
                        state.setResource(action.resourceKey(), merged);
                        state.save();
                        ConsoleOutput.printYellow("  Updated " + action.resourceKey()
                                + " (destroyed and recreated — the manual way)");
                    }

                    case "destroy" -> {
                        ConsoleOutput.printRed("  Destroying " + action.resourceKey()
                                + "... (navigating to console)");
                        handler.destroy(provider, action.attributes());
                        state.removeResource(action.resourceKey());
                        state.save();
                        ConsoleOutput.printRed("  Destroyed " + action.resourceKey());
                    }
                }

                succeeded.merge(action.actionType(), 1, Integer::sum);

            } catch (Exception e) {
                System.out.println();
                ConsoleOutput.printBoldRed("  Error operating on " + action.resourceKey()
                        + ": " + e.getMessage());
                ConsoleOutput.print("  The browser may have encountered an unexpected console state.");
                ConsoleOutput.print("  State file has been preserved up to the last successful operation.");
                failed.add(action.resourceKey());
                System.out.println();
                ConsoleOutput.printYellow("  Continuing with remaining resources...");
                System.out.println();
            }
        }

        provider.close();

        System.out.println();
        ConsoleOutput.printBoldGreen("Apply complete! Resources: "
                + succeeded.get("create") + " added, "
                + succeeded.get("update") + " changed, "
                + succeeded.get("destroy") + " destroyed.");

        if (!failed.isEmpty()) {
            ConsoleOutput.printBoldRed(failed.size() + " resource(s) failed:");
            for (String key : failed) {
                ConsoleOutput.print("  - " + key);
            }
            System.out.println();
            ConsoleOutput.print("Run \"amanoform plan\" to see the current state "
                    + "and retry the failed operations.");
            System.out.println();
            System.exit(1);
        }

        System.out.println();
    }

    private int[] countActions(List<PlannedAction> actions) {
        int create = 0, update = 0, destroy = 0;
        for (PlannedAction action : actions) {
            switch (action.actionType()) {
                case "create" -> create++;
                case "update" -> update++;
                case "destroy" -> destroy++;
            }
        }
        return new int[]{create, update, destroy};
    }
}
