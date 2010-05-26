package org.sonatype.tycho.plugins.p2.publisher;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.io.RawInputStreamFacade;
import org.codehaus.tycho.ArtifactDescription;
import org.codehaus.tycho.TargetEnvironment;
import org.codehaus.tycho.TychoProject;
import org.codehaus.tycho.model.ProductConfiguration;
import org.sonatype.tycho.plugins.p2.AbstractP2AppInvokerMojo;

/**
 * This goal invokes the various publisher applications involved.
 * It publishes in this order:
 * <ol>
 * <li>features and bundles that have been assembled by the prepare-features-and-bundles mojo</li>
 * <li>a product if there is indeed a product file.</li>
 * <li>categories if there is a site.xml or a categories definition file.</li>
 * </ol>
 * <p>
 * @see http://wiki.eclipse.org/Equinox/p2/Publisher
 * </p>
 * @goal publish-repository
 */
public class PublishRepositoryMojo extends AbstractP2AppInvokerMojo {
	
	public static String FEATURES_AND_BUNDLES_PUBLISHER_APP_NAME = "org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher";
	public static String PRODUCT_PUBLISHER_APP_NAME = "org.eclipse.equinox.p2.publisher.ProductPublisher";
	public static String CATEGORIES_PUBLISHER_APP_NAME = "org.eclipse.equinox.p2.publisher.CategoryPublisher";

	
	public void execute() throws MojoExecutionException, MojoFailureException {
    	publishFeaturesAndBundles();
		publishProducts();
		publishCategories();
    }
	
	protected void publishFeaturesAndBundles()
	throws MojoExecutionException, MojoFailureException {
		try
		{
			//see http://wiki.eclipse.org/Equinox/p2/Publisher#Features_And_Bundles_Publisher_Application
			Commandline cli = super.getCommandLine(FEATURES_AND_BUNDLES_PUBLISHER_APP_NAME);
			cli.addArguments(new String[] {
					"-metadataRepository", getRepositoryValue(),
					"-artifactRepository", getRepositoryValue(),
					"-source", targetRepository.getCanonicalPath(),
					"-publishArtifacts"});
			cli.addArguments(getConfigsParameter());
			cli.addArguments(getCompressFlag());
			
			super.execute(cli, null);
		}
		catch (IOException ioe)
		{
			throw new MojoExecutionException("Unable to execute the publisher", ioe);
		}
	}

	protected void publishProducts()
	throws MojoExecutionException, MojoFailureException {
		for (File prod : getProductFiles())
		try
		{
			//see http://wiki.eclipse.org/Equinox/p2/Publisher#Product_Publisher
			
			//first prepare an "expanded" product configuration (and eventually a small config.ini
			//if we still find out that the bundles.info does not get generated.)
			ExpandedProductConfiguration conf = expandProductConfiguration(prod);
			
			Commandline cli = super.getCommandLine(FEATURES_AND_BUNDLES_PUBLISHER_APP_NAME);
			cli.addArguments(new String[] {
					"-metadataRepository", getRepositoryValue(),
					"-artifactRepository", getRepositoryValue(),
					"-productFile", conf.location.getCanonicalPath(),
					"-append",
					"-executables", getEquinoxExecutableFeature(),
					"-publishArtifacts"});
			cli.addArguments(getFlavorParameter(conf.parsed.getId()));
			cli.addArguments(getConfigsParameter());
			cli.addArguments(getCompressFlag());
			
			super.execute(cli, null);
		}
		catch (IOException ioe)
		{
			throw new MojoExecutionException("Unable to execute the publisher", ioe);
		}
	}

	protected void publishCategories()
	throws MojoExecutionException, MojoFailureException {
		for (File categoryDef : getCategoriesFiles())
		{
			try
			{
				// see http://wiki.eclipse.org/Equinox/p2/Publisher#Category_Publisher
				
				Commandline cli = super.getCommandLine(CATEGORIES_PUBLISHER_APP_NAME);
				cli.addArguments(new String[] {
						"-metadataRepository", getRepositoryValue(),
						"-categoryDefinition", categoryDef.getCanonicalPath(),
						"-categoryQualifier"});
				cli.addArguments(getCompressFlag());
				
				super.execute(cli, null);
			}
			catch (IOException ioe)
			{
				throw new MojoExecutionException("Unable to execute the publisher for the categories definition " + categoryDef.getAbsolutePath(), ioe);
			}
		}
	}
	
	private static class ExpandedProductConfiguration
	{
		public ProductConfiguration parsed;
		public File location;
	}
	
