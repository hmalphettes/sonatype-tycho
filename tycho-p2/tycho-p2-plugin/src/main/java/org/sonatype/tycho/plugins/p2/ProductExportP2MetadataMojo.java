package org.sonatype.tycho.plugins.p2;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.io.RawInputStreamFacade;
import org.codehaus.tycho.ArtifactDescription;
import org.codehaus.tycho.PluginDescription;
import org.codehaus.tycho.TargetEnvironment;
import org.codehaus.tycho.TargetPlatform;
import org.codehaus.tycho.TychoConstants;
import org.codehaus.tycho.TychoProject;
import org.codehaus.tycho.model.BundleConfiguration;
import org.codehaus.tycho.model.ProductConfiguration;

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
	public static String PRODUCT_DIRECTOR_APP_NAME = "org.eclipse.equinox.p2.director";

//copied from ProductExportMojo	
	/** key in the project context to easily access the environments. Contains List<TargetEnvironment> */
	public static String PRODUCT_EXPORT_ENVIRONMENTS = TychoConstants.CTX_BASENAME + "/ProductExportMojo/environments";
	/** key in the project context to easily access the expandedProductConfigurationFile */
	public static String PRODUCT_EXPORT_EXPANDED_PRODUCT_CONFIGURATION_FILE = TychoConstants.CTX_BASENAME + "/ProductExportMojo/expandedProductConfigurationFile";
	/** key in the project context to easily access if we build a single environment or not. Contains a Boolean. */
	public static String PRODUCT_EXPORT_SEPARATE_ENVIRONMENTS = TychoConstants.CTX_BASENAME + "/ProductExportMojo/separateEnvironments";
	/** key in the project context to easily access if we produce a p2 enable product. Contains a Boolean. */
	public static String PRODUCT_EXPORT_ENABLE_P2 = TychoConstants.CTX_BASENAME + "/ProductExportMojo/enableP2";

    /** @parameter expression="${session}" */
    protected MavenSession session;

    /**
     * If true (the default), produce separate directory structure for each supported runtime environment.
     * Read on the package product export configuration.
     */
    private Boolean separateEnvironments = null;
    
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
     * Read it from the ProductExportMojo
     */
    private File expandedProductFile;
    
	/**
	 * The new profile to be created during p2 Director install & 
	 * the default profile for the the application which is set in config.ini
	 * 
	 * @parameter expression="${profile}"
	 */
	private String profile;
    
    private TargetEnvironment currentEnvironment;

    private File currentRepository;
    
    /**
     * Parsed product configuration file
     */
    private ProductConfiguration productConfiguration;

    
    public void execute() throws MojoExecutionException, MojoFailureException
    {	
    	Boolean enableP2 = (Boolean)project.getContextValue(PRODUCT_EXPORT_ENABLE_P2);
    	if (enableP2 == null) {
    		throw new MojoFailureException("The mojo 'product-export' must be executed be for the mojo 'product-export-p2-metadata'" +
    				" ");
    	}
    	if (!enableP2) {
    		return;//nothing to do.
    	}
    	expandedProductFile = (File)project.getContextValue(PRODUCT_EXPORT_EXPANDED_PRODUCT_CONFIGURATION_FILE);
    	separateEnvironments = (Boolean) project.getContextValue(PRODUCT_EXPORT_SEPARATE_ENVIRONMENTS);
    	

    	if (!separateEnvironments) {
    		return;//unsupported
    	}
    	
        if ( !expandedProductFile.exists() )
        {
            throw new MojoExecutionException( "Product configuration file not found "
                + expandedProductFile.getAbsolutePath() );
        }

        try
        {
            getLog().debug( "Parsing productConfiguration" );
            productConfiguration = ProductConfiguration.read( expandedProductFile );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error reading product configuration file", e );
        }
    	computeProfile();

    	
        for ( TargetEnvironment environment : getEnvironments() )
        {
            File target = getTarget( environment );
            processOneEnvironment(target, environment);

        }

    }
    
    private void processOneEnvironment(File target, TargetEnvironment environment)
    throws MojoExecutionException, MojoFailureException
    {
    	File targetEclipse = new File( target, "eclipse" );
        if (!targetEclipse.exists())
        {
        	throw new MojoFailureException("The folder '" + 
        			targetEclipse.getAbsolutePath() + "' for the exported product does not exist.");
        }
        
        currentTarget = targetEclipse;
        currentEnvironment = environment;
        currentRepository = new File(project.getBuild().getDirectory(), toString(environment) + "_repository");
        currentRepository.mkdirs();
        
        //Step-1 publish the features and bundles:
        //http://wiki.eclipse.org/Equinox/p2/Publisher#Features_And_Bundles_Publisher_Application
        currentPublisherApp = FeatureP2MetadataMojo.FEATURES_AND_BUNDLES_PUBLISHER_APP_NAME;
        currentOtherArguments = new String[] {
        		"-configs", toString(environment),
        		"-compress",
        		"-publishArtifacts"
        };
        super.execute();
        
        //Step-2 publish the product:
        //http://wiki.eclipse.org/Equinox/p2/Publisher#Product_Publisher
        currentPublisherApp = PRODUCT_PUBLISHER_APP_NAME;
        currentOtherArguments = new String[] {
        		"-productFile", expandedProductFile.getAbsolutePath(),
        		"-append",
        		"-publishArtifacts",
        		"-executables", getEquinoxExecutableFeature(),
        		"-flavor", "tooling",
        		"-configs", toString(environment)
        };
        super.execute();
        
        //Step-3 invoke director to install the repo.
        currentPublisherApp = PRODUCT_DIRECTOR_APP_NAME;
        currentOtherArguments = null;
        //TODO: save the config.ini file before it gets overridden
        //and decide whether we want to use the one generated by p2's director or the
        //one generated in ExportMojo
        File oriConfigIni = new File(targetEclipse, "configuration/config.ini");
        if (oriConfigIni.exists())
        {
            try {
				FileUtils.copyFile(oriConfigIni, new File(targetEclipse, "configuration/config.ini.before-p2director"));
			} catch (IOException e) {
				getLog().warn("Unable to save a backup of the config.ini file", e);
			}
        }
        super.execute();
        cleanup(targetEclipse);
    }
    
    /**
     * After P2 Director has installed the IUs and regenrated a couple of files it
     * seems there are a couple of things not quite right.
     * Maybe some parameters are missing when we invoke the P2 director or
     * there are enhancements to be filed.
     * 
     * Here are the tweaks:
     * <ul
     * <li> if we are using the simpleconfigurator then p2 overrode the osgi.bundles. we
     * override it and put back the single bundle to start: simpleconfigurator.</li>
     * <li> the configuration/org.eclipse.update/platform.xml file is not taking
     * into account the -roaming parameter
     * and use an aboslute path to the folder where the features are defined
     * we override that line.</li>
     * </ul>
     * 
     * @param targetEclipse
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    private void cleanup(File targetEclipse) throws MojoExecutionException, MojoFailureException
    {
    	File oriConfigIni = new File(targetEclipse, "configuration/config.ini");
    	try {
			FileUtils.copyFile(oriConfigIni, new File(targetEclipse, "configuration/config.ini.after-p2director"));
		} catch (IOException e) {
			getLog().warn("Unable to save a backup of the config.ini file", e);
		}

    	
        //for now: if we see that the simpleconfigurator is used
        //then override the osgi.bundles property written by p2
    	String simpleconfiguratorInitUrl = null;
    	InputStream in = null;
    	OutputStream out = null;
    	try
    	{
    		Properties props = new Properties();
    		in = new BufferedInputStream(new FileInputStream(oriConfigIni));
    		props.load(in);
    		IOUtil.close(in);
    		in = null;
    		simpleconfiguratorInitUrl = (String)props.get("org.eclipse.equinox.simpleconfigurator.configUrl");
    		if (simpleconfiguratorInitUrl != null)
    		{
    			props.setProperty("osgi.bundles", "org.eclipse.equinox.simpleconfigurator@start");
    			out = new BufferedOutputStream(new FileOutputStream(oriConfigIni));
    			props.store(out, "");
    		}
    		
    	}
    	catch (IOException ioe)
    	{
    		getLog().warn("Unable to load the " + oriConfigIni.getAbsolutePath() + ".", ioe);
    	}
    	finally
    	{
    		if (in != null) IOUtil.close(in);
    		if (out != null) IOUtil.close(out);
    	}
    	
    	//now update the platform.xml
    	//this is certainly some quick code. hoping to do not have to do this when we catch up with p2.
    	File platformXml = new File(targetEclipse, "configuration/org.eclipse.update/platform.xml");
    	if (!platformXml.exists())
    	{
    		//complain?
    		return;
    	}
    	try {
			FileUtils.copyFile(platformXml, new File(targetEclipse, "configuration/org.eclipse.update/platform.xml.before-force-roaming"));
		} catch (IOException e) {
			getLog().warn("Unable to save a backup of the config.ini file", e);
		}
    	LineNumberReader lr = null;
    	BufferedWriter writer = null;
    	try
    	{
    		lr = new LineNumberReader(new InputStreamReader(new FileInputStream(platformXml), "UTF-8"));
    		LinkedList<String> lines = new LinkedList<String>();
    		String line = lr.readLine();
    		boolean needUpdate = false;
    		while (line != null)
    		{
    			if (line.trim().startsWith("<site ") && line.indexOf("url=\"file:") != -1)
    			{
    				needUpdate = true;
    				line = "	<site enabled=\"true\" updateable=\"true\" policy=\"USER-EXCLUDE\" url=\"platform:/base/\">";
    			}
    			lines.add(line);
    		}
    		IOUtil.close(lr);
    		lr = null;
    		if (needUpdate)
    		{
    			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(platformXml)));
    			while (!lines.isEmpty())
    			{
    				writer.write(lines.removeFirst());
    				writer.newLine();
    			}
    		}
    	}
    	catch (IOException ioe)
    	{
    		throw new MojoExecutionException("Unable to update the " + platformXml.getAbsolutePath(), ioe);
    	}
    	finally
    	{
    		if (lr != null) IOUtil.close(lr);
    		if (writer != null) IOUtil.close(writer);
    	}
    	
        
    }
    
    private void computeProfile()
    {
	    if ( profile == null )
	    {
	    	//let's do something a bit nicer:
	    	//the last segment of the product id. unless it is 'product'. then look before.
	    	String productId = productConfiguration.getId();
	    	StringTokenizer tokenizer = new StringTokenizer(productId, ".");
	    	ArrayList<String> toks = new ArrayList<String>();
	    	while (tokenizer.hasMoreElements())
	    	{
	    		toks.add(tokenizer.nextToken());
	    	}
	    	String[] segs = toks.toArray(new String[toks.size()]);
	    	for (int i = segs.length -1; i >= 0; i--)
	    	{
	    		if (segs[i].toLowerCase().indexOf("prod") == -1)
	    		{
	    			if (i > 0 && segs[i].toLowerCase().equals("sdk"))
	    			{
	    				profile = segs[i-1] + "SDK";
	    				break;
	    			}
	    			else
	    			{
	    				profile = segs[i];
	    				break;
	    			}
	    		}
	    	}
	    	if (profile == null)
	    	{
	    		profile = "profile";
	    	}
	    	getLog().info("Computed profile " + profile);
	    }

    }
    
    protected String[] getDefaultPublisherArguments() throws IOException
    {
    	if (currentPublisherApp.equals(PRODUCT_DIRECTOR_APP_NAME))
    	{
    		return new String[] {
					"-nosplash", // 
					"-application",	PRODUCT_DIRECTOR_APP_NAME, // 
					"-installIU", productConfiguration.getId(),//
					"-metadataRepository", getUpdateSiteLocation().toURI().toURL().toExternalForm(), //
					"-artifactRepository", getUpdateSiteLocation().toURI().toURL().toExternalForm(), //
					"-destination",	currentTarget.getCanonicalPath(),
					"-profile",	profile,
					"-profileProperties", "org.eclipse.update.install.features=true",
					"-bundlepool", currentTarget.getCanonicalPath(),
					"-p2.os", currentEnvironment.getOs(), 
					"-p2.ws", currentEnvironment.getWs(),
					"-p2.arch",	currentEnvironment.getArch(),
					"-roaming"
    		};
    	}
    	return super.getDefaultPublisherArguments();
    }
    
    
    
    
    /**
     * @return The vm arg line passed to the publisher app.
     */
    protected String internalGetVmArgLine()
    {
    	if (currentPublisherApp.equals(PRODUCT_DIRECTOR_APP_NAME))
    	{
    		return "-Declipse.p2.data.area="+currentTarget+"/p2/" + (argLine != null ? " " + argLine : "");
    	}
    	return argLine;
    }
    
    
    /**
     * Same code than in the ProductExportMojo
     * @return
     * @throws MojoExecutionException
     */
    private String getEquinoxExecutableFeature() throws MojoExecutionException, MojoFailureException {
        ArtifactDescription artifact =
            getTargetPlatform().getArtifact( TychoProject.ECLIPSE_FEATURE, "org.eclipse.equinox.executable", null );

        if ( artifact == null )
        {
            throw new MojoExecutionException( "Native launcher is not found for " + currentEnvironment.toString() );
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
    
    /**
     * @return the name of the eclipse application to use.
     */
    protected String getPublisherApplication() {
    	return currentPublisherApp;
    }
    
    /**
     * @return the root folder of repository.
     */
    @Override
    protected File getUpdateSiteLocation()
    {
        return currentRepository;
    }
    /**
     * @return the root folder of the product currently indexed.
     */
    @Override
    protected File getSourceLocation()
    {
    	return currentTarget;
    }
    
    /**
     * By default returns null.
     * @return some more arguments added to the command line to invoke the publisher.
     * For example the product needs to be passed the config argument.
     */
    protected String[] getOtherPublisherArguments()
    {
    	return currentOtherArguments;
    }

	private void setPropertyIfNotNull(Properties properties, String key, String value)
	{
		if (value != null)
		{
			properties.setProperty(key, value);
		}
	}
	
	/**
	 * TODO: review this. It does not look right for a runtime app where there is no org.eclipse.core.runtime
	 * @return
	 */
	private String getFeaturesOsgiBundles()
	{
		String bundles = "org.eclipse.equinox.common@2:start," +
				"org.eclipse.update.configurator@3:start," +
				"org.eclipse.core.runtime@start,org.eclipse.equinox.ds@1:start," +
				"org.eclipse.equinox.simpleconfigurator@1:start" ;
		return bundles;
	}


//duplicated from ProductExportMojo in the maven-osgi-packaging plugin    
    private List<TargetEnvironment> getEnvironments()
    {
        return (List<TargetEnvironment>)project.getContextValue(PRODUCT_EXPORT_ENVIRONMENTS);
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
