"""AWS Console provider for Amanoform.

This module handles the critical task of opening a real web browser
and logging into the AWS Management Console, just as a human operator
would. Because that's what we are — operators.
"""

import os

from playwright.sync_api import sync_playwright, Browser, Page


class AWSConsoleProvider:
    """Manages a browser session authenticated to the AWS Console.

    Instead of using the AWS SDK or CLI (which would defeat the purpose),
    this provider opens a Chromium browser and navigates the AWS Console
    UI to perform infrastructure operations manually, but automatically.
    """

    AWS_CONSOLE_URL = "https://console.aws.amazon.com"
    AWS_SIGNIN_URL = "https://signin.aws.amazon.com/signin"

    def __init__(self, region: str = "us-east-1", headless: bool = True):
        self.region = region
        self.headless = headless
        self._playwright = None
        self._browser: Browser | None = None
        self._page: Page | None = None

    @property
    def page(self) -> Page:
        """The active browser page for console interaction."""
        if self._page is None:
            raise RuntimeError(
                "No active browser session. Did you call provider.login()?"
            )
        return self._page

    def login(self) -> None:
        """Open a browser and authenticate to the AWS Console.

        Credentials are read from environment variables:
            AMANOFORM_AWS_ACCOUNT_ID  — the 12-digit AWS account ID
            AMANOFORM_AWS_USERNAME    — IAM username
            AMANOFORM_AWS_PASSWORD    — IAM password

        This is intentionally less secure than using IAM roles or SSO.
        We believe in the manual approach.
        """
        account_id = os.environ.get("AMANOFORM_AWS_ACCOUNT_ID", "")
        username = os.environ.get("AMANOFORM_AWS_USERNAME", "")
        password = os.environ.get("AMANOFORM_AWS_PASSWORD", "")

        if not all([account_id, username, password]):
            raise RuntimeError(
                "Missing AWS Console credentials.\n"
                "Amanoform requires the following environment variables:\n"
                "  AMANOFORM_AWS_ACCOUNT_ID\n"
                "  AMANOFORM_AWS_USERNAME\n"
                "  AMANOFORM_AWS_PASSWORD\n\n"
                "Unlike other tools, Amanoform authenticates through the browser,\n"
                "just like you would. Set these variables and try again."
            )

        self._playwright = sync_playwright().start()
        self._browser = self._playwright.chromium.launch(headless=self.headless)
        context = self._browser.new_context(
            viewport={"width": 1920, "height": 1080},
            user_agent=(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/120.0.0.0 Safari/537.36 "
                "Amanoform/0.1.0"
            ),
        )
        self._page = context.new_page()

        # Navigate to the IAM user sign-in page
        signin_url = "https://{account}.signin.aws.amazon.com/console".format(
            account=account_id
        )
        self._page.goto(signin_url)
        self._page.wait_for_load_state("networkidle")

        # Fill in credentials — the old-fashioned way
        self._page.fill("#username", username)
        self._page.fill("#password", password)
        self._page.click("#signin_button")
        self._page.wait_for_load_state("networkidle")

        # Set the region by navigating to the region selector
        self._navigate_to_region(self.region)

    def navigate_to_service(self, service_path: str) -> None:
        """Navigate to a specific AWS service page.

        Args:
            service_path: The URL path segment for the service
                         (e.g., 'ec2/v2/home', 's3/home').
        """
        url = "https://{region}.console.aws.amazon.com/{path}?region={region}".format(
            region=self.region, path=service_path
        )
        self.page.goto(url)
        self.page.wait_for_load_state("networkidle")

    def take_screenshot(self, name: str = "screenshot") -> bytes:
        """Capture a screenshot of the current console state.

        Useful for plan output and drift detection via visual diff.
        """
        return self.page.screenshot(full_page=True)

    def close(self) -> None:
        """Close the browser session."""
        if self._browser:
            self._browser.close()
        if self._playwright:
            self._playwright.stop()

    def _navigate_to_region(self, region: str) -> None:
        """Switch the AWS Console to the specified region."""
        url = "https://{region}.console.aws.amazon.com/console/home?region={region}".format(
            region=region
        )
        self.page.goto(url)
        self.page.wait_for_load_state("networkidle")
