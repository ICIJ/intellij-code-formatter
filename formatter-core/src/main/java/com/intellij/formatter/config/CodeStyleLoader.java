package com.intellij.formatter.config;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.psi.codeStyle.ProjectCodeStyleSettingsManager;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static com.intellij.formatter.bootstrap.FormatterBootstrap.getProject;
import static com.intellij.formatter.bootstrap.FormatterBootstrap.initialize;

/**
 * Utility class for loading IntelliJ IDEA code style settings from XML files.
 *
 * <p>This loader supports various code style XML formats exported from IntelliJ IDEA:</p>
 * <ul>
 *     <li>Direct code_scheme exports</li>
 *     <li>Project-level code style configurations</li>
 *     <li>Component-wrapped configurations</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Load and apply a custom code style before formatting
 * new CodeStyleLoader("/path/to/my-code-style.xml").applyFromXml();
 *
 * // Now format code with the loaded style
 * String formatted = StandaloneFormatter.formatCode(code, "MyClass.java");
 * }</pre>
 *
 * @see com.intellij.formatter.core.StandaloneFormatter
 */
public final class CodeStyleLoader {
    /**
     * Path, on this jar's own classpath, of the ICIJ code style shipped in
     * {@code formatter-core/src/main/resources/icij-codestyle.xml}.
     */
    private static final String BUNDLED_STYLE_RESOURCE = "/icij-codestyle.xml";
    private final String content;

    public CodeStyleLoader(String filePath) throws IOException {
        if (filePath == null) {
            content = loadBundled();
        } else {
            content = loadFromFile(filePath);
        }
    }

    public CodeStyleLoader applyFromXml() throws IOException, JDOMException {
        initialize();
        applySettings(JDOMUtil.load(content));
        return this;
    }

    /**
     * Reverts to IntelliJ's default code style scheme, undoing any style previously
     * applied via {@link #loadFromFile(String)}.
     */
    public CodeStyleLoader resetToDefault() {
        var project = getProject();
        var settingsManager = project.getService(ProjectCodeStyleSettingsManager.class);
        if (settingsManager != null) {
            settingsManager.USE_PER_PROJECT_SETTINGS = false;
        }
        return this;
    }

    /**
     * Loads code style settings from the specified XML file and applies them to the project.
     *
     * <p>The file must be a valid IntelliJ IDEA code style export. The loader automatically
     * detects and handles different XML structures including direct code_scheme elements,
     * project configurations, and component wrappers.</p>
     *
     * @param filePath the absolute path to the code style XML file
     * @throws IOException if the file cannot be read, parsed, or applied
     */
    String loadFromFile(@NotNull String filePath) throws IOException {
        System.err.println("Loading code style from: " + filePath);
        var path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new IOException("Code style file not found: " + filePath);
        }
        return Files.readString(path);
    }

    /**
     * Loads the ICIJ code style bundled in this jar (see {@link #BUNDLED_STYLE_RESOURCE}), for
     * callers - such as the CLI and the Maven plugin, which forks it - that want ICIJ's style
     * applied by default whenever no explicit style file is given. This resource lives here,
     * on formatter-core's own classpath, rather than being resolved by a caller in a different
     * jar/classloader (e.g. the Maven plugin), which cannot see inside this jar's resources.
     *
     * @throws IOException if the bundled resource is missing or fails to parse
     */
    String loadBundled() throws IOException {
        System.err.println("Loading bundled ICIJ code style");
        try (var in = CodeStyleLoader.class.getResourceAsStream(BUNDLED_STYLE_RESOURCE)) {
            if (in == null) {
                throw new IOException("Bundled " + BUNDLED_STYLE_RESOURCE + " resource not found on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void applySettings(Element rootElement) {
        var project = getProject();
        var settingsManager = project.getService(ProjectCodeStyleSettingsManager.class);

        if (settingsManager == null) {
            throw new IllegalStateException("ProjectCodeStyleSettingsManager not available");
        }

        var codeStyleElement = findCodeStyleElement(rootElement);
        if (codeStyleElement == null) {
            throw new IllegalStateException("No code style settings found in file");
        }
        var settings = settingsManager.getMainProjectCodeStyle();
        if (settings == null) {
            settings = settingsManager.createSettings();
        }
        settings.readExternal(codeStyleElement);
        settingsManager.setMainProjectCodeStyle(settings);
        // Without this, CodeStyleSettingsManager.getCurrentSettings() ignores the settings
        // just registered above and falls back to the default IntelliJ scheme.
        settingsManager.USE_PER_PROJECT_SETTINGS = true;
    }

    private Element findCodeStyleElement(Element root) {
        var rootName = root.getName();

        if ("code_scheme".equals(rootName)) {
            return root;
        }

        if ("component".equals(rootName) && "ProjectCodeStyleConfiguration".equals(root.getAttributeValue("name"))) {
            var stateElement = root.getChild("state");
            return stateElement != null ? stateElement.getChild("code_scheme") : root.getChild("code_scheme");
        }

        if ("project".equals(rootName)) {
            for (var child : root.getChildren("component")) {
                if ("ProjectCodeStyleConfiguration".equals(child.getAttributeValue("name"))) {
                    return findCodeStyleElement(child);
                }
            }
        }

        var codeScheme = root.getChild("code_scheme");
        return codeScheme != null ? codeScheme : root;
    }
}
