package org.codehaus.tycho.eclipsepackaging;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.tycho.TargetEnvironment;
import org.codehaus.tycho.TargetPlatformConfiguration;
import org.codehaus.tycho.TychoConstants;

/**
 * TODO. right now it does nothing. first make the p2 stuff work.
 * @goal product-export-packaging
 */
public class PackageProductExportMojo extends AbstractTychoPackagingMojo {
	
    /**
     * @parameter expression="${tycho.product.createArchive}" default-value="true"
     */
    private boolean createProductArchive;

    /**
     * If true (the default), produce separate directory structure for each supported runtime environment.
     * 
     * @parameter default-value="true"
     */
    private boolean separateEnvironments = true;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if ( separateEnvironments )
        {
            for ( TargetEnvironment environment : getEnvironments() )
            {
                File target = ProductExportMojo.getTarget( environment, separateEnvironments, project );

                if ( createProductArchive )
                {
                    createProductArchive( target, ProductExportMojo.toString( environment ) );
                }
            }
        }
        else
        {
            File target = ProductExportMojo.getTarget( null, separateEnvironments, project );


            if ( createProductArchive )
            {
                createProductArchive( target, null );
            }
            
        }

    }
    
    
    private void createProductArchive( File target, String classifier )
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
	    if ( separateEnvironments )
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
	
	    if ( separateEnvironments )
	    {
	        projectHelper.attachArtifact( project, destFile, classifier );
	    }
	    else
	    {
	        // main artifact
	        project.getArtifact().setFile( destFile );
	    }
	}

    private List<TargetEnvironment> getEnvironments()
    {
        return (List<TargetEnvironment>) project.getContextValue(ProductExportMojo.PRODUCT_EXPORT_ENVIRONMENTS);
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

}
