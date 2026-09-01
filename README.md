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
- **Automatic Setup**: Maven handles IntelliJ IDEA download and configuration

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
- **Maven 3.9+** (wrapper included)

## Quick Start

### 1. Build the Project

```bash
git clone <repository-url>

# Download and extract IntelliJ IDEA first, as its own command
./mvnw generate-sources

# Then build the formatter
./mvnw package
```

> **Note**: These must be two separate `./mvnw` invocations, not `./mvnw generate-sources package`. Maven resolves this project's dependencies once, up front, based on whatever already exists on disk when the command starts — before running any phase, including `generate-sources` itself. On a fresh clone, `target/ide` doesn't exist yet, so a single invocation that includes `package` fails immediately, before ever reaching the phase that would download IntelliJ IDEA and create those files. Running `generate-sources` as its own invocation first populates `target/ide`; only then does a second invocation find the files already present. This two-step is only needed on a fresh clone, or any time after `./mvnw clean` (which removes `target/`, including the extracted JARs) — once `target/ide` exists, ordinary `./mvnw package` runs work fine on their own.
>
> The first build downloads IntelliJ IDEA Community Edition (~1.5GB) and extracts the required JARs. Subsequent builds skip the download (cached); extraction re-runs each time.

### 2. Format Files

Using the wrapper script:

```bash
# Format every .java file under a directory (recursively)
./formatter-core/scripts/idea-format src/main/java

# Check formatting without writing changes (exit 1 if anything is non-compliant)
./formatter-core/scripts/idea-format --check src/main/java

# Format with custom code style
./formatter-core/scripts/idea-format --style my-codestyle.xml src/main/java
```

`.git`, `build`, `target`, `out`, and `node_modules` directories are skipped
automatically, at any depth.

### Custom Code Style

Export your IntelliJ code style and use it:

```bash
# Export from IntelliJ: Settings > Editor > Code Style > Export > IntelliJ IDEA code style XML
./formatter-core/scripts/idea-format --style my-codestyle.xml src/main/java
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

## Maven Plugin

Besides the CLI, this project ships a Maven plugin that lints or auto-fixes
Java formatting as part of a normal build, using the codestyle fixed in
`maven-plugin/src/main/resources/icij-codestyle.xml` — no per-project
configuration needed.

Add it to your `pom.xml` to fail the build on non-compliant files (bound to
the `process-sources` phase):

```xml
<plugin>
  <groupId>org.icij</groupId>
  <artifactId>intellij-code-formatter-maven-plugin</artifactId>
  <version>1.0-SNAPSHOT</version>
  <executions>
    <execution>
      <goals><goal>check</goal></goals>
    </execution>
  </executions>
</plugin>
```

To fix files instead of failing the build, use the `format` goal instead of
`check`. You can also run either goal directly without editing your `pom.xml`:

```bash
mvn org.icij:intellij-code-formatter-maven-plugin:format
```

Both goals only scan `${project.build.sourceDirectory}` (`src/main/java` by
default) for `.java` files. Internally, each goal forks a `java` subprocess
running `formatter-core`'s CLI with the `--add-opens` flags it requires —
this is necessary because those flags can't be applied to your build's
already-running Maven JVM, so expect each bound module to pay a small
(~2-3 second) JVM startup cost.

### Packaging and the first run

The plugin is **self-contained**: `formatter-core`'s jar is embedded inside the
plugin jar as a nested resource, so consumers resolve a single artifact and
never need the core's coordinates in their `<repositories>`.

Because the mojo forks `java -jar`, it needs the core as a real file on disk.
On the very first execution it extracts the embedded jar to:

```
${settings.localRepository}/.cache/icij-formatter/intellij-code-formatter-<plugin version>.jar
```

That costs one ~157 MB write, once per plugin version per machine — not once
per module and not once per build. Every later execution finds the cached file
and reuses it. Keeping the cache inside the local repository means CI jobs that
already cache `~/.m2` get the extracted jar for free.

Two consequences worth knowing:

- the plugin jar itself is ~157 MB, downloaded once at plugin resolution;
- with the cache, the core ends up on disk twice (inside the plugin jar in
  `~/.m2`, and extracted). Trimming the core's shade is tracked separately.

If you consume the plugin from JitPack, remember that Maven looks up *plugins*
in `<pluginRepositories>`, not `<repositories>`:

```xml
<pluginRepositories>
  <pluginRepository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </pluginRepository>
