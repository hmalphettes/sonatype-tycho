package org.codehaus.tycho.eclipsepackaging;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.util.ArchiveEntryUtils;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.SelectorUtils;
import org.codehaus.plexus.util.io.RawInputStreamFacade;
import org.codehaus.tycho.ArtifactDependencyVisitor;
import org.codehaus.tycho.ArtifactDependencyWalker;
import org.codehaus.tycho.ArtifactDescription;
import org.codehaus.tycho.PluginDescription;
import org.codehaus.tycho.TargetEnvironment;
import org.codehaus.tycho.TargetPlatformConfiguration;
import org.codehaus.tycho.TychoConstants;
import org.codehaus.tycho.TychoProject;
import org.codehaus.tycho.model.BundleConfiguration;
import org.codehaus.tycho.model.ProductConfiguration;
import org.codehaus.tycho.osgitools.BundleReader;
import org.codehaus.tycho.utils.PlatformPropertiesUtils;
import org.eclipse.pde.internal.swt.tools.IconExe;

/**
 * @goal product-export
 */
public class ProductExportMojo
    extends AbstractTychoPackagingMojo
{
	/** key in the project context to easily access the environments. Contains List<TargetEnvironment> */
	public static String PRODUCT_EXPORT_ENVIRONMENTS = TychoConstants.CTX_BASENAME + "/ProductExportMojo/environments";
	/** key in the project context to easily access the expandedProductConfigurationFile */
	public static String PRODUCT_EXPORT_EXPANDED_PRODUCT_CONFIGURATION_FILE = TychoConstants.CTX_BASENAME + "/ProductExportMojo/expandedProductConfigurationFile";
	/** key in the project context to easily access if we build a single environment or not. Contains a Boolean. */
	public static String PRODUCT_EXPORT_SEPARATE_ENVIRONMENTS = TychoConstants.CTX_BASENAME + "/ProductExportMojo/separateEnvironments";
	/** key in the project context to easily access if we produce a p2 enable product. Contains a Boolean. */
	public static String PRODUCT_EXPORT_ENABLE_P2 = TychoConstants.CTX_BASENAME + "/ProductExportMojo/enableP2";
	
    /**
     * The product configuration, a .product file. This file manages all aspects of a product definition from its
     * constituent plug-ins to configuration files to branding.
     * 
     * @parameter expression="${productConfiguration}" default-value="${project.basedir}/${project.artifactId}.product"
     */
    private File productConfigurationFile;

    /**
     * @parameter expression="${productConfiguration}/../p2.inf"
     */
    private File p2inf;

    /**
     * Location of generated .product file with all versions replaced with their expanded values.
     * 
     * @parameter expression="${project.build.directory}/${project.artifactId}.product"
     */
    private File expandedProductFile;

    /**
     * Parsed product configuration file
     */
    private ProductConfiguration productConfiguration;

    /**
     * @parameter
     * @deprecated use target-platform-configuration <environments/> element
     */
    private TargetEnvironment[] environments;

    /** expression="${tycho.product.enableP2}" 
     * @parameter default-value="true"
     */
    private boolean enableP2;
    
    /**
     * @parameter default-value="false"
     */
    private boolean includeSources;

    /**
     * If true (the default), produce separate directory structure for each supported runtime environment.
     * 
     * @parameter default-value="true"
     */
    private boolean separateEnvironments = true;

    /**
     * @parameter expression="${tycho.product.createArchive}" default-value="true"
     */
    private boolean createProductArchive;

    /**
     * If true, all included features and bundles will be packed. If false (the default), all features will be unpacked
     * and bundles will honour unpack value of <plugin/> element.
     * 
     * @parameter default-value="false"
     */
    private boolean forcePackedDependencies;

    /**
     * @component
     */
    private BundleReader manifestReader;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( !productConfigurationFile.exists() )
        {
            throw new MojoExecutionException( "Product configuration file not found "
                + productConfigurationFile.getAbsolutePath() );
        }

        try
        {
            getLog().debug( "Parsing productConfiguration" );
            productConfiguration = ProductConfiguration.read( productConfigurationFile );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error reading product configuration file", e );
        }

        // build results will vary from system to system without explicit target environment configuration
        if ( productConfiguration.includeLaunchers() && getTargetPlatformConfiguration().isImplicitTargetEnvironment()
            && environments == null )
        {
            throw new MojoFailureException( "Product includes native launcher but no target environment was specified" );
        }
        
 
        if ( separateEnvironments )
        {
        	List<TargetEnvironment> environments = getEnvironments();
            for ( TargetEnvironment environment : getEnvironments() )
            {
                File target = getTarget( environment );
                File targetEclipse = new File( target, "eclipse" );
                targetEclipse.mkdirs();

                generateDotEclipseProduct( targetEclipse );
                includeRootFiles( environment, targetEclipse );

                ProductAssembler assembler = new ProductAssembler( session, manifestReader, targetEclipse, environment );
                assembler.setIncludeSources( includeSources );
                getDependencyWalker( environment ).walk( assembler );

                generateConfigIni( environment, targetEclipse );
                generateLauncherIni( environment, targetEclipse );

                if ( productConfiguration.includeLaunchers() )
                {
                    copyExecutable( environment, targetEclipse );
                }
                
                //in the "expanded" product file specify the config.ini file
                //otherwise p2's ProductPublisher does not read the config.ini
                //file and does not generate the bundles.info.
                //see org.eclipse.equinox.p2.publisher.eclipse#createDataLoader
                try
                {
                	productConfiguration.setConfigIni(
                		targetEclipse.getCanonicalPath() + "/configuration/config.ini",
                		environment.getWs());
                }
                catch (IOException ioe)
                {
                	productConfiguration.setConfigIni(
                    		targetEclipse.getAbsolutePath() + "/configuration/config.ini",
                    		environment.getWs());
                }

            }
        }
        else
        {
            File target = getTarget( null );
            File targetEclipse = new File( target, "eclipse" );
            targetEclipse.mkdirs();

            generateDotEclipseProduct( targetEclipse );
            generateConfigIni( null, targetEclipse );

            for ( TargetEnvironment environment : getEnvironments() )
            {
                includeRootFiles( environment, targetEclipse );
            }

            ProductAssembler assembler = new ProductAssembler( session, manifestReader, targetEclipse, null );
            assembler.setIncludeSources( includeSources );
            if ( forcePackedDependencies )
            {
                assembler.setUnpackFeatures( false );
                assembler.setUnpackPlugins( false );
            }
            getDependencyWalker().walk( assembler );

            if ( productConfiguration.includeLaunchers() )
            {
                for ( TargetEnvironment environment : getEnvironments() )
                {
                    copyExecutable( environment, targetEclipse );
                }
            }

        }

        // String version = getTychoProjectFacet().getArtifactKey( project ).getVersion();
        // String productVersion = VersioningHelper.getExpandedVersion( project, version );
        // productConfiguration.setVersion( productVersion.toString() );

        
        
        try
        {
        	
            ProductConfiguration.write( productConfiguration, expandedProductFile );

            if ( p2inf.canRead() )
            {
                FileUtils.copyFile( p2inf, new File( expandedProductFile.getParentFile(), p2inf.getName() ) );
            }
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error writing expanded product configuration file", e );
        }
        
        project.setContextValue(PRODUCT_EXPORT_ENVIRONMENTS, getEnvironments());
        project.setContextValue(PRODUCT_EXPORT_ENABLE_P2, enableP2);
        project.setContextValue(PRODUCT_EXPORT_EXPANDED_PRODUCT_CONFIGURATION_FILE, expandedProductFile);
        project.setContextValue(PRODUCT_EXPORT_SEPARATE_ENVIRONMENTS, separateEnvironments);
        
        
    }

    private ArtifactDependencyWalker getDependencyWalker( TargetEnvironment environment )
    {
        return getTychoProjectFacet( TychoProject.ECLIPSE_APPLICATION ).getDependencyWalker( project, environment );
    }

    private List<TargetEnvironment> getEnvironments()
    {
        if ( environments != null )
        {
            getLog().warn(
                           "maven-osgi-packaging-plugin <environments/> is deprecated. use target-platform-configuration <environments/>." );
            return Arrays.asList( environments );
        }

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
        return getTarget(environment, separateEnvironments, project);
    }
    
    static File getTarget( TargetEnvironment environment, boolean separateEnvironments, MavenProject project )
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

    static String toString( TargetEnvironment environment )
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

    /**
     * Root files are files that must be packaged with an Eclipse install but are not features or plug-ins. These files
     * are added to the root or to a specified sub folder of the build.
     * 
     * <pre>
     * root=
     * root.<confi>=
     * root.folder.<subfolder>=
     * root.<config>.folder.<subfolder>=
     * </pre>
     * 
     * Not supported are the properties root.permissions and root.link.
     * 
     * @see http://help.eclipse.org/ganymede/index.jsp?topic=/org.eclipse.pde.doc.user/tasks/pde_rootfiles.htm
     * @throws MojoExecutionException
     */
    private void includeRootFiles( TargetEnvironment environment, File target )
        throws MojoExecutionException
    {
        Properties properties = project.getProperties();
        String generatedBuildProperties = properties.getProperty( "generatedBuildProperties" );
        getLog().debug( "includeRootFiles from " + generatedBuildProperties );
        if ( generatedBuildProperties != null )
        {
            Properties rootProperties = new Properties();
            try
            {
                rootProperties.load( new FileInputStream( new File( project.getBasedir(), generatedBuildProperties ) ) );
                if ( !rootProperties.isEmpty() )
                {
                    String config = getConfig( environment );
                    String root = "root";
                    String rootConfig = "root." + config;
                    String rootFolder = "root.folder.";
                    String rootConfigFolder = "root." + config + ".folder.";
                    Set<Entry<Object, Object>> entrySet = rootProperties.entrySet();
                    for ( Iterator iterator = entrySet.iterator(); iterator.hasNext(); )
                    {
                        Entry<String, String> entry = (Entry<String, String>) iterator.next();
                        String key = entry.getKey().trim();
                        // root=
                        if ( root.equals( key ) )
                        {
                            handleRootEntry( target, entry.getValue(), null );
                        }
                        // root.xxx=
                        else if ( rootConfig.equals( key ) )
                        {
                            handleRootEntry( target, entry.getValue(), null );
                        }
                        // root.folder.yyy=
                        else if ( key.startsWith( rootFolder ) )
                        {
                            String subFolder = entry.getKey().substring( ( rootFolder.length() ) );
                            handleRootEntry( target, entry.getValue(), subFolder );
                        }
                        // root.xxx.folder.yyy=
                        else if ( key.startsWith( rootConfigFolder ) )
                        {
                            String subFolder = entry.getKey().substring( ( rootConfigFolder.length() ) );
                            handleRootEntry( target, entry.getValue(), subFolder );
                        }
                        else
                        {
                            getLog().debug( "ignoring property " + entry.getKey() + "=" + entry.getValue() );
                        }
                    }
                }
            }
            catch ( FileNotFoundException e )
            {
                throw new MojoExecutionException( "Error including root files for product", e );
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Error including root files for product", e );
            }
        }
    }

    /**
     * @param rootFileEntry files and directories seperated by semicolons, the syntax is:
     *            <ul>
     *            <li>for a relative file: file:license.html,...</li>
     *            <li>for a absolute file: absolute:file:/eclipse/about.html,...</li>
     *            <li>for a relative folder: rootfiles,...</li>
     *            <li>for a absolute folder: absolute:/eclipse/rootfiles,...</li>
     *            </ul>
     * @param subFolder the sub folder to which the root file entries are copied to
     * @throws MojoExecutionException
     */
    private void handleRootEntry( File target, String rootFileEntries, String subFolder )
        throws MojoExecutionException
    {
        StringTokenizer t = new StringTokenizer( rootFileEntries, "," );
        File destination = target;
        if ( subFolder != null )
        {
            destination = new File( target, subFolder );
        }
        while ( t.hasMoreTokens() )
        {
            String rootFileEntry = t.nextToken();
            String fileName = rootFileEntry.trim();
            boolean isAbsolute = false;
            if ( fileName.startsWith( "absolute:" ) )
            {
                isAbsolute = true;
                fileName = fileName.substring( "absolute:".length() );
            }
            if ( fileName.startsWith( "file" ) )
            {
                fileName = fileName.substring( "file:".length() );
            }
            File source = null;
            if ( !isAbsolute )
            {
                source = new File( project.getBasedir(), fileName );
            }
            else
            {
                source = new File( fileName );
            }

            try
            {
                if ( source.isFile() )
                {
                    FileUtils.copyFileToDirectory( source, destination );
                }
                else if ( source.isDirectory() )
                {
                    FileUtils.copyDirectoryStructure( source, destination );
                }
                else
                {
                    getLog().warn( "Skipping root entry " + rootFileEntry );
                }
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Coult not copy root entry " + fileName, e );
            }
        }
    }

    private String getConfig( TargetEnvironment environment )
    {
        String os = environment.getOs();
        String ws = environment.getWs();
        String arch = environment.getArch();
        StringBuffer config = new StringBuffer( os ).append( "." ).append( ws ).append( "." ).append( arch );
        return config.toString();
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

    private void generateDotEclipseProduct( File target )
        throws MojoExecutionException
    {
        getLog().debug( "Generating .eclipseproduct" );
        Properties props = new Properties();
        setPropertyIfNotNull( props, "version", productConfiguration.getVersion() );
        setPropertyIfNotNull( props, "name", productConfiguration.getName() );
        setPropertyIfNotNull( props, "id", productConfiguration.getId() );

        File eclipseproduct = new File( target, ".eclipseproduct" );
        try
        {
            FileOutputStream fos = new FileOutputStream( eclipseproduct );
            props.store( fos, "Eclipse Product File" );
            fos.close();
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error creating .eclipseproduct file.", e );
        }
    }
    
    /**
     * Transforms the launcherArgs element in the product file into a $launcher.ini file.
     * @param environment
     * @param target
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    private void generateLauncherIni( TargetEnvironment environment, File target )
    throws MojoExecutionException, MojoFailureException
    {
    	String os = environment == null ? null : environment.getOs();
    	String launcherName = productConfiguration.getLauncher() != null
    		? productConfiguration.getLauncher().getName() : null;
    	if (launcherName == null)
    	{
    		return;
    	}
    	String allProgArgs = productConfiguration.getLauncherArgsForProgram(null);
    	String osProgArgs = os != null ? productConfiguration.getLauncherArgsForProgram(os) : null;
    	String allVmArgs = productConfiguration.getLauncherArgsForVM(null);
    	String osVmArgs = os != null ? productConfiguration.getLauncherArgsForVM(os) : null;
    	if (allProgArgs != null || osProgArgs != null || allVmArgs != null || osVmArgs != null)
    	{
    		File launcherIni = new File(target, launcherName + ".ini");
    		BufferedWriter writer = null;
    		try {
    			if (!launcherIni.exists()) launcherIni.createNewFile();
    			boolean osIsWin = os != null && os.indexOf("win") != -1;
    			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(launcherIni), "UTF-8"));
    			//TODO: output the other arguments that we can live without? -startup and --startup-library ?
    			writeArgLine(allProgArgs, writer, osIsWin);
    			writeArgLine(osProgArgs, writer, osIsWin);
    			if (allVmArgs != null || osVmArgs != null) writeArgLine("-vmargs", writer, osIsWin);
    			writeArgLine(allVmArgs, writer, osIsWin);
    			writeArgLine(osVmArgs, writer, osIsWin);
    			writer.flush();
    		}
    		catch (IOException e)
    		{
    			throw new MojoExecutionException("Unable to output the " + launcherIni.getName() + " file", e);
    		}
    		finally
    		{
    			if (writer != null) IOUtil.close(writer);
    		}
    	}
    	
    }
    
    private void writeArgLine(String line, BufferedWriter writer, boolean osIsWin) throws IOException
    {
    	if (line == null || line.length() == 0)
		{
			return;
		}
    	writer.append(line);
    	writer.append(osIsWin ? "\n\r" : "\n");
    }

    /**
     * Generate a new default config.ini file or use the custom one specified in the environment.
     * (TYCHO-422).
     * @param environment The current environment or null for a non environment specific packaging.
     * @param target The eclipse folder assembled.
     */
    private void generateConfigIni( TargetEnvironment environment, File target )
        throws MojoExecutionException, MojoFailureException
    {
    	String os = environment == null ? null : environment.getOs();
    	String customConfigIni = os != null ? productConfiguration.getConfigIni(os) : null;
    	if (customConfigIni != null && customConfigIni.length() > 0)
    	{
    		getLog().debug( "Using " + customConfigIni );
    		if (customConfigIni.charAt(0) == '/')
    		{
    			//relative path to the project.
    			customConfigIni = customConfigIni.substring(1);
    		}
    		File customIniF = new File(project.getBasedir(), customConfigIni);
    		if (!customIniF.exists())
    		{
    			throw new MojoFailureException("Unable to locate the custom config.ini for " + os
    					+ " that should be located at " + customIniF.getAbsolutePath());
    		}
    		File configsFolder = new File( target, "configuration" );
            configsFolder.mkdirs();
    		File destConfigIni = new File(configsFolder, "config.ini");
            try {
				FileUtils.copyFile(customIniF, destConfigIni);
			} catch (IOException e) {
				throw new MojoFailureException("Unable to copy the custom config.ini for " + os
    					+ " that located at " + customIniF.getAbsolutePath(), e);
			}
    	}
    	
        getLog().debug( "Generating config.ini" );
        Properties props = new Properties();
        String id = productConfiguration.getProduct();
        if ( id != null )
        {
            String splash = id.split( "\\." )[0];
            setPropertyIfNotNull( props, "osgi.splashPath", "platform:/base/plugins/" + splash );
        }
    	//when there is no application
    	//add the proper parameters for this runtime application:
    	if ( productConfiguration.getApplication() == null )
    	{
    		setPropertyIfNotNull( props, "eclipse.ignoreApp", "true" );
    		setPropertyIfNotNull( props, "osgi.noShutdown", "true" );
    	}

        setPropertyIfNotNull( props, "eclipse.product", id );
        // TODO check if there are any other levels
        setPropertyIfNotNull( props, "osgi.bundles.defaultStartLevel", "4" );
        
        File configsFolder = new File( target, "configuration" );
        configsFolder.mkdirs();
        generateOSGiBundles( props, environment, configsFolder );

        File configIni = new File( configsFolder, "config.ini" );
        try
        {
            FileOutputStream fos = new FileOutputStream( configIni );
            props.store( fos, "Product Runtime Configuration File" );
            fos.close();
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error creating .eclipseproduct file.", e );
        }
    }

    /**
     * Semi-smart generation of the osgi.bundle property for the config.ini file.
     * Looks into the configurations element of the product file where the plugins to start are listed.
     * <ol>
     * <li>if the parameter forceConfigIniOsgiBundlesListAll is set then for to
     *  generate or not generate the whole list. otherwise: </li>
     * <li>If there is no such thing at all (for example tycho-runtime product file) then osgi.bundle is the list
     * of all bundles packaged in the product with auto started if preset:
     * org.eclipse.equinox.common, org.eclipse.update.configurator, org.eclipse.core.runtime,
     * org.eclipse.equinox.simpleconfigurator, org.eclipse.equinox.ds</li>
     * <li>If there is a list of bundles to autostart and org.eclipse.update.configurator belongs to them
     * or org.eclipse.equinox.simpleconfigurator is one of them, then don't list all the p2 bundles.</li>
     * </ol>
     */
    private void generateOSGiBundles( Properties props, TargetEnvironment environment, File configurationFolder )
        throws MojoFailureException, MojoExecutionException
    {
        Map<String, BundleConfiguration> bundlesToStart = productConfiguration.getPluginConfiguration();
        Map<String, PluginDescription> bundles =
            new LinkedHashMap<String, PluginDescription>( getBundles( environment ) );

        boolean autoListAllBundles = enableP2 ? false : true;
        if ( bundlesToStart == null || bundlesToStart.isEmpty() )
        {
        	autoListAllBundles = true;
            bundlesToStart = new HashMap<String, BundleConfiguration>();

            // This is the well known set of bundles for Eclipse based RCP application for 3.3 till 3.6 without p2
        	// if we see the p2's simpleconfigurator then we add it and that makes it work for p2 (should)
            // here is the doc of what the PDEBuild does:
            // http://help.eclipse.org/galileo/index.jsp?topic=/org.eclipse.pde.doc.user/tasks/pde_p2_configuringproducts.htm
    		bundlesToStart.put( "org.eclipse.equinox.common", // 
		                        new BundleConfiguration( "org.eclipse.equinox.common", 2, true ) );
    		if (bundles.containsKey("org.eclipse.update.configurator"))
		    {
		    	bundlesToStart.put( "org.eclipse.update.configurator", //
		                        new BundleConfiguration( "org.eclipse.update.configurator", 4, true ) );
		    	autoListAllBundles = false;
		    }
		    if (bundles.containsKey("org.eclipse.core.runtime"))
		    {
		    	bundlesToStart.put( "org.eclipse.core.runtime", // 
                    new BundleConfiguration( "org.eclipse.core.runtime", -1, true ) );
		    }
		    if (bundles.containsKey("org.eclipse.equinox.ds"))
		    {
		    	bundlesToStart.put( "org.eclipse.equinox.ds", // 
                    new BundleConfiguration( "org.eclipse.equinox.ds", 1, true ) );
		    }
		    if (bundles.containsKey("org.eclipse.equinox.simpleconfigurator"))
		    {
		    	//for now we choose to not consider that simpleconfigurator is actually in use
		    	bundlesToStart.put( "org.eclipse.equinox.simpleconfigurator", // 
                    new BundleConfiguration( "org.eclipse.equinox.simpleconfigurator", 1, true ) );
		    }
        }
        
        if (!autoListAllBundles)
        {
        	//only list the bundles explicitly declared in the product file's configuration element:
        	Map<String, PluginDescription> actuallyListedBundles = new LinkedHashMap<String, PluginDescription>();
        	for (BundleConfiguration bundleConf : bundlesToStart.values())
        	{
        		PluginDescription pluginDesc = bundles.get(bundleConf.getId());
        		if (pluginDesc == null)
        		{
        			throw new MojoFailureException("No plugin " + bundleConf.getId() + 
        					" is packaged by the product " + productConfiguration.getId() +
        					" although it is listed in the plugins to start.");
        		}
        		actuallyListedBundles.put(pluginDesc.getKey().getId(), pluginDesc);
        	}
        	bundles = actuallyListedBundles;
        }

        StringBuilder osgiBundles = new StringBuilder();
        for ( PluginDescription plugin : bundles.values() )
        {
            String bundleId = plugin.getKey().getId();

            // reverse engineering discovered
            // this plugin is not present on config.ini, and if so nothing
            // starts
            if ( "org.eclipse.osgi".equals( bundleId ) )
            {
                continue;
            }

            if ( osgiBundles.length() > 0 )
            {
                osgiBundles.append( ',' );
            }

            osgiBundles.append( bundleId );

            BundleConfiguration startup = bundlesToStart.get( bundleId );

            if ( startup != null )
            {
                osgiBundles.append( '@' );

                if ( startup.getStartLevel() > 0 )
                {
                    osgiBundles.append( startup.getStartLevel() );
                }

                if ( startup.isAutoStart() )
                {
                    if ( startup.getStartLevel() > 0 )
                    {
                        osgiBundles.append( ':' );
                    }
                    osgiBundles.append( "start" );
                }
            }
        }
        setPropertyIfNotNull( props, "osgi.bundles", osgiBundles.toString() );
    }

    private Map<String, PluginDescription> getBundles( TargetEnvironment environment )
    {
        final Map<String, PluginDescription> bundles = new LinkedHashMap<String, PluginDescription>();
        getDependencyWalker( environment ).walk( new ArtifactDependencyVisitor()
        {
            @Override
            public void visitPlugin( PluginDescription plugin )
            {
                bundles.put( plugin.getKey().getId(), plugin );
            }
        } );
        return bundles;
    }

    private void copyExecutable( TargetEnvironment environment, File target )
        throws MojoExecutionException, MojoFailureException
    {
        getLog().debug( "Creating launcher" );

        ArtifactDescription artifact =
            getTargetPlatform().getArtifact( TychoProject.ECLIPSE_FEATURE, "org.eclipse.equinox.executable", null );

        if ( artifact == null )
        {
            throw new MojoExecutionException( "Native launcher is not found for " + environment.toString() );
        }

        File location = artifact.getLocation();

        String os = environment.getOs();
        String ws = environment.getWs();
        String arch = environment.getArch();

        try
        {
            String launcherRelPath = "bin/" + ws + "/" + os + "/" + arch + "/";
            String excludes = "**/eclipsec*";

            if ( location.isDirectory() )
            {
                copyDirectory( new File( location, launcherRelPath ), target, excludes );
            }
            else
            {
                unzipDirectory( location, launcherRelPath, target, excludes );
            }
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Unable to copy launcher executable", e );
        }

        File launcher = getLauncher( environment, target );

        // make launcher executable
        try
        {
            getLog().debug( "running chmod" );
            ArchiveEntryUtils.chmod( launcher, 0755, null );
        }
        catch ( ArchiverException e )
        {
            throw new MojoExecutionException( "Unable to make launcher being executable", e );
        }

        File osxEclipseApp = null;

        // Rename launcher
        if ( productConfiguration.getLauncher() != null && productConfiguration.getLauncher().getName() != null )
        {
            String launcherName = productConfiguration.getLauncher().getName();
            String newName = launcherName;

            // win32 has extensions
            if ( PlatformPropertiesUtils.OS_WIN32.equals( os ) )
            {
                String extension = FileUtils.getExtension( launcher.getAbsolutePath() );
                newName = launcherName + "." + extension;
            }
            else if ( PlatformPropertiesUtils.OS_MACOSX.equals( os ) )
            {
                // the launcher is renamed to "eclipse", because
                // this is the value of the CFBundleExecutable
                // property within the Info.plist file.
                // see http://jira.codehaus.org/browse/MNGECLIPSE-1087
                newName = "eclipse";
            }

            getLog().debug( "Renaming launcher to " + newName );
            File newLauncher = new File( launcher.getParentFile(), newName );
            if ( !launcher.renameTo( newLauncher ) )
            {
                throw new MojoExecutionException( "Could not rename native launcher to " + newName );
            }
            launcher = newLauncher;

            // macosx: the *.app directory is renamed to the
            // product configuration launcher name
            // see http://jira.codehaus.org/browse/MNGECLIPSE-1087
            if ( PlatformPropertiesUtils.OS_MACOSX.equals( os ) )
            {
                newName = launcherName + ".app";
                getLog().debug( "Renaming Eclipse.app to " + newName );
                File eclipseApp = new File( target, "Eclipse.app" );
                osxEclipseApp = new File( eclipseApp.getParentFile(), newName );
                eclipseApp.renameTo( osxEclipseApp );
                // ToDo: the "Info.plist" file must be patched, so that the
                // property "CFBundleName" has the value of the
                // launcherName variable
            }
        }

        // icons
        if ( productConfiguration.getLauncher() != null )
        {
            if ( PlatformPropertiesUtils.OS_WIN32.equals( os ) )
            {
                getLog().debug( "win32 icons" );
                List<String> icons = productConfiguration.getW32Icons();

                if ( icons != null )
                {
                    getLog().debug( icons.toString() );
                    try
                    {
                        String[] args = new String[icons.size() + 1];
                        args[0] = launcher.getAbsolutePath();

                        int pos = 1;
                        for ( String string : icons )
                        {
                            args[pos] = string;
                            pos++;
                        }

                        IconExe.main( args );
                    }
                    catch ( Exception e )
                    {
                        throw new MojoExecutionException( "Unable to replace icons", e );
                    }
                }
                else
                {
                    getLog().debug( "icons is null" );
                }
            }
            else if ( PlatformPropertiesUtils.OS_LINUX.equals( os ) )
            {
                String icon = productConfiguration.getLinuxIcon();
                if ( icon != null )
                {
                    try
                    {
                        File sourceXPM = new File( project.getBasedir(), removeFirstSegment( icon ) );
                        File targetXPM = new File( launcher.getParentFile(), "icon.xpm" );
                        FileUtils.copyFile( sourceXPM, targetXPM );
                    }
                    catch ( IOException e )
                    {
                        throw new MojoExecutionException( "Unable to create ico.xpm", e );
                    }
                }
            }
            else if ( PlatformPropertiesUtils.OS_MACOSX.equals( os ) )
            {
                String icon = productConfiguration.getMacIcon();
                if ( icon != null )
                {
                    try
                    {
                        if ( osxEclipseApp == null )
                        {
                            osxEclipseApp = new File( target, "Eclipse.app" );
                        }

                        File source = new File( project.getBasedir(), removeFirstSegment( icon ) );
                        File targetFolder = new File( osxEclipseApp, "/Resources/" + source.getName() );

                        FileUtils.copyFile( source, targetFolder );
                        // Modify eclipse.ini
                        File iniFile = new File( osxEclipseApp + "/Contents/MacOS/eclipse.ini" );
                        if ( iniFile.exists() && iniFile.canWrite() )
                        {
                            StringBuffer buf = readFileToString( iniFile );
                            int pos = buf.indexOf( "Eclipse.icns" );
                            buf.replace( pos, pos + 12, source.getName() );
                            writeStringToFile( iniFile, buf.toString() );
                        }
                    }
                    catch ( Exception e )
                    {
                        throw new MojoExecutionException( "Unable to create macosx icon", e );
                    }
                }
            }
        }
    }

    private void writeStringToFile( File iniFile, String string )
        throws IOException
    {
        OutputStream os = new BufferedOutputStream( new FileOutputStream( iniFile ) );
        try
        {
            IOUtil.copy( string, os );
        }
        finally
        {
            IOUtil.close( os );
        }
    }

    private StringBuffer readFileToString( File iniFile )
        throws IOException
    {
        InputStream is = new BufferedInputStream( new FileInputStream( iniFile ) );
        try
        {
            StringWriter buffer = new StringWriter();

            IOUtil.copy( is, buffer, "UTF-8" );

            return buffer.getBuffer();
        }
        finally
        {
            IOUtil.close( is );
        }
    }

    private void unzipDirectory( File source, String sourceRelPath, File target, String excludes )
        throws IOException
    {
        ZipFile zip = new ZipFile( source );
        try
        {
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while ( entries.hasMoreElements() )
            {
                ZipEntry entry = entries.nextElement();

                if ( entry.isDirectory() )
                {
                    continue;
                }

                String name = entry.getName();

                if ( name.startsWith( sourceRelPath ) && !SelectorUtils.matchPath( excludes, name ) )
                {
                    File targetFile = new File( target, name.substring( sourceRelPath.length() ) );
                    targetFile.getParentFile().mkdirs();
                    FileUtils.copyStreamToFile( new RawInputStreamFacade( zip.getInputStream( entry ) ), targetFile );
                }
            }
        }
        finally
        {
            zip.close();
        }
    }

    private void copyDirectory( File source, File target, String excludes )
        throws IOException
    {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir( source );
        ds.setExcludes( new String[] { excludes } );

        ds.scan();

        for ( String relPath : ds.getIncludedFiles() )
        {
            File targetFile = new File( target, relPath );
            targetFile.getParentFile().mkdirs();
            FileUtils.copyFile( new File( source, relPath ), targetFile );
        }
    }

    private String removeFirstSegment( String path )
    {
        int idx = path.indexOf( '/' );
        if ( idx < 0 )
        {
            return null;
        }

        if ( idx == 0 )
        {
            idx = path.indexOf( '/', 1 );
        }

        if ( idx < 0 )
        {
            return null;
        }

        return path.substring( idx );
    }

    private File getLauncher( TargetEnvironment environment, File target )
        throws MojoExecutionException
    {
        String os = environment.getOs();

        if ( PlatformPropertiesUtils.OS_WIN32.equals( os ) )
        {
            return new File( target, "launcher.exe" );
        }

        if ( PlatformPropertiesUtils.OS_LINUX.equals( os ) || PlatformPropertiesUtils.OS_SOLARIS.equals( os )
            || PlatformPropertiesUtils.OS_HPUX.equals( os ) || PlatformPropertiesUtils.OS_AIX.equals( os ) )
        {
            return new File( target, "launcher" );
        }

        if ( PlatformPropertiesUtils.OS_MACOSX.equals( os ) )
        {
            // TODO need to check this at macos
            return new File( target, "Eclipse.app/Contents/MacOS/launcher" );
        }

        throw new MojoExecutionException( "Unexpected OS: " + os );
    }

    private void setPropertyIfNotNull( Properties properties, String key, String value )
    {
        if ( value != null )
        {
            properties.setProperty( key, value );
        }
    }
}
