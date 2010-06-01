package org.sonatype.tycho.plugins.p2.tools;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.cli.Commandline;
import org.sonatype.tycho.plugins.p2.AbstractP2AppInvokerMojo;

/**
 * Invoke the application RecreateRepositoryApplication aniefer mentions that it
 * will take care of packed repositories and update their checksum.
 * @goal fix-checksums
 */
public class FixCheckSumMojo extends AbstractP2AppInvokerMojo {
	
	public static String PUBLISHER_BUNDLE_ID = "org.sonatype.tycho.p2.publisher";
	public static String RECREATE_REPOSITORY_APPLICATION = PUBLISHER_BUNDLE_ID + ".RecreateRepository";
	
	public void execute() throws MojoExecutionException, MojoFailureException {
		try
		{
			Commandline cli = super.getCommandLine(RECREATE_REPOSITORY_APPLICATION);
			cli.addArguments(new String[] {"-artifactRepository", targetRepository.toURI().toURL().toExternalForm()});
			super.execute(cli, null);
		}
		catch (IOException ioe)
		{
			throw new MojoExecutionException("Error invoking the RecreateRepository application", ioe);
		}
	}

	
}
