package org.icij.formatter.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractFormatterMojoResolveTest {

    static class TestMojo extends AbstractFormatterMojo {
        @Override boolean checkOnly() { return false; }
        @Override void handleExitCode(int exitCode) { }
    }

    @Test
    void resolveCodeStyle_noOverride_extractsBundledResourceToATempFile() throws Exception {
        var mojo = new TestMojo();

        var resolved = mojo.resolveCodeStyle();

        assertTrue(resolved.isFile());
        var content = Files.readString(resolved.toPath());
        assertTrue(content.contains("code_scheme"));
    }

    @Test
    void resolveCodeStyle_overridePresentAndReadable_returnsItDirectly(@TempDir Path tempDir) throws Exception {
        var override = tempDir.resolve("custom-style.xml");
        Files.writeString(override, "<code_scheme name=\"Custom\" version=\"1\"/>");
        var mojo = new TestMojo();
        mojo.codeStyle = override.toFile();

        var resolved = mojo.resolveCodeStyle();

        assertEquals(override.toFile(), resolved);
    }

    @Test
    void resolveCodeStyle_overrideMissing_throws(@TempDir Path tempDir) {
        var mojo = new TestMojo();
        mojo.codeStyle = tempDir.resolve("does-not-exist.xml").toFile();

        assertThrows(MojoExecutionException.class, mojo::resolveCodeStyle);
    }

    @Test
    void resolveCoreJar_matchingArtifactPresent_returnsItsFile() throws Exception {
        var artifact = mock(Artifact.class);
        when(artifact.getGroupId()).thenReturn("org.icij");
        when(artifact.getArtifactId()).thenReturn("intellij-code-formatter");
        var jarFile = new File("/tmp/intellij-code-formatter.jar");
        when(artifact.getFile()).thenReturn(jarFile);

        var descriptor = new PluginDescriptor();
        descriptor.setArtifacts(List.of(artifact));

        var mojo = new TestMojo();
        mojo.pluginDescriptor = descriptor;

        assertEquals(jarFile, mojo.resolveCoreJar());
    }

    @Test
    void resolveCoreJar_noMatchingArtifact_throws() {
        var descriptor = new PluginDescriptor();
        descriptor.setArtifacts(List.of());

        var mojo = new TestMojo();
        mojo.pluginDescriptor = descriptor;

        assertThrows(MojoExecutionException.class, mojo::resolveCoreJar);
    }
}
