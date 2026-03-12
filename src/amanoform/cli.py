"""Amanoform CLI — the standard interface for manual infrastructure automation."""

import sys

import click
from rich.console import Console

from amanoform import __version__
from amanoform.config import load_config
from amanoform.state import State
from amanoform.provider import AWSConsoleProvider
from amanoform.resources import get_resource_handler

console = Console()


def _build_actions(cfg: dict, state: State, targets: tuple[str, ...] = ()) -> list[tuple]:
    """Compare config against state and return a list of planned actions.

    Each action is a tuple of (action_type, resource_key, resource_type, attrs).
    action_type is one of: "create", "update", "destroy".

    If targets is non-empty, only actions matching those resource keys are included.
    """
    actions = []
    seen_keys = set()

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
            seen_keys.add(resource_key)

            if targets and resource_key not in targets:
                continue

            existing = state.get_resource(resource_key)

            if existing is None:
                actions.append(("create", resource_key, resource_type, attrs))
            else:
                changes = handler.detect_drift(existing, attrs)
                if changes:
                    actions.append(("update", resource_key, resource_type, attrs))

    # Resources in state but no longer in config should be destroyed
    for resource_key in state.list_resources():
        if resource_key in seen_keys:
            continue

        if targets and resource_key not in targets:
            continue

        resource_type = resource_key.split(".")[0]
        resource_data = state.get_resource(resource_key)
        actions.append(("destroy", resource_key, resource_type, resource_data))

    return actions


def _print_action(action_type: str, resource_key: str, verb: str = "") -> None:
    """Print a single action line in plan/apply output."""
    symbols = {
        "create": ("[green]+[/green]", "[green]created[/green]"),
        "update": ("[yellow]~[/yellow]", "[yellow]updated[/yellow]"),
        "destroy": ("[red]-[/red]", "[red]destroyed[/red]"),
    }
    symbol, label = symbols[action_type]
    if verb:
        console.print("  {symbol} {key} {verb}".format(symbol=symbol, key=resource_key, verb=verb))
    else:
        console.print(
            "  {symbol} {key} will be {label}".format(
                symbol=symbol, key=resource_key, label=label
            )
        )


def _print_plan_summary(actions: list[tuple]) -> None:
    """Print the Plan: N to add, N to change, N to destroy line."""
    counts = {"create": 0, "update": 0, "destroy": 0}
    for action_type, *_ in actions:
        counts[action_type] += 1
    console.print(
        "\n[bold]Plan:[/bold] {create} to add, {update} to change, {destroy} to destroy.\n".format(
            create=counts["create"],
            update=counts["update"],
            destroy=counts["destroy"],
        )
    )


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
@click.option("--target", "-t", multiple=True, help="Target specific resources (e.g., af_ec2_instance.web).")
def plan(config, target):
    """Generate and display a manual execution plan.

    Shows the infrastructure changes that Amanoform will carry out
    by navigating the AWS Management Console on your behalf.
    """
    console.print("\n[bold]Amanoform v{version}[/bold]\n".format(version=__version__))

    cfg = load_config(config)
    state = State.load()

    console.print("Refreshing state by visually inspecting the AWS Console...\n")

    actions = _build_actions(cfg, state, targets=target)

    if not actions:
        console.print("[green]No changes.[/green] Your infrastructure matches the configuration.")
        console.print("Amanoform has finished inspecting the console.\n")
        return

    descriptions = {
        "create": "(via manual browser interaction)",
        "update": "(the operator will navigate to the resource and modify settings)",
        "destroy": "(the operator will locate and terminate the resource)",
    }
    for action_type, resource_key, *_ in actions:
        _print_action(action_type, resource_key, verb="will be {label} {desc}".format(
            label={"create": "[green]created[/green]", "update": "[yellow]updated[/yellow]", "destroy": "[red]destroyed[/red]"}[action_type],
            desc=descriptions[action_type],
        ))

    _print_plan_summary(actions)


