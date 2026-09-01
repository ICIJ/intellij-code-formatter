package org.icij.formatter.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractFormatterMojoExecuteTest {

    static class RecordingMojo extends AbstractFormatterMojo {
        final List<Integer> handledExitCodes = new ArrayList<>();

        @Override boolean checkOnly() { return true; }

        @Override void handleExitCode(int exitCode, List<String> report) {
            handledExitCodes.add(exitCode);
        }
    }

    @Test
    void skipTrue_returnsWithoutTouchingAnythingElse() {
        var mojo = new RecordingMojo();
        mojo.skip = true;
        // directory/pluginDescriptor left null on purpose: if execute() didn't
        // short-circuit on skip, resolveCoreJar() would NPE on pluginDescriptor.
        mojo.directory = null;

        assertDoesNotThrow(mojo::execute);
        assertTrue(mojo.handledExitCodes.isEmpty());
    }

    @Test
    void directoryMissing_returnsWithoutTouchingAnythingElse(@TempDir Path tempDir) {
        var mojo = new RecordingMojo();
        mojo.skip = false;
        mojo.directory = tempDir.resolve("does-not-exist").toFile();
        // pluginDescriptor left null on purpose, for the same reason as above.

        assertDoesNotThrow(mojo::execute);
        assertTrue(mojo.handledExitCodes.isEmpty());
    }
}
