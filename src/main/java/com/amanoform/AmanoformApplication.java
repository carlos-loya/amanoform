package com.amanoform;

import com.amanoform.cli.AmanoformCommandLineInterface;
import picocli.CommandLine;

/**
 * Main entry point for the Amanoform Enterprise Manual Infrastructure
 * Automation Platform.
 *
 * <p>This class bootstraps the command-line interface and delegates execution
 * to the appropriate subcommand handler. In Python this would be two lines.
 * In Java, it is a class with a main method, a package declaration, and
 * this documentation block you're reading right now.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 * @since 0.1.0
 */
public class AmanoformApplication {

    /** The current version of Amanoform. */
    public static final String VERSION = "0.1.0";

    /**
     * Application entry point.
     *
     * @param args command-line arguments passed from the shell
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new AmanoformCommandLineInterface())
                .execute(args);
        System.exit(exitCode);
    }
}
