package org.icij.formatter.maven;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractFormatterMojoBuildCommandTest {

    private final File coreJar = new File("/tmp/intellij-code-formatter.jar");
    private final File codeStyle = new File("/tmp/icij-codestyle.xml");
    private final File directory = new File("/tmp/src/main/java");

    @Test
    void checkMode_includesCheckFlagAndAllAddOpensFlags() {
        var command = AbstractFormatterMojo.buildCommand("/usr/bin/java", coreJar, codeStyle, true, directory);

        assertEquals("/usr/bin/java", command.get(0));
        assertTrue(command.containsAll(AbstractFormatterMojo.ADD_OPENS_FLAGS));
        assertTrue(command.contains("-jar"));
        assertEquals(coreJar.getAbsolutePath(), command.get(command.indexOf("-jar") + 1));
        assertTrue(command.contains("--style"));
        assertEquals(codeStyle.getAbsolutePath(), command.get(command.indexOf("--style") + 1));
        assertTrue(command.contains("--check"));
        assertEquals(directory.getAbsolutePath(), command.get(command.size() - 1));
    }

    @Test
    void formatMode_omitsCheckFlag() {
        var command = AbstractFormatterMojo.buildCommand("/usr/bin/java", coreJar, codeStyle, false, directory);

        assertFalse(command.contains("--check"));
        assertEquals(directory.getAbsolutePath(), command.get(command.size() - 1));
    }
}
