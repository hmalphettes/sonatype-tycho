package org.sonatype.tycho.p2.publisher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.internal.p2.core.helpers.LogHelper;
import org.eclipse.equinox.internal.p2.publisher.Activator;
import org.eclipse.equinox.internal.p2.publisher.Messages;
import org.eclipse.equinox.internal.p2.publisher.eclipse.FeatureParser;
import org.eclipse.equinox.internal.p2.publisher.eclipse.IProductDescriptor;
import org.eclipse.equinox.internal.p2.updatesite.SiteXMLAction;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.metadata.MetadataFactory;
import org.eclipse.equinox.p2.metadata.MetadataFactory.InstallableUnitDescription;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.metadata.VersionRange;
import org.eclipse.equinox.p2.publisher.IPublisherAction;
import org.eclipse.equinox.p2.publisher.Publisher;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.PublisherResult;
import org.eclipse.equinox.p2.publisher.actions.ICapabilityAdvice;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.p2.publisher.eclipse.Feature;
import org.eclipse.equinox.p2.publisher.eclipse.FeaturesAction;
import org.eclipse.equinox.p2.publisher.eclipse.ProductAction;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.StateObjectFactory;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.BundleException;
import org.sonatype.tycho.p2.facade.P2Generator;
import org.sonatype.tycho.p2.facade.internal.P2Resolver;
import org.sonatype.tycho.p2.maven.repository.xmlio.ArtifactsIO;
import org.sonatype.tycho.p2.maven.repository.xmlio.MetadataIO;
import org.sonatype.tycho.p2.publisher.model.ProductFile2;
import org.sonatype.tycho.p2.publisher.repo.TransientArtifactRepository;

