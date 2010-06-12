package org.sonatype.tycho.p2.resolver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository;
import org.eclipse.equinox.internal.p2.artifact.repository.simple.SimpleArtifactRepository;
import org.eclipse.equinox.internal.p2.core.helpers.OrderedProperties;
import org.eclipse.equinox.internal.p2.director.Explanation;
import org.eclipse.equinox.internal.p2.director.Projector;
import org.eclipse.equinox.internal.p2.director.QueryableArray;
import org.eclipse.equinox.internal.p2.director.SimplePlanner;
import org.eclipse.equinox.internal.p2.director.Slicer;
import org.eclipse.equinox.internal.p2.metadata.IRequiredCapability;
import org.eclipse.equinox.internal.p2.metadata.InstallableUnit;
import org.eclipse.equinox.internal.p2.repository.CacheManager;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.metadata.MetadataFactory;
import org.eclipse.equinox.p2.metadata.MetadataFactory.InstallableUnitDescription;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.metadata.VersionRange;
import org.eclipse.equinox.p2.metadata.expression.IMatchExpression;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.PublisherResult;
import org.eclipse.equinox.p2.publisher.actions.JREAction;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.IQueryable;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.IRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRequest;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.equinox.p2.repository.spi.AbstractRepository;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.equinox.spi.p2.publisher.PublisherHelper;
import org.sonatype.tycho.p2.Activator;
import org.sonatype.tycho.p2.facade.internal.IArtifactFacade;
import org.sonatype.tycho.p2.facade.internal.LocalRepositoryReader;
import org.sonatype.tycho.p2.facade.internal.LocalTychoRepositoryIndex;
import org.sonatype.tycho.p2.facade.internal.P2Logger;
import org.sonatype.tycho.p2.facade.internal.P2RepositoryCache;
import org.sonatype.tycho.p2.facade.internal.P2ResolutionResult;
import org.sonatype.tycho.p2.facade.internal.P2Resolver;
import org.sonatype.tycho.p2.facade.internal.RepositoryReader;
import org.sonatype.tycho.p2.facade.internal.TychoRepositoryIndex;
import org.sonatype.tycho.p2.maven.repository.LocalArtifactRepository;
import org.sonatype.tycho.p2.maven.repository.LocalMetadataRepository;
import org.sonatype.tycho.p2.maven.repository.MavenArtifactRepository;
import org.sonatype.tycho.p2.maven.repository.MavenMetadataRepository;
import org.sonatype.tycho.p2.maven.repository.MavenMirrorRequest;
import org.sonatype.tycho.p2.maven.repository.xmlio.MetadataIO;
import org.sonatype.tycho.p2.publisher.P2GeneratorImpl;
import org.sonatype.tycho.p2.repository.TychoP2RepositoryCacheManager;

