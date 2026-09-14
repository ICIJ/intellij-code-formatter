package org.icij.formatter.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ForkIntegrationTest {

    @Test
    void checkReportsNonCompliantFile_thenFormatFixesIt_thenCheckPasses(@TempDir Path tempDir) throws Exception {
        var coreJar = new File(System.getProperty("formatterCoreJar"));
        assumeTrue(coreJar.isFile(),
                "formatter-core jar not built yet; run `./mvnw package` from the repo root first: " + coreJar);

        var srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("Bad.java"), "public class Bad{public void m(){int x=1;}}\n");

        var codeStyle = classpathResourceAsFile("/icij-codestyle.xml", tempDir);
        var javaExecutable = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

        assertEquals(1, run(AbstractFormatterMojo.buildCommand(
                javaExecutable, coreJar, codeStyle, true, srcDir.toFile())),
                "expected check to report the non-compliant file");

        assertEquals(0, run(AbstractFormatterMojo.buildCommand(
                javaExecutable, coreJar, codeStyle, false, srcDir.toFile())),
                "expected format to succeed");

        assertEquals(0, run(AbstractFormatterMojo.buildCommand(
                javaExecutable, coreJar, codeStyle, true, srcDir.toFile())),
                "expected the file to be compliant after formatting");
    }

    /**
     * The failure the user actually sees must name the offending files: the forked report
     * is logged at INFO, which Maven drops under {@code -q} and in quiet CI setups.
     */
    @Test
    void checkMojo_failureMessageNamesTheOffendingFile(@TempDir Path tempDir) throws Exception {
        var coreJar = new File(System.getProperty("formatterCoreJar"));
        assumeTrue(coreJar.isFile(),
                "formatter-core jar not built yet; run `./mvnw package` from the repo root first: " + coreJar);

        var srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("Bad.java"), "public class Bad{public void m(){int x=1;}}\n");

        var mojo = new CheckMojo() {
            @Override File resolveCoreJar() {
                return coreJar;
            }
        };
        mojo.directory = srcDir.toFile();
        mojo.codeStyle = classpathResourceAsFile("/icij-codestyle.xml", tempDir);

        var failure = assertThrows(MojoFailureException.class, mojo::execute);

        var badFile = new File(mojo.directory, "Bad.java").getAbsolutePath();
        assertTrue(failure.getMessage().contains(badFile),
                "expected the failure message to name " + badFile + ", was:\n" + failure.getMessage());
    }

    /**
     * Extracts a classpath resource to a real file.
     *
     */
    private static File classpathResourceAsFile(String name, Path tempDir) throws IOException {
        try (InputStream in = ForkIntegrationTest.class.getResourceAsStream(name)) {
            Objects.requireNonNull(in, name + " not found on the classpath");
            var target = tempDir.resolve(Path.of(name).getFileName());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return target.toFile();
        }
    }

    private static int run(List<String> command) throws Exception {
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes());
        var exitCode = process.waitFor();
        if (exitCode != 0 && exitCode != 1) {
            throw new AssertionError("Unexpected exit code " + exitCode + ", output:\n" + output);
        }
        return exitCode;
    }
}
