package org.sonatype.tycho.plugins.p2.director;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.tycho.TargetEnvironment;
import org.sonatype.tycho.plugins.p2.AbstractP2Mojo;

/**
 * Invokes p2 director to install a product archive for each environment specified
 * into a folder.
 * <p>
 * @see http://wiki.eclipse.org/Equinox_p2_director_application
 * </p>
 * 
 * @goal archive-products
 */
public class ArchiveProductsMojo extends AbstractP2Mojo {

	public void execute() throws MojoExecutionException, MojoFailureException {
    	
    }

	
    private File getTarget( TargetEnvironment environment )
    {
        File target = new File( project.getBuild().getDirectory(), toString( environment ) );
        target.mkdirs();

        return target;
    }

}
