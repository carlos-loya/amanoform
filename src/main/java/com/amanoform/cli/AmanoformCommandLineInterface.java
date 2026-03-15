package com.amanoform.cli;

import com.amanoform.AmanoformApplication;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Top-level command group for the Amanoform CLI.
 *
 * <p>This class serves as the root command for the Amanoform command-line
 * interface, delegating to subcommands for specific operations. In Python's
 * Click library, this was a decorated function. In Java's Picocli, it is
 * a class with annotations, implementing Runnable, with a reference to
 * every subcommand class.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
@Command(
    name = "amanoform",
    description = "Amanoform — Infrastructure as Code, by hand.%n%n"
            + "Provision and manage cloud infrastructure through enterprise-grade%n"
            + "manual browser automation.",
    mixinStandardHelpOptions = true,
    version = AmanoformApplication.VERSION,
    subcommands = {
        InitCommand.class,
        PlanCommand.class,
        ApplyCommand.class,
        DestroyCommand.class,
    }
)
public class AmanoformCommandLineInterface implements Runnable {

    /**
     * When invoked without a subcommand, display help text.
     */
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
