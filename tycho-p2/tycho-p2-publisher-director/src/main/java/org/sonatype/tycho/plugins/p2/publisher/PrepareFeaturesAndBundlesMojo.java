package org.sonatype.tycho.plugins.p2.publisher;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.sonatype.tycho.plugins.p2.AbstractP2Mojo;

/**
 * Reads product file(s), site(s) files and categories definition files
 * finds the corresponding features and bundles; places them in the target/eclipse folder.
 * The p2 publisher application can start working on that eclipse folder later.
 * 
 * @goal prepare-features-and-bundles
 */
public class PrepareFeaturesAndBundlesMojo extends AbstractP2Mojo {
	
	public void execute() throws MojoExecutionException, MojoFailureException {
		
		targetRepository.mkdirs();
    	FeaturesAndBundlesAssembler assembler = new FeaturesAndBundlesAssembler(session, targetRepository);
        // expandVersion();
        try
        {
            getDependencyWalker().walk( assembler );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
    }

}
