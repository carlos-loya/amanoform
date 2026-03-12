"""Security Group resource handler for Amanoform.

This module automates the creation and deletion of VPC Security Groups
by navigating the AWS Console. Each firewall rule requires its own
sequence of clicks — type, protocol, port range, source. A security
group with 10 rules means roughly 46 clicks. This is peak Amanoform.
"""

import time

from amanoform.provider import AWSConsoleProvider


def _parse_rules(rules_str: str) -> list[dict]:
    """Parse a comma-separated rules string into structured rules.

    Format: "port:protocol:source,port:protocol:source,..."
    Examples:
        "80:tcp:0.0.0.0/0"
        "80:tcp:0.0.0.0/0,22:tcp:10.0.0.0/8"
        "0:all:0.0.0.0/0"
    """
    if not rules_str:
        return []

    rules = []
    for rule_str in rules_str.split(","):
        parts = rule_str.strip().split(":")
        if len(parts) == 3:
            port, protocol, source = parts
            rules.append({
                "port": port.strip(),
                "protocol": protocol.strip(),
                "source": source.strip(),
            })
    return rules


class SecurityGroupHandler:
    """Provisions VPC Security Groups through the AWS Console UI.

    Each inbound and outbound rule requires clicking "Add rule", selecting
    a type from a dropdown, entering a port range, and specifying a source.
    The handler repeats this process for every rule in the configuration,
    with the mechanical patience that only a browser automation script
    can provide.
    """

    def create(self, provider: AWSConsoleProvider, attrs: dict) -> dict:
        """Create a Security Group through the console.

        Navigates to: VPC > Security Groups > Create security group
        Then clicks "Add rule" for each inbound and outbound rule.
        """
        page = provider.page

        # Navigate to VPC Security Groups
        provider.navigate_to_service("vpc/home#SecurityGroups")
        time.sleep(2)

        # Click "Create security group"
        page.click('button:has-text("Create security group")')
        page.wait_for_load_state("networkidle")
        time.sleep(2)

        # Fill in security group name
        sg_name = attrs.get("name", "amanoform-sg")
        page.fill(
            'input[data-testid="security-group-name-input"]',
            sg_name,
        )
        time.sleep(0.5)

        # Fill in description
        description = attrs.get("description", "Managed by Amanoform")
        desc_input = page.locator(
            'input[data-testid="security-group-description-input"]'
        )
        if desc_input.count() > 0:
            desc_input.fill(description)
        else:
            page.locator('textarea:near(:text("Description"))').first.fill(description)
        time.sleep(0.5)

        # Select VPC if specified
        vpc_id = attrs.get("vpc_id", "")
        if vpc_id:
            vpc_dropdown = page.locator('[data-testid="vpc-selector"]')
            if vpc_dropdown.count() > 0:
                vpc_dropdown.click()
                time.sleep(1)
                page.locator(
                    'option:has-text("{vpc}")'.format(vpc=vpc_id)
                ).first.click()
                time.sleep(0.5)

        # Add inbound rules
        ingress_rules = _parse_rules(attrs.get("ingress_rules", ""))
        for rule in ingress_rules:
            self._add_rule(page, "inbound", rule)

        # Add outbound rules
        egress_rules = _parse_rules(attrs.get("egress_rules", ""))
        for rule in egress_rules:
            self._add_rule(page, "outbound", rule)

        # Scroll to bottom and click "Create security group"
        page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
        time.sleep(1)
        page.click('button:has-text("Create security group")')
        page.wait_for_load_state("networkidle")
        time.sleep(3)

        # Extract security group ID
        sg_id = self._extract_sg_id(page, sg_name)

        return {
            "sg_id": sg_id,
            "name": sg_name,
            "description": description,
            "vpc_id": vpc_id,
            "ingress_rules": attrs.get("ingress_rules", ""),
            "egress_rules": attrs.get("egress_rules", ""),
            "status": "created",
        }

    def destroy(self, provider: AWSConsoleProvider, resource_data: dict) -> None:
        """Delete a Security Group through the console.

        Navigates to: VPC > Security Groups > select > Actions > Delete
        """
        page = provider.page
        sg_id = resource_data.get("sg_id", "")
        sg_name = resource_data.get("name", "")

        if not sg_id and not sg_name:
            return

        # Navigate to VPC Security Groups
        provider.navigate_to_service("vpc/home#SecurityGroups")
        time.sleep(2)

        # Search for the security group
        search_term = sg_id or sg_name
        search_input = page.locator('[placeholder="Search"]').first
        search_input.fill(search_term)
        page.keyboard.press("Enter")
        time.sleep(2)

        # Select the security group
        sg_row = page.locator(
            'tr:has-text("{term}")'.format(term=search_term)
        ).first
        sg_row.locator('input[type="checkbox"]').click()
        time.sleep(1)

        # Actions > Delete security groups
        page.click('button:has-text("Actions")')
        time.sleep(1)
        page.click('text="Delete security groups"')
        time.sleep(1)

        # Type "delete" to confirm
        confirm_input = page.locator('input[placeholder="delete"]')
        if confirm_input.count() > 0:
            confirm_input.fill("delete")
        else:
            page.locator('input[type="text"]').last.fill("delete")
        time.sleep(0.5)

        page.locator('button:has-text("Delete")').last.click()
        page.wait_for_load_state("networkidle")
        time.sleep(2)

    def detect_drift(self, existing: dict, desired: dict) -> list[str]:
        """Detect configuration drift for Security Group attributes.

        Rule changes are detected by comparing the serialized rule strings.
        """
        drifted = []
        for key in ("name", "description", "vpc_id", "ingress_rules", "egress_rules"):
            if key in desired and existing.get(key) != desired[key]:
                drifted.append(key)
        return drifted

    def _add_rule(self, page, direction: str, rule: dict) -> None:
        """Add a single inbound or outbound rule by clicking through the form.

        Each rule requires: click "Add rule", select type, enter port,
        enter source/destination. ~4 clicks per rule.
        """
        section_text = "Inbound rules" if direction == "inbound" else "Outbound rules"

        # Find the "Add rule" button in the correct section
        section = page.locator(
            'div:has(:text("{text}"))'.format(text=section_text)
        ).first
        add_button = section.locator('button:has-text("Add rule")')
        add_button.click()
        time.sleep(1)

        # Get the last rule row (the one we just added)
        rule_rows = section.locator("tr").all()
        last_row = rule_rows[-1] if rule_rows else section

        # Select protocol/type
        protocol = rule.get("protocol", "tcp")
        port = rule.get("port", "")

        type_dropdown = last_row.locator("select").first
        if type_dropdown.count() > 0:
            if protocol == "all" and port == "0":
                type_dropdown.select_option(label="All traffic")
            elif protocol == "tcp" and port == "80":
                type_dropdown.select_option(label="HTTP")
            elif protocol == "tcp" and port == "443":
                type_dropdown.select_option(label="HTTPS")
            elif protocol == "tcp" and port == "22":
                type_dropdown.select_option(label="SSH")
            else:
                type_dropdown.select_option(label="Custom TCP")
                time.sleep(0.5)
                # Fill in port range
                port_input = last_row.locator('input[placeholder="Port range"]')
                if port_input.count() > 0:
                    port_input.fill(port)
        time.sleep(0.5)

        # Fill in source/destination
        source = rule.get("source", "0.0.0.0/0")
        source_input = last_row.locator(
            'input[placeholder="Source"], input[placeholder="Destination"]'
        ).first
        if source_input.count() > 0:
            source_input.fill(source)
        time.sleep(0.5)

    def _extract_sg_id(self, page, sg_name: str) -> str:
        """Extract the security group ID from the creation result."""
        try:
            content = page.content()
            import re

            match = re.search(r"sg-[0-9a-f]{8,17}", content)
            if match:
                return match.group(0)
        except Exception:
            pass

        return "sg-unknown-manual"
