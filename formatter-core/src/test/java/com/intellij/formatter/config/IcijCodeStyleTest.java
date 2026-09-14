package com.intellij.formatter.config;

import com.intellij.formatter.core.CodeStyleLoadException;
import com.intellij.formatter.core.FormattingException;
import com.intellij.formatter.core.StandaloneFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests using the actual ICIJ code style shipped in maven-plugin/src/main/resources/icij-codestyle.xml,
 * as opposed to the synthetic configs in {@link CustomConfigTest}.
 */
@DisplayName("ICIJ Code Style Tests")
class IcijCodeStyleTest {

    private static String icijCodeStylePath() {
        // The real file used in production, not a copy, so this test always reflects the current style.
        var path = Path.of("..", "maven-plugin", "src", "main", "resources", "icij-codestyle.xml").normalize();
        assertTrue(Files.exists(path), "icij-codestyle.xml not found at " + path.toAbsolutePath());
        return path.toString();
    }

    @AfterEach
    void resetCodeStyle() {
        // The formatter's IntelliJ project is a JVM-wide singleton, so a custom style
        // loaded here would otherwise leak into unrelated tests in other classes.
        CodeStyleLoader.resetToDefault();
    }

    @Test
    @DisplayName("Empty class body is kept on one line (matches real IntelliJ IDEA)")
    void emptyClassBodyKeptOnOneLine() throws CodeStyleLoadException, FormattingException {
        CodeStyleLoader.loadFromFile(icijCodeStylePath());

        var input = "public class Test {}";
        var expected = "public class Test {}";

        var actual = StandaloneFormatter.formatCode(input, "Test.java");
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Simple one-line class body is expanded to multiple lines (matches real IntelliJ IDEA)")
    void simpleClassBodyExpandedToMultipleLines() throws CodeStyleLoadException, FormattingException {
        CodeStyleLoader.loadFromFile(icijCodeStylePath());

        var input = "public class Test { int x; }";
        var expected = "public class Test {\n    int x;\n}";

        var actual = StandaloneFormatter.formatCode(input, "Test.java");
        assertEquals(expected, actual);
    }
}