@cli.command()
@click.option("--config", "-c", default="main.af", help="Path to Amanoform configuration file.")
@click.option("--auto-approve", is_flag=True, help="Skip interactive approval of the plan.")
@click.option("--target", "-t", multiple=True, help="Target specific resources (e.g., af_ec2_instance.web).")
@click.option(
    "--headless/--no-headless",
    default=True,
    help="Run browser in headless mode (default: headless).",
)
def apply(config, auto_approve, target, headless):
    """Build or change infrastructure by manually operating the AWS Console.

    Amanoform will open a browser session, navigate to the appropriate
    AWS Console pages, and perform the required click sequences to
    provision your infrastructure. Resources removed from config will
    be terminated through the console.
    """
    console.print("\n[bold]Amanoform v{version}[/bold]\n".format(version=__version__))

    cfg = load_config(config)
    state = State.load()
    provider_cfg = cfg.get("provider", {}).get("aws", {})
    region = provider_cfg.get("region", "us-east-1")

    actions = _build_actions(cfg, state, targets=target)

    if not actions:
        console.print("[green]No changes.[/green] Infrastructure is up to date.\n")
        return

    console.print("Amanoform will perform the following actions:\n")
    for action_type, resource_key, *_ in actions:
        _print_action(action_type, resource_key)

    _print_plan_summary(actions)

    if not auto_approve:
        if not click.confirm("Do you want to perform these actions?"):
            console.print("\n[yellow]Apply cancelled.[/yellow]\n")
            return

    console.print("\n[bold]Opening browser session to AWS Console...[/bold]\n")

    provider = AWSConsoleProvider(region=region, headless=headless)
    provider.login()

    succeeded = {"create": 0, "update": 0, "destroy": 0}
    failed = []

    for action_type, resource_key, resource_type, attrs in actions:
        handler = get_resource_handler(resource_type)
        if handler is None:
            console.print(
                "  [red]Error:[/red] No handler for {type}, skipping {key}.".format(
                    type=resource_type, key=resource_key
                )
            )
            failed.append(resource_key)
            continue

        try:
            if action_type == "create":
                console.print(
                    "  [green]Creating[/green] {key}... (navigating to console)".format(
                        key=resource_key
                    )
                )
                result = handler.create(provider, attrs)
                state.set_resource(resource_key, {**attrs, **result})
                state.save()
                console.print(
                    "  [green]Created[/green] {key}: {result}".format(
                        key=resource_key, result=result
                    )
                )

            elif action_type == "update":
                console.print(
                    "  [yellow]Updating[/yellow] {key}... (navigating to console)".format(
                        key=resource_key
                    )
                )
                existing = state.get_resource(resource_key)
                result = handler.destroy(provider, existing)
                result = handler.create(provider, attrs)
                state.set_resource(resource_key, {**attrs, **result})
                state.save()
                console.print(
                    "  [yellow]Updated[/yellow] {key} (destroyed and recreated — "
                    "the manual way)".format(key=resource_key)
                )

            elif action_type == "destroy":
                console.print(
                    "  [red]Destroying[/red] {key}... (navigating to console)".format(
                        key=resource_key
                    )
                )
                handler.destroy(provider, attrs)
                state.remove_resource(resource_key)
                state.save()
                console.print("  [red]Destroyed[/red] {key}".format(key=resource_key))

            succeeded[action_type] += 1

        except Exception as e:
            console.print(
                "\n  [bold red]Error[/bold red] operating on {key}: {error}".format(
                    key=resource_key, error=e
                )
            )
            console.print(
                "  The browser may have encountered an unexpected console state."
            )
            console.print(
                "  State file has been preserved up to the last successful operation."
            )
            failed.append(resource_key)
            console.print(
                "\n  [yellow]Continuing with remaining resources...[/yellow]\n"
            )

    provider.close()

    console.print(
        "\n[bold green]Apply complete![/bold green] Resources: "
        "{created} added, {updated} changed, {destroyed} destroyed.".format(
            created=succeeded["create"],
            updated=succeeded["update"],
            destroyed=succeeded["destroy"],
        )
    )

    if failed:
        console.print(
            "[bold red]{n} resource(s) failed:[/bold red]".format(n=len(failed))
        )
        for key in failed:
            console.print("  - {key}".format(key=key))
        console.print(
            "\nRun [bold]amanoform plan[/bold] to see the current state "
            "and retry the failed operations.\n"
        )
        sys.exit(1)

    console.print()


