package com.amanoform.resources.rds;

import com.amanoform.provider.AWSManagementConsoleSessionProvider;
import com.amanoform.resources.ResourceHandler;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RDS Instance manual provisioning handler for Amanoform.
 *
 * <p>This handler automates the creation and deletion of RDS database
 * instances by navigating the AWS Console's database creation wizard.
 * It's one of the longest wizards in AWS, requiring extensive scrolling
 * and careful form interaction. The browser handles it with more patience
 * than any human operator ever could.</p>
 *
 * <p>The RDS creation wizard is a marathon of dropdowns, radio buttons,
 * and expandable sections. This handler scrolls through every single
 * one of them, just like you would at 3am during an incident.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
public class RDSInstanceManualProvisioningHandler implements ResourceHandler {

    private static final Map<String, String> ENGINE_LABELS = new HashMap<>();

    static {
        ENGINE_LABELS.put("postgres", "PostgreSQL");
        ENGINE_LABELS.put("mysql", "MySQL");
        ENGINE_LABELS.put("mariadb", "MariaDB");
        ENGINE_LABELS.put("oracle", "Oracle");
        ENGINE_LABELS.put("sqlserver", "SQL Server");
        ENGINE_LABELS.put("aurora-mysql", "Aurora (MySQL Compatible)");
        ENGINE_LABELS.put("aurora-postgresql", "Aurora (PostgreSQL Compatible)");
    }

    /**
     * Create an RDS instance through the console wizard.
     *
     * <p>Navigates to: RDS &gt; Create database.
     * Then methodically fills out every section of the multi-page form.
     * Estimated time: 30-60 seconds. Estimated clicks: ~22.</p>
     *
     * @param provider the authenticated browser session provider
     * @param attributes resource attributes from the configuration file
     * @return a map containing the database identifier and other output attributes
     */
    @Override
    public Map<String, Object> create(AWSManagementConsoleSessionProvider provider,
                                       Map<String, Object> attributes) {
        WebDriver driver = provider.getDriver();

        // Navigate to RDS
        provider.navigateToService("rds/home");
        sleepQuietly(2000);

        // Click "Create database"
        driver.findElement(
                By.xpath("//button[contains(text(),'Create database')]")).click();
        sleepQuietly(3000);

        // Select creation method — "Standard create" (the thorough way)
        driver.findElement(
                By.xpath("//label[contains(text(),'Standard create')]")).click();
        sleepQuietly(1000);

        // Select engine type
        String engine = getStringAttribute(attributes, "engine", "postgres");
        String engineLabel = ENGINE_LABELS.getOrDefault(engine, "PostgreSQL");
        driver.findElement(
                By.xpath("//label[contains(text(),'" + engineLabel + "')]")).click();
        sleepQuietly(1000);

        // Select engine version if specified
        String engineVersion = getStringAttribute(attributes, "engine_version", "");
        if (!engineVersion.isEmpty()) {
            List<WebElement> versionDropdowns = driver.findElements(
                    By.cssSelector("[data-testid='engine-version-selector']"));
            if (!versionDropdowns.isEmpty()) {
                versionDropdowns.get(0).click();
                sleepQuietly(1000);
                driver.findElement(
                        By.xpath("//option[contains(text(),'" + engineVersion + "')]")).click();
                sleepQuietly(500);
            }
        }

        // Select template — "Free tier" if micro, otherwise "Dev/Test"
        String instanceClass = getStringAttribute(attributes, "instance_class", "db.t3.micro");
        if (instanceClass.contains("micro")) {
            driver.findElement(
                    By.xpath("//label[contains(text(),'Free tier')]")).click();
        } else {
            driver.findElement(
                    By.xpath("//label[contains(text(),'Dev/Test')]")).click();
        }
        sleepQuietly(1000);

        // Fill in DB instance identifier
        String identifier = getStringAttribute(attributes, "identifier", "amanoform-db");
        WebElement identifierInput = driver.findElement(
                By.cssSelector("[data-testid='db-instance-identifier-input']"));
        identifierInput.clear();
        identifierInput.sendKeys(identifier);
        sleepQuietly(500);

        // Fill in master username
        String username = getStringAttribute(attributes, "username", "admin");
        List<WebElement> usernameInputs = driver.findElements(
                By.cssSelector("[data-testid='master-username-input']"));
        if (!usernameInputs.isEmpty()) {
            usernameInputs.get(0).clear();
            usernameInputs.get(0).sendKeys(username);
        } else {
            WebElement fallback = driver.findElement(By.name("masterUsername"));
            fallback.clear();
            fallback.sendKeys(username);
        }
        sleepQuietly(500);

        // Select "Self managed" credentials and fill in password
        driver.findElement(
                By.xpath("//label[contains(text(),'Self managed')]")).click();
        sleepQuietly(500);

        String password = getStringAttribute(attributes, "password", "amanoform-default-pw");
        List<WebElement> passwordInputs = driver.findElements(
                By.cssSelector("input[type='password']"));
        if (!passwordInputs.isEmpty()) {
            passwordInputs.get(0).clear();
            passwordInputs.get(0).sendKeys(password);
        }
        sleepQuietly(500);

        // Confirm password
        if (passwordInputs.size() > 1) {
            passwordInputs.get(1).clear();
            passwordInputs.get(1).sendKeys(password);
        }
        sleepQuietly(500);

        // Select instance class
        List<WebElement> classSelectors = driver.findElements(
                By.cssSelector("[data-testid='instance-class-selector']"));
        if (!classSelectors.isEmpty()) {
            classSelectors.get(0).click();
            sleepQuietly(1000);
            driver.findElement(
                    By.xpath("//option[contains(text(),'" + instanceClass + "')]")).click();
            sleepQuietly(500);
        }

        // Configure storage
        String storage = getStringAttribute(attributes, "storage", "20");
        List<WebElement> storageInputs = driver.findElements(
                By.cssSelector("[data-testid='storage-size-input']"));
        if (!storageInputs.isEmpty()) {
            storageInputs.get(0).clear();
            storageInputs.get(0).sendKeys(storage);
        }
        sleepQuietly(500);

        // Scroll down — the RDS form is exceptionally long
        ((JavascriptExecutor) driver).executeScript(
                "window.scrollTo(0, document.body.scrollHeight)");
        sleepQuietly(1000);

        // Click "Create database"
        driver.findElement(
                By.xpath("//button[contains(text(),'Create database')]")).click();
        sleepQuietly(5000);

        Map<String, Object> result = new HashMap<>();
        result.put("identifier", identifier);
        result.put("engine", engine);
        result.put("engine_version", engineVersion);
        result.put("instance_class", instanceClass);
        result.put("username", username);
        result.put("storage", storage);
        result.put("status", "creating");
        return result;
    }

