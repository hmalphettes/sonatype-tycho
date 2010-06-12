package org.sonatype.tycho.versions;

import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.sonatype.tycho.versions.engine.VersionsEngine;

/**
 * @author igor
 * @goal set-version
 * @aggregator
 * @requiresProject true
 * @requiresDirectInvocation true
 */
public class SetMojo
    extends AbstractMojo
{
    /**
     * The new version number to set.
     * 
     * @parameter expression="${newVersion}"
     * @required
     */
    private String newVersion;

    /**
     * Comma separated list of artifact ids to set the new version to.
     * <p/>
     * By default, the new version will be set on the current project and all references to the project, including all
     * <parent/> elements if the project is a parent pom.
     * 
     * @parameter expression="${artifacts}" default-value="${project.artifactId}"
     */
    private String artifacts;

    /**
     * @parameter expression="${session}"
     */
    protected MavenSession session;

    /**
     * @component
     */
    private VersionsEngine engine;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( newVersion == null || newVersion.length() == 0 )
        {
            throw new MojoExecutionException( "Missing required parameter newVersion" );
        }

        try
        {
            engine.addBasedir( session.getCurrentProject().getBasedir() );

            // initial changes
            StringTokenizer st = new StringTokenizer( artifacts, "," );
            while ( st.hasMoreTokens() )
            {
                String artifactId = st.nextToken().trim();
                engine.addVersionChange( artifactId, newVersion );
            }

            engine.apply();
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Could not set version", e );
        }
    }

}
