package com.amanoform.resources.securitygroup;

import com.amanoform.provider.AWSManagementConsoleSessionProvider;
import com.amanoform.resources.ResourceHandler;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Security Group manual provisioning handler for Amanoform.
 *
 * <p>This handler automates the creation and deletion of VPC Security Groups
 * by navigating the AWS Console. Each firewall rule requires its own
 * sequence of clicks — type, protocol, port range, source. A security
 * group with 10 rules means roughly 46 clicks. This is peak Amanoform.</p>
 *
 * <p>Each inbound and outbound rule requires clicking "Add rule", selecting
 * a type from a dropdown, entering a port range, and specifying a source.
 * The handler repeats this process for every rule in the configuration,
 * with the mechanical patience that only a browser automation script
 * can provide.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
public class SecurityGroupManualProvisioningHandler implements ResourceHandler {

    private static final Pattern SG_ID_PATTERN = Pattern.compile("sg-[0-9a-f]{8,17}");

    /**
     * Create a Security Group through the console.
     *
     * <p>Navigates to: VPC &gt; Security Groups &gt; Create security group.
     * Then clicks "Add rule" for each inbound and outbound rule.</p>
     *
     * @param provider the authenticated browser session provider
     * @param attributes resource attributes from the configuration file
     * @return a map containing the security group ID and other output attributes
     */
    @Override
    public Map<String, Object> create(AWSManagementConsoleSessionProvider provider,
                                       Map<String, Object> attributes) {
        WebDriver driver = provider.getDriver();

        // Navigate to VPC Security Groups
        provider.navigateToService("vpc/home#SecurityGroups");
        sleepQuietly(2000);

        // Click "Create security group"
        driver.findElement(
                By.xpath("//button[contains(text(),'Create security group')]")).click();
        sleepQuietly(3000);

        // Fill in security group name
        String sgName = getStringAttribute(attributes, "name", "amanoform-sg");
        WebElement nameInput = driver.findElement(
                By.cssSelector("[data-testid='security-group-name-input']"));
        nameInput.clear();
        nameInput.sendKeys(sgName);
        sleepQuietly(500);

        // Fill in description
        String description = getStringAttribute(attributes, "description", "Managed by Amanoform");
        List<WebElement> descInputs = driver.findElements(
                By.cssSelector("[data-testid='security-group-description-input']"));
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

        // Select VPC if specified
        String vpcId = getStringAttribute(attributes, "vpc_id", "");
        if (!vpcId.isEmpty()) {
            List<WebElement> vpcDropdowns = driver.findElements(
                    By.cssSelector("[data-testid='vpc-selector']"));
            if (!vpcDropdowns.isEmpty()) {
                vpcDropdowns.get(0).click();
                sleepQuietly(1000);
                driver.findElement(
                        By.xpath("//option[contains(text(),'" + vpcId + "')]")).click();
                sleepQuietly(500);
            }
        }

        // Add inbound rules
        String ingressRulesStr = getStringAttribute(attributes, "ingress_rules", "");
        List<Map<String, String>> ingressRules = parseRules(ingressRulesStr);
        for (Map<String, String> rule : ingressRules) {
            addRule(driver, "Inbound rules", rule);
        }

        // Add outbound rules
        String egressRulesStr = getStringAttribute(attributes, "egress_rules", "");
        List<Map<String, String>> egressRules = parseRules(egressRulesStr);
        for (Map<String, String> rule : egressRules) {
            addRule(driver, "Outbound rules", rule);
        }

        // Scroll to bottom and click "Create security group"
        ((JavascriptExecutor) driver).executeScript(
                "window.scrollTo(0, document.body.scrollHeight)");
        sleepQuietly(1000);

        driver.findElement(
                By.xpath("//button[contains(text(),'Create security group')]")).click();
        sleepQuietly(3000);

        // Extract security group ID
        String sgId = extractSgId(driver);

        Map<String, Object> result = new HashMap<>();
        result.put("sg_id", sgId);
        result.put("name", sgName);
        result.put("description", description);
        result.put("vpc_id", vpcId);
        result.put("ingress_rules", ingressRulesStr);
        result.put("egress_rules", egressRulesStr);
        result.put("status", "created");
        return result;
    }

    /**
     * Delete a Security Group through the console.
     *
     * <p>Navigates to: VPC &gt; Security Groups &gt; select &gt;
     * Actions &gt; Delete</p>
     *
     * @param provider the authenticated browser session provider
     * @param resourceData the resource's current state data
     */
    @Override
    public void destroy(AWSManagementConsoleSessionProvider provider,
                        Map<String, Object> resourceData) {
        WebDriver driver = provider.getDriver();
        String sgId = getStringAttribute(resourceData, "sg_id", "");
        String sgName = getStringAttribute(resourceData, "name", "");

        if (sgId.isEmpty() && sgName.isEmpty()) {
            return;
        }

        // Navigate to VPC Security Groups
        provider.navigateToService("vpc/home#SecurityGroups");
        sleepQuietly(2000);

        // Search for the security group
        String searchTerm = sgId.isEmpty() ? sgName : sgId;
        WebElement searchInput = driver.findElement(
                By.cssSelector("[placeholder='Search']"));
        searchInput.sendKeys(searchTerm);
        searchInput.sendKeys(Keys.ENTER);
        sleepQuietly(2000);

        // Select the security group
        WebElement sgRow = driver.findElement(
                By.xpath("//tr[contains(.,'" + searchTerm + "')]"));
        sgRow.findElement(By.cssSelector("input[type='checkbox']")).click();
        sleepQuietly(1000);

        // Actions > Delete security groups
        driver.findElement(
                By.xpath("//button[contains(text(),'Actions')]")).click();
        sleepQuietly(1000);
        driver.findElement(
                By.xpath("//*[text()='Delete security groups']")).click();
        sleepQuietly(1000);

        // Type "delete" to confirm
        List<WebElement> confirmInputs = driver.findElements(
                By.cssSelector("input[placeholder='delete']"));
        if (!confirmInputs.isEmpty()) {
            confirmInputs.get(0).sendKeys("delete");
        } else {
            List<WebElement> textInputs = driver.findElements(
                    By.cssSelector("input[type='text']"));
            if (!textInputs.isEmpty()) {
                textInputs.get(textInputs.size() - 1).sendKeys("delete");
            }
        }
        sleepQuietly(500);

        List<WebElement> deleteButtons = driver.findElements(
                By.xpath("//button[contains(text(),'Delete')]"));
        if (!deleteButtons.isEmpty()) {
            deleteButtons.get(deleteButtons.size() - 1).click();
        }
        sleepQuietly(2000);
    }

