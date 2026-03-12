"""Amanoform CLI — the standard interface for manual infrastructure automation."""

import click
from rich.console import Console

from amanoform import __version__
from amanoform.config import load_config
from amanoform.state import State
from amanoform.provider import AWSConsoleProvider
from amanoform.resources import get_resource_handler

console = Console()


@click.group()
@click.version_option(version=__version__, prog_name="amanoform")
def cli():
    """Amanoform — Infrastructure as Code, by hand.

    Provision and manage cloud infrastructure through enterprise-grade
    manual browser automation.
    """


@cli.command()
def init():
    """Prepare your working directory for Amanoform operations.

    Installs the required browser binaries and validates your
    configuration files.
    """
    console.print("\n[bold]Amanoform v{version}[/bold]\n".format(version=__version__))
    console.print("Initializing the Amanoform provider plugins...")
    console.print("  - Installing browser automation runtime (Chromium)...")

    from playwright.sync_api import sync_playwright

    with sync_playwright() as p:
        # Trigger browser download if not present
        try:
            browser = p.chromium.launch(headless=True)
            browser.close()
        except Exception:
            console.print("    [yellow]Browser not found. Installing...[/yellow]")
            import subprocess
            subprocess.run(["playwright", "install", "chromium"], check=True)

    console.print("  - Browser runtime installed successfully.")
    console.print("\n[bold green]Amanoform has been successfully initialized![/bold green]")
    console.print("\nYou may now begin working with Amanoform. Try running")
    console.print('"amanoform plan" to see any changes that are required')
    console.print("for your infrastructure.\n")


@cli.command()
@click.option("--config", "-c", default="main.af", help="Path to Amanoform configuration file.")
def plan(config):
    """Generate and display a manual execution plan.

    Shows the infrastructure changes that Amanoform will carry out
    by navigating the AWS Management Console on your behalf.
    """
    console.print("\n[bold]Amanoform v{version}[/bold]\n".format(version=__version__))

    cfg = load_config(config)
    state = State.load()
    provider_cfg = cfg.get("provider", {}).get("aws", {})

    console.print("Refreshing state by visually inspecting the AWS Console...\n")

    actions = []
    for resource_type, resources in cfg.get("resource", {}).items():
        handler = get_resource_handler(resource_type)
        if handler is None:
            console.print(
                "  [red]Error:[/red] No manual workflow defined for resource type "
                '"{type}". Cannot proceed.'.format(type=resource_type)
            )
            continue

        for name, attrs in resources.items():
            resource_key = "{type}.{name}".format(type=resource_type, name=name)
            existing = state.get_resource(resource_key)

            if existing is None:
                actions.append(("create", resource_key, attrs))
                console.print(
                    "  [green]+[/green] {key} will be [green]created[/green] "
                    "(via manual browser interaction)".format(key=resource_key)
                )
            else:
                changes = handler.detect_drift(existing, attrs)
                if changes:
                    actions.append(("update", resource_key, attrs))
                    console.print(
                        "  [yellow]~[/yellow] {key} will be [yellow]updated[/yellow] "
                        "(the operator will navigate to the resource and modify settings)".format(
                            key=resource_key
                        )
                    )

    # Check for resources in state but not in config (destroy)
    for resource_key in state.list_resources():
        resource_type = resource_key.rsplit(".", 1)[0]
        parts = resource_key.split(".")
        if len(parts) == 2:
            rtype, rname = parts
            if rname not in cfg.get("resource", {}).get(rtype, {}):
                actions.append(("destroy", resource_key, {}))
                console.print(
                    "  [red]-[/red] {key} will be [red]destroyed[/red] "
                    "(the operator will locate and terminate the resource)".format(
                        key=resource_key
                    )
                )

    if not actions:
        console.print("[green]No changes.[/green] Your infrastructure matches the configuration.")
        console.print("Amanoform has finished inspecting the console.\n")
        return

    console.print(
        "\n[bold]Plan:[/bold] {create} to add, {update} to change, {destroy} to destroy.\n".format(
            create=sum(1 for a in actions if a[0] == "create"),
            update=sum(1 for a in actions if a[0] == "update"),
            destroy=sum(1 for a in actions if a[0] == "destroy"),
        )
    )