@SuppressWarnings( "restriction" )
public class P2ResolverImpl
    implements P2Resolver
{

    private static final IInstallableUnit[] IU_ARRAY = new IInstallableUnit[0];

    private static final IArtifactRequest[] ARTIFACT_REQUEST_ARRAY = new IArtifactRequest[0];

    private static final IRequiredCapability[] REQUIRED_CAPABILITY_ARRAY = new IRequiredCapability[0];

    private P2GeneratorImpl generator = new P2GeneratorImpl( true );

    private P2RepositoryCache repositoryCache;

    /**
     * All known P2 metadata repositories, including maven local repository
     */
    private List<IMetadataRepository> metadataRepositories = new ArrayList<IMetadataRepository>();

    /**
     * All known P2 artifact repositories, NOT including maven local repository.
     */
    private List<IArtifactRepository> artifactRepositories = new ArrayList<IArtifactRepository>();

    /** maven local repository as P2 IArtifactRepository */
    private LocalArtifactRepository localRepository;

    /** maven local repository as P2 IMetadataRepository */
    private LocalMetadataRepository localMetadataRepository;

    /**
     * Maps maven artifact location (project basedir or local repo path) to installable units
     */
    private Map<File, Set<IInstallableUnit>> mavenArtifactIUs = new HashMap<File, Set<IInstallableUnit>>();

    /**
     * Maps maven artifact location (project basedir or local repo path) to project type
     */
    private Map<File, String> mavenArtifactTypes = new LinkedHashMap<File, String>();

    /**
     * Maps installable unit id to locations of reactor projects
     */
    private Map<String, Set<File>> iuReactorProjects = new HashMap<String, Set<File>>();

    private IProgressMonitor monitor = new NullProgressMonitor();

    /**
     * Target runtime environment properties
     */
    private List<Map<String, String>> environments;

    private List<IRequirement> additionalRequirements = new ArrayList<IRequirement>();

    private P2Logger logger;

    private boolean offline;

    private IProvisioningAgent agent;

    private File localRepositoryLocation;

    public P2ResolverImpl()
    {
    }

    public void addMavenProject( IArtifactFacade artifact )
    {
        if ( !generator.isSupported( artifact.getPackagingType() ) )
        {
            return;
        }

        LinkedHashSet<IInstallableUnit> units = doAddMavenArtifact( artifact );

        for ( IInstallableUnit iu : units )
        {
            Set<File> projects = iuReactorProjects.get( iu.getId() );
            if ( projects == null )
            {
                projects = new HashSet<File>();
                iuReactorProjects.put( iu.getId(), projects );
            }
            // TODO do we support multiple versions of the same project
            projects.add( artifact.getLocation() );
        }
    }

    public void addMavenArtifact( IArtifactFacade artifact )
    {
        doAddMavenArtifact( artifact );
    }

    protected LinkedHashSet<IInstallableUnit> doAddMavenArtifact( IArtifactFacade artifact )
    {
        LinkedHashSet<IInstallableUnit> units = new LinkedHashSet<IInstallableUnit>();

        generator.generateMetadata( artifact, environments, units, null );

        mavenArtifactTypes.put( artifact.getLocation(), artifact.getPackagingType() );

        mavenArtifactIUs.put( artifact.getLocation(), units );

        return units;
    }

    public void addP2Repository( URI location )
    {
        // check metadata cache, first
        IMetadataRepository metadataRepository = (IMetadataRepository) repositoryCache.getMetadataRepository( location );
        IArtifactRepository artifactRepository = (IArtifactRepository) repositoryCache.getArtifactRepository( location );
        if ( metadataRepository != null && ( offline || artifactRepository != null ) )
        {
            // cache hit
            metadataRepositories.add( metadataRepository );
            if ( artifactRepository != null )
            {
                artifactRepositories.add( artifactRepository );
            }
            logger.info( "Adding repository (cached) " + location.toASCIIString() );
            return;
        }

        if ( agent == null )
        {
            if ( localRepositoryLocation == null )
            {
                throw new IllegalStateException( "Maven local repository location is null" );
            }

            try
            {
                agent = Activator.newProvisioningAgent();

                TychoP2RepositoryCacheManager cacheMgr = new TychoP2RepositoryCacheManager();
                cacheMgr.setOffline( offline );
                cacheMgr.setLocalRepositoryLocation( localRepositoryLocation );

                agent.registerService( CacheManager.SERVICE_NAME, cacheMgr );
            }
            catch ( ProvisionException e )
            {
                throw new RuntimeException( e );
            }
        }

        try
        {
            IMetadataRepositoryManager metadataRepositoryManager =
                (IMetadataRepositoryManager) agent.getService( IMetadataRepositoryManager.SERVICE_NAME );
            if ( metadataRepositoryManager == null )
            {
                throw new IllegalStateException( "No metadata repository manager found" ); //$NON-NLS-1$
            }

            metadataRepository = metadataRepositoryManager.loadRepository( location, monitor );
            metadataRepositories.add( metadataRepository );

            if ( !offline || URIUtil.isFileURI( location ) )
            {
                IArtifactRepositoryManager artifactRepositoryManager =
                    (IArtifactRepositoryManager) agent.getService( IArtifactRepositoryManager.SERVICE_NAME );
                if ( artifactRepositoryManager == null )
                {
                    throw new IllegalStateException( "No artifact repository manager found" ); //$NON-NLS-1$
                }

                artifactRepository = artifactRepositoryManager.loadRepository( location, monitor );
                artifactRepositories.add( artifactRepository );

                forceSingleThreadedDownload( artifactRepositoryManager, artifactRepository );
            }

            repositoryCache.putRepository( location, metadataRepository, artifactRepository );

            // processPartialIUs( metadataRepository, artifactRepository );
        }
        catch ( ProvisionException e )
        {
            throw new RuntimeException( e );
        }
    }

    protected void forceSingleThreadedDownload( IArtifactRepositoryManager artifactRepositoryManager,
                                                IArtifactRepository artifactRepository )
    {
        try
        {
            if ( artifactRepository instanceof SimpleArtifactRepository )
            {
                forceSingleThreadedDownload( (SimpleArtifactRepository) artifactRepository );
            }
            else if ( artifactRepository instanceof CompositeArtifactRepository )
            {
                forceSingleThreadedDownload( artifactRepositoryManager,
                                             (CompositeArtifactRepository) artifactRepository );
            }
        }
        catch ( Exception e )
        {
            // we've tried
        }
    }

    protected void forceSingleThreadedDownload( IArtifactRepositoryManager artifactRepositoryManager,
                                                CompositeArtifactRepository artifactRepository )
        throws ProvisionException
    {
        List<URI> children = (List<URI>) artifactRepository.getChildren();
        for ( URI child : children )
        {
            forceSingleThreadedDownload( artifactRepositoryManager,
                                         artifactRepositoryManager.loadRepository( child, monitor ) );
        }
    }

    protected void forceSingleThreadedDownload( SimpleArtifactRepository artifactRepository )
        throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException
    {
        Field field = AbstractRepository.class.getDeclaredField( "properties" );
        field.setAccessible( true );
        OrderedProperties p = (OrderedProperties) field.get( artifactRepository );
        p.put( org.eclipse.equinox.internal.p2.artifact.repository.simple.SimpleArtifactRepository.PROP_MAX_THREADS,
               "1" );
    }

    public List<P2ResolutionResult> resolveProject( File projectLocation )
    {
        ArrayList<P2ResolutionResult> results = new ArrayList<P2ResolutionResult>();

        for ( Map<String, String> properties : environments )
        {
            P2ResolutionResult result = new P2ResolutionResult();
            resolveProject( result, projectLocation, properties );
            results.add( result );
        }

        return results;
    }

    protected void resolveProject( P2ResolutionResult result, File projectLocation, Map<String, String> properties )
    {
        Map<String, String> newSelectionContext = SimplePlanner.createSelectionContext( properties );

        IInstallableUnit[] availableIUs = gatherAvailableInstallableUnits( monitor );

        Set<IInstallableUnit> rootIUs = getProjectIUs( projectLocation );

        Set<IInstallableUnit> extraIUs = createAdditionalRequirementsIU();

        Set<IInstallableUnit> rootWithExtraIUs = new LinkedHashSet<IInstallableUnit>();
        rootWithExtraIUs.addAll( rootIUs );
        rootWithExtraIUs.addAll( extraIUs );

        Slicer slicer = new Slicer( new QueryableArray( availableIUs ), newSelectionContext, false );
        IQueryable<IInstallableUnit> slice = slicer.slice( rootWithExtraIUs.toArray( IU_ARRAY ), monitor );

        if ( slice != null )
        {
            Projector projector = new Projector( slice, newSelectionContext, new HashSet<IInstallableUnit>(), false );
            projector.encode( createMetaIU( rootIUs ), extraIUs.toArray( IU_ARRAY ) /* alreadyExistingRoots */,
                              new QueryableArray( new IInstallableUnit[0] ) /* installed IUs */,
                              rootIUs /* newRoots */, monitor );
            IStatus s = projector.invokeSolver( monitor );
            if ( s.getSeverity() == IStatus.ERROR )
            {
                Set<Explanation> explanation = projector.getExplanation( monitor );

                System.out.println( properties.toString() );
                System.out.println( explanation );

                throw new RuntimeException( new ProvisionException( s ) );
            }
            Collection<IInstallableUnit> newState = projector.extractSolution();

            fixSWT( newState, availableIUs, newSelectionContext );

            List<MavenMirrorRequest> requests = new ArrayList<MavenMirrorRequest>();
            for ( IInstallableUnit iu : newState )
            {
                if ( getReactorProjectBasedir( iu ) == null )
                {
                    Collection<IArtifactKey> artifactKeys = iu.getArtifacts();
                    for ( IArtifactKey key : artifactKeys )
                    {
                        requests.add( new MavenMirrorRequest( key, localRepository ) );
                    }
                }
            }

            for ( IArtifactRepository artifactRepository : artifactRepositories )
            {
                artifactRepository.getArtifacts( requests.toArray( ARTIFACT_REQUEST_ARRAY ), monitor );

                requests = filterCompletedRequests( requests );
            }

            localRepository.save();
            localMetadataRepository.save();

            // check for locally installed artifacts, which are not available from any remote repo
            for ( Iterator<MavenMirrorRequest> iter = requests.iterator(); iter.hasNext(); )
            {
                MavenMirrorRequest request = iter.next();
                if ( localRepository.contains( request.getArtifactKey() ) )
                {
                    iter.remove();
                }
            }

            if ( !requests.isEmpty() )
            {
                StringBuilder msg = new StringBuilder( "Could not download artifacts from any repository\n" );
                for ( MavenMirrorRequest request : requests )
                {
                    msg.append( "   " ).append( request.getArtifactKey().toExternalForm() ).append( '\n' );
                }

                throw new RuntimeException( msg.toString() );
            }

            for ( IInstallableUnit iu : newState )
            {
                File basedir = getReactorProjectBasedir( iu );
                if ( basedir != null )
                {
                    addReactorProject( result, iu, basedir );
                }
                else
                {
                    for ( IArtifactKey key : iu.getArtifacts() )
                    {
                        addArtifactFile( result, iu, key );
                    }
                }
            }
        }
    }

    private File getReactorProjectBasedir( IInstallableUnit iu )
    {
        for ( Map.Entry<File, Set<IInstallableUnit>> entry : mavenArtifactIUs.entrySet() )
        {
            if ( entry.getValue().contains( iu ) )
            {
                return entry.getKey();
            }
        }
        return null;
    }

    private void fixSWT( Collection<IInstallableUnit> ius, IInstallableUnit[] availableIUs,
                         Map<String, String> newSelectionContext )
    {

        boolean swt = false;
        for ( IInstallableUnit iu : ius )
        {
            if ( "org.eclipse.swt".equals( iu.getId() ) )
            {
                swt = true;
                break;
            }
        }

        if ( !swt )
        {
            return;
        }

        IInstallableUnit swtFragment = null;

        all_ius: for ( IInstallableUnit iu : availableIUs )
        {
            if ( iu.getId().startsWith( "org.eclipse.swt" ) && isApplicable( newSelectionContext, iu.getFilter() ) )
            {
                for ( IProvidedCapability provided : iu.getProvidedCapabilities() )
                {
                    if ( "osgi.fragment".equals( provided.getNamespace() )
                        && "org.eclipse.swt".equals( provided.getName() ) )
                    {
                        if ( swtFragment == null || swtFragment.getVersion().compareTo( iu.getVersion() ) < 0 )
                        {
                            swtFragment = iu;
                        }
                        continue all_ius;
                    }
                }
            }
        }

        if ( swtFragment == null )
        {
            throw new RuntimeException( "Could not determine SWT implementation fragment bundle" );
        }

        ius.add( swtFragment );
    }

    protected boolean isApplicable( Map<String, String> selectionContext, IMatchExpression<IInstallableUnit> filter )
    {
        if ( filter == null )
        {
            return true;
        }

        return filter.isMatch( InstallableUnit.contextIU( selectionContext ) );
    }

    private LinkedHashSet<IInstallableUnit> getProjectIUs( File location )
    {
        LinkedHashSet<IInstallableUnit> ius = new LinkedHashSet<IInstallableUnit>( mavenArtifactIUs.get( location ) );

        return ius;
    }

    private Set<IInstallableUnit> createAdditionalRequirementsIU()
    {
        LinkedHashSet<IInstallableUnit> result = new LinkedHashSet<IInstallableUnit>();

        if ( !additionalRequirements.isEmpty() )
        {
            InstallableUnitDescription iud = new MetadataFactory.InstallableUnitDescription();
            String time = Long.toString( System.currentTimeMillis() );
            iud.setId( "extra-" + time );
            iud.setVersion( Version.createOSGi( 0, 0, 0, time ) );
            iud.setRequirements( additionalRequirements.toArray( REQUIRED_CAPABILITY_ARRAY ) );

            result.add( MetadataFactory.createInstallableUnit( iud ) );
        }

        return result;
    }

    private void addArtifactFile( P2ResolutionResult platform, IInstallableUnit iu, IArtifactKey key )
    {
        File file = getLocalArtifactFile( key );
        if ( file == null )
        {
            return;
        }

        String id = iu.getId();
        String version = iu.getVersion().toString();

        if ( PublisherHelper.OSGI_BUNDLE_CLASSIFIER.equals( key.getClassifier() ) )
        {
            platform.addArtifact( P2Resolver.TYPE_ECLIPSE_PLUGIN, id, version, file );
        }
        else if ( PublisherHelper.ECLIPSE_FEATURE_CLASSIFIER.equals( key.getClassifier() ) )
        {
            String featureId = getFeatureId( iu );
            if ( featureId != null )
            {
                platform.addArtifact( P2Resolver.TYPE_ECLIPSE_FEATURE, featureId, version, file );
            }
        }

        // ignore other/unknown artifacts, like binary blobs for now.
        // throw new IllegalArgumentException();
    }

    private void addReactorProject( P2ResolutionResult platform, IInstallableUnit iu, File basedir )
    {
        String type = mavenArtifactTypes.get( basedir );
        String id = iu.getId();
        String version = iu.getVersion().toString();

        if ( P2Resolver.TYPE_ECLIPSE_PLUGIN.equals( type ) || P2Resolver.TYPE_ECLIPSE_TEST_PLUGIN.equals( type ) )
        {
            platform.addArtifact( type, id, version, basedir );
        }
        else if ( P2Resolver.TYPE_ECLIPSE_FEATURE.equals( type ) )
        {
            String featureId = getFeatureId( iu );
            if ( featureId != null )
            {
                platform.addArtifact( P2Resolver.TYPE_ECLIPSE_FEATURE, featureId, version, basedir );
            }
        }
        else if ( basedir.isFile() && basedir.getName().endsWith( ".jar" ) )
        {
            // TODO how do we get here???
            platform.addArtifact( P2Resolver.TYPE_ECLIPSE_PLUGIN, id, version, basedir );
        }

        // we don't care about eclipse-update-site and eclipse-application projects for now
        // throw new IllegalArgumentException();
    }

    private String getFeatureId( IInstallableUnit iu )
    {
        for ( IProvidedCapability provided : iu.getProvidedCapabilities() )
        {
            if ( PublisherHelper.CAPABILITY_NS_UPDATE_FEATURE.equals( provided.getNamespace() ) )
            {
                return provided.getName();
            }
        }
        return null;
    }

    private File getLocalArtifactFile( IArtifactKey key )
    {
        for ( IArtifactDescriptor descriptor : localRepository.getArtifactDescriptors( key ) )
        {
            URI uri = localRepository.getLocation( descriptor );
            if ( uri != null )
            {
                return new File( uri );
            }
        }

        return null;
    }

    private List<MavenMirrorRequest> filterCompletedRequests( List<MavenMirrorRequest> requests )
    {
        ArrayList<MavenMirrorRequest> filteredRequests = new ArrayList<MavenMirrorRequest>();
        for ( MavenMirrorRequest request : requests )
        {
            if ( request.getResult() == null || !request.getResult().isOK() )
            {
                filteredRequests.add( request );
            }
        }
        return filteredRequests;
    }

    public IInstallableUnit[] gatherAvailableInstallableUnits( IProgressMonitor monitor )
    {
        Set<IInstallableUnit> result = new LinkedHashSet<IInstallableUnit>();

        for ( Set<File> projects : iuReactorProjects.values() )
        {
            for ( File location : projects )
            {
                Set<IInstallableUnit> projectIUs = mavenArtifactIUs.get( location );
                if ( projectIUs != null )
                {
                    result.addAll( projectIUs );
                }
            }
        }

        for ( Collection<IInstallableUnit> ius : mavenArtifactIUs.values() )
        {
            for ( IInstallableUnit iu : ius )
            {
                if ( !iuReactorProjects.containsKey( iu.getId() ) )
                {
                    result.add( iu );
                }
            }
        }

        SubMonitor sub = SubMonitor.convert( monitor, metadataRepositories.size() * 200 );
        for ( IMetadataRepository repository : metadataRepositories )
        {
            IQueryResult<IInstallableUnit> matches = repository.query( QueryUtil.ALL_UNITS, sub.newChild( 100 ) );
            for ( Iterator<IInstallableUnit> it = matches.iterator(); it.hasNext(); )
            {
                IInstallableUnit iu = it.next();

                if ( !isPartialIU( iu ) )
                {
                    if ( !iuReactorProjects.containsKey( iu.getId() ) )
                    {
                        result.add( iu );
                    }
                }
                else
                {
                    System.out.println( "PARTIAL IU: " + iu );
                }
            }
        }
        result.addAll( createJREIUs() );
        sub.done();
        return result.toArray( IU_ARRAY );
    }

    private static boolean isPartialIU( IInstallableUnit iu )
    {
        return Boolean.valueOf( iu.getProperty( IInstallableUnit.PROP_PARTIAL_IU ) ).booleanValue();
    }

    /**
     * these dummy IUs are needed to satisfy Import-Package requirements to packages provided by the JDK.
     */
    private Collection<IInstallableUnit> createJREIUs()
    {
        PublisherResult results = new PublisherResult();
        // TODO use the appropriate profile name
        new JREAction( (String) null ).perform( new PublisherInfo(), results, new NullProgressMonitor() );
        return results.query( QueryUtil.ALL_UNITS, new NullProgressMonitor() ).toSet();
    }

    private IInstallableUnit createMetaIU( Set<IInstallableUnit> rootIUs )
    {
        InstallableUnitDescription iud = new MetadataFactory.InstallableUnitDescription();
        String time = Long.toString( System.currentTimeMillis() );
        iud.setId( time );
        iud.setVersion( Version.createOSGi( 0, 0, 0, time ) );

        ArrayList<IRequirement> capabilities = new ArrayList<IRequirement>();
        for ( IInstallableUnit iu : rootIUs )
        {
            VersionRange range = new VersionRange( iu.getVersion(), true, iu.getVersion(), true );
            capabilities.add( MetadataFactory.createRequirement( IInstallableUnit.NAMESPACE_IU_ID, iu.getId(), range,
                                                                 iu.getFilter(), 1 /* min */, iu.isSingleton() ? 1
                                                                                 : Integer.MAX_VALUE /* max */, true /* greedy */) );
        }

        capabilities.addAll( additionalRequirements );

        iud.setRequirements( (IRequirement[]) capabilities.toArray( new IRequirement[capabilities.size()] ) );
        return MetadataFactory.createInstallableUnit( iud );
    }

    public void setLocalRepositoryLocation( File location )
    {
        this.localRepositoryLocation = location;
        URI uri = location.toURI();

        localRepository = (LocalArtifactRepository) repositoryCache.getArtifactRepository( uri );
        localMetadataRepository = (LocalMetadataRepository) repositoryCache.getMetadataRepository( uri );

        if ( localRepository == null || localMetadataRepository == null )
        {
            RepositoryReader contentLocator = new LocalRepositoryReader( location );
            LocalTychoRepositoryIndex artifactsIndex =
                new LocalTychoRepositoryIndex( location, LocalTychoRepositoryIndex.ARTIFACTS_INDEX_RELPATH );
            LocalTychoRepositoryIndex metadataIndex =
                new LocalTychoRepositoryIndex( location, LocalTychoRepositoryIndex.METADATA_INDEX_RELPATH );

            localRepository = new LocalArtifactRepository( location, artifactsIndex, contentLocator );
            localMetadataRepository = new LocalMetadataRepository( uri, metadataIndex, contentLocator );

            repositoryCache.putRepository( uri, localMetadataRepository, localRepository );
        }

        // XXX remove old
        metadataRepositories.add( localMetadataRepository );
    }

    public void setEnvironments( List<Map<String, String>> environments )
    {
        this.environments = environments;
    }

    public void addDependency( String type, String id, String version )
    {
        if ( P2Resolver.TYPE_INSTALLABLE_UNIT.equals( type ) )
        {
            additionalRequirements.add( MetadataFactory.createRequirement( IInstallableUnit.NAMESPACE_IU_ID, id,
                                                                           new VersionRange( version ), null, false,
                                                                           true ) );
        }
        else if ( P2Resolver.TYPE_ECLIPSE_PLUGIN.equals( type ) )
        {
            // BundlesAction#CAPABILITY_NS_OSGI_BUNDLE
            additionalRequirements.add( MetadataFactory.createRequirement( "osgi.bundle", id,
                                                                           new VersionRange( version ), null, false,
                                                                           true ) );
        }
    }

    public void addMavenRepository( URI location, TychoRepositoryIndex projectIndex, RepositoryReader contentLocator )
    {
        MavenMetadataRepository metadataRepository =
            (MavenMetadataRepository) repositoryCache.getMetadataRepository( location );
        MavenArtifactRepository artifactRepository =
            (MavenArtifactRepository) repositoryCache.getArtifactRepository( location );

        if ( metadataRepository == null || artifactRepository == null )
        {
            metadataRepository = new MavenMetadataRepository( location, projectIndex, contentLocator );
            artifactRepository = new MavenArtifactRepository( location, projectIndex, contentLocator );

            repositoryCache.putRepository( location, metadataRepository, artifactRepository );
        }

        metadataRepositories.add( metadataRepository );
        artifactRepositories.add( artifactRepository );
    }

    public void setLogger( P2Logger logger )
    {
        this.logger = logger;
        this.monitor = new LoggingProgressMonitor( logger );
    }

    public void setRepositoryCache( P2RepositoryCache repositoryCache )
    {
        this.repositoryCache = repositoryCache;
    }

    // creating copy&paste from org.eclipse.equinox.internal.p2.repository.Credentials.forLocation(URI, boolean,
    // AuthenticationInfo)
    public void setCredentials( URI location, String username, String password )
    {
        ISecurePreferences securePreferences = SecurePreferencesFactory.getDefault();

        // if URI is not opaque, just getting the host may be enough
        String host = location.getHost();
        if ( host == null )
        {
            String scheme = location.getScheme();
            if ( URIUtil.isFileURI( location ) || scheme == null )
            {
                // If the URI references a file, a password could possibly be needed for the directory
                // (it could be a protected zip file representing a compressed directory) - in this
                // case the key is the path without the last segment.
                // Using "Path" this way may result in an empty string - which later will result in
                // an invalid key.
                host = new Path( location.toString() ).removeLastSegments( 1 ).toString();
            }
            else
            {
                // it is an opaque URI - details are unknown - can only use entire string.
                host = location.toString();
            }
        }
        String nodeKey;
        try
        {
            nodeKey = URLEncoder.encode( host, "UTF-8" ); //$NON-NLS-1$
        }
        catch ( UnsupportedEncodingException e2 )
        {
            // fall back to default platform encoding
            try
            {
                // Uses getProperty "file.encoding" instead of using deprecated URLEncoder.encode(String location)
                // which does the same, but throws NPE on missing property.
                String enc = System.getProperty( "file.encoding" );//$NON-NLS-1$
                if ( enc == null )
                {
                    throw new UnsupportedEncodingException(
                                                            "No UTF-8 encoding and missing system property: file.encoding" ); //$NON-NLS-1$
                }
                nodeKey = URLEncoder.encode( host, enc );
            }
            catch ( UnsupportedEncodingException e )
            {
                throw new RuntimeException( e );
            }
        }
        String nodeName = IRepository.PREFERENCE_NODE + '/' + nodeKey;

        ISecurePreferences prefNode = securePreferences.node( nodeName );

        try
        {
            if ( !username.equals( prefNode.get( IRepository.PROP_USERNAME, username ) )
                || !password.equals( prefNode.get( IRepository.PROP_PASSWORD, password ) ) )
            {
                logger.info( "Redefining access credentials for repository host " + host );
            }
            prefNode.put( IRepository.PROP_USERNAME, username, false );
            prefNode.put( IRepository.PROP_PASSWORD, password, false );
        }
        catch ( StorageException e )
        {
            throw new RuntimeException( e );
        }
    }

    public void setOffline( boolean offline )
    {
        this.offline = offline;
    }

    @SuppressWarnings( "unused" )
    private void dumpInstallableUnits( IQueryable<IInstallableUnit> source, boolean verbose )
    {
        IQueryResult<IInstallableUnit> collector = source.query( QueryUtil.ALL_UNITS, monitor );
        dumpInstallableUnits( collector.toSet(), verbose );
    }

    private void dumpInstallableUnits( Collection<IInstallableUnit> ius, boolean verbose )
    {
        if ( verbose )
        {
            try
            {
                OutputStream os = new FileOutputStream( "/dev/stdout" ); // will this work?
                try
                {
                    new MetadataIO().writeXML( new LinkedHashSet<IInstallableUnit>( ius ), os );
                }
                finally
                {
                    os.close();
                }
            }
            catch ( IOException e )
            {
                e.printStackTrace();
            }
        }
        else
        {
            for ( IInstallableUnit iu : ius )
            {
                System.out.println( iu.toString() );
            }
        }
    }

    @SuppressWarnings( "unused" )
    private void dumpInstallableUnits( IInstallableUnit[] ius, boolean verbose )
    {
        dumpInstallableUnits( Arrays.asList( ius ), verbose );
    }

    public void stop()
    {
        if ( agent != null )
        {
            agent.stop();
        }
    }
}
