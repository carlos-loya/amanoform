package com.amanoform.provider;

import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * AWS Management Console browser session provider for Amanoform.
 *
 * <p>This class handles the critical task of opening a real web browser
 * and logging into the AWS Management Console, just as a human operator
 * would. Because that's what we are — operators.</p>
 *
 * <p>Instead of using the AWS SDK or CLI (which would defeat the purpose),
 * this provider opens a Chromium browser via Selenium WebDriver and
 * navigates the AWS Console UI to perform infrastructure operations
 * manually, but automatically.</p>
 *
 * <p>The class name {@code AWSManagementConsoleSessionProvider} is 45
 * characters long. In Python, the equivalent class was called
 * {@code AWSConsoleProvider}, a mere 18 characters. This 150% increase
 * in character count is consistent with the Java-to-Python verbosity
 * ratio observed across the rest of this codebase.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
public class AWSManagementConsoleSessionProvider {

    private static final String AWS_CONSOLE_URL = "https://console.aws.amazon.com";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/120.0.0.0 Safari/537.36 "
            + "Amanoform/0.1.0";

    private final String region;
    private final boolean headless;
    private WebDriver driver;

    /**
     * Construct a new AWS Management Console session provider.
     *
     * @param region the AWS region to operate in (e.g., "us-east-1")
     * @param headless whether to run the browser in headless mode
     */
    public AWSManagementConsoleSessionProvider(String region, boolean headless) {
        this.region = region;
        this.headless = headless;
        this.driver = null;
    }

    /**
     * Get the active Selenium WebDriver instance.
     *
     * @return the WebDriver for console interaction
     * @throws RuntimeException if no active browser session exists
     */
    public WebDriver getDriver() {
        if (driver == null) {
            throw new RuntimeException(
                    "No active browser session. Did you call provider.login()? "
                    + "Amanoform cannot click buttons in a browser that isn't open.");
        }
        return driver;
    }

    /**
     * Open a browser and authenticate to the AWS Management Console.
     *
     * <p>Credentials are read from environment variables:</p>
     * <ul>
     *   <li>{@code AMANOFORM_AWS_ACCOUNT_ID} — the 12-digit AWS account ID</li>
     *   <li>{@code AMANOFORM_AWS_USERNAME} — IAM username</li>
     *   <li>{@code AMANOFORM_AWS_PASSWORD} — IAM password</li>
     * </ul>
     *
     * <p>This is intentionally less secure than using IAM roles or SSO.
     * We believe in the manual approach.</p>
     */
    public void login() {
        String accountId = System.getenv().getOrDefault("AMANOFORM_AWS_ACCOUNT_ID", "");
        String username = System.getenv().getOrDefault("AMANOFORM_AWS_USERNAME", "");
        String password = System.getenv().getOrDefault("AMANOFORM_AWS_PASSWORD", "");

        if (accountId.isEmpty() || username.isEmpty() || password.isEmpty()) {
            throw new RuntimeException(
                    "Missing AWS Console credentials.\n"
                    + "Amanoform requires the following environment variables:\n"
                    + "  AMANOFORM_AWS_ACCOUNT_ID\n"
                    + "  AMANOFORM_AWS_USERNAME\n"
                    + "  AMANOFORM_AWS_PASSWORD\n\n"
                    + "Unlike other tools, Amanoform authenticates through the browser,\n"
                    + "just like you would. Set these variables and try again.");
        }

        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--user-agent=" + USER_AGENT);
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        driver = new ChromeDriver(options);

        // Navigate to the IAM user sign-in page
        String signinUrl = "https://" + accountId + ".signin.aws.amazon.com/console";
        driver.get(signinUrl);
        waitForPageLoad();

        // Fill in credentials — the old-fashioned way
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.id("signin_button")).click();
        waitForPageLoad();

        // Set the region by navigating to the region selector
        navigateToRegion(region);
    }

    /**
     * Navigate to a specific AWS service page in the console.
     *
     * @param servicePath the URL path segment for the service
     *                    (e.g., "ec2/v2/home", "s3/home")
     */
    public void navigateToService(String servicePath) {
        String url = "https://" + region + ".console.aws.amazon.com/"
                + servicePath + "?region=" + region;
        driver.get(url);
        waitForPageLoad();
    }

    /**
     * Capture a screenshot of the current console state.
     *
     * <p>Useful for plan output and drift detection via visual diff.</p>
     *
     * @return the screenshot as a byte array
     */
    public byte[] takeScreenshot() {
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
    }

    /**
     * Close the browser session and release all associated resources.
     *
     * <p>This method should be called in a finally block, because in Java
     * we don't have context managers. We have try-finally. And
     * try-with-resources. And AutoCloseable. And finalize() which is
     * deprecated. It's a rich ecosystem.</p>
     */
    public void close() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

    /**
     * Switch the AWS Console to the specified region.
     *
     * @param region the AWS region identifier (e.g., "us-east-1")
     */
    private void navigateToRegion(String region) {
        String url = "https://" + region + ".console.aws.amazon.com/console/home?region=" + region;
        driver.get(url);
        waitForPageLoad();
    }

    /**
     * Wait for the page to finish loading.
     *
     * <p>In Playwright, this was {@code page.wait_for_load_state("networkidle")}.
     * In Selenium, we wait for the document ready state to be "complete" and
     * then add a generous sleep because the AWS Console is a React application
     * and "complete" is a relative term.</p>
     */
    private void waitForPageLoad() {
        new WebDriverWait(driver, Duration.ofSeconds(30))
                .until(ExpectedConditions.jsReturnsValue(
                        "return document.readyState === 'complete' ? 'yes' : null"));
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