@cli.command()
@click.option("--config", "-c", default="main.af", help="Path to Amanoform configuration file.")
@click.option("--auto-approve", is_flag=True, help="Skip interactive approval of the plan.")
@click.option(
    "--headless/--no-headless",
    default=True,
    help="Run browser in headless mode (default: headless).",
)
def apply(config, auto_approve, headless):
    """Build or change infrastructure by manually operating the AWS Console.

    Amanoform will open a browser session, navigate to the appropriate
    AWS Console pages, and perform the required click sequences to
    provision your infrastructure.
    """
    console.print("\n[bold]Amanoform v{version}[/bold]\n".format(version=__version__))

    cfg = load_config(config)
    state = State.load()
    provider_cfg = cfg.get("provider", {}).get("aws", {})
    region = provider_cfg.get("region", "us-east-1")

    actions = []
    for resource_type, resources in cfg.get("resource", {}).items():
        handler = get_resource_handler(resource_type)
        if handler is None:
            console.print(
                '  [red]Error:[/red] No manual workflow for "{type}".'.format(type=resource_type)
            )
            return

        for name, attrs in resources.items():
            resource_key = "{type}.{name}".format(type=resource_type, name=name)
            existing = state.get_resource(resource_key)
            if existing is None:
                actions.append(("create", resource_key, resource_type, name, attrs))

    if not actions:
        console.print("[green]No changes.[/green] Infrastructure is up to date.\n")
        return

    console.print("Amanoform will perform the following actions:\n")
    for action, key, *_ in actions:
        console.print("  [green]+[/green] {key} will be [green]created[/green]".format(key=key))

    console.print(
        "\n[bold]Plan:[/bold] {n} to add, 0 to change, 0 to destroy.\n".format(n=len(actions))
    )

    if not auto_approve:
        if not click.confirm("Do you want to perform these actions?"):
            console.print("\n[yellow]Apply cancelled.[/yellow]\n")
            return

    console.print("\n[bold]Opening browser session to AWS Console...[/bold]\n")

    provider = AWSConsoleProvider(region=region, headless=headless)
    provider.login()

    for action, key, resource_type, name, attrs in actions:
        console.print(
            "  [green]Creating[/green] {key}... (navigating to console)".format(key=key)
        )
        handler = get_resource_handler(resource_type)
        result = handler.create(provider, attrs)
        state.set_resource(key, {**attrs, **result})
        console.print("  [green]Created[/green] {key}: {result}".format(key=key, result=result))

    state.save()
    provider.close()

    console.print(
        "\n[bold green]Apply complete![/bold green] "
        "Resources: {n} added, 0 changed, 0 destroyed.\n".format(n=len(actions))
    )


@cli.command()
@click.option("--config", "-c", default="main.af", help="Path to Amanoform configuration file.")
@click.option("--auto-approve", is_flag=True, help="Skip interactive approval.")
@click.option(
    "--headless/--no-headless",
    default=True,
    help="Run browser in headless mode (default: headless).",
)
def destroy(config, auto_approve, headless):
    """Destroy all Amanoform-managed infrastructure.

    Navigates to each managed resource in the AWS Console and performs
    the manual termination sequence.
    """
    console.print("\n[bold]Amanoform v{version}[/bold]\n".format(version=__version__))

    cfg = load_config(config)
    state = State.load()
    provider_cfg = cfg.get("provider", {}).get("aws", {})
    region = provider_cfg.get("region", "us-east-1")

    resources = state.list_resources()
    if not resources:
        console.print("[green]No resources to destroy.[/green]\n")
        return

    console.print("Amanoform will destroy the following resources:\n")
    for key in resources:
        console.print("  [red]-[/red] {key}".format(key=key))

    if not auto_approve:
        if not click.confirm("\nDo you really want to destroy all resources?"):
            console.print("\n[yellow]Destroy cancelled.[/yellow]\n")
            return

    console.print("\n[bold]Opening browser session to AWS Console...[/bold]\n")

    provider = AWSConsoleProvider(region=region, headless=headless)
    provider.login()

    for key in resources:
        resource_type = key.split(".")[0]
        handler = get_resource_handler(resource_type)
        resource_data = state.get_resource(key)
        console.print(
            "  [red]Destroying[/red] {key}... (navigating to console)".format(key=key)
        )
        handler.destroy(provider, resource_data)
        state.remove_resource(key)
        console.print("  [red]Destroyed[/red] {key}".format(key=key))

    state.save()
    provider.close()

    console.print(
        "\n[bold green]Destroy complete![/bold green] "
        "Resources: {n} destroyed.\n".format(n=len(resources))
    )
