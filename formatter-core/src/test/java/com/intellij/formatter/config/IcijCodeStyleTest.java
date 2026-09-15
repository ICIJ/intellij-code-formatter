package com.intellij.formatter.config;

import com.intellij.formatter.core.StandaloneFormatter;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests using the actual ICIJ code style shipped in formatter-core/src/main/resources/icij-codestyle.xml
 * (also embedded and reused as-is by the maven-plugin module), as opposed to the synthetic configs in
 * {@link CustomConfigTest}.
 */
@DisplayName("ICIJ Code Style Tests")
class IcijCodeStyleTest {
    private CodeStyleLoader loader;

    @BeforeEach
    void setUp() throws IOException {
        loader = new CodeStyleLoader(null);
    }
    @AfterEach
    void resetCodeStyle() {
        loader.resetToDefault();
    }

    @Test
    @DisplayName("Empty class body is kept on one line (matches real IntelliJ IDEA)")
    void emptyClassBodyKeptOnOneLine() throws Exception {
        loader.applyFromXml();

        var input = "public class Test {}";
        var expected = "public class Test {}";

        var actual = StandaloneFormatter.formatCode(input, "Test.java");
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Simple one-line class body is expanded to multiple lines (matches real IntelliJ IDEA)")
    void simpleClassBodyExpandedToMultipleLines() throws Exception {
        loader.applyFromXml();

        var input = "public class Test { int x; }";
        var expected = "public class Test {\n    int x;\n}";

        var actual = StandaloneFormatter.formatCode(input, "Test.java");
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("loadBundled() applies the same style as loading icij-codestyle.xml by path")
    void loadBundled_appliesTheSameStyleAsLoadFromFile() throws Exception {
        loader.applyFromXml();

        var input = "public class Test { int x; }";
        var expected = "public class Test {\n    int x;\n}";

        var actual = StandaloneFormatter.formatCode(input, "Test.java");
        assertEquals(expected, actual);
    }
}
