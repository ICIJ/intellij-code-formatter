package org.icij.formatter.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormatMojoTest {

    @Test
    void checkOnly_isFalse() {
        assertFalse(new FormatMojo().checkOnly());
    }

    @Test
    void handleExitCode_zero_succeeds() {
        assertDoesNotThrow(() -> new FormatMojo().handleExitCode(0));
    }

    @Test
    void handleExitCode_nonZero_throwsMojoExecutionException() {
        assertThrows(MojoExecutionException.class, () -> new FormatMojo().handleExitCode(2));
    }
}
