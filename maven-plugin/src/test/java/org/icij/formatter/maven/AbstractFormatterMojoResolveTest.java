package org.icij.formatter.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractFormatterMojoResolveTest {

    static class TestMojo extends AbstractFormatterMojo {
        @Override boolean checkOnly() { return false; }
        @Override void handleExitCode(int exitCode, List<String> report) { }
    }

    private static ByteArrayInputStream payload(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
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
    void extractToCache_targetMissing_createsParentsAndWritesIt(@TempDir Path tempDir) throws Exception {
        var target = tempDir.resolve("nested/dir/core.jar");

        var returned = AbstractFormatterMojo.extractToCache(payload("core-bytes"), target);

        assertEquals(target, returned);
        assertEquals("core-bytes", Files.readString(target));
    }

    @Test
    void extractToCache_targetAlreadyPresent_leavesItUntouched(@TempDir Path tempDir) throws Exception {
        var target = tempDir.resolve("core.jar");
        Files.writeString(target, "already-cached");

        AbstractFormatterMojo.extractToCache(payload("would-overwrite"), target);

        // The cache hit must short-circuit: re-writing 157 MB once per module is the
        // whole thing this method exists to avoid.
        assertEquals("already-cached", Files.readString(target));
    }

    @Test
    void extractToCache_leavesNoPartFileBehind(@TempDir Path tempDir) throws Exception {
        var target = tempDir.resolve("core.jar");

        AbstractFormatterMojo.extractToCache(payload("core-bytes"), target);

        try (Stream<Path> entries = Files.list(tempDir)) {
            var leftovers = entries.filter(p -> !p.equals(target)).toList();
            assertTrue(leftovers.isEmpty(), "unexpected leftovers in the cache dir: " + leftovers);
        }
    }

    @Test
    void coreJarCachePath_isKeyedByPluginVersionUnderTheLocalRepository(@TempDir Path tempDir) {
        var descriptor = new PluginDescriptor();
        descriptor.setVersion("2.1.0");
        var mojo = new TestMojo();
        mojo.pluginDescriptor = descriptor;
        mojo.localRepository = tempDir.toFile();

        assertEquals(
                tempDir.resolve(".cache/icij-formatter/intellij-code-formatter-2.1.0.jar"),
                mojo.coreJarCachePath());
    }

    @Test
    void resolveCoreJar_cacheHit_returnsItWithoutTouchingTheEmbeddedResource(@TempDir Path tempDir)
            throws Exception {
        var descriptor = new PluginDescriptor();
        descriptor.setVersion("9.9.9");
        var mojo = new TestMojo();
        mojo.pluginDescriptor = descriptor;
        mojo.localRepository = tempDir.toFile();

        var cached = mojo.coreJarCachePath();
        Files.createDirectories(cached.getParent());
        Files.writeString(cached, "pretend-core-jar");

        assertEquals(cached.toFile(), mojo.resolveCoreJar());
        assertEquals("pretend-core-jar", Files.readString(cached));
    }

    /**
     * Guards the maven-dependency-plugin wiring in pom.xml: without the copy into
     * target/classes, the plugin ships with no core to fork and every goal fails at runtime.
     */
    @Test
    void embeddedCoreJar_isPresentOnTheClasspath() {
        assertNotNull(
                AbstractFormatterMojo.class.getResource(AbstractFormatterMojo.EMBEDDED_CORE_JAR),
                AbstractFormatterMojo.EMBEDDED_CORE_JAR + " missing: check the "
                        + "maven-dependency-plugin embed-formatter-core execution");
    }
}
