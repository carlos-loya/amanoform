package com.amanoform.cli;

import com.amanoform.AmanoformApplication;
import com.amanoform.config.AmanoformConfigurationParser;
import com.amanoform.planning.InfrastructureActionPlanner;
import com.amanoform.planning.PlannedAction;
import com.amanoform.state.AmanoformInfrastructureStateManager;
import com.amanoform.util.ConsoleOutput;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Subcommand: {@code amanoform plan}
 *
 * <p>Generates and displays a manual execution plan showing the
 * infrastructure changes that Amanoform will carry out by navigating
 * the AWS Management Console on your behalf.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
@Command(
    name = "plan",
    description = "Generate and display a manual execution plan.%n%n"
            + "Shows the infrastructure changes that Amanoform will carry out%n"
            + "by navigating the AWS Management Console on your behalf."
)
public class PlanCommand implements Runnable {

    @Option(names = {"-c", "--config"}, defaultValue = "main.af",
            description = "Path to Amanoform configuration file.")
    private String config;

    @Option(names = {"-t", "--target"}, description = "Target specific resources (e.g., af_ec2_instance.web).")
    private String[] targets;

    @Override
    public void run() {
        System.out.println();
        ConsoleOutput.printBold("Amanoform v" + AmanoformApplication.VERSION);
        System.out.println();

        Map<String, Object> cfg = AmanoformConfigurationParser.loadConfig(config);
        AmanoformInfrastructureStateManager state = AmanoformInfrastructureStateManager.load();

        ConsoleOutput.print("Refreshing state by visually inspecting the AWS Console...");
        System.out.println();

        Set<String> targetSet = new HashSet<>();
        if (targets != null) {
            for (String target : targets) {
                targetSet.add(target);
            }
        }

        List<PlannedAction> actions = InfrastructureActionPlanner.buildActions(cfg, state, targetSet);

        if (actions.isEmpty()) {
            ConsoleOutput.printGreen("No changes. Your infrastructure matches the configuration.");
            ConsoleOutput.print("Amanoform has finished inspecting the console.");
            System.out.println();
            return;
        }

        Map<String, String> descriptions = Map.of(
                "create", "(via manual browser interaction)",
                "update", "(the operator will navigate to the resource and modify settings)",
                "destroy", "(the operator will locate and terminate the resource)"
        );

        Map<String, String> labels = Map.of(
                "create", "\033[32mcreated\033[0m",
                "update", "\033[33mupdated\033[0m",
                "destroy", "\033[31mdestroyed\033[0m"
        );

        for (PlannedAction action : actions) {
            String suffix = "will be " + labels.get(action.actionType()) + " "
                    + descriptions.get(action.actionType());
            ConsoleOutput.printAction(action.actionType(), action.resourceKey(), suffix);
        }

        int[] counts = countActions(actions);
        ConsoleOutput.printPlanSummary(counts[0], counts[1], counts[2]);
    }

    /**
     * Count actions by type.
     *
     * @param actions the list of planned actions
     * @return an array of [create, update, destroy] counts
     */
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
