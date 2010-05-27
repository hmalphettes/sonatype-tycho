package org.sonatype.tycho.plugins.p2;

import java.io.File;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.tycho.ArtifactDependencyWalker;
import org.codehaus.tycho.ArtifactDescription;
import org.codehaus.tycho.TargetEnvironment;
import org.codehaus.tycho.TargetPlatform;
import org.codehaus.tycho.TargetPlatformConfiguration;
import org.codehaus.tycho.TychoConstants;
import org.codehaus.tycho.TychoProject;
import org.codehaus.tycho.buildversion.VersioningHelper;
import org.codehaus.tycho.osgitools.EclipseRepositoryProject;

/**
 * Easy access to the configuration parameters of mojos that invoke p2 publisher or p2 director.
 * Pointers to product files and categories definition files.
 * 
 */
public abstract class AbstractP2Mojo extends AbstractMojo {

    /** 
     * Built directory where the repository is created.
     * @parameter expression="${project.build.directory}/repository"
     */
    protected File targetRepository;

    /** @parameter expression="${session}" */
    protected MavenSession session;

    /** @parameter expression="${project}" */
    protected MavenProject project;

    /**
     * Build qualifier. Recommended way to set this parameter is using build-qualifier goal.
     * 
     * @parameter expression="${buildQualifier}"
     */
    protected String qualifier;

    /** @component */
    protected PlexusContainer plexus;

    /** @component */
    protected MavenProjectHelper projectHelper;

    protected static String toString( TargetEnvironment environment )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( environment.getOs() ).append( '.' )
        			.append( environment.getWs() ).append( '.' )
        			.append(environment.getArch() );
        if ( environment.getNl() != null )
        {
            sb.append( '.' ).append( environment.getNl() );
        }
        return sb.toString();
    }
    
    protected ArtifactDependencyWalker getDependencyWalker()
    {
        return getTychoProjectFacet().getDependencyWalker( project );
    }

    protected EclipseRepositoryProject getEclipseRepositoryProject()
    {
    	return (EclipseRepositoryProject)getTychoProjectFacet();
    }
    
    protected TychoProject getTychoProjectFacet()
    {
        return getTychoProjectFacet( project.getPackaging() );
    }

    protected TychoProject getTychoProjectFacet(String packaging)
    {
        TychoProject facet;
        try
        {
            facet = (TychoProject) session.lookup(TychoProject.class.getName(), packaging);
        }
        catch ( ComponentLookupException e )
        {
            throw new IllegalStateException( "Could not lookup required component", e );
        }
        return facet;
    }

    protected TargetPlatform getTargetPlatform()
    {
        return getTychoProjectFacet().getTargetPlatform( project );
    }

    protected void expandVersion()
    {
        String originalVersion = getTychoProjectFacet().getArtifactKey( project ).getVersion();

        VersioningHelper.setExpandedVersion( project, originalVersion, qualifier );
    }

    protected String getVersion( ArtifactDescription artifact )
    {
        String version = artifact.getKey().getVersion();
        MavenProject project = artifact.getMavenProject();
        if ( project != null )
        {
            version = VersioningHelper.getExpandedVersion( project, version );
        }
        return version;
    }
	
    
    protected List<TargetEnvironment> getEnvironments()
    {
        return getTargetPlatformConfiguration().getEnvironments();
    }

    protected TargetPlatformConfiguration getTargetPlatformConfiguration()
    {
        TargetPlatformConfiguration configuration =
            (TargetPlatformConfiguration) project.getContextValue( TychoConstants.CTX_TARGET_PLATFORM_CONFIGURATION );

        if ( configuration == null )
        {
            throw new IllegalStateException(
                                             "Project build target platform configuration has not been initialized properly." );
        }

        return configuration;
    }
    
    public List<File> getProductFiles()
    {
    	return getEclipseRepositoryProject().getProductFiles(project);
    }

    public List<File> getCategoriesFiles()
    {
    	return getEclipseRepositoryProject().getCategoriesFiles(project);
    }
    
    protected void createArchive( File target, String classifier )
    throws MojoExecutionException
	{
	    ZipArchiver zipper;
	    try
	    {
	        zipper = (ZipArchiver) plexus.lookup( ZipArchiver.ROLE, "zip" );
	    }
	    catch ( ComponentLookupException e )
	    {
	        throw new MojoExecutionException( "Unable to resolve ZipArchiver", e );
	    }
	
	    StringBuilder filename = new StringBuilder( project.getBuild().getFinalName() );
	    if ( classifier != null )
	    {
	        filename.append( '-' ).append( classifier );
	    }
	    filename.append( ".zip" );
	
	    File destFile = new File( project.getBuild().getDirectory(), filename.toString() );
	
	    try
	    {
	        zipper.addDirectory( target );
	        zipper.setDestFile( destFile );
	        zipper.createArchive();
	    }
	    catch ( Exception e )
	    {
	        throw new MojoExecutionException( "Error packing product", e );
	    }
	
	    if ( classifier != null )
	    {
	        projectHelper.attachArtifact( project, destFile, classifier );
	    }
	    else
	    {
	        // main artifact
	        project.getArtifact().setFile( destFile );
	    }
	}


}