@cli.command()
@click.option("--config", "-c", default="main.af", help="Path to Amanoform configuration file.")
@click.option("--auto-approve", is_flag=True, help="Skip interactive approval.")
@click.option("--target", "-t", multiple=True, help="Target specific resources to destroy.")
@click.option(
    "--headless/--no-headless",
    default=True,
    help="Run browser in headless mode (default: headless).",
)
def destroy(config, auto_approve, target, headless):
    """Destroy Amanoform-managed infrastructure.

    Navigates to each managed resource in the AWS Console and performs
    the manual termination sequence. Use --target to destroy specific
    resources, or run without targets to destroy everything.
    """
    console.print("\n[bold]Amanoform v{version}[/bold]\n".format(version=__version__))

    cfg = load_config(config)
    state = State.load()
    provider_cfg = cfg.get("provider", {}).get("aws", {})
    region = provider_cfg.get("region", "us-east-1")

    all_resources = state.list_resources()
    if target:
        resources_to_destroy = [k for k in all_resources if k in target]
        unknown = [t for t in target if t not in all_resources]
        for key in unknown:
            console.print(
                "  [yellow]Warning:[/yellow] {key} is not in the state file, skipping.".format(
                    key=key
                )
            )
    else:
        resources_to_destroy = all_resources

    if not resources_to_destroy:
        console.print("[green]No resources to destroy.[/green]\n")
        return

    console.print("Amanoform will destroy the following resources:\n")
    for key in resources_to_destroy:
        console.print("  [red]-[/red] {key}".format(key=key))

    if not auto_approve:
        if not click.confirm("\nDo you really want to destroy these resources?"):
            console.print("\n[yellow]Destroy cancelled.[/yellow]\n")
            return

    console.print("\n[bold]Opening browser session to AWS Console...[/bold]\n")

    provider = AWSConsoleProvider(region=region, headless=headless)
    provider.login()

    destroyed = 0
    failed = []

    for key in resources_to_destroy:
        resource_type = key.split(".")[0]
        handler = get_resource_handler(resource_type)

        if handler is None:
            console.print(
                "  [red]Error:[/red] No handler for {type}, cannot destroy {key}.".format(
                    type=resource_type, key=key
                )
            )
            failed.append(key)
            continue

        resource_data = state.get_resource(key)
        console.print(
            "  [red]Destroying[/red] {key}... (navigating to console)".format(key=key)
        )

        try:
            handler.destroy(provider, resource_data)
            state.remove_resource(key)
            state.save()
            console.print("  [red]Destroyed[/red] {key}".format(key=key))
            destroyed += 1
        except Exception as e:
            console.print(
                "\n  [bold red]Error[/bold red] destroying {key}: {error}".format(
                    key=key, error=e
                )
            )
            console.print(
                "  Resource may still exist in AWS. State has been preserved."
            )
            failed.append(key)
            console.print(
                "  [yellow]Continuing with remaining resources...[/yellow]\n"
            )

    provider.close()

    console.print(
        "\n[bold green]Destroy complete![/bold green] "
        "Resources: {n} destroyed.".format(n=destroyed)
    )

    if failed:
        console.print(
            "[bold red]{n} resource(s) failed:[/bold red]".format(n=len(failed))
        )
        for key in failed:
            console.print("  - {key}".format(key=key))
        console.print(
            "\nThese resources may still exist in AWS. "
            "Run [bold]amanoform destroy[/bold] to retry.\n"
        )
        sys.exit(1)

    console.print()