    /**
     * Delete an RDS instance through the console.
     *
     * <p>Navigates to: RDS &gt; Databases &gt; select instance &gt;
     * Actions &gt; Delete. Then unchecks the final snapshot option and
     * types the confirmation string one keystroke at a time, because
     * AWS demands it.</p>
     *
     * @param provider the authenticated browser session provider
     * @param resourceData the resource's current state data
     */
    @Override
    public void destroy(AWSManagementConsoleSessionProvider provider,
                        Map<String, Object> resourceData) {
        WebDriver driver = provider.getDriver();
        String identifier = getStringAttribute(resourceData, "identifier", "");

        if (identifier.isEmpty()) {
            return;
        }

        // Navigate to RDS databases list
        provider.navigateToService("rds/home#databases");
        sleepQuietly(3000);

        // Find and select the database
        WebElement dbRow = driver.findElement(
                By.xpath("//tr[contains(.,'" + identifier + "')]"));
        dbRow.findElement(By.cssSelector("input[type='radio']")).click();
        sleepQuietly(1000);

        // Click Actions > Delete
        driver.findElement(
                By.xpath("//button[contains(text(),'Actions')]")).click();
        sleepQuietly(1000);
        driver.findElement(
                By.xpath("//*[text()='Delete']")).click();
        sleepQuietly(2000);

        // Uncheck "Create final snapshot" if present
        List<WebElement> snapshotCheckboxes = driver.findElements(
                By.xpath("//input[@type='checkbox'][ancestor::*[contains(.,'Create final snapshot')]]"));
        if (!snapshotCheckboxes.isEmpty() && snapshotCheckboxes.get(0).isSelected()) {
            snapshotCheckboxes.get(0).click();
            sleepQuietly(500);
        }

        // Check "I acknowledge" checkbox if present
        List<WebElement> ackCheckboxes = driver.findElements(
                By.xpath("//input[@type='checkbox'][ancestor::*[contains(.,'I acknowledge')]]"));
        if (!ackCheckboxes.isEmpty() && !ackCheckboxes.get(0).isSelected()) {
            ackCheckboxes.get(0).click();
            sleepQuietly(500);
        }

        // Type the confirmation string "delete me"
        List<WebElement> confirmInputs = driver.findElements(
                By.cssSelector("input[placeholder='delete me']"));
        if (!confirmInputs.isEmpty()) {
            confirmInputs.get(0).sendKeys("delete me");
        } else {
            List<WebElement> textInputs = driver.findElements(
                    By.cssSelector("input[type='text']"));
            if (!textInputs.isEmpty()) {
                textInputs.get(textInputs.size() - 1).sendKeys("delete me");
            }
        }
        sleepQuietly(500);

        // Click "Delete"
        List<WebElement> deleteButtons = driver.findElements(
                By.xpath("//button[contains(text(),'Delete')]"));
        if (!deleteButtons.isEmpty()) {
            deleteButtons.get(deleteButtons.size() - 1).click();
        }
        sleepQuietly(3000);
    }

    /**
     * Detect configuration drift for RDS instance attributes.
     *
     * <p>Most RDS changes require a reboot or replacement. We detect
     * the drift — the operator decides what to do about it at 3am.</p>
     */
    @Override
    public List<String> detectDrift(Map<String, Object> existing, Map<String, Object> desired) {
        List<String> drifted = new ArrayList<>();
        String[] keysToCheck = {"engine", "engine_version", "instance_class", "identifier", "storage"};

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

    private void sleepQuietly(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String getStringAttribute(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }
}