@SuppressWarnings( "restriction" )
public class P2GeneratorImpl
    implements P2Generator
{
    private static final String[] SUPPORTED_TYPES = { P2Resolver.TYPE_ECLIPSE_PLUGIN,
        P2Resolver.TYPE_ECLIPSE_TEST_PLUGIN, P2Resolver.TYPE_ECLIPSE_FEATURE, P2Resolver.TYPE_ECLIPSE_UPDATE_SITE,
        P2Resolver.TYPE_ECLIPSE_APPLICATION };

    /**
     * Whether we need full p2 metadata (false) or just required capabilities.
     */
    private boolean dependenciesOnly;

    private IProgressMonitor monitor = new NullProgressMonitor();

    public P2GeneratorImpl( boolean dependenciesOnly )
    {
        this.dependenciesOnly = dependenciesOnly;
    }

    public void generateMetadata( File location, String packaging, String groupId, String artifactId, String version,
                                  File content, File artifacts )
        throws IOException
    {
        LinkedHashSet<IInstallableUnit> units = new LinkedHashSet<IInstallableUnit>();
        LinkedHashSet<IArtifactDescriptor> artifactDescriptors = new LinkedHashSet<IArtifactDescriptor>();

        generateMetadata( location, packaging, groupId, artifactId, version, null, units, artifactDescriptors );

        new MetadataIO().writeXML( units, content );
        new ArtifactsIO().writeXML( artifactDescriptors, artifacts );
    }

    private IRequirement[] extractExtraEntriesAsIURequirement( File location )
    {
        Properties buildProperties = loadProperties( location );
        if ( buildProperties == null || buildProperties.size() == 0 )
            return null;
        ArrayList<IRequirement> result = new ArrayList<IRequirement>();
        Set<Entry<Object, Object>> pairs = buildProperties.entrySet();
        for ( Entry<Object, Object> pair : pairs )
        {
            if ( !( pair.getValue() instanceof String ) )
                continue;
            String buildPropertyKey = (String) pair.getKey();
            if ( buildPropertyKey.startsWith( "extra." ) )
            {
                createRequirementFromExtraClasspathProperty( result, ( (String) pair.getValue() ).split( "," ) );
            }
        }

        String extra = buildProperties.getProperty( "jars.extra.classpath" );
        if ( extra != null )
        {
            createRequirementFromExtraClasspathProperty( result, extra.split( "," ) );
        }
        if ( result.isEmpty() )
            return null;
        return result.toArray( new IRequirement[result.size()] );
    }

    private void createRequirementFromExtraClasspathProperty( ArrayList<IRequirement> result, String[] urls )
    {
        for ( int i = 0; i < urls.length; i++ )
        {
            createRequirementFromPlatformURL( result, urls[i].trim() );
        }
    }

    private void createRequirementFromPlatformURL( ArrayList<IRequirement> result, String url )
    {
        Pattern platformURL = Pattern.compile( "platform:/(plugin|fragment)/([^/]*)(/)*.*" );
        Matcher m = platformURL.matcher( url );
        if ( m.matches() )
            result.add( MetadataFactory.createRequirement( IInstallableUnit.NAMESPACE_IU_ID, m.group( 2 ),
                                                                  VersionRange.emptyRange, null, false, false ) );
    }

    private static Properties loadProperties( File project )
    {
        File file = new File( project, "build.properties" );

        Properties buildProperties = new Properties();
        if ( file.canRead() )
        {
            InputStream is = null;
            try
            {
                try
                {
                    is = new FileInputStream( file );
                    buildProperties.load( is );
                }
                finally
                {
                    if ( is != null )
                        is.close();
                }
            }
            catch ( Exception e )
            {
                // ignore
            }
        }

        return buildProperties;
    }

    public void generateMetadata( File location, String packaging, String groupId, String artifactId, String version,
                                  List<Map<String,String>> environments, Set<IInstallableUnit> units,
                                  Set<IArtifactDescriptor> artifacts )
    {
        TransientArtifactRepository artifactsRepository = new TransientArtifactRepository();

        PublisherInfo request = new PublisherInfo();
        request.setArtifactRepository( artifactsRepository );

        request.addAdvice( new MavenPropertiesAdvice( groupId, artifactId, version ) );
        final IRequirement[] extraRequirements = extractExtraEntriesAsIURequirement( location );
        request.addAdvice( new ICapabilityAdvice()
        {

            public boolean isApplicable( String configSpec, boolean includeDefault, String id, Version version )
            {
                return true;
            }

            public IRequirement[] getRequiredCapabilities( InstallableUnitDescription iu )
            {
                return extraRequirements;
            }

            public IProvidedCapability[] getProvidedCapabilities( InstallableUnitDescription iu )
            {
                return null;
            }

            public IRequirement[] getMetaRequiredCapabilities( InstallableUnitDescription iu )
            {
                return null;
            }
        } );
        IPublisherAction[] actions = getPublisherActions( location, packaging, artifactId, version, environments );

        PublisherResult result = new PublisherResult();

        new Publisher( request, result ).publish( actions, monitor );

        if ( units != null )
        {
            units.addAll( result.getIUs( null, null ) );
        }

        if ( artifacts != null )
        {
            artifacts.addAll( artifactsRepository.getArtifactDescriptors() );
        }
    }
    
    private IPublisherAction[] getPublisherActions( File location, String packaging, String id, String version,
                                                    List<Map<String,String>> environments )
    {
        if ( P2Resolver.TYPE_ECLIPSE_PLUGIN.equals( packaging )
            || P2Resolver.TYPE_ECLIPSE_TEST_PLUGIN.equals( packaging ) )
        {
        	//TYCHO-192: setup the source bundle generated by the project as part of the IUs
        	//that the target platform is aware of.
        	Dictionary<String, String> runBundle = BundlesAction.loadManifest(location);
        	Dictionary<String, String> sourceBundleManifest = new Hashtable<String, String>();
        	if (runBundle != null)
        	{
        		String symbolicName = runBundle.get("Bundle-SymbolicName");
        		String runVersion = runBundle.get("Bundle-Version");
        		if (symbolicName != null && runVersion != null) {
        			//generate a minimum source bundle manifest.
        			//note that when the feature or site is assembled,
        			//the actual source bundle's manifest is the one indexed by p2.
        			//not this dummy minimum manifest.
        			int semiCol = symbolicName.indexOf(';');
        			if (semiCol != -1) {
        				symbolicName = symbolicName.substring(0, semiCol);
        			}
        			sourceBundleManifest.put("Bundle-Description", "Minimum source bundle manifest generated by tycho");
        			sourceBundleManifest.put("Manifest-Version", "1.0");
        			sourceBundleManifest.put("Bundle-ManifestVersion", "2");
        			sourceBundleManifest.put("Bundle-SymbolicName", symbolicName + ".source");
        			sourceBundleManifest.put("Bundle-Version", runVersion);
        			sourceBundleManifest.put("Eclipse-SourceBundle", symbolicName +";version=\"" + runVersion + "\"");
        			BundleDescription sourceBundle = BundlesAction.createBundleDescription(sourceBundleManifest,
        					new File(location, "target/" + id + "-" + version + "-sources.jar"));
//        			System.err.println(sourceBundleManifest);
//        			System.err.println("### sourceBundle " + (sourceBundle != null ? sourceBundle.getSymbolicName()
//        					+" - " + sourceBundle.getLocation() : " null! "));
        			if (sourceBundle != null) {
        				return new IPublisherAction[] { new BundlesAction( new File[] { location } ),
                			new BundlesAction(new BundleDescription[] { sourceBundle } ) };
        			} else {
        				//to get the actual exception: find the equinox log or use directly the StateBuilder which reports the issue
        				//instead of the BundlesAction static method.
        				System.err.println("WARNING Unable to generate the p2-IU for the source bundle of " + packaging + ":" + id);
        			}
        		}
        	}
        	return new IPublisherAction[] { new BundlesAction( new File[] { location } ) };
        }
        else if ( P2Resolver.TYPE_ECLIPSE_FEATURE.equals( packaging ) )
        {
            Feature feature = new FeatureParser().parse( location );
            feature.setLocation( location.getAbsolutePath() );
            if ( dependenciesOnly )
            {
                return new IPublisherAction[] { new FeatureDependenciesAction( feature ) };
            }
            else
            {
                return new IPublisherAction[] { new FeaturesAction( new Feature[] { feature } ) };
            }
        }
        else if ( P2Resolver.TYPE_ECLIPSE_APPLICATION.equals( packaging ) )
        {
            String product = new File( location, id + ".product" ).getAbsolutePath();
            try
            {
                IProductDescriptor productDescriptor = new ProductFile2( product );
                if ( dependenciesOnly )
                {
                    return new IPublisherAction[] { new ProductDependenciesAction( productDescriptor, environments ) };
                }
                else
                {
                    return new IPublisherAction[] { new ProductAction( product, productDescriptor, null, null ) };
                }
            }
            catch ( Exception e )
            {
                throw new RuntimeException( e );
            }
        }
        else if ( P2Resolver.TYPE_ECLIPSE_UPDATE_SITE.equals( packaging ) )
        {
            if ( dependenciesOnly )
            {
                return new IPublisherAction[] { new SiteDependenciesAction( location, id, version ) };
            }
            else
            {
                return new IPublisherAction[] { new SiteXMLAction( location.toURI(), null ) };
            }
        }
        else if ( location.isFile() && location.getName().endsWith( ".jar" ) )
        {
            return new IPublisherAction[] { new BundlesAction( new File[] { location } ) };
        }

        throw new IllegalArgumentException();
    }

    public boolean isSupported( String type )
    {
        return Arrays.asList( SUPPORTED_TYPES ).contains( type );
    }
}
