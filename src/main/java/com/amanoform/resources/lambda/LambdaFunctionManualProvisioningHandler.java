package com.amanoform.resources.lambda;

import com.amanoform.provider.AWSManagementConsoleSessionProvider;
import com.amanoform.resources.ResourceHandler;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lambda Function manual provisioning handler for Amanoform.
 *
 * <p>This handler automates the creation of Lambda functions by navigating
 * the AWS Console's function creation page and — in the proudest moment
 * of this project — pasting source code directly into the browser-based
 * Monaco editor. Character by character, if necessary.</p>
 *
 * <p>The Lambda console has a built-in code editor powered by Monaco
 * (the same engine as VS Code). This handler opens that editor and
 * types your function code into it, because uploading a zip file
 * through the API would be too easy.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
public class LambdaFunctionManualProvisioningHandler implements ResourceHandler {

    private static final Pattern FUNCTION_ARN_PATTERN = Pattern.compile(
            "arn:aws:lambda:[a-z0-9-]+:\\d+:function:[\\w-]+");

    /**
     * Create a Lambda function through the console.
     *
     * <p>Navigates to: Lambda &gt; Create function.
     * Then fills in the function configuration and pastes code into
     * the browser-based editor.</p>
     *
     * @param provider the authenticated browser session provider
     * @param attributes resource attributes from the configuration file
     * @return a map containing the function name, ARN, and other output attributes
     */
    @Override
    public Map<String, Object> create(AWSManagementConsoleSessionProvider provider,
                                       Map<String, Object> attributes) {
        WebDriver driver = provider.getDriver();

        // Navigate to Lambda
        provider.navigateToService("lambda/home");
        sleepQuietly(2000);

        // Click "Create function"
        driver.findElement(
                By.xpath("//button[contains(text(),'Create function')]")).click();
        sleepQuietly(3000);

        // Select "Author from scratch" (should be default)
        driver.findElement(
                By.xpath("//label[contains(text(),'Author from scratch')]")).click();
        sleepQuietly(1000);

        // Fill in function name
        String functionName = getStringAttribute(attributes, "function_name", "amanoform-function");
        WebElement nameInput = driver.findElement(
                By.cssSelector("[data-testid='function-name-input']"));
        nameInput.clear();
        nameInput.sendKeys(functionName);
        sleepQuietly(500);

        // Select runtime
        String runtime = getStringAttribute(attributes, "runtime", "python3.12");
        List<WebElement> runtimeDropdowns = driver.findElements(
                By.cssSelector("[data-testid='runtime-selector']"));
        if (!runtimeDropdowns.isEmpty()) {
            runtimeDropdowns.get(0).click();
            sleepQuietly(1000);
            driver.findElement(
                    By.xpath("//option[contains(text(),'" + runtime + "')]")).click();
        }
        sleepQuietly(500);

        // Select architecture (x86_64 by default)
        String architecture = getStringAttribute(attributes, "architecture", "x86_64");
        if ("arm64".equals(architecture)) {
            driver.findElement(
                    By.xpath("//label[contains(text(),'arm64')]")).click();
            sleepQuietly(500);
        }

        // Click "Create function"
        driver.findElement(
                By.xpath("//button[contains(text(),'Create function')]")).click();
        sleepQuietly(5000);

        // If a source file is specified, paste it into the editor
        String sourceFile = getStringAttribute(attributes, "source_file", "");
        if (!sourceFile.isEmpty()) {
            Path sourcePath = Path.of(sourceFile);
            if (Files.exists(sourcePath)) {
                try {
                    String code = Files.readString(sourcePath);
                    pasteCodeIntoEditor(driver, code);
                } catch (IOException e) {
                    // If we can't read the file, we proceed without it.
                    // The function will have the default hello-world code,
                    // which is arguably better than most production code.
                }
            }
        }

        // Configure memory and timeout if non-default
        int memory = getIntAttribute(attributes, "memory", 128);
        int timeout = getIntAttribute(attributes, "timeout", 3);
        if (memory != 128 || timeout != 3) {
            configureGeneralSettings(driver, memory, timeout);
        }

        // Extract function ARN
        String functionArn = extractFunctionArn(driver, functionName);

        Map<String, Object> result = new HashMap<>();
        result.put("function_name", functionName);
        result.put("runtime", runtime);
        result.put("architecture", architecture);
        result.put("memory", memory);
        result.put("timeout", timeout);
        result.put("arn", functionArn);
        result.put("status", "active");
        return result;
    }

