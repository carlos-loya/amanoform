"""EC2 Instance resource handler for Amanoform.

This module automates the process of launching an EC2 instance by
navigating the AWS Console's "Launch Instance" wizard. Every click,
every dropdown, every text field — all handled automatically, by hand.
"""

import time

from amanoform.provider import AWSConsoleProvider


class EC2InstanceHandler:
    """Provisions EC2 instances through the AWS Console UI.

    Instead of calling the ec2:RunInstances API (which would be far too
    efficient), this handler opens the EC2 launch wizard in a real browser
    and fills out the form like a diligent cloud operator.
    """

    def create(self, provider: AWSConsoleProvider, attrs: dict) -> dict:
        """Launch an EC2 instance through the console launch wizard.

        Navigates to: EC2 > Instances > Launch Instance
        Then fills out the form fields based on the provided attributes.
        """
        page = provider.page

        # Navigate to EC2 dashboard
        provider.navigate_to_service("ec2/v2/home")
        time.sleep(2)  # Wait for the console to fully render

        # Click "Launch Instance"
        page.click('button:has-text("Launch instance")')
        page.wait_for_load_state("networkidle")
        time.sleep(2)

        # Fill in the instance name
        name = attrs.get("name", "amanoform-instance")
        name_input = page.locator('input[placeholder="Example: My web server"]')
        if name_input.count() > 0:
            name_input.fill(name)
        else:
            # Fallback: try the name tag input
            page.fill('[data-testid="ec2-name-input"]', name)

        # Select the AMI
        ami = attrs.get("ami", "")
        if ami:
            # Click on "Browse more AMIs" and search
            browse_link = page.locator('text="Browse more AMIs"')
            if browse_link.count() > 0:
                browse_link.click()
                time.sleep(1)
                page.fill('[placeholder="Search our AMI catalog"]', ami)
                page.keyboard.press("Enter")
                time.sleep(2)
                # Select the first result
                page.locator('[data-testid="ami-select-button"]').first.click()
                time.sleep(1)

        # Select instance type
        instance_type = attrs.get("instance_type", "t2.micro")
        type_selector = page.locator('[data-testid="instance-type-selector"]')
        if type_selector.count() > 0:
            type_selector.click()
            time.sleep(1)
            page.fill('[data-testid="instance-type-search"]', instance_type)
            time.sleep(1)
            page.locator(
                'tr:has-text("{type}")'.format(type=instance_type)
            ).first.click()

        # Key pair — select "Proceed without a key pair" if no key specified
        key_name = attrs.get("key_name", "")
        if not key_name:
            page.locator('text="Proceed without a key pair"').click()

        # Click "Launch Instance"
        page.click('button:has-text("Launch instance")')
        page.wait_for_load_state("networkidle")
        time.sleep(3)

        # Try to extract the instance ID from the success page
        instance_id = self._extract_instance_id(page)

        return {
            "instance_id": instance_id,
            "ami": ami,
            "instance_type": instance_type,
            "name": name,
            "status": "running",
        }

    def destroy(self, provider: AWSConsoleProvider, resource_data: dict) -> None:
        """Terminate an EC2 instance through the console.

        Navigates to: EC2 > Instances > select instance > Instance State > Terminate
        """
        page = provider.page
        instance_id = resource_data.get("instance_id", "")

        if not instance_id:
            return

        # Navigate to EC2 instances list
        provider.navigate_to_service("ec2/v2/home#Instances")
        time.sleep(2)

        # Search for the instance by ID
        search_input = page.locator('[placeholder="Search"]').first
        search_input.fill(instance_id)
        page.keyboard.press("Enter")
        time.sleep(2)

        # Select the instance
        page.locator(
            'tr:has-text("{id}")'.format(id=instance_id)
        ).first.click()
        time.sleep(1)

        # Open "Instance State" dropdown
        page.click('button:has-text("Instance state")')
        time.sleep(1)

        # Click "Terminate instance"
        page.click('text="Terminate instance"')
        time.sleep(1)

        # Confirm termination
        page.click('button:has-text("Terminate")')
        page.wait_for_load_state("networkidle")
        time.sleep(2)

    def detect_drift(self, existing: dict, desired: dict) -> list[str]:
        """Detect configuration drift by comparing state to config.

        In a perfect world, we'd take a screenshot of the console and
        use computer vision to detect drift. For now, we compare attributes.
        """
        drifted = []
        for key in ("ami", "instance_type", "name"):
            if key in desired and existing.get(key) != desired[key]:
                drifted.append(key)
        return drifted

    def _extract_instance_id(self, page) -> str:
        """Extract the instance ID from the launch success page."""
        try:
            # Look for the instance ID link on the success page
            id_link = page.locator('a[href*="instanceId"]').first
            if id_link.count() > 0:
                text = id_link.inner_text()
                if text.startswith("i-"):
                    return text

            # Fallback: look for any text matching i-xxxxx pattern
            content = page.content()
            import re

            match = re.search(r"i-[0-9a-f]{8,17}", content)
            if match:
                return match.group(0)
        except Exception:
            pass

        return "i-unknown-manual-launch"
