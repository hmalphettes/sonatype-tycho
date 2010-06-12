package org.sonatype.tycho.plugins.p2.publisher;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.tycho.buildversion.VersioningHelper;
import org.sonatype.tycho.plugins.p2.AbstractP2Mojo;

/**
 * Just zip the repository.
 * 
 * @goal archive-repository
 */
public class ArchiveRepositoryMojo extends AbstractP2Mojo {

	public void execute() throws MojoExecutionException, MojoFailureException {
		archiveRepository();
    }
	
	/**
	 * Archive the resulting repository as a zip and set it as the main artifact of this project.
	 * @throws MojoExecutionException
	 */
	protected void archiveRepository() throws MojoExecutionException
	{
		String version = getTychoProjectFacet().getArtifactKey( project ).getVersion();
		version = VersioningHelper.getExpandedVersion(project, version);
        version = version.replace(VersioningHelper.QUALIFIER, qualifier);
		super.createArchive(targetRepository, null, true, project.getArtifactId() + "-" + version, "repository");
	}
	
}
