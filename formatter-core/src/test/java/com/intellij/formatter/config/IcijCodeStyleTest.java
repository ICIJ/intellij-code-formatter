package com.intellij.formatter.config;

import com.intellij.formatter.core.CodeStyleLoadException;
import com.intellij.formatter.core.FormattingException;
import com.intellij.formatter.core.StandaloneFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests using the actual ICIJ code style shipped in formatter-core/src/main/resources/icij-codestyle.xml
 * (also embedded and reused as-is by the maven-plugin module), as opposed to the synthetic configs in
 * {@link CustomConfigTest}.
 */
@DisplayName("ICIJ Code Style Tests")
class IcijCodeStyleTest {

    private static String icijCodeStylePath() {
        // Loaded from the classpath, not a copy, so this test always reflects the current style.
        var url = IcijCodeStyleTest.class.getClassLoader().getResource("icij-codestyle.xml");
        Objects.requireNonNull(url, "icij-codestyle.xml not found on the classpath");
        return Path.of(url.getPath()).toString();
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
