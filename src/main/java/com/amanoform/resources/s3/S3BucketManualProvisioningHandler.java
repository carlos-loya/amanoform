package com.amanoform.resources.s3;

import com.amanoform.provider.AWSManagementConsoleSessionProvider;
import com.amanoform.resources.ResourceHandler;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * S3 Bucket manual provisioning handler for Amanoform.
 *
 * <p>This handler automates the creation and deletion of S3 buckets by
 * navigating the AWS Console's bucket creation wizard. Because calling
 * the {@code CreateBucket} API would be far too straightforward.</p>
 *
 * <p>Navigates the S3 bucket creation form, toggles versioning checkboxes,
 * and clicks "Create bucket" with the confidence of someone who has
 * done this a thousand times — because the browser has.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
public class S3BucketManualProvisioningHandler implements ResourceHandler {

    /**
     * Create an S3 bucket through the console wizard.
     *
     * <p>Navigates to: S3 &gt; Create bucket.
     * Then fills out every field the way a responsible operator would.</p>
     *
     * @param provider the authenticated browser session provider
     * @param attributes resource attributes from the configuration file
     * @return a map containing the bucket name and other output attributes
     */
    @Override
    public Map<String, Object> create(AWSManagementConsoleSessionProvider provider,
                                       Map<String, Object> attributes) {
        WebDriver driver = provider.getDriver();

        // Navigate to S3
        provider.navigateToService("s3/home");
        sleepQuietly(2000);

        // Click "Create bucket"
        driver.findElement(
                By.xpath("//button[contains(text(),'Create bucket')]")).click();
        sleepQuietly(3000);

        // Fill in bucket name
        String bucketName = getStringAttribute(attributes, "bucket", "amanoform-bucket");
        WebElement bucketInput = driver.findElement(
                By.cssSelector("[data-testid='bucket-name-input']"));
        bucketInput.clear();
        bucketInput.sendKeys(bucketName);
        sleepQuietly(1000);

        // Configure bucket versioning
        Object versioningObj = attributes.getOrDefault("versioning", false);
        boolean versioning = Boolean.TRUE.equals(versioningObj);
        if (versioning) {
            List<WebElement> enableLabels = driver.findElements(
                    By.xpath("//label[contains(text(),'Enable')]"));
            if (!enableLabels.isEmpty()) {
                enableLabels.get(0).click();
                sleepQuietly(500);
            }
        }

        // Configure public access settings
        String acl = getStringAttribute(attributes, "acl", "private");
        if ("public-read".equals(acl)) {
            // Uncheck "Block all public access"
            List<WebElement> blockCheckboxes = driver.findElements(
                    By.cssSelector("[data-testid='block-public-access']"));
            if (!blockCheckboxes.isEmpty() && blockCheckboxes.get(0).isSelected()) {
                blockCheckboxes.get(0).click();
                sleepQuietly(500);
            }

            // AWS requires you to acknowledge the warning
            List<WebElement> ackCheckboxes = driver.findElements(
                    By.cssSelector("[data-testid='acknowledge-public-access']"));
            if (!ackCheckboxes.isEmpty() && !ackCheckboxes.get(0).isSelected()) {
                ackCheckboxes.get(0).click();
                sleepQuietly(500);
            }
        }

        // Configure default encryption (SSE-S3 is the default, which is fine)
        String encryption = getStringAttribute(attributes, "encryption", "AES256");
        if ("aws:kms".equals(encryption)) {
            List<WebElement> kmsOptions = driver.findElements(
                    By.xpath("//label[contains(text(),'AWS Key Management Service')]"));
            if (!kmsOptions.isEmpty()) {
                kmsOptions.get(0).click();
                sleepQuietly(500);
            }
        }

        // Click "Create bucket"
        driver.findElement(
                By.xpath("//button[contains(text(),'Create bucket')]")).click();
        sleepQuietly(3000);

        Map<String, Object> result = new HashMap<>();
        result.put("bucket", bucketName);
        result.put("versioning", versioning);
        result.put("acl", acl);
        result.put("encryption", encryption);
        result.put("arn", "arn:aws:s3:::" + bucketName);
        result.put("status", "created");
        return result;
    }

