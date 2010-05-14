package org.codehaus.tycho.eclipsepackaging;

import java.io.File;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.tycho.TargetEnvironment;
import org.codehaus.tycho.TargetPlatformConfiguration;
import org.codehaus.tycho.TychoConstants;

/**
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
    
    /**
     * Location of generated .product file with all versions replaced with their expanded values.
     * 
     * @parameter expression="${project.build.directory}/${project.artifactId}.product"
     */
    private File expandedProductFile;


    public void execute() throws MojoExecutionException, MojoFailureException {
        if ( separateEnvironments )
        {
            for ( TargetEnvironment environment : getEnvironments() )
            {
                File target = ProductExportMojo.getTarget( environment, separateEnvironments, project );

                if ( createProductArchive )
                {
                	String classifier = ProductExportMojo.toString( environment );
                    File archive = createProductArchive( target, classifier );
                    projectHelper.attachArtifact( project, archive, classifier );
                }
            }
            project.getArtifact().setFile( expandedProductFile );
        }
        else
        {
            if ( createProductArchive )
            {
                File target = ProductExportMojo.getTarget( null, separateEnvironments, project );
                File archive = createProductArchive( target, null );
                // main artifact
    	        project.getArtifact().setFile( archive );
            }
            else
            {
            	project.getArtifact().setFile( expandedProductFile );
            }
            
        }
        
    }
    
    /**
     * @param target
     * @param classifier
     * @return The generated zip.
     * @throws MojoExecutionException
     */
    private File createProductArchive( File target, String classifier )
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
	    
	    return destFile;
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
