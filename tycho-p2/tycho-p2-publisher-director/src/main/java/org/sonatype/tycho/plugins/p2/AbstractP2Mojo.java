package org.sonatype.tycho.plugins.p2;

import java.io.File;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.archiver.AbstractArchiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.tar.TarArchiver;
import org.codehaus.plexus.archiver.tar.TarArchiver.TarCompressionMethod;
import org.codehaus.plexus.archiver.tar.TarLongFileMode;
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
    
    /**
     * 
     * @param target
     * @param classifier
     * @param isZip true for a zip, false for a tar.gz
     * @throws MojoExecutionException
     */
    protected void createArchive( File target, String classifier, boolean isZip,
    		String destFileNameNoExtensionOfClassifier, String topLevelDirectory )
    throws MojoExecutionException
	{
    	AbstractArchiver zipper = null;
	    try
	    {
	    	if (isZip)
	    	{
	    		zipper =(AbstractArchiver) plexus.lookup( ZipArchiver.ROLE, "zip" );
	    	}
	    	else
	    	{
	    		//the TarArchiver does not supporting symbolic links: instead it archives the linked file.
	    		TarArchiver tzipper = (TarArchiver) plexus.lookup(TarArchiver.ROLE, "tar");
	    		TarCompressionMethod gzip = new TarCompressionMethod();
	    		TarLongFileMode tarFileMode = new TarLongFileMode();
	    		try {
					gzip.setValue("gzip");
		    		tarFileMode.setValue(TarLongFileMode.GNU);
				} catch (ArchiverException e) {
					throw new MojoExecutionException("Unable to setup the compression method", e);
				}
	    		tzipper.setCompression(gzip);
	    		tzipper.setLongfile(tarFileMode);
	    		
	    		zipper= tzipper;
	    	}
	    }
	    catch ( ComponentLookupException e )
	    {
	        throw new MojoExecutionException( "Unable to resolve ZipArchiver", e );
	    }
	
	    StringBuilder filename = new StringBuilder( destFileNameNoExtensionOfClassifier == null
	    		? project.getBuild().getFinalName()
	    		: destFileNameNoExtensionOfClassifier);
	    if ( classifier != null && filename.indexOf(classifier) == -1)
	    {
	        filename.append( '-' ).append( classifier );
	    }
	    filename.append( isZip ? ".zip" :  ".tar.gz" );
	
	    File destFile = new File( project.getBuild().getDirectory(), filename.toString() );
	
	    boolean jobDone = false;
	    if (!isZip)
	    {
	    	jobDone = createTarGzOnCmdLine(target, destFile, topLevelDirectory);
	    }
	    
	    if (!jobDone)
	    {
		    try
		    {
		        if (topLevelDirectory == null)
		        {
		        	zipper.addDirectory( target );
		        }
		        else
		        {
		        	topLevelDirectory = topLevelDirectory.endsWith("/")
        				? topLevelDirectory : (topLevelDirectory + "/");
		        	zipper.addDirectory( target, topLevelDirectory);
		        }
		        System.err.println("directory " + target.getAbsolutePath() +  " with prefix " + topLevelDirectory);
		        zipper.setDestFile( destFile );
		        zipper.createArchive();
		    }
		    catch ( Exception e )
		    {
		        throw new MojoExecutionException( "Error packing product", e );
		    }
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
    
    private static Boolean TAR_AVAILABLE = null;
    
    /**
     * The plexus archiver does not save symbolic links.
     * This is a workaround that will work only when building on a unix machine.
     * We need the symbolic links support for the macosx launcher.
     * @param target
     * @param classifier
     * @throws MojoExecutionException
     */
    private boolean createTarGzOnCmdLine( File target, File dest, String topLevelDirectory )
    throws MojoExecutionException
	{
    	if (TAR_AVAILABLE == null)
    	{
	    	try
	    	{
	    		Process proc = Runtime.getRuntime().exec("tar --version");
	    		int res = proc.waitFor();
	    		if (res != 0)
	    		{
	    			getLog().warn("No tar available on the command line.");
	    			TAR_AVAILABLE = Boolean.FALSE;
	    			return false;
	    		}
			} catch (Exception e) {
				TAR_AVAILABLE = Boolean.FALSE;
				return false;
			}
			TAR_AVAILABLE = Boolean.TRUE;
    	}
    	else if (TAR_AVAILABLE == Boolean.FALSE)
    	{
    		return false;
    	}
    	
		String oriName = null;
		File wkDir = target;
    	try
    	{//tar -czvf
    		String cmdLink = "tar -czf " + dest.getAbsolutePath() + /*" -C " + target.getAbsolutePath() +*/ " .";
    		if (topLevelDirectory != null) {
    			oriName = dest.getName();
    			wkDir = target.getParentFile();
    			if (!topLevelDirectory.equals(target.getName())) {
	    			String mvCmdLine = "mv " + target.getName() + " " + topLevelDirectory;
	    			getLog().info("mv cmd-line: " + mvCmdLine + " executed in " + wkDir.getAbsolutePath());
	    			Process proc = Runtime.getRuntime().exec(mvCmdLine, null, wkDir);
	        		int res = proc.waitFor();
	        		if (res != 0)
	        		{
	        			getLog().error("Unable to run mv on the command line: " + mvCmdLine + " in the folder " + wkDir.getAbsolutePath());
	        			return false;
	        		}
    			}
        		cmdLink = "tar -czf ../" + dest.getName() + " " + topLevelDirectory;
    		}
    		
    		getLog().info("tar-gzip cmd-line: " + cmdLink);
    		Process proc = Runtime.getRuntime().exec(cmdLink, null, wkDir);
    		int res = proc.waitFor();
    		if (res != 0)
    		{
    			getLog().error("Unable to run tar on the command line." + " executed in " + wkDir.getAbsolutePath());
    			return false;
    		}
    		return true;
		} catch (Exception e) {
			getLog().error("Unable to run tar on the command line" + " in " + wkDir.getAbsolutePath(), e);
		}
		return false;
	}

}
