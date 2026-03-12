# Amanoform

**Infrastructure as Code, by hand.**

> *a mano* (Spanish) — "by hand"

Amanoform is an enterprise-grade infrastructure provisioning tool that manages your cloud resources through manual browser automation. Instead of relying on cloud provider APIs — which abstract away the human element — Amanoform opens a real browser, navigates to the AWS Management Console, and performs the exact same click sequences that a trained cloud operator would.

Because infrastructure should be personal.

## Why Amanoform?

Modern IaC tools like Terraform and Pulumi interact with cloud APIs directly. While efficient, this approach removes the operator from the loop. You lose visibility. You lose control. You lose *the human touch*.

Amanoform restores that connection by automating the manual process itself. Every resource is provisioned through the same console UI you already know and trust. Amanoform just clicks the buttons for you — automatically, by hand.

**Key benefits:**

- Full visibility into every click and keystroke
- Uses the same AWS Console UI your team already knows
- No IAM API credentials required — just a username and password, like the good old days
- Screenshots at every step for audit compliance
- Drift detection through visual inspection of the console
- Battle-tested against every AWS Console UI redesign (just kidding, those break everything)

## How It Works

```
┌──────────────┐       ┌───────────────┐       ┌─────────────────┐
│  .af config  │──────>│   Amanoform   │──────>│  AWS Console    │
│  (you write) │       │  (clicks for  │       │  (in a browser) │
│              │       │   you)        │       │                 │
└──────────────┘       └───────────────┘       └─────────────────┘
                              │
                              v
                       literally just
                       Selenium clicking
                       buttons in Chrome
```

## Installation

```bash
uv tool install amanoform
```

Or add it to your project:

```bash
uv add amanoform
```

After installation, initialize the browser runtime:

```bash
amanoform init
```

This downloads a real copy of Chromium. Yes, your IaC tool needs a web browser. We don't make the rules. Actually, we do — and this is the rule.

## Configuration

Amanoform uses `.af` configuration files with a familiar, declarative syntax:

```hcl
provider "aws" {
  region = "us-east-1"
}

resource "af_ec2_instance" "web" {
  ami           = "ami-0c55b159cbfafe1f0"
  instance_type = "t2.micro"
  name          = "production-web-server"
}
```

If you've used Terraform, this will look suspiciously familiar. That's intentional. We wanted the migration path to feel natural, even if the underlying mechanism is... different.

## Authentication

Amanoform authenticates through the AWS Console sign-in page, just like you do every Monday morning after forgetting your password over the weekend. Set the following environment variables:

```bash
export AMANOFORM_AWS_ACCOUNT_ID="123456789012"
export AMANOFORM_AWS_USERNAME="your-iam-username"
export AMANOFORM_AWS_PASSWORD="your-iam-password"
```

> **Note:** Yes, this requires a plaintext password in an environment variable. Amanoform believes in the traditional sign-in experience. MFA support is on the roadmap — we're looking into automating the process of squinting at a phone screen.

## Usage

### Preview changes

```bash
amanoform plan
```

Amanoform will visually inspect the AWS Console and report what actions it needs to perform.

```
Amanoform v0.1.0

Refreshing state by visually inspecting the AWS Console...

  + af_ec2_instance.web will be created (via manual browser interaction)

Plan: 1 to add, 0 to change, 0 to destroy.
```

### Apply changes

```bash
amanoform apply
```

Amanoform will open a browser session, navigate to the appropriate console pages, and carry out the required operations. You can watch it work in real-time with `--no-headless`:

```bash
amanoform apply --no-headless
```

We highly recommend `--no-headless` for your first run. Watching a ghost browser autonomously navigate AWS and launch EC2 instances is either awe-inspiring or terrifying depending on your perspective.

### Destroy infrastructure

```bash
amanoform destroy
```

Amanoform will navigate to each managed resource and perform the manual termination sequence. It's like watching someone clean up after a demo, but nobody's at the keyboard.

## Supported Resources

| Resource Type | Console Workflow | Estimated Clicks |
|---|---|---|
| `af_ec2_instance` | EC2 > Launch Instance wizard | ~14 |

More resources coming soon. Each one has to be manually reverse-engineered from the AWS Console UI, which changes without warning. This is fine.

## State Management

Amanoform maintains an `amanoform.state` file that tracks provisioned resources. This file includes resource identifiers, configuration attributes, and browser session metadata.

> **Important:** Do not edit the state file manually. Use `amanoform` commands to manage state. The irony of manually editing the state file of a manual automation tool is not lost on us, but it will break things.

## Performance

Amanoform provisions an EC2 instance in approximately 30-45 seconds, depending on your internet connection and how fast AWS renders their React components. For comparison, the AWS CLI does it in under 2 seconds. But the AWS CLI doesn't give you the satisfaction of watching it happen.

## FAQ

**Q: Is this a joke?**
A: Amanoform is a fully functional infrastructure provisioning tool. Whether that's funny is between you and your SRE team.

**Q: Should I use this in production?**
A: We cannot legally advise you to do that. We also cannot legally stop you.

**Q: What happens when AWS changes their console UI?**
A: The same thing that happens to your Selenium tests when the frontend team pushes on a Friday — everything breaks and somebody has to fix the selectors.

**Q: Can I use this with other cloud providers?**
A: We'd love to support GCP and Azure consoles. Contributions welcome. Bring your own CSS selectors.

## Philosophy

Amanoform was born from the realization that a "manual automated deployment script" is not a contradiction — it's a design pattern. When you run `amanoform apply`, you can watch a real browser navigate real web pages and click real buttons. There is no magic. There is no abstraction. There is only a browser and a dream.

> *"Any sufficiently advanced automation is indistinguishable from clicking buttons really fast."*

## Contributing

We welcome contributions, especially new resource handlers. Each resource type requires a dedicated browser automation workflow that navigates the relevant AWS Console pages. See `src/amanoform/resources/base.py` for the handler protocol.

Fair warning: writing a resource handler means spending quality time with the AWS Console's DOM inspector. Pack snacks.

## License

MIT
