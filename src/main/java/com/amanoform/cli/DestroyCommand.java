package com.amanoform.cli;

import com.amanoform.AmanoformApplication;
import com.amanoform.config.AmanoformConfigurationParser;
import com.amanoform.provider.AWSManagementConsoleSessionProvider;
import com.amanoform.resources.ResourceHandler;
import com.amanoform.resources.ResourceHandlerRegistryFactory;
import com.amanoform.state.AmanoformInfrastructureStateManager;
import com.amanoform.util.ConsoleOutput;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Subcommand: {@code amanoform destroy}
 *
 * <p>Destroys Amanoform-managed infrastructure by navigating to each
 * managed resource in the AWS Management Console and performing the
 * manual termination sequence.</p>
 *
 * <p>It's like watching someone clean up after a demo, but nobody's
 * at the keyboard.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
@Command(
    name = "destroy",
    description = "Destroy Amanoform-managed infrastructure.%n%n"
            + "Navigates to each managed resource in the AWS Console and performs%n"
            + "the manual termination sequence. Use --target to destroy specific%n"
            + "resources, or run without targets to destroy everything."
)
public class DestroyCommand implements Runnable {

    @Option(names = {"-c", "--config"}, defaultValue = "main.af",
            description = "Path to Amanoform configuration file.")
    private String config;

    @Option(names = {"--auto-approve"},
            description = "Skip interactive approval.")
    private boolean autoApprove;

    @Option(names = {"-t", "--target"},
            description = "Target specific resources to destroy.")
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

        List<String> allResources = state.listResources();
        List<String> resourcesToDestroy;

        if (targets != null && targets.length > 0) {
            List<String> targetList = List.of(targets);
            resourcesToDestroy = allResources.stream()
                    .filter(targetList::contains)
                    .collect(Collectors.toList());

            for (String target : targetList) {
                if (!allResources.contains(target)) {
                    ConsoleOutput.printYellow("  Warning: " + target
                            + " is not in the state file, skipping.");
                }
            }
        } else {
            resourcesToDestroy = new ArrayList<>(allResources);
        }

        if (resourcesToDestroy.isEmpty()) {
            ConsoleOutput.printGreen("No resources to destroy.");
            System.out.println();
            return;
        }

        ConsoleOutput.print("Amanoform will destroy the following resources:");
        System.out.println();
        for (String key : resourcesToDestroy) {
            ConsoleOutput.printRed("  - " + key);
        }

        if (!autoApprove) {
            System.out.println();
            System.out.print("Do you really want to destroy these resources? (y/N): ");
            Scanner scanner = new Scanner(System.in);
            String response = scanner.nextLine().trim().toLowerCase();
            if (!response.equals("y") && !response.equals("yes")) {
                System.out.println();
                ConsoleOutput.printYellow("Destroy cancelled.");
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

        int destroyed = 0;
        List<String> failed = new ArrayList<>();

        for (String key : resourcesToDestroy) {
            String resourceType = key.split("\\.")[0];
            ResourceHandler handler = ResourceHandlerRegistryFactory.getHandler(resourceType);

            if (handler == null) {
                ConsoleOutput.printRed("  Error: No handler for " + resourceType
                        + ", cannot destroy " + key + ".");
                failed.add(key);
                continue;
            }

            Map<String, Object> resourceData = state.getResource(key);
            ConsoleOutput.printRed("  Destroying " + key + "... (navigating to console)");

            try {
                handler.destroy(provider, resourceData);
                state.removeResource(key);
                state.save();
                ConsoleOutput.printRed("  Destroyed " + key);
                destroyed++;
            } catch (Exception e) {
                System.out.println();
                ConsoleOutput.printBoldRed("  Error destroying " + key + ": " + e.getMessage());
                ConsoleOutput.print("  Resource may still exist in AWS. State has been preserved.");
                failed.add(key);
                ConsoleOutput.printYellow("  Continuing with remaining resources...");
                System.out.println();
            }
        }

        provider.close();

        System.out.println();
        ConsoleOutput.printBoldGreen("Destroy complete! Resources: " + destroyed + " destroyed.");

        if (!failed.isEmpty()) {
            ConsoleOutput.printBoldRed(failed.size() + " resource(s) failed:");
            for (String key : failed) {
                ConsoleOutput.print("  - " + key);
            }
            System.out.println();
            ConsoleOutput.print("These resources may still exist in AWS. "
                    + "Run \"amanoform destroy\" to retry.");
            System.out.println();
            System.exit(1);
        }

        System.out.println();
    }
}
