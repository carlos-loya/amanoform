package com.amanoform.resources.iam;

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
 * IAM Role manual provisioning handler for Amanoform.
 *
 * <p>This handler automates the creation and deletion of IAM roles by
 * navigating the AWS Console's role creation wizard. The policy search
 * box uses a custom autocomplete component, which makes this handler
 * a particularly rewarding exercise in browser automation.</p>
 *
 * <p>The IAM role creation wizard is a multi-step process: select trusted
 * entity, attach policies (one checkbox click per policy), name the role,
 * and review. The policy search box is where the real fun begins —
 * AWS uses a custom autocomplete that requires careful timing.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
public class IAMRoleManualProvisioningHandler implements ResourceHandler {

    /**
     * Map of common service principals to their console labels.
     *
     * <p>In Python, this was a class-level dict literal. In Java, it's a
     * static final Map populated in a static initializer block. Ten entries
     * require ten {@code put()} calls. This is the price of type safety.</p>
     */
    private static final Map<String, String> SERVICE_LABELS = new HashMap<>();

    static {
        SERVICE_LABELS.put("lambda.amazonaws.com", "Lambda");
        SERVICE_LABELS.put("ec2.amazonaws.com", "EC2");
        SERVICE_LABELS.put("ecs-tasks.amazonaws.com", "Elastic Container Service Task");
        SERVICE_LABELS.put("states.amazonaws.com", "Step Functions");
        SERVICE_LABELS.put("apigateway.amazonaws.com", "API Gateway");
        SERVICE_LABELS.put("s3.amazonaws.com", "S3");
        SERVICE_LABELS.put("sns.amazonaws.com", "SNS");
        SERVICE_LABELS.put("sqs.amazonaws.com", "SQS");
        SERVICE_LABELS.put("codebuild.amazonaws.com", "CodeBuild");
        SERVICE_LABELS.put("codepipeline.amazonaws.com", "CodePipeline");
    }

    private static final Pattern ROLE_ARN_PATTERN = Pattern.compile(
            "arn:aws:iam::\\d+:role/[\\w+=,.@-]+");

    /**
     * Create an IAM role through the console wizard.
     *
     * <p>Navigates to: IAM &gt; Roles &gt; Create role.
     * Then selects the trusted entity, attaches policies, and names the role.
     * This is a three-step wizard, and we click through every step.</p>
     *
     * @param provider the authenticated browser session provider
     * @param attributes resource attributes from the configuration file
     * @return a map containing the role name, ARN, and other output attributes
     */
    @Override
    public Map<String, Object> create(AWSManagementConsoleSessionProvider provider,
                                       Map<String, Object> attributes) {
        WebDriver driver = provider.getDriver();

        // Navigate to IAM Roles
        provider.navigateToService("iam/home#/roles");
        sleepQuietly(2000);

        // Click "Create role"
        driver.findElement(
                By.xpath("//button[contains(text(),'Create role')]")).click();
        sleepQuietly(3000);

        // Step 1: Select trusted entity type — "AWS service"
        driver.findElement(
                By.xpath("//label[contains(text(),'AWS service')]")).click();
        sleepQuietly(1000);

        // Select the service use case
        String assumeRolePolicy = getStringAttribute(attributes, "assume_role_policy",
                "lambda.amazonaws.com");
        String serviceLabel = SERVICE_LABELS.getOrDefault(assumeRolePolicy, "Lambda");

        List<WebElement> serviceOptions = driver.findElements(
                By.xpath("//label[contains(text(),'" + serviceLabel + "')]"));
        if (!serviceOptions.isEmpty()) {
            serviceOptions.get(0).click();
        } else {
            // Fallback: search for the service
            List<WebElement> searchInputs = driver.findElements(
                    By.cssSelector("[placeholder='Search']"));
            if (!searchInputs.isEmpty()) {
                searchInputs.get(0).sendKeys(serviceLabel);
                sleepQuietly(1000);
                driver.findElement(
                        By.xpath("//label[contains(text(),'" + serviceLabel + "')]")).click();
            }
        }
        sleepQuietly(1000);

        // Click "Next"
        driver.findElement(
                By.xpath("//button[contains(text(),'Next')]")).click();
        sleepQuietly(3000);

        // Step 2: Attach permission policies
        String policiesStr = getStringAttribute(attributes, "policies", "");
        if (!policiesStr.isEmpty()) {
            String[] policies = policiesStr.split(",");
            for (String policyName : policies) {
                attachPolicy(driver, policyName.trim());
            }
        }

        // Click "Next"
        driver.findElement(
                By.xpath("//button[contains(text(),'Next')]")).click();
        sleepQuietly(3000);

        // Step 3: Name the role
        String roleName = getStringAttribute(attributes, "name", "amanoform-role");
        WebElement roleNameInput = driver.findElement(
                By.cssSelector("[data-testid='role-name-input']"));
        roleNameInput.clear();
        roleNameInput.sendKeys(roleName);
        sleepQuietly(500);

        // Fill in description if provided
        String description = getStringAttribute(attributes, "description", "Managed by Amanoform");
        List<WebElement> descInputs = driver.findElements(
                By.cssSelector("[data-testid='role-description-input']"));
        if (!descInputs.isEmpty()) {
            descInputs.get(0).clear();
            descInputs.get(0).sendKeys(description);
        } else {
            List<WebElement> textareas = driver.findElements(By.tagName("textarea"));
            if (!textareas.isEmpty()) {
                textareas.get(0).clear();
                textareas.get(0).sendKeys(description);
            }
        }
        sleepQuietly(500);

        // Click "Create role"
        driver.findElement(
                By.xpath("//button[contains(text(),'Create role')]")).click();
        sleepQuietly(3000);

        // Extract role ARN
        String roleArn = extractRoleArn(driver, roleName);

        Map<String, Object> result = new HashMap<>();
        result.put("name", roleName);
        result.put("description", description);
        result.put("assume_role_policy", assumeRolePolicy);
        result.put("policies", policiesStr);
        result.put("arn", roleArn);
        result.put("status", "created");
        return result;
    }

