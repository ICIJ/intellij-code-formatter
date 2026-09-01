package org.icij.formatter.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;

// Temporary: gives maven-plugin-plugin's descriptor generator a @Mojo to
// find before CheckMojo/FormatMojo exist.
@Mojo(name = "placeholder")
public class PlaceholderMojo extends AbstractMojo {
    @Override
    public void execute() {
        // no-op
    }
}
