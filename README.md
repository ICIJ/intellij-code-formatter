# IntelliJ IDEA Code Formatter (Standalone)

A standalone code formatter that leverages IntelliJ IDEA's powerful formatting engine without requiring a full IDE installation. The CLI recursively formats Java source directories; the underlying library also supports Kotlin, XML, JSON, YAML, Groovy, and Properties files programmatically.

## Features

- **Multiple File Types (Library API)**: Java, Kotlin, Groovy, XML, HTML, JSON, YAML, Properties supported via programmatic `StandaloneFormatter` API
- **Precise Formatting**: Uses IntelliJ IDEA's native formatting engine for identical results
- **Directory-Wide**: Recursively formats every `.java` file under a directory
- **CI-Friendly Check Mode**: Verify formatting without writing changes, with exit codes for CI gating
- **Custom Code Styles**: Load IntelliJ code style configurations exported from the IDE
- **Headless Operation**: No GUI required, perfect for CI/CD pipelines
- **Self-Contained**: Single fat JAR with all dependencies bundled
- **Automatic Setup**: Gradle handles IntelliJ IDEA download and configuration

## Supported File Types

The following file types are supported via the library API (`StandaloneFormatter.formatCode`). The `idea-format` CLI's directory mode currently formats `.java` files only.

| Type       | Extensions                                          |
|------------|-----------------------------------------------------|
| Java       | `.java`                                             |
| Kotlin     | `.kt`, `.kts`, `.gradle.kts`                        |
| Groovy     | `.groovy`, `.gradle`                                |
| XML        | `.xml`, `.xsd`, `.xsl`, `.xslt`, `.wsdl`, `.fxml`, `.pom` |
| HTML       | `.html`, `.htm`, `.xhtml`                           |
| JSON       | `.json`                                             |
| YAML       | `.yaml`, `.yml`                                     |
| Properties | `.properties`                                       |

## Requirements

- **Java 21** or higher
- **Gradle 7.0+** (wrapper included)

## Quick Start

### 1. Build the Project

```bash
git clone <repository-url>
cd vscode-idea-code-formatter

# Download IntelliJ IDEA and build the formatter
./gradlew build
```

> **Note**: The first build downloads IntelliJ IDEA Community Edition (~600MB) and extracts the required JARs. Subsequent builds skip this step.

### 2. Format Files

Using the wrapper script:

```bash
# Format every .java file under a directory (recursively)
./scripts/idea-format src/main/java

# Check formatting without writing changes (exit 1 if anything is non-compliant)
./scripts/idea-format --check src/main/java

# Format with custom code style
./scripts/idea-format --style my-codestyle.xml src/main/java
```

`.git`, `build`, `target`, `out`, and `node_modules` directories are skipped
automatically, at any depth.

Or run directly with Java:

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
     --add-opens java.base/java.io=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -jar build/libs/vscode-idea-code-formatter.jar src/main/java
```

### Custom Code Style

Export your IntelliJ code style and use it:

```bash
# Export from IntelliJ: Settings > Editor > Code Style > Export > IntelliJ IDEA code style XML
./scripts/idea-format --style my-codestyle.xml src/main/java
```

## Programmatic Usage

Use the formatter as a library in your Java applications:

```java
import com.intellij.formatter.core.StandaloneFormatter;
import com.intellij.formatter.core.FormattingException;
import com.intellij.formatter.core.CodeStyleLoadException;
import com.intellij.formatter.config.CodeStyleLoader;

public class Example {
    public static void main(String[] args) throws FormattingException, CodeStyleLoadException {
        // Optional: Load custom code style before formatting
        CodeStyleLoader.loadFromFile("/path/to/code-style.xml");

        // Format Java code
        String javaCode = "public class Test{void method(){}}";
        String formattedJava = StandaloneFormatter.formatCode(javaCode, "Test.java");

        // Format XML
        String xml = "<root><child>text</child></root>";
        String formattedXml = StandaloneFormatter.formatCode(xml, "config.xml");

        // Format JSON
        String json = "{\"name\":\"test\",\"value\":123}";
        String formattedJson = StandaloneFormatter.formatCode(json, "data.json");
    }
}
```

## Building

### Available Gradle Tasks

| Task           | Description                                  |
|----------------|----------------------------------------------|
| `build`        | Build JAR and VSCode extension               |
| `fatJar`       | Create the fat JAR with all dependencies     |
| `buildVscode`  | Build VSCode extension only                  |
| `vscodePackage`| Package VSCode extension as .vsix            |
| `setupIde`     | Download and extract IntelliJ IDEA           |
| `cleanIde`     | Remove downloaded IntelliJ IDEA JARs         |
| `run`          | Run the formatter (use `--args` for options) |

### Build Examples

```bash
# Full build (JAR + VSCode extension)
./gradlew build

