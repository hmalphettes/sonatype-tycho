package org.codehaus.tycho.buildversion;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.tycho.TychoProject;

/**
 * Validates project Maven and OSGi versions. For SNAPSHOT versions, OSGi version qualifier must be ".qualifier" and
 * unqualified Maven and OSGi versions must be equal. For RELEASE versions, OSGi and Maven versions must be equal.
 * 
 * @goal validate-version
 * @phase validate
 */
public class ValidateVersionMojo
    extends AbstractVersionMojo
{
    /**
     * If <code>true</code> (the default) will fail the build if Maven and OSGi project versions do not match. If
     * <code>false</code> will issue a warning but will not fail the build if Maven and OSGi project versions do not
     * match.
     * 
     * @parameter default-value="true"
     */
    private boolean strictVersions = true;

    /**
     * @parameter expression="${forceContextQualifier}"
     */
    private String forceContextQualifier;

    
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        String mavenVersion = project.getVersion();
        String osgiVersion = getOSGiVersion();

        if ( osgiVersion == null )
        {
            return;
        }

        if ( project.getArtifact().isSnapshot() )
        {
            validateSnapshotVersion( mavenVersion, osgiVersion );
        }
        else
        {
            validateReleaseVersion( mavenVersion, osgiVersion );
        }
    }

    public void validateReleaseVersion( String mavenVersion, String osgiVersion )
        throws MojoExecutionException
    {
    	
    	if (forceContextQualifier != null && osgiVersion.endsWith(VersioningHelper.QUALIFIER))
    	{
    		//TYCHO-349 make sure that the forceContextqualifier property is defined
    		//and that when we replace it we get the same than the maven version
    		String osgiVersionNoQualifier = osgiVersion.substring(0, osgiVersion.length() - VersioningHelper.QUALIFIER.length());
    		osgiVersion = osgiVersionNoQualifier + forceContextQualifier;
    		if (osgiVersion.endsWith(".")) {
    			//this is the case where there qualifier is the empty string and the version was 4.0.0.qualifier
    			//in that case remove the last '.'
    			osgiVersion = osgiVersion.substring(0, osgiVersion.length()-1);
    		}
    	}
    	
        if ( !mavenVersion.equals( osgiVersion ) )
        {
        	fail( "OSGi version " + osgiVersion + " in " + getOSGiMetadataFileName() + " does not match Maven version "
                + mavenVersion + " in pom.xml" );
        }
    }

    private String getOSGiMetadataFileName()
    {
        // Kinda hack

        String packaging = project.getPackaging();

        if ( TychoProject.ECLIPSE_PLUGIN.equals( packaging ) || TychoProject.ECLIPSE_TEST_PLUGIN.equals( packaging ) )
        {
            return "META-INF/MANIFEST.MF";
        }
        else if ( TychoProject.ECLIPSE_FEATURE.equals( packaging ) )
        {
            return "feature.xml";
        }
        else if ( TychoProject.ECLIPSE_APPLICATION.equals( packaging ) )
        {
            return project.getArtifactId() + ".product";
        }
        else if ( TychoProject.ECLIPSE_REPOSITORY.equals( packaging ) )
        {
            return project.getArtifactId();
        }
        return "<unknown packaging=" + packaging + ">";
    }

    public void validateSnapshotVersion( String mavenVersion, String osgiVersion )
        throws MojoExecutionException
    {
        if ( !osgiVersion.endsWith( VersioningHelper.QUALIFIER ) )
        {
            fail( "OSGi version " + osgiVersion + " must have .qualifier qualifier for SNAPSHOT builds" );
        }
        else
        {
            String unqualifiedMavenVersion = mavenVersion.substring( 0, mavenVersion.length() - "-SNAPSHOT".length() );
            String unqualifiedOSGiVersion =
                osgiVersion.substring( 0, osgiVersion.length() - VersioningHelper.QUALIFIER.length() - 1 );
            if ( !unqualifiedMavenVersion.equals( unqualifiedOSGiVersion ) )
            {
                fail( "Unqualified OSGi version " + osgiVersion + " must match unqualified Maven version "
                    + mavenVersion + " for SNAPSHOT builds" );
            }
        }
    }

    private void fail( String message )
        throws MojoExecutionException
    {
        if ( strictVersions )
        {
            throw new MojoExecutionException( message );
        }
        else
        {
            getLog().warn( message );
        }
    }
}