    /**
     * Delete an IAM role through the console.
     *
     * <p>Navigates to: IAM &gt; Roles &gt; search &gt; select &gt; Delete.
     * Then types the role name to confirm deletion.</p>
     *
     * @param provider the authenticated browser session provider
     * @param resourceData the resource's current state data
     */
    @Override
    public void destroy(AWSManagementConsoleSessionProvider provider,
                        Map<String, Object> resourceData) {
        WebDriver driver = provider.getDriver();
        String roleName = getStringAttribute(resourceData, "name", "");

        if (roleName.isEmpty()) {
            return;
        }

        // Navigate to IAM Roles
        provider.navigateToService("iam/home#/roles");
        sleepQuietly(2000);

        // Search for the role
        WebElement searchInput = driver.findElement(
                By.cssSelector("[placeholder='Search']"));
        searchInput.sendKeys(roleName);
        searchInput.sendKeys(Keys.ENTER);
        sleepQuietly(2000);

        // Click on the role to open it
        driver.findElement(
                By.xpath("//a[contains(text(),'" + roleName + "')]")).click();
        sleepQuietly(2000);

        // Click "Delete"
        driver.findElement(
                By.xpath("//button[contains(text(),'Delete')]")).click();
        sleepQuietly(1000);

        // Type the role name to confirm
        List<WebElement> confirmInputs = driver.findElements(
                By.cssSelector("input[placeholder='" + roleName + "']"));
        if (!confirmInputs.isEmpty()) {
            confirmInputs.get(0).sendKeys(roleName);
        } else {
            List<WebElement> textInputs = driver.findElements(
                    By.cssSelector("input[type='text']"));
            if (!textInputs.isEmpty()) {
                textInputs.get(textInputs.size() - 1).sendKeys(roleName);
            }
        }
        sleepQuietly(500);

        // Confirm deletion
        List<WebElement> deleteButtons = driver.findElements(
                By.xpath("//button[contains(text(),'Delete')]"));
        if (!deleteButtons.isEmpty()) {
            deleteButtons.get(deleteButtons.size() - 1).click();
        }
        sleepQuietly(2000);
    }

    /**
     * Detect configuration drift for IAM role attributes.
     */
    @Override
    public List<String> detectDrift(Map<String, Object> existing, Map<String, Object> desired) {
        List<String> drifted = new ArrayList<>();
        String[] keysToCheck = {"name", "assume_role_policy", "policies", "description"};

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
     * Search for and attach a single IAM policy.
     *
     * <p>The policy search box uses a custom autocomplete component.
     * We clear the search, type the policy name, wait for results,
     * and check the box. ~3 clicks per policy. In Selenium, each click
     * requires finding the element first, naturally.</p>
     *
     * @param driver the active WebDriver session
     * @param policyName the policy name to search for and attach
     */
    private void attachPolicy(WebDriver driver, String policyName) {
        // Find the policy search input
        List<WebElement> filterInputs = driver.findElements(
                By.cssSelector("input[placeholder='Filter policies']"));
        WebElement searchInput;
        if (!filterInputs.isEmpty()) {
            searchInput = filterInputs.get(0);
        } else {
            searchInput = driver.findElement(
                    By.cssSelector("[placeholder='Search']"));
        }

        searchInput.clear();
        sleepQuietly(500);
        searchInput.sendKeys(policyName);
        sleepQuietly(2000); // Wait for the autocomplete to populate

        // Check the checkbox for the policy
        WebElement policyRow = driver.findElement(
                By.xpath("//tr[contains(.,'" + policyName + "')]"));
        WebElement checkbox = policyRow.findElement(
                By.cssSelector("input[type='checkbox']"));
        if (!checkbox.isSelected()) {
            checkbox.click();
        }
        sleepQuietly(500);
    }

    /**
     * Extract the role ARN from the creation result page.
     *
     * @param driver the active WebDriver session
     * @param roleName the expected role name
     * @return the extracted ARN, or a placeholder
     */
    private String extractRoleArn(WebDriver driver, String roleName) {
        try {
            String pageSource = driver.getPageSource();
            Matcher matcher = ROLE_ARN_PATTERN.matcher(pageSource);
            if (matcher.find()) {
                return matcher.group(0);
            }
        } catch (Exception ignored) {
            // Extraction failed
        }

        return "arn:aws:iam::000000000000:role/" + roleName;
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
