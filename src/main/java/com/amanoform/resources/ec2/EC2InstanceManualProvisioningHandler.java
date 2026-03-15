package com.amanoform.resources.ec2;

import com.amanoform.provider.AWSManagementConsoleSessionProvider;
import com.amanoform.resources.ResourceHandler;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EC2 Instance manual provisioning handler for Amanoform.
 *
 * <p>This handler automates the process of launching an EC2 instance by
 * navigating the AWS Console's "Launch Instance" wizard. Every click,
 * every dropdown, every text field — all handled automatically, by hand.</p>
 *
 * <p>Instead of calling the {@code ec2:RunInstances} API (which would be far
 * too efficient), this handler opens the EC2 launch wizard in a real browser
 * and fills out the form like a diligent cloud operator.</p>
 *
 * <p>Note that this class uses Selenium WebDriver instead of Playwright.
 * Selenium has been the industry standard for browser automation since 2004,
 * making it the natural choice for an enterprise-grade manual infrastructure
 * automation platform. The fact that it requires approximately 40% more code
 * than Playwright for the same operations is a feature, not a bug.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
public class EC2InstanceManualProvisioningHandler implements ResourceHandler {

    private static final Pattern INSTANCE_ID_PATTERN = Pattern.compile("i-[0-9a-f]{8,17}");

    /**
     * Launch an EC2 instance through the console launch wizard.
     *
     * <p>Navigates to: EC2 &gt; Instances &gt; Launch Instance.
     * Then fills out the form fields based on the provided attributes.</p>
     *
     * @param provider the authenticated browser session provider
     * @param attributes resource attributes from the configuration file
     * @return a map containing the instance ID and other output attributes
     */
    @Override
    public Map<String, Object> create(AWSManagementConsoleSessionProvider provider,
                                       Map<String, Object> attributes) {
        WebDriver driver = provider.getDriver();

        // Navigate to EC2 dashboard
        provider.navigateToService("ec2/v2/home");
        sleepQuietly(2000); // Wait for the console to fully render

        // Click "Launch Instance"
        driver.findElement(
                By.xpath("//button[contains(text(),'Launch instance')]")).click();
        sleepQuietly(3000);

        // Fill in the instance name
        String name = getStringAttribute(attributes, "name", "amanoform-instance");
        List<WebElement> nameInputs = driver.findElements(
                By.cssSelector("input[placeholder='Example: My web server']"));
        if (!nameInputs.isEmpty()) {
            nameInputs.get(0).clear();
            nameInputs.get(0).sendKeys(name);
        } else {
            // Fallback: try the name tag input
            WebElement nameInput = driver.findElement(
                    By.cssSelector("[data-testid='ec2-name-input']"));
            nameInput.clear();
            nameInput.sendKeys(name);
        }

        // Select the AMI
        String ami = getStringAttribute(attributes, "ami", "");
        if (!ami.isEmpty()) {
            // Click on "Browse more AMIs" and search
            List<WebElement> browseLinks = driver.findElements(
                    By.xpath("//*[text()='Browse more AMIs']"));
            if (!browseLinks.isEmpty()) {
                browseLinks.get(0).click();
                sleepQuietly(1000);

                WebElement searchInput = driver.findElement(
                        By.cssSelector("[placeholder='Search our AMI catalog']"));
                searchInput.sendKeys(ami);
                searchInput.sendKeys(Keys.ENTER);
                sleepQuietly(2000);

                // Select the first result
                driver.findElement(
                        By.cssSelector("[data-testid='ami-select-button']")).click();
                sleepQuietly(1000);
            }
        }

        // Select instance type
        String instanceType = getStringAttribute(attributes, "instance_type", "t2.micro");
        List<WebElement> typeSelectors = driver.findElements(
                By.cssSelector("[data-testid='instance-type-selector']"));
        if (!typeSelectors.isEmpty()) {
            typeSelectors.get(0).click();
            sleepQuietly(1000);

            WebElement typeSearch = driver.findElement(
                    By.cssSelector("[data-testid='instance-type-search']"));
            typeSearch.sendKeys(instanceType);
            sleepQuietly(1000);

            driver.findElement(
                    By.xpath("//tr[contains(.,'" + instanceType + "')]")).click();
        }

        // Key pair — select "Proceed without a key pair" if no key specified
        String keyName = getStringAttribute(attributes, "key_name", "");
        if (keyName.isEmpty()) {
            driver.findElement(
                    By.xpath("//*[text()='Proceed without a key pair']")).click();
        }

        // Click "Launch Instance"
        driver.findElement(
                By.xpath("//button[contains(text(),'Launch instance')]")).click();
        sleepQuietly(3000);

        // Try to extract the instance ID from the success page
        String instanceId = extractInstanceId(driver);

        Map<String, Object> result = new HashMap<>();
        result.put("instance_id", instanceId);
        result.put("ami", ami);
        result.put("instance_type", instanceType);
        result.put("name", name);
        result.put("status", "running");
        return result;
    }

