package com.amanoform.cli;

import com.amanoform.AmanoformApplication;
import com.amanoform.util.ConsoleOutput;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import picocli.CommandLine.Command;

/**
 * Subcommand: {@code amanoform init}
 *
 * <p>Prepares the working directory for Amanoform operations by validating
 * that the browser automation runtime (ChromeDriver) is available and
 * functional.</p>
 *
 * <p>In the Python version, this downloaded Chromium via Playwright.
 * In the Java version, Selenium Manager handles ChromeDriver discovery
 * and download automatically. But we still need this command to exist
 * because users expect an init step, and who are we to deny them
 * ceremony.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
@Command(
    name = "init",
    description = "Prepare your working directory for Amanoform operations.%n%n"
            + "Installs the required browser binaries and validates your%n"
            + "configuration files."
)
public class InitCommand implements Runnable {

    @Override
    public void run() {
        System.out.println();
        ConsoleOutput.printBold("Amanoform v" + AmanoformApplication.VERSION);
        System.out.println();
        ConsoleOutput.print("Initializing the Amanoform provider plugins...");
        ConsoleOutput.print("  - Validating browser automation runtime (ChromeDriver)...");

        try {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");

            ChromeDriver driver = new ChromeDriver(options);
            driver.quit();

            ConsoleOutput.print("  - Browser runtime validated successfully.");
        } catch (Exception e) {
            ConsoleOutput.printYellow("  Browser runtime validation failed: " + e.getMessage());
            ConsoleOutput.print("  Please ensure Google Chrome or Chromium is installed.");
            ConsoleOutput.print("  Selenium Manager will attempt to download ChromeDriver");
            ConsoleOutput.print("  automatically on first use.");
            return;
        }

        System.out.println();
        ConsoleOutput.printBoldGreen("Amanoform has been successfully initialized!");
        System.out.println();
        ConsoleOutput.print("You may now begin working with Amanoform. Try running");
        ConsoleOutput.print("\"amanoform plan\" to see any changes that are required");
        ConsoleOutput.print("for your infrastructure.");
        System.out.println();
    }
}
