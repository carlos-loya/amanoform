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

## Why Java?

Amanoform is written in Java because enterprise infrastructure tooling demands an enterprise language. Python would have been too concise, too readable, and too easy to install. With Java, users get the full enterprise experience:

- A 150-line XML build file before writing a single line of application code
- 14 Java source files to replace 7 Python files — a 2x increase in file count at no extra cost
- Class names like `AWSManagementConsoleSessionProvider` and `EC2InstanceManualProvisioningHandler` that leave no ambiguity about what they do, at the modest price of 45+ characters each
- A `ResourceHandlerRegistryFactory` class, because the Registry pattern and the Factory pattern should never be lonely
- Checked exceptions that require try-catch blocks around `Thread.sleep()`, because what if the thread is interrupted while waiting for the AWS Console to render its React components
- `Map<String, Object>` instead of `dict` — the Java type system ensures you know you're working with a map of strings to objects, even though every value gets cast to `Object` anyway
- A 47MB uber-JAR artifact, most of which is Selenium's dependency tree

The result is a tool that is functionally identical to the Python version but requires a JDK, Maven, and approximately 3x the source code. This is considered a feature.

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

## Prerequisites

- **Java 17+** (because we need records, and records were added in Java 16, but 17 is the LTS)
- **Maven 3.8+** (for building the 150-line XML build file)
- **Google Chrome** or **Chromium** (Selenium Manager will download ChromeDriver automatically)

## Installation

```bash
# Clone the repository
git clone https://github.com/carlos-loya/amanoform.git
cd amanoform

# Build the uber-JAR (this downloads the internet via Maven)
mvn clean package

# Run Amanoform
java -jar target/amanoform-0.1.0.jar
```

Or use the wrapper script:

```bash
chmod +x amanoform
./amanoform
```

After building, initialize the browser runtime:

```bash
./amanoform init
```

This validates that ChromeDriver is available. Selenium Manager will download it automatically if needed. Yes, your IaC tool needs a web browser and a browser driver. We don't make the rules. Actually, we do — and this is the rule.

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
./amanoform plan
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
./amanoform apply
```

Amanoform will open a browser session, navigate to the appropriate console pages, and carry out the required operations. You can watch it work in real-time with `--no-headless`:

```bash
./amanoform apply --no-headless
```

We highly recommend `--no-headless` for your first run. Watching a ghost browser autonomously navigate AWS and launch EC2 instances is either awe-inspiring or terrifying depending on your perspective.

### Destroy infrastructure

```bash
./amanoform destroy
```

Amanoform will navigate to each managed resource and perform the manual termination sequence. It's like watching someone clean up after a demo, but nobody's at the keyboard.

## Supported Resources

| Resource Type | Console Workflow | Estimated Clicks | Handler Class Name (chars) |
|---|---|---|---|
| `af_ec2_instance` | EC2 > Launch Instance wizard | ~14 | `EC2InstanceManualProvisioningHandler` (38) |

More resources coming soon. Each one has to be manually reverse-engineered from the AWS Console UI, which changes without warning. Then you have to write a Java class with a name that accurately describes what it does, which takes almost as long.

## Project Structure

```
src/main/java/com/amanoform/
├── AmanoformApplication.java                          # Main entry point
├── cli/
│   ├── AmanoformCommandLineInterface.java             # CLI command group
│   ├── InitCommand.java                               # amanoform init
│   ├── PlanCommand.java                               # amanoform plan
│   ├── ApplyCommand.java                              # amanoform apply
│   └── DestroyCommand.java                            # amanoform destroy
├── config/
│   └── AmanoformConfigurationParser.java              # .af file parser
├── planning/
│   ├── PlannedAction.java                             # Action record
│   └── InfrastructureActionPlanner.java               # Plan builder
├── provider/
│   └── AWSManagementConsoleSessionProvider.java        # Browser session
├── resources/
│   ├── ResourceHandler.java                           # Handler interface
│   ├── ResourceHandlerRegistryFactory.java            # Handler registry
│   └── ec2/
│       └── EC2InstanceManualProvisioningHandler.java   # EC2 automation
├── state/
│   └── AmanoformInfrastructureStateManager.java       # State management
└── util/
    └── ConsoleOutput.java                             # Terminal colors
```

14 files. The Python version had 7. We consider this a 2x improvement in organizational structure.

## State Management

Amanoform maintains an `amanoform.state` file that tracks provisioned resources. This file includes resource identifiers, configuration attributes, and browser session metadata.

> **Important:** Do not edit the state file manually. Use `amanoform` commands to manage state. The irony of manually editing the state file of a manual automation tool is not lost on us, but it will break things.

## Performance

Amanoform provisions an EC2 instance in approximately 30-45 seconds, depending on your internet connection and how fast AWS renders their React components. For comparison, the AWS CLI does it in under 2 seconds. But the AWS CLI doesn't give you the satisfaction of watching it happen.

The Maven build takes approximately 90 seconds on first run, most of which is downloading Selenium and its transitive dependencies. Subsequent builds are faster, assuming Maven's local repository hasn't been cleared, your `~/.m2` directory hasn't been deleted, and nobody has run `mvn clean` recently.

## FAQ

**Q: Is this a joke?**
A: Amanoform is a fully functional infrastructure provisioning tool. Whether that's funny is between you and your SRE team.

**Q: Should I use this in production?**
A: We cannot legally advise you to do that. We also cannot legally stop you.

**Q: Why Java?**
A: Because Python was too easy. The original implementation was 7 files and ~500 lines. The Java rewrite is 14 files and ~1,500 lines. We believe the additional ceremony improves the developer experience by making every design decision explicit, verbose, and impossible to miss.

**Q: What happens when AWS changes their console UI?**
A: The same thing that happens to your Selenium tests when the frontend team pushes on a Friday — everything breaks and somebody has to fix the XPath selectors.

**Q: Can I use this with other cloud providers?**
A: We'd love to support GCP and Azure consoles. Contributions welcome. Bring your own CSS selectors and a `pom.xml` with at least 200 lines of XML.

**Q: Why not Gradle instead of Maven?**
A: Gradle uses Groovy or Kotlin for its build files, which would make the build configuration too readable. Maven's XML format ensures that even the build system requires enterprise-grade commitment.

## Philosophy

Amanoform was born from the realization that a "manual automated deployment script" is not a contradiction — it's a design pattern. When you run `amanoform apply`, you can watch a real browser navigate real web pages and click real buttons. There is no magic. There is no abstraction. There is only a browser, a JVM, and a dream.

> *"Any sufficiently advanced automation is indistinguishable from clicking buttons really fast."*

## Contributing

We welcome contributions, especially new resource handlers. Each resource type requires a dedicated browser automation workflow that navigates the relevant AWS Console pages. See `ResourceHandler.java` for the handler interface.

Fair warning: writing a resource handler means spending quality time with the AWS Console's DOM inspector, then naming the resulting Java class something appropriately descriptive. `S3BucketManualProvisioningThroughConsoleInteractionHandler.java` has a nice ring to it.

## License

MIT