    /**
     * Terminate an EC2 instance through the AWS Management Console.
     *
     * <p>Navigates to: EC2 &gt; Instances &gt; select instance &gt;
     * Instance State &gt; Terminate</p>
     *
     * @param provider the authenticated browser session provider
     * @param resourceData the resource's current state data
     */
    @Override
    public void destroy(AWSManagementConsoleSessionProvider provider,
                        Map<String, Object> resourceData) {
        WebDriver driver = provider.getDriver();
        String instanceId = getStringAttribute(resourceData, "instance_id", "");

        if (instanceId.isEmpty()) {
            return;
        }

        // Navigate to EC2 instances list
        provider.navigateToService("ec2/v2/home#Instances");
        sleepQuietly(2000);

        // Search for the instance by ID
        WebElement searchInput = driver.findElement(
                By.cssSelector("[placeholder='Search']"));
        searchInput.sendKeys(instanceId);
        searchInput.sendKeys(Keys.ENTER);
        sleepQuietly(2000);

        // Select the instance
        driver.findElement(
                By.xpath("//tr[contains(.,'" + instanceId + "')]")).click();
        sleepQuietly(1000);

        // Open "Instance State" dropdown
        driver.findElement(
                By.xpath("//button[contains(text(),'Instance state')]")).click();
        sleepQuietly(1000);

        // Click "Terminate instance"
        driver.findElement(
                By.xpath("//*[text()='Terminate instance']")).click();
        sleepQuietly(1000);

        // Confirm termination
        driver.findElement(
                By.xpath("//button[contains(text(),'Terminate')]")).click();
        sleepQuietly(2000);
    }

    /**
     * Detect configuration drift by comparing state to desired config.
     *
     * <p>In a perfect world, we'd take a screenshot of the console and
     * use computer vision to detect drift. For now, we compare attributes.
     * In Java. With explicit null checks. For each field. Individually.</p>
     *
     * @param existing current state from the state file
     * @param desired desired state from the configuration
     * @return a list of attribute names that have drifted
     */
    @Override
    public List<String> detectDrift(Map<String, Object> existing, Map<String, Object> desired) {
        List<String> drifted = new ArrayList<>();
        String[] keysToCheck = {"ami", "instance_type", "name"};

        for (String key : keysToCheck) {
            if (desired.containsKey(key)) {
                Object existingValue = existing.get(key);
                Object desiredValue = desired.get(key);

                if (existingValue == null && desiredValue != null) {
                    drifted.add(key);
                } else if (existingValue != null && !existingValue.equals(desiredValue)) {
                    drifted.add(key);
                }
            }
        }

        return drifted;
    }

    /**
     * Extract the instance ID from the launch success page.
     *
     * @param driver the active WebDriver session
     * @return the extracted instance ID, or a fallback value
     */
    private String extractInstanceId(WebDriver driver) {
        try {
            // Look for the instance ID link on the success page
            List<WebElement> idLinks = driver.findElements(
                    By.cssSelector("a[href*='instanceId']"));
            if (!idLinks.isEmpty()) {
                String text = idLinks.get(0).getText();
                if (text.startsWith("i-")) {
                    return text;
                }
            }

            // Fallback: look for any text matching i-xxxxx pattern in the page source
            String pageSource = driver.getPageSource();
            Matcher matcher = INSTANCE_ID_PATTERN.matcher(pageSource);
            if (matcher.find()) {
                return matcher.group(0);
            }
        } catch (Exception ignored) {
            // If extraction fails, we'll use the fallback value below
        }

        return "i-unknown-manual-launch";
    }

    /**
     * Sleep for the specified number of milliseconds, swallowing the
     * InterruptedException that Java requires us to handle even though
     * it will never be thrown in this context.
     *
     * <p>In Python, this was {@code time.sleep(seconds)}. In Java, it's a
     * method call wrapped in a try-catch block for a checked exception
     * that exists because Java's threading model was designed in 1995
     * when the language designers were still figuring out concurrency.</p>
     *
     * @param milliseconds the duration to sleep
     */
    private void sleepQuietly(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Safely extract a string attribute from a map.
     *
     * <p>Because {@code Map.getOrDefault()} returns {@code Object} and
     * we need a {@code String}, and casting in Java requires either an
     * explicit cast or a helper method. We chose the helper method,
     * adding 15 lines of code to avoid writing {@code (String)} twelve
     * times.</p>
     *
     * @param map the attributes map
     * @param key the attribute key
     * @param defaultValue the default value if the key is missing
     * @return the string value
     */
    private String getStringAttribute(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }
}
