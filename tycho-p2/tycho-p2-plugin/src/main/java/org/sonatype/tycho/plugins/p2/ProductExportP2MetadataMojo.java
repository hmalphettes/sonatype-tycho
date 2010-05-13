package org.sonatype.tycho.plugins.p2;

import java.io.File;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.tycho.ArtifactDescription;
import org.codehaus.tycho.TargetEnvironment;
import org.codehaus.tycho.TargetPlatform;
import org.codehaus.tycho.TargetPlatformConfiguration;
import org.codehaus.tycho.TychoConstants;
import org.codehaus.tycho.TychoProject;

/**
 * See http://wiki.eclipse.org/Equinox/p2/Publisher#Product_Publisher
 * 
 * This mojo is different from the other ones as we need to support
 * the multi-environment export.
 * 
 * @goal product-export-p2-metadata
 */
public class ProductExportP2MetadataMojo extends AbstractP2MetadataMojo {
	
	public static String PRODUCT_PUBLISHER_APP_NAME = "org.eclipse.equinox.p2.publisher.ProductPublisher";

    /** @parameter expression="${session}" */
    protected MavenSession session;

    /**
     * If true (the default), produce separate directory structure for each supported runtime environment.
     * TODO: this should be read on the package product export configuration.
     * 
     * @parameter default-value="true"
     */
    private boolean separateEnvironments = true;
    
    /**
     * When execute iterates over each environment
     * this file is the root directory of the currently selected product
     */
    private File currentTarget;
    
    /**
     * First we use the FeaturesAndBundlePublisher for the plugins and features
     * in the non-p2 product.
     * Then we switch to the ProductPublisher.
     * 
     * We do this for each product exported.
     */
    private String currentPublisherApp;
    
    /**
     * For each target environment and for both phases
     * of the publish, we need slightly different arguments.
     */
    private String[] currentOtherArguments;
    
    /**
     * Location of generated .product file with all versions replaced with their expanded values.
     * TODO: read it from the ProductExportMojo
     * 
     * @parameter expression="${project.build.directory}/${project.artifactId}.product"
     */
    private File expandedProductFile;
    
    private TargetEnvironment currentEnvironment;



    public void execute()
    	throws MojoExecutionException, MojoFailureException
    {
    	if (!separateEnvironments) {
    		return;//unsupported
    	}
    	
        for ( TargetEnvironment environment : getEnvironments() )
        {
            File target = getTarget( environment );
            File targetEclipse = new File( target, "eclipse" );
            
            if (!targetEclipse.exists())
            {
            	throw new MojoFailureException("The folder '" + 
            			targetEclipse.getAbsolutePath() + "' for the exported product does not exist.");
            }
            
            currentTarget = targetEclipse;
            currentEnvironment = environment;
            
            //publish the features and bundles:
            //http://wiki.eclipse.org/Equinox/p2/Publisher#Features_And_Bundles_Publisher_Application
            currentPublisherApp = FeatureP2MetadataMojo.FEATURES_AND_BUNDLES_PUBLISHER_APP_NAME;
            currentOtherArguments = new String[] {
            		"-configs", toString(environment),
            		"-compress",
            		"-publishArtifacts",
            		"-console", "-consolelog"
            };
            super.execute();
            
            //now the product:
            //http://wiki.eclipse.org/Equinox/p2/Publisher#Product_Publisher
            currentPublisherApp = PRODUCT_PUBLISHER_APP_NAME;
            currentOtherArguments = new String[] {
            		"-productFile", expandedProductFile.getAbsolutePath(),
            		"-append",
            		"-publishArtifacts",
            		"-executables", getEquinoxExecutableFeature(),
            		"-flavor", "tooling",
            		"-configs", toString(environment),
            		"-console", "-consolelog"
            };
            super.execute();
        }

    }
    
    private String getEquinoxExecutableFeature() throws MojoExecutionException {
        ArtifactDescription artifact =
            getTargetPlatform().getArtifact( TychoProject.ECLIPSE_FEATURE, "org.eclipse.equinox.executable", null );

        if ( artifact == null )
        {
            throw new MojoExecutionException( "Native launcher is not found for " + currentEnvironment.toString() );
        }

        return artifact.getLocation().getAbsolutePath();
    	
    	//on my machine:
    	//.m2/repository/p2/org/eclipse/update/feature/org.eclipse.equinox.executable/3.4.0.v20100505-7M7J8ZFIhIeyngAml28OC5/org.eclipse.equinox.executable-3.4.0.v20100505-7M7J8ZFIhIeyngAml28OC5.jar
//    	return "/home/hmalphettes/.m2/repository/p2/org/eclipse/update/feature/org.eclipse.equinox.executable/" +
//    			"3.4.0.v20100505-7M7J8ZFIhIeyngAml28OC5/" +
//    			"org.eclipse.equinox.executable-3.4.0.v20100505-7M7J8ZFIhIeyngAml28OC5.jar";
    }
    
    /**
     * @return the name of the eclipse application to use.
     */
    protected String getPublisherApplication() {
    	return currentPublisherApp;
    }
    
    /**
     * @return the root folder of the product currently indexed.
     */
    @Override
    protected File getUpdateSiteLocation()
    {
        return currentTarget;
    }
    
    /**
     * By default returns null.
     * @return some more arguments added to the command line to invoke the publisher.
     * For example the product needs to be passed the config argument.
     */
    protected String[] getOtherPublisherArguments() {
    	return currentOtherArguments;
    }

    
//duplicated from ProductExportMojo in the maven-osgi-packaging plugin    
    private List<TargetEnvironment> getEnvironments()
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
    
    private File getTarget( TargetEnvironment environment )
    {
        File target;

        if ( separateEnvironments )
        {
            target = new File( project.getBuild().getDirectory(), toString( environment ) );
        }
        else
        {
            target = new File( project.getBuild().getDirectory(), "product" );
        }

        target.mkdirs();

        return target;
    }


    private String toString( TargetEnvironment environment )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( environment.getOs() ).append( '.' ).append( environment.getWs() ).append( '.' ).append(
                                                                                                           environment.getArch() );
        if ( environment.getNl() != null )
        {
            sb.append( '.' ).append( environment.getNl() );
        }
        return sb.toString();
    }

    protected TargetPlatform getTargetPlatform()
    {
        return getTychoProjectFacet().getTargetPlatform( project );
    }
    
    protected TychoProject getTychoProjectFacet()
    {
        return getTychoProjectFacet( project.getPackaging() );
    }

    protected TychoProject getTychoProjectFacet( String packaging )
    {
        TychoProject facet;
        try
        {
            facet = (TychoProject) session.lookup( TychoProject.class.getName(), packaging );
        }
        catch ( ComponentLookupException e )
        {
            throw new IllegalStateException( "Could not lookup required component", e );
        }
        return facet;
    }

}
