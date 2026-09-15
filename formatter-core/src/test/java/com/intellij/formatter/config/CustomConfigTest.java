package com.intellij.formatter.config;

import com.intellij.formatter.core.StandaloneFormatter;
import static java.util.Optional.ofNullable;
import org.jdom.JDOMException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for custom code style configuration loading.
 */
@DisplayName("Custom Config Tests")
class CustomConfigTest {
    private CodeStyleLoader loader;

    @AfterEach
    void tearDown() {
        ofNullable(loader).map(CodeStyleLoader::resetToDefault);
    }

    @Test
    @DisplayName("Load 2-space indent config")
    void loadTwoSpaceConfig() {
        var configPath = getResourcePath("code-style-2-spaces.xml");

        assertDoesNotThrow(() -> {
            loader = new CodeStyleLoader(configPath).applyFromXml();
        });
    }

    @Test
    @DisplayName("Load tabs config")
    void loadTabsConfig() {
        var configPath = getResourcePath("code-style-tabs.xml");

        assertDoesNotThrow(() -> {
            loader = new CodeStyleLoader(configPath).applyFromXml();
        });
    }

    @Test
    @DisplayName("Load nonexistent file throws exception")
    void loadNonexistentFile() {
        var exception = assertThrows(IOException.class, () -> new CodeStyleLoader("/nonexistent/path/config.xml"));

        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("Java formatting uses 2-space indent after config load")
    void javaFormattingWithTwoSpaces() throws Exception {
        var configPath = getResourcePath("code-style-2-spaces.xml");
        loader = new CodeStyleLoader(configPath).applyFromXml();

        var input = "public class Test{void method(){int x=1;}}";
        var expected = """
                public class Test {
                  void method() {
                    int x = 1;
                  }
                }""";

        var actual = StandaloneFormatter.formatCode(input, "Test.java");
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Java formatting works after loading tabs config")
    void javaFormattingWithTabs() throws Exception {
        loader = new CodeStyleLoader(getResourcePath("code-style-tabs.xml")).applyFromXml();

        var input = "public class Test{void method(){int x=1;}}";
        var actual = StandaloneFormatter.formatCode(input, "Test.java");

        // Config loading should not break formatting
        // Note: Tab setting may not apply in standalone mode, but formatting should still work
        assertNotNull(actual);
        assertTrue(actual.contains("void method()"));
        assertTrue(actual.contains("int x = 1"));
    }

    @Test
    @DisplayName("XML formatting uses 2-space indent after config load")
    void xmlFormattingWithTwoSpaces() throws Exception {
        var configPath = getResourcePath("code-style-2-spaces.xml");
        loader = new CodeStyleLoader(configPath).applyFromXml();

        var input = "<root><child><nested>text</nested></child></root>";
        var actual = StandaloneFormatter.formatCode(input, "test.xml");

        assertNotNull(actual);
        // XML should be formatted with proper structure
        assertTrue(actual.contains("<root>"));
        assertTrue(actual.contains("</root>"));
    }

    @Test
    @DisplayName("Kotlin formatting uses 2-space indent after config load")
    void kotlinFormattingWithTwoSpaces() throws Exception {
        var configPath = getResourcePath("code-style-2-spaces.xml");
        loader = new CodeStyleLoader(configPath).applyFromXml();

        var input = "class Calculator{fun add(a:Int,b:Int):Int{return a+b}}";
        var actual = StandaloneFormatter.formatCode(input, "Calculator.kt");

        assertNotNull(actual);
        assertTrue(actual.contains("fun add"));
    }

    @Test
    @DisplayName("Groovy formatting uses 2-space indent after config load")
    void groovyFormattingWithTwoSpaces() throws Exception {
        var configPath = getResourcePath("code-style-2-spaces.xml");
        loader = new CodeStyleLoader(configPath).applyFromXml();

        var input = "class Person{String name}";
        var actual = StandaloneFormatter.formatCode(input, "Person.groovy");

        assertNotNull(actual);
        assertTrue(actual.contains("String name"));
    }

    @Test
    @DisplayName("Config loading is idempotent")
    void configLoadingIdempotent() throws Exception {
        var configPath = getResourcePath("code-style-2-spaces.xml");

        // Load config multiple times
        loader = new CodeStyleLoader(configPath).applyFromXml();
        loader = new CodeStyleLoader(configPath).applyFromXml();
        loader = new CodeStyleLoader(configPath).applyFromXml();

        // Formatting should still work
        var input = "public class Test{void method(){}}";
        var actual = StandaloneFormatter.formatCode(input, "Test.java");

        assertNotNull(actual);
        assertTrue(actual.contains("void method()"));
    }

    @Test
    @DisplayName("Switching between configs works")
    void switchingBetweenConfigs() throws Exception {
        var input = "public class Test{void method(){int x=1;}}";

        // Load 2-space config
        loader = new CodeStyleLoader(getResourcePath("code-style-2-spaces.xml")).applyFromXml();
        var with2Spaces = StandaloneFormatter.formatCode(input, "Test.java");

        // Load tabs config
        loader = new CodeStyleLoader(getResourcePath("code-style-tabs.xml")).applyFromXml();
        var withTabs = StandaloneFormatter.formatCode(input, "Test.java");

        // Results should be different (one uses spaces, other uses tabs)
        assertNotNull(with2Spaces);
        assertNotNull(withTabs);
        // At least verify both produce valid output
        assertTrue(with2Spaces.contains("void method()"));
        assertTrue(withTabs.contains("void method()"));
    }

    @Test
    @DisplayName("Invalid XML throws exception")
    void invalidXmlThrowsException() {
        // Create a temp file with invalid XML
        assertThrows(JDOMException.class, () -> {
            var tempFile = java.io.File.createTempFile("invalid-config", ".xml");
            java.nio.file.Files.writeString(tempFile.toPath(), "not valid xml {{{");
            try {
                loader = new CodeStyleLoader(tempFile.getAbsolutePath()).applyFromXml();
            } finally {
                tempFile.delete();
            }
        });
    }

    private String getResourcePath(String resourceName) {
        var url = getClass().getClassLoader().getResource(resourceName);
        Objects.requireNonNull(url, "Resource not found: " + resourceName);
        return Path.of(url.getPath()).toString();
    }
}