</pluginRepositories>
```

## Building

### Available Maven Goals

| Goal                | Description                                             |
|----------------------|----------------------------------------------------------|
| `package`            | Compile, test, and build the fat JAR                    |
| `test`               | Run the test suite                                      |
| `exec:java`           | Run the formatter (use `-Dexec.args` for options)       |
| `clean`               | Remove all build output, including downloaded/extracted IntelliJ IDEA JARs |

### Build Examples

```bash
# Full build (fat JAR)
./mvnw package

# Run tests only
./mvnw test

# Run via Maven
./mvnw -pl formatter-core exec:java -Dexec.args="src/main/java"
./mvnw -pl formatter-core exec:java -Dexec.args="--check src/main/java"

# Clean everything, including downloaded IDE JARs
./mvnw clean
```

> After `./mvnw clean`, rebuild with the two-step invocation from [Quick Start](#1-build-the-project) (`./mvnw generate-sources` then `./mvnw package`) — a single `./mvnw package` will fail since `target/ide` was just removed.

## Project Architecture

```
├── pom.xml                       # Reactor aggregator (packaging=pom)
├── formatter-core/               # CLI + library (unchanged from before the reactor split)
│   ├── pom.xml                   # Build configuration with IDE download/extraction plugins
│   ├── src/main/java/
│   │   └── com/intellij/formatter/
│   │       ├── JetbrainsFormatterApplication.java  # CLI entry point
│   │       ├── bootstrap/
│   │       │   ├── FormatterBootstrap.java         # IntelliJ Platform initialization
│   │       │   ├── HeadlessMockApplication.java    # Headless application mock
│   │       │   └── SilentLogger.java               # Log suppression
│   │       ├── config/
│   │       │   └── CodeStyleLoader.java            # Code style XML loading
│   │       ├── core/
│   │       │   ├── StandaloneFormatter.java        # Main formatting API
│   │       │   ├── JavaFileTraverser.java          # Recursive .java file discovery
│   │       │   ├── DirectoryFormatter.java         # Directory-wide format/check runs
│   │       │   ├── FormatReport.java               # Format/check run results
│   │       │   ├── FormattingException.java        # Formatting errors
│   │       │   └── CodeStyleLoadException.java     # Config loading errors
│   │       └── services/                           # IntelliJ service implementations
│   ├── scripts/
│   │   └── idea-format                             # Shell wrapper script
│   └── target/ide/                                 # Downloaded IntelliJ JARs, generated by Maven build (gitignored)
└── maven-plugin/                 # Maven plugin (this module never touches IntelliJ classes directly —
    ├── pom.xml                   # it forks formatter-core's shaded jar as a subprocess)
    └── src/main/
        ├── java/org/icij/formatter/maven/
        │   ├── AbstractFormatterMojo.java          # Shared parameters, subprocess fork, exit code handling
        │   ├── CheckMojo.java                      # `check` goal — lints, fails the build, writes nothing
        │   └── FormatMojo.java                     # `format` goal — rewrites non-compliant files
        └── resources/
            └── icij-codestyle.xml                  # Fixed default codestyle bundled into the plugin jar
```

## How It Works

The formatter creates a minimal IntelliJ Platform environment that runs headlessly:

1. **Bootstrap**: Initializes a mock IntelliJ application with minimal required services
2. **PSI Parsing**: Uses IntelliJ's Program Structure Interface (PSI) to parse source code
3. **Code Style**: Applies configured code style settings via `CodeStyleManager`
4. **Formatting**: Executes IntelliJ's formatting model to produce formatted output

This approach ensures **identical formatting results** to IntelliJ IDEA while running without a GUI.

## Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| `OutOfMemoryError` | Increase heap: `-Xmx2g` |
| `IllegalAccessError` | Ensure all `--add-opens` flags are present |
| `FileNotFoundException` for JAR | Run `./mvnw package` first |

### Debug Mode

Enable verbose logging by setting the system property:

```bash
java -Didea.log.debug=true ... -jar formatter.jar src/main/java
```

## Release process

To create a release, push a tag matching x.y.z where x, y and z are integers which will trigger a github release using 
github actions. The released artifact will then be available in jitpack repositories using 	
```
<dependency>
<groupId>com.github.icij</groupId>
<artifactId>intellij-code-formatter</artifactId>
<version>Tag</version>
</dependency>
```
## License

MIT License

## Acknowledgments

Built on top of [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/) by JetBrains.