    /**
     * Detect configuration drift for Security Group attributes.
     *
     * <p>Rule changes are detected by comparing the serialized rule strings.</p>
     */
    @Override
    public List<String> detectDrift(Map<String, Object> existing, Map<String, Object> desired) {
        List<String> drifted = new ArrayList<>();
        String[] keysToCheck = {"name", "description", "vpc_id", "ingress_rules", "egress_rules"};

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
     * Parse a comma-separated rules string into structured rules.
     *
     * <p>Format: "port:protocol:source,port:protocol:source,..."</p>
     * <p>Examples: "80:tcp:0.0.0.0/0" or "80:tcp:0.0.0.0/0,22:tcp:10.0.0.0/8"</p>
     *
     * <p>In Python, this was a module-level function. In Java, it is a
     * private method, because functions cannot exist outside of classes.
     * The parsing logic is identical. The ceremony is not.</p>
     *
     * @param rulesStr the comma-separated rules string
     * @return a list of rule maps with port, protocol, and source keys
     */
    private List<Map<String, String>> parseRules(String rulesStr) {
        List<Map<String, String>> rules = new ArrayList<>();
        if (rulesStr == null || rulesStr.isEmpty()) {
            return rules;
        }

        for (String ruleStr : rulesStr.split(",")) {
            String[] parts = ruleStr.trim().split(":");
            if (parts.length == 3) {
                Map<String, String> rule = new HashMap<>();
                rule.put("port", parts[0].trim());
                rule.put("protocol", parts[1].trim());
                rule.put("source", parts[2].trim());
                rules.add(rule);
            }
        }

        return rules;
    }

    /**
     * Add a single inbound or outbound rule by clicking through the form.
     *
     * <p>Each rule requires: click "Add rule", select type, enter port,
     * enter source/destination. ~4 clicks per rule. In Selenium, each
     * click requires finding the element first, which adds approximately
     * 6 lines of code per click. Enterprise efficiency.</p>
     *
     * @param driver the active WebDriver session
     * @param sectionText "Inbound rules" or "Outbound rules"
     * @param rule the rule map with port, protocol, and source keys
     */
    private void addRule(WebDriver driver, String sectionText, Map<String, String> rule) {
        // Find the "Add rule" button in the correct section
        WebElement section = driver.findElement(
                By.xpath("//div[contains(.,'" + sectionText + "')]"));
        WebElement addButton = section.findElement(
                By.xpath(".//button[contains(text(),'Add rule')]"));
        addButton.click();
        sleepQuietly(1000);

        // Get the last rule row (the one we just added)
        List<WebElement> ruleRows = section.findElements(By.tagName("tr"));
        if (ruleRows.isEmpty()) {
            return;
        }
        WebElement lastRow = ruleRows.get(ruleRows.size() - 1);

        // Select protocol/type
        String protocol = rule.getOrDefault("protocol", "tcp");
        String port = rule.getOrDefault("port", "");

        List<WebElement> selects = lastRow.findElements(By.tagName("select"));
        if (!selects.isEmpty()) {
            Select typeDropdown = new Select(selects.get(0));

            if ("all".equals(protocol) && "0".equals(port)) {
                typeDropdown.selectByVisibleText("All traffic");
            } else if ("tcp".equals(protocol) && "80".equals(port)) {
                typeDropdown.selectByVisibleText("HTTP");
            } else if ("tcp".equals(protocol) && "443".equals(port)) {
                typeDropdown.selectByVisibleText("HTTPS");
            } else if ("tcp".equals(protocol) && "22".equals(port)) {
                typeDropdown.selectByVisibleText("SSH");
            } else {
                typeDropdown.selectByVisibleText("Custom TCP");
                sleepQuietly(500);
                // Fill in port range
                List<WebElement> portInputs = lastRow.findElements(
                        By.cssSelector("input[placeholder='Port range']"));
                if (!portInputs.isEmpty()) {
                    portInputs.get(0).sendKeys(port);
                }
            }
        }
        sleepQuietly(500);

        // Fill in source/destination
        String source = rule.getOrDefault("source", "0.0.0.0/0");
        List<WebElement> sourceInputs = lastRow.findElements(
                By.cssSelector("input[placeholder='Source'], input[placeholder='Destination']"));
        if (!sourceInputs.isEmpty()) {
            sourceInputs.get(0).sendKeys(source);
        }
        sleepQuietly(500);
    }

    /**
     * Extract the security group ID from the creation result.
     *
     * @param driver the active WebDriver session
     * @return the extracted security group ID, or a fallback value
     */
    private String extractSgId(WebDriver driver) {
        try {
            String pageSource = driver.getPageSource();
            Matcher matcher = SG_ID_PATTERN.matcher(pageSource);
            if (matcher.find()) {
                return matcher.group(0);
            }
        } catch (Exception ignored) {
            // Extraction failed
        }

        return "sg-unknown-manual";
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
