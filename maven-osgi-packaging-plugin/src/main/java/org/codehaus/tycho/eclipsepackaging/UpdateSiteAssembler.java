package org.codehaus.tycho.eclipsepackaging;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Map;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.gzip.GZipCompressor;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.tycho.ArtifactDependencyVisitor;
import org.codehaus.tycho.FeatureDescription;
import org.codehaus.tycho.PluginDescription;
import org.codehaus.tycho.buildversion.VersioningHelper;
import org.codehaus.tycho.eclipsepackaging.pack200.Pack200Archiver;
import org.codehaus.tycho.model.PluginRef;
import org.codehaus.tycho.osgitools.BundleReader;

/**
 * Assembles standard eclipse update site directory structure on local filesystem.
 * 
 * @author igor
 */
public class UpdateSiteAssembler
    extends ArtifactDependencyVisitor
{

    public static final String PLUGINS_DIR = "plugins/";

    public static final String FEATURES_DIR = "features/";

    private final MavenSession session;

    private final File target;

    private Map<String, String> archives;

    /**
     * If true, will copy/generate pack200 archives in additional to plugin jar files.
     */
    private boolean pack200;

    /**
     * If true, generated update site will include plugins folders for plugins with PluginRef.unpack. If false, will
     * include plugin jars regardless of PluginRef.unpack.
     */
    private boolean unpackPlugins;

    /**
     * If true, generated update site will include feature directories. If false, generated update site will include
     * feature jars.
     */
    private boolean unpackFeatures;
    
    protected final BundleReader manifestReader;

    public UpdateSiteAssembler( MavenSession session, File target, BundleReader manifestReader )
    {
        this.session = session;
        this.target = target;
        this.manifestReader = manifestReader;
    }

    public boolean visitFeature( FeatureDescription feature )
    {
        File location = feature.getLocation();
        String artifactId = feature.getKey().getId();
        String version = feature.getKey().getVersion();

        MavenProject featureProject = feature.getMavenProject();

        if ( featureProject != null )
        {
            version = VersioningHelper.getExpandedVersion( featureProject, version );

            location = featureProject.getArtifact().getFile();

            if ( location.isDirectory() )
            {
                throw new IllegalStateException( "Should at least run ``package'' phase" );
            }
        }

        if ( unpackFeatures )
        {
            File outputJar = getOutputFile( FEATURES_DIR, artifactId, version, null );
            if ( location.isDirectory() )
            {
                copyDir( location, outputJar );
            }
            else
            {
                unpackJar( location, outputJar );
            }
        }
        else
        {
            File outputJar = getOutputFile( FEATURES_DIR, artifactId, version, ".jar" );
            if ( location.isDirectory() )
            {
                packDir( location, outputJar );
            }
            else
            {
                copyFile( location, outputJar );
            }
        }

        return true; // keep visiting
    }

    private File getOutputFile( String prefix, String id, String version, String extension )
    {
        StringBuilder sb = new StringBuilder( prefix );
        sb.append( id );
        sb.append( '_' );
        sb.append( version );
        if ( extension != null )
        {
            sb.append( extension );
        }

        return new File( target, sb.toString() );
    }
    
    /**
     * Returns the location of the built artifact.
     * When the plugin passed is a source bundle looks into the attached artifact for the one with
     * the 'sources' classifier.
     * Otherwise returns the main artifact built by the project.
     * 
     * @param plugin The plugin built now used in a feature or a product.
     * @param bundleProject The project where this plugin is generated. Must not be null.
     * @param manifestReader
     * @return The location of the artifact built by this reactor project.
     */
    static File getBuiltArtifactLocation(PluginDescription plugin, MavenProject bundleProject, BundleReader manifestReader) {
    	File location = null;
    	if (isSourceBundle(plugin, manifestReader))
    	{
    		Artifact builtSources = null;
    		for (Artifact attached : bundleProject.getAttachedArtifacts())
    		{
    			if ("sources".equals(attached.getClassifier()))
    			{
    				builtSources = attached;
    				break;
    			}
    		}
    		if (builtSources != null)
    		{
    			location = builtSources.getFile();
    		}
    		else
    		{
    			//error?
    			System.err.println("WARNING: the bundle " + plugin.getPluginRef().getId() +
    					" looks like a source bundle usually generated" +
    					" by the maven-osgi-source plugin. No such artifact was built by the project " + bundleProject);
    			//in the mean time default on the main artifact.
    			location = bundleProject.getArtifact().getFile();
    		}
    	}
    	else
    	{
    		location = bundleProject.getArtifact().getFile();
    	}

        if ( location == null || location.isDirectory() )
        {
            throw new IllegalStateException( "At least ``package'' phase execution is required" );
        }
        return location;
    }
    
    
    public void visitPlugin( PluginDescription plugin )
    {
        String bundleId = plugin.getKey().getId();
        String version = plugin.getKey().getVersion();

        String relPath = PLUGINS_DIR + bundleId + "_" + version + ".jar";
        if ( archives != null && archives.containsKey( relPath ) )
        {
            copyUrl( archives.get( relPath ), new File( target, relPath ) );
            return;
        }

        File location = plugin.getLocation();
        if ( location == null )
        {
            throw new IllegalStateException( "Unresolved bundle reference " + bundleId + "_" + version );
        }
        MavenProject bundleProject = plugin.getMavenProject();
        if ( bundleProject != null )
        {
        	location = getBuiltArtifactLocation(plugin, bundleProject, manifestReader);
        	version = VersioningHelper.getExpandedVersion( bundleProject, version );
        }

        if ( unpackPlugins && isDirectoryShape( plugin, location ) )
        {
            // need a directory
            File outputJar = getOutputFile( PLUGINS_DIR, bundleId, version, null );

            if ( location.isDirectory() )
            {
                copyDir( location, outputJar );
            }
            else
            {
                unpackJar( location, outputJar );
            }
        }
        else
        {
            // need a jar
            File outputJar = getOutputFile( PLUGINS_DIR, bundleId, version, ".jar" );

            if ( location.isDirectory() )
            {
                packDir( location, outputJar );
            }
            else
            {
                copyFile( location, outputJar );
            }

//            if ( pack200 )
//            {
//                shipPack200( outputJar );
//            }
        }
    }
    
    static boolean isSourceBundle( PluginDescription plugin, BundleReader manifestReader )
    {
    	if (plugin.getMavenProject() != null && plugin.getKey().getId().endsWith(".source"))
    	{
    		//TYCHO-192
    		//when this is the attached sources... we can't read the manifest as
    		//the file would point to the main runtime bundle; not the generated one.
    		return true;
    	}
        Manifest mf = manifestReader.loadManifest( plugin.getLocation() );
        return manifestReader.parseHeader( "Eclipse-SourceBundle", mf ) != null;
    }

    protected boolean isSourceBundle( PluginDescription plugin )
    {
        return isSourceBundle(plugin, manifestReader);
    }

    
    protected boolean isDirectoryShape( PluginDescription plugin, File location )
    {
        PluginRef pluginRef = plugin.getPluginRef();
        return ( ( pluginRef != null && pluginRef.isUnpack() ) || location.isDirectory() );
    }

    private void unpackJar( File location, File outputJar )
    {
        ZipUnArchiver unzip;
        try
        {
            unzip = (ZipUnArchiver) session.lookup( ZipUnArchiver.ROLE, "zip" );
        }
        catch ( ComponentLookupException e )
        {
            throw new RuntimeException( "Could not lookup required component", e );
        }

        outputJar.mkdirs();

        if ( !outputJar.isDirectory() )
        {
            throw new RuntimeException( "Could not create output directory " + outputJar.getAbsolutePath() );
        }

        unzip.setSourceFile( location );
        unzip.setDestDirectory( outputJar );

        try
        {
            unzip.extract();
        }
        catch ( ArchiverException e )
        {
            throw new RuntimeException( "Could not unpack jar", e );
        }
    }

    private void copyDir( File location, File outputJar )
    {
        try
        {
            FileUtils.copyDirectoryStructure( location, outputJar );
        }
        catch ( IOException e )
        {
            throw new RuntimeException( "Could not copy directory", e );
        }
    }

    private void copyUrl( String source, File destination )
    {
        try
        {
            URL url = new URL( source );
            InputStream is = url.openStream();
            try
            {
                OutputStream os = new BufferedOutputStream( new FileOutputStream( destination ) );
                try
                {
                    IOUtil.copy( is, os );
                }
                finally
                {
                    os.close();
                }
            }
            finally
            {
                is.close();
            }
        }
        catch ( IOException e )
        {
            throw new RuntimeException( "Could not copy URL contents", e );
        }
    }

    private void copyFile( File source, File destination )
    {
        try
        {
            FileUtils.copyFile( source, destination );
        }
        catch ( IOException e )
        {
            throw new RuntimeException( "Could not copy file", e );
        }
    }

    private void shipPack200( File jar )
    {
        // TODO validate if the jar has been pack200 pre-conditioned

        File outputPack = new File( jar.getParentFile(), jar.getName() + ".pack" );

        Pack200Archiver packArchiver = new Pack200Archiver();
        packArchiver.setSourceJar( jar );
        packArchiver.setDestFile( outputPack );
        try
        {
            packArchiver.createArchive();
        }
        catch ( IOException e )
        {
            throw new RuntimeException( "Could not create pack200 archive", e );
        }

        GZipCompressor gzCompressor = new GZipCompressor();
        gzCompressor.setDestFile( new File( jar.getParentFile(), jar.getName() + ".pack.gz" ) );
        gzCompressor.setSourceFile( outputPack );
        try
        {
            gzCompressor.execute();
        }
        catch ( ArchiverException e )
        {
            throw new RuntimeException( e.getMessage(), e );
        }

        outputPack.delete();
    }

    private void packDir( File sourceDir, File targetZip )
    {
        ZipArchiver archiver;
        try
        {
            archiver = (ZipArchiver) session.lookup( ZipArchiver.ROLE, "zip" );
        }
        catch ( ComponentLookupException e )
        {
            throw new RuntimeException( "Unable to resolve ZipArchiver", e );
        }

        archiver.setDestFile( targetZip );
        try
        {
            archiver.addDirectory( sourceDir );
            archiver.createArchive();
        }
        catch ( IOException e )
        {
            throw new RuntimeException( "Error packing zip", e );
        }
        catch ( ArchiverException e )
        {
            throw new RuntimeException( "Error packing zip", e );
        }
    }

    public void setArchives( Map<String, String> archives )
    {
        this.archives = archives;
    }

    public void setPack200( boolean pack200 )
    {
        this.pack200 = pack200;
    }

    public void setUnpackPlugins( boolean unpack )
    {
        this.unpackPlugins = unpack;
    }

    public void setUnpackFeatures( boolean unpack )
    {
        this.unpackFeatures = unpack;
    }
}