    /**
     * Delete a Lambda function through the console.
     *
     * <p>Navigates to: Lambda &gt; Functions &gt; select function &gt;
     * Actions &gt; Delete. Then types "delete" in the confirmation dialog.</p>
     *
     * @param provider the authenticated browser session provider
     * @param resourceData the resource's current state data
     */
    @Override
    public void destroy(AWSManagementConsoleSessionProvider provider,
                        Map<String, Object> resourceData) {
        WebDriver driver = provider.getDriver();
        String functionName = getStringAttribute(resourceData, "function_name", "");

        if (functionName.isEmpty()) {
            return;
        }

        // Navigate to Lambda functions list
        provider.navigateToService("lambda/home#/functions");
        sleepQuietly(2000);

        // Click on the function
        driver.findElement(
                By.xpath("//a[contains(text(),'" + functionName + "')]")).click();
        sleepQuietly(2000);

        // Click Actions > Delete function
        driver.findElement(
                By.xpath("//button[contains(text(),'Actions')]")).click();
        sleepQuietly(1000);
        driver.findElement(
                By.xpath("//*[text()='Delete function']")).click();
        sleepQuietly(1000);

        // Type "delete" in the confirmation input
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

        // Confirm deletion
        List<WebElement> deleteButtons = driver.findElements(
                By.xpath("//button[contains(text(),'Delete')]"));
        if (!deleteButtons.isEmpty()) {
            deleteButtons.get(deleteButtons.size() - 1).click();
        }
        sleepQuietly(2000);
    }

    /**
     * Detect configuration drift for Lambda function attributes.
     */
    @Override
    public List<String> detectDrift(Map<String, Object> existing, Map<String, Object> desired) {
        List<String> drifted = new ArrayList<>();
        String[] keysToCheck = {"function_name", "runtime", "memory", "timeout", "architecture"};

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
     * Paste source code into the Lambda console's Monaco editor.
     *
     * <p>This is either going to be trivially easy or an absolute nightmare.
     * We select all existing code and replace it with our own. In Selenium,
     * this means finding the Monaco editor element, sending Cmd+A to select
     * all, then typing the new code. What could go wrong.</p>
     *
     * @param driver the active WebDriver session
     * @param code the source code to paste
     */
    private void pasteCodeIntoEditor(WebDriver driver, String code) {
        // Click on the code editor area to focus it
        List<WebElement> editors = driver.findElements(By.cssSelector(".monaco-editor"));
        if (editors.isEmpty()) {
            // Try the code tab first
            List<WebElement> codeTabs = driver.findElements(
                    By.xpath("//button[contains(text(),'Code')]"));
            if (!codeTabs.isEmpty()) {
                codeTabs.get(0).click();
                sleepQuietly(2000);
            }
            editors = driver.findElements(By.cssSelector(".monaco-editor"));
        }

        if (!editors.isEmpty()) {
            WebElement editor = editors.get(0);
            editor.click();
            sleepQuietly(500);

            // Select all existing code (Cmd+A)
            editor.sendKeys(Keys.chord(Keys.COMMAND, "a"));
            sleepQuietly(200);

            // Type the new code
            editor.sendKeys(code);
            sleepQuietly(1000);
        }

        // Click "Deploy"
        driver.findElement(
                By.xpath("//button[contains(text(),'Deploy')]")).click();
        sleepQuietly(3000);
    }

    /**
     * Navigate to Configuration &gt; General settings and update memory/timeout.
     *
     * @param driver the active WebDriver session
     * @param memory memory in MB
     * @param timeout timeout in seconds
     */
    private void configureGeneralSettings(WebDriver driver, int memory, int timeout) {
        driver.findElement(
                By.xpath("//button[contains(text(),'Configuration')]")).click();
        sleepQuietly(1000);

        driver.findElement(
                By.xpath("//*[text()='General configuration']")).click();
        sleepQuietly(1000);

        driver.findElement(
                By.xpath("//button[contains(text(),'Edit')]")).click();
        sleepQuietly(2000);

        // Set memory
        List<WebElement> numberInputs = driver.findElements(
                By.cssSelector("input[type='number']"));
        if (!numberInputs.isEmpty()) {
            numberInputs.get(0).clear();
            numberInputs.get(0).sendKeys(String.valueOf(memory));
        }
        sleepQuietly(500);

        // Set timeout
        if (numberInputs.size() > 1) {
            numberInputs.get(1).clear();
            numberInputs.get(1).sendKeys(String.valueOf(timeout));
        }
        sleepQuietly(500);

        driver.findElement(
                By.xpath("//button[contains(text(),'Save')]")).click();
        sleepQuietly(2000);
    }

    /**
     * Extract the function ARN from the detail page.
     *
     * @param driver the active WebDriver session
     * @param functionName the expected function name
     * @return the extracted ARN, or a placeholder
     */
    private String extractFunctionArn(WebDriver driver, String functionName) {
        try {
            String pageSource = driver.getPageSource();
            Matcher matcher = FUNCTION_ARN_PATTERN.matcher(pageSource);
            if (matcher.find()) {
                return matcher.group(0);
            }
        } catch (Exception ignored) {
            // Extraction failed, use fallback
        }

        return "arn:aws:lambda:unknown:000000000000:function:" + functionName;
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

    private int getIntAttribute(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