# Build only the fat JAR
./gradlew fatJar

# Download IntelliJ IDEA dependencies only
./gradlew setupIde

# Run via Gradle
./gradlew run --args="src/main/java"
./gradlew run --args="--check src/main/java"

# Clean downloaded IDE JARs
./gradlew cleanIde
```

## Project Architecture

```
vscode-idea-code-formatter/
├── build.gradle.kts              # Build configuration with IDE download tasks
├── src/main/java/
│   └── com/intellij/formatter/
│       ├── JetbrainsFormatterApplication.java  # CLI entry point
│       ├── bootstrap/
│       │   ├── FormatterBootstrap.java         # IntelliJ Platform initialization
│       │   ├── HeadlessMockApplication.java    # Headless application mock
│       │   └── SilentLogger.java               # Log suppression
│       ├── config/
│       │   └── CodeStyleLoader.java            # Code style XML loading
│       ├── core/
│       │   ├── StandaloneFormatter.java        # Main formatting API
│       │   ├── JavaFileTraverser.java          # Recursive .java file discovery
│       │   ├── DirectoryFormatter.java         # Directory-wide format/check runs
│       │   ├── FormatReport.java               # Format/check run results
│       │   ├── FormattingException.java        # Formatting errors
│       │   └── CodeStyleLoadException.java     # Config loading errors
│       └── services/                           # IntelliJ service implementations
│           ├── codestyle/                      # Code style providers
│           ├── document/                       # Document management
│           ├── filetype/                       # File type detection
│           ├── formatting/                     # Formatting services
│           ├── project/                        # Project services
│           └── psi/                            # PSI (code model) services
├── scripts/
│   └── idea-format                             # Shell wrapper script
├── vscode-extension/                           # VSCode extension source
└── ide/                                        # Downloaded IntelliJ JARs (gitignored)
```

## How It Works

The formatter creates a minimal IntelliJ Platform environment that runs headlessly:

1. **Bootstrap**: Initializes a mock IntelliJ application with minimal required services
2. **PSI Parsing**: Uses IntelliJ's Program Structure Interface (PSI) to parse source code
3. **Code Style**: Applies configured code style settings via `CodeStyleManager`
4. **Formatting**: Executes IntelliJ's formatting model to produce formatted output

This approach ensures **identical formatting results** to IntelliJ IDEA while running without a GUI.

## VSCode Integration

### VSCode Extension

A ready-to-use VSCode extension is built automatically:

> **Known issue**: The bundled extension currently does **not** work with this
> version of the CLI. It still invokes the formatter JAR using the old
> single-file `--lines`/`--style` contract, which the directory-only CLI no
> longer accepts — every format command from the extension will fail with
> exit code `2`. See [CHANGELOG.md](CHANGELOG.md) for details; fixing the
> extension is tracked as separate follow-up work.

```bash
# Build both JAR and VSCode extension
./gradlew build

# Install the extension
code --install-extension vscode-extension/intellij-formatter-0.1.0.vsix
```

The extension provides:
- Format on demand (`Shift+Alt+F`)
- Format selection (`Ctrl+K Ctrl+F`)
- Per-language enable/disable settings
- Custom code style support via settings

See [vscode-extension/README.md](vscode-extension/README.md) for detailed extension documentation.

### Manual Integration with Tasks

The formatter now operates on entire directories, so this task will format all `.java` files in your workspace rather than just a single file:

```json
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "Format with IntelliJ",
      "type": "shell",
      "command": "java",
      "args": [
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.io=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "-jar", "${workspaceFolder}/build/libs/vscode-idea-code-formatter.jar",
        "${workspaceFolder}"
      ],
      "problemMatcher": []
    }
  ]
}
```

## CI/CD Integration

Example GitHub Actions workflow:

```yaml
- name: Setup Java
  uses: actions/setup-java@v3
  with:
    java-version: '21'
    distribution: 'temurin'

- name: Check Formatting
  run: |
    java --add-opens java.base/java.lang=ALL-UNNAMED \
         --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
         --add-opens java.base/java.io=ALL-UNNAMED \
         --add-opens java.base/java.util=ALL-UNNAMED \
         -jar vscode-idea-code-formatter.jar --check src/main/java
```

## Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| `OutOfMemoryError` | Increase heap: `-Xmx2g` |
| `IllegalAccessError` | Ensure all `--add-opens` flags are present |
| `FileNotFoundException` for JAR | Run `./gradlew build` first |

### Debug Mode

Enable verbose logging by setting the system property:

```bash
java -Didea.log.debug=true ... -jar formatter.jar src/main/java
```

## License

MIT License

## Acknowledgments

Built on top of [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/) by JetBrains.
