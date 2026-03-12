"""RDS Instance resource handler for Amanoform.

This module automates the creation and deletion of RDS database instances
by navigating the AWS Console's database creation wizard. It's one of
the longest wizards in AWS, requiring extensive scrolling and careful
form interaction. The browser handles it with more patience than any
human operator ever could.
"""

import time

from amanoform.provider import AWSConsoleProvider


class RDSInstanceHandler:
    """Provisions RDS database instances through the AWS Console UI.

    The RDS creation wizard is a marathon of dropdowns, radio buttons,
    and expandable sections. This handler scrolls through every single
    one of them, just like you would at 3am during an incident.
    """

    def create(self, provider: AWSConsoleProvider, attrs: dict) -> dict:
        """Create an RDS instance through the console wizard.

        Navigates to: RDS > Create database
        Then methodically fills out every section of the multi-page form.
        Estimated time: 30-60 seconds. Estimated clicks: ~22.
        """
        page = provider.page

        # Navigate to RDS
        provider.navigate_to_service("rds/home")
        time.sleep(2)

        # Click "Create database"
        page.click('button:has-text("Create database")')
        page.wait_for_load_state("networkidle")
        time.sleep(3)

        # Select creation method — "Standard create" (the thorough way)
        page.locator('label:has-text("Standard create")').click()
        time.sleep(1)

        # Select engine type
        engine = attrs.get("engine", "postgres")
        engine_labels = {
            "postgres": "PostgreSQL",
            "mysql": "MySQL",
            "mariadb": "MariaDB",
            "oracle": "Oracle",
            "sqlserver": "SQL Server",
            "aurora-mysql": "Aurora (MySQL Compatible)",
            "aurora-postgresql": "Aurora (PostgreSQL Compatible)",
        }
        engine_label = engine_labels.get(engine, "PostgreSQL")
        page.locator(
            'label:has-text("{label}")'.format(label=engine_label)
        ).first.click()
        time.sleep(1)

        # Select engine version if specified
        engine_version = attrs.get("engine_version", "")
        if engine_version:
            version_dropdown = page.locator('[data-testid="engine-version-selector"]')
            if version_dropdown.count() > 0:
                version_dropdown.click()
                time.sleep(1)
                page.locator(
                    'option:has-text("{v}")'.format(v=engine_version)
                ).first.click()
                time.sleep(0.5)

        # Select template — "Free tier" if instance class is micro, otherwise "Dev/Test"
        instance_class = attrs.get("instance_class", "db.t3.micro")
        if "micro" in instance_class:
            page.locator('label:has-text("Free tier")').click()
        else:
            page.locator('label:has-text("Dev/Test")').click()
        time.sleep(1)

        # Fill in DB instance identifier
        identifier = attrs.get("identifier", "amanoform-db")
        page.fill(
            'input[data-testid="db-instance-identifier-input"]',
            identifier,
        )
        time.sleep(0.5)

        # Fill in master username
        username = attrs.get("username", "admin")
        username_input = page.locator('input[data-testid="master-username-input"]')
        if username_input.count() > 0:
            username_input.fill(username)
        else:
            # Fallback selector
            page.locator('input[name="masterUsername"]').fill(username)
        time.sleep(0.5)

        # Select "Self managed" credentials and fill in password
        page.locator('label:has-text("Self managed")').first.click()
        time.sleep(0.5)

        password = attrs.get("password", "amanoform-default-pw")
        password_input = page.locator('input[type="password"]').first
        password_input.fill(password)
        time.sleep(0.5)

        # Confirm password
        confirm_input = page.locator('input[type="password"]').nth(1)
        if confirm_input.count() > 0:
            confirm_input.fill(password)
        time.sleep(0.5)

        # Select instance class
        # The console has a dropdown — we need to search for our class
        class_selector = page.locator('[data-testid="instance-class-selector"]')
        if class_selector.count() > 0:
            class_selector.click()
            time.sleep(1)
            page.locator(
                'option:has-text("{cls}")'.format(cls=instance_class)
            ).first.click()
            time.sleep(0.5)

        # Configure storage
        storage = attrs.get("storage", 20)
        storage_input = page.locator('input[data-testid="storage-size-input"]')
        if storage_input.count() > 0:
            storage_input.fill(str(storage))
        time.sleep(0.5)

        # Scroll down — the RDS form is exceptionally long
        page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
        time.sleep(1)

        # Click "Create database"
        page.click('button:has-text("Create database")')
        page.wait_for_load_state("networkidle")
        time.sleep(5)

        # Extract the DB identifier from the success/detail page
        db_identifier = self._extract_identifier(page, identifier)

        return {
            "identifier": db_identifier,
            "engine": engine,
            "engine_version": engine_version,
            "instance_class": instance_class,
            "username": username,
            "storage": storage,
            "status": "creating",
        }

    def destroy(self, provider: AWSConsoleProvider, resource_data: dict) -> None:
        """Delete an RDS instance through the console.

        Navigates to: RDS > Databases > select instance > Actions > Delete
        Then unchecks the final snapshot option and types the confirmation
        string one keystroke at a time, because AWS demands it.
        """
        page = provider.page
        identifier = resource_data.get("identifier", "")

        if not identifier:
            return

        # Navigate to RDS databases list
        provider.navigate_to_service("rds/home#databases")
        time.sleep(3)

        # Find and select the database
        db_row = page.locator(
            'tr:has-text("{id}")'.format(id=identifier)
        ).first
        db_row.locator('input[type="radio"]').click()
        time.sleep(1)

        # Click Actions > Delete
        page.click('button:has-text("Actions")')
        time.sleep(1)
        page.click('text="Delete"')
        time.sleep(2)

        # Uncheck "Create final snapshot" if present
        final_snapshot = page.locator(
            'input[type="checkbox"]:near(:text("Create final snapshot"))'
        )
        if final_snapshot.count() > 0 and final_snapshot.is_checked():
            final_snapshot.uncheck()
            time.sleep(0.5)

        # Check "I acknowledge" checkbox if present
        ack_checkbox = page.locator(
            'input[type="checkbox"]:near(:text("I acknowledge"))'
        )
        if ack_checkbox.count() > 0:
            ack_checkbox.check()
            time.sleep(0.5)

        # Type the confirmation string "delete me"
        confirm_input = page.locator('input[placeholder="delete me"]')
        if confirm_input.count() > 0:
            confirm_input.fill("delete me")
        else:
            # Some versions of the console use a different placeholder
            page.locator('input[type="text"]').last.fill("delete me")
        time.sleep(0.5)

        # Click "Delete"
        page.locator('button:has-text("Delete")').last.click()
        page.wait_for_load_state("networkidle")
        time.sleep(3)

    def detect_drift(self, existing: dict, desired: dict) -> list[str]:
        """Detect configuration drift for RDS instance attributes.

        Most RDS changes require a reboot or replacement. We detect
        the drift — the operator decides what to do about it at 3am.
        """
        drifted = []
        for key in ("engine", "engine_version", "instance_class", "identifier", "storage"):
            if key in desired and existing.get(key) != desired[key]:
                drifted.append(key)
        return drifted

    def _extract_identifier(self, page, expected: str) -> str:
        """Extract the DB instance identifier from the creation result page."""
        try:
            content = page.content()
            if expected in content:
                return expected
        except Exception:
            pass

        return expected