	/**
	 * Workaround the current limitations of the ProductPublisher mentionned
	 * on the wiki: http://wiki.eclipse.org/Equinox/p2/Publisher#Product_Publisher 
	 */
	private ExpandedProductConfiguration expandProductConfiguration(File productFile)
	throws MojoExecutionException {
        try
        {
        	File expandedProductFile = new File(project.getBuild().getDirectory(), productFile.getName());
        	ProductConfiguration productConfiguration = ProductConfiguration.read(productFile);
        	
        	//this is where we can accomodate things.
        	
        	ProductConfiguration.write( productConfiguration, expandedProductFile );
        	File p2inf = new File(project.getBasedir(), "p2.inf");
            if ( p2inf.canRead() )
            {
            	File newP2Inf = new File( expandedProductFile.getParentFile(), p2inf.getName() );
            	if (!newP2Inf.exists())
            	{
            		FileUtils.copyFile( p2inf, newP2Inf);
            	}
            }
        	ExpandedProductConfiguration res = new ExpandedProductConfiguration();
        	res.location = expandedProductFile;
        	res.parsed = productConfiguration;
        	return res;
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error writing expanded product configuration file", e );
        }

	}
	
	/**
	 * @return the value of the -configs argument: a list of config identifiers separated
	 * by a comma.
	 */
	private String[] getConfigsParameter()
	{
		List<TargetEnvironment> envs = getEnvironments();
		if (envs.isEmpty())
		{
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (TargetEnvironment env : envs)
		{
			if (sb.length() > 0)
			{
				sb.append(",");
			}
			sb.append(env.getWs() + "." + env.getOs() + "." + env.getArch());
		}
		return new String[] { "-configs", sb.toString() };
	}
	
	/**
	 * @return The value of the -metadataRepository and -artifactRepository (always the same for us so far)
	 */
	private String getRepositoryValue()
	{
		try {
			return targetRepository.toURI().toURL().toExternalForm();
		} catch (MalformedURLException e) {
			//will never happen?
			getLog().warn("Unexpected exception", e);
			return "file:" + targetRepository.getAbsolutePath();
		}
	}
	
	/**
	 * Currently always return 'tooling'. Do something else?
	 * @param productId
	 * @return
	 */
	private String[] getFlavorParameter(String productId)
	{
		return new String[] {"-flavor", "tooling"};
	}
	
	/**
	 * @return The '-compress' flag or empty if we don't want to compress.
	 */
	private String[] getCompressFlag()
	{
		return new String[] {"-compress"};
	}
	
    /**
     * Same code than in the ProductExportMojo
     * @return
     * @throws MojoExecutionException
     */
    private String getEquinoxExecutableFeature() throws MojoExecutionException, MojoFailureException
    {
        ArtifactDescription artifact =
            getTargetPlatform().getArtifact( TychoProject.ECLIPSE_FEATURE, "org.eclipse.equinox.executable", null );

        if ( artifact == null )
        {
            throw new MojoExecutionException( "Unable to locate the equinox launcher feature (aka delta-pack)" );
        }
        
        File equinoxExecFeature = artifact.getLocation();
        if (equinoxExecFeature.isDirectory())
        {
        	return equinoxExecFeature.getAbsolutePath();
        }
        else
        {
        	File unzipped = new File(project.getBuild().getOutputDirectory(),
        			artifact.getKey().getId() + "-" + artifact.getKey().getVersion());
        	if (unzipped.exists())
        	{
        		return unzipped.getAbsolutePath();
        	}
        	//unzip now then:
        	ZipFile zip = null;
            try
            {
            	zip = new ZipFile( equinoxExecFeature );
                Enumeration<? extends ZipEntry> entries = zip.entries();

                while ( entries.hasMoreElements() )
                {
                    ZipEntry entry = entries.nextElement();

                    if ( entry.isDirectory() )
                    {
                        continue;
                    }

                    String name = entry.getName();

                    File targetFile = new File( unzipped, name );
                    targetFile.getParentFile().mkdirs();
                    FileUtils.copyStreamToFile( new RawInputStreamFacade( zip.getInputStream( entry ) ), targetFile );
                }
                return unzipped.getAbsolutePath();
            }
            catch (IOException cause)
            {
            	throw new MojoFailureException("Unable to unzip the eqiuinox executable feature", cause);
            }
            finally
            {
                if (zip != null) try { zip.close(); } catch (IOException ioe) {}
            }
        }
    	
    	//on my machine:
    	//.m2/repository/p2/org/eclipse/update/feature/org.eclipse.equinox.executable/3.4.0.v20100505-7M7J8ZFIhIeyngAml28OC5/org.eclipse.equinox.executable-3.4.0.v20100505-7M7J8ZFIhIeyngAml28OC5.jar
//    	return "/home/hmalphettes/.m2/repository/p2/org/eclipse/update/feature/org.eclipse.equinox.executable/" +
//    			"3.4.0.v20100505-7M7J8ZFIhIeyngAml28OC5/" +
//    			"org.eclipse.equinox.executable-3.4.0.v20100505-7M7J8ZFIhIeyngAml28OC5.jar";
    }
}