    /**
     * Delete an S3 bucket through the console.
     *
     * <p>This is a multi-step process because AWS requires you to empty
     * the bucket before deleting it. Each step involves its own
     * confirmation dialog, because AWS really wants to make sure.</p>
     *
     * <p>Navigates to: S3 &gt; bucket &gt; Empty &gt; confirm &gt;
     * Delete &gt; confirm</p>
     *
     * @param provider the authenticated browser session provider
     * @param resourceData the resource's current state data
     */
    @Override
    public void destroy(AWSManagementConsoleSessionProvider provider,
                        Map<String, Object> resourceData) {
        WebDriver driver = provider.getDriver();
        String bucketName = getStringAttribute(resourceData, "bucket", "");

        if (bucketName.isEmpty()) {
            return;
        }

        // Navigate to S3 bucket list
        provider.navigateToService("s3/home");
        sleepQuietly(2000);

        // Find and click on the bucket
        driver.findElement(
                By.xpath("//a[contains(text(),'" + bucketName + "')]")).click();
        sleepQuietly(2000);

        // Step 1: Empty the bucket (required before deletion)
        driver.findElement(
                By.xpath("//button[contains(text(),'Empty')]")).click();
        sleepQuietly(1000);

        // AWS requires you to type "permanently delete" to confirm
        List<WebElement> confirmInputs = driver.findElements(
                By.cssSelector("input[placeholder='permanently delete']"));
        if (!confirmInputs.isEmpty()) {
            confirmInputs.get(0).sendKeys("permanently delete");
            sleepQuietly(500);
            driver.findElement(
                    By.xpath("//button[contains(text(),'Empty')]")).click();
            sleepQuietly(3000);
        }

        // Navigate back to S3 bucket list
        provider.navigateToService("s3/home");
        sleepQuietly(2000);

        // Step 2: Delete the bucket
        // Select the bucket via its radio button
        WebElement bucketRow = driver.findElement(
                By.xpath("//tr[contains(.,'" + bucketName + "')]"));
        bucketRow.findElement(By.cssSelector("input[type='radio']")).click();
        sleepQuietly(1000);

        // Click "Delete"
        driver.findElement(
                By.xpath("//button[contains(text(),'Delete')]")).click();
        sleepQuietly(1000);

        // AWS requires you to type the bucket name to confirm deletion
        List<WebElement> nameConfirmInputs = driver.findElements(
                By.cssSelector("input[placeholder='" + bucketName + "']"));
        if (!nameConfirmInputs.isEmpty()) {
            nameConfirmInputs.get(0).sendKeys(bucketName);
        } else {
            // Fallback: find any text input
            List<WebElement> textInputs = driver.findElements(
                    By.cssSelector("input[type='text']"));
            if (!textInputs.isEmpty()) {
                textInputs.get(textInputs.size() - 1).sendKeys(bucketName);
            }
        }

        sleepQuietly(500);
        driver.findElement(
                By.xpath("//button[contains(text(),'Delete bucket')]")).click();
        sleepQuietly(2000);
    }

    /**
     * Detect configuration drift for S3 bucket attributes.
     *
     * <p>Bucket names are immutable (you can't rename a bucket without
     * deleting and recreating it), so a name change is a forced replacement.</p>
     *
     * @param existing current state from the state file
     * @param desired desired state from the configuration
     * @return a list of attribute names that have drifted
     */
    @Override
    public List<String> detectDrift(Map<String, Object> existing, Map<String, Object> desired) {
        List<String> drifted = new ArrayList<>();
        String[] keysToCheck = {"bucket", "versioning", "acl", "encryption"};

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
