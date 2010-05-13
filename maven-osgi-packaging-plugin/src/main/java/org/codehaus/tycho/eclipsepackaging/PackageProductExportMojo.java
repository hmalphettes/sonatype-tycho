package org.codehaus.tycho.eclipsepackaging;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

/**
 * TODO. right now it does nothing. first make the p2 stuff work.
 * @goal product-export-packaging
 */
public class PackageProductExportMojo extends AbstractMojo {
	
    /**
     * @parameter expression="${project}"
     * @required
     */
    protected MavenProject project;

    /**
     * Generated product exports
     * 
     * @parameter expression="${project.build.directory}"
     */
    private File target;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (target == null || !target.isDirectory()) {
            throw new MojoExecutionException(
                    "Target site folder does not exist at: " + target != null ? target.getAbsolutePath() : "null");
        }
    }
    
}
