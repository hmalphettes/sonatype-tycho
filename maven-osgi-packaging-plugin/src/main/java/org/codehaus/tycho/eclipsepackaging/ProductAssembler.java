package org.codehaus.tycho.eclipsepackaging;

import java.io.File;
import java.util.jar.Manifest;

import org.apache.maven.execution.MavenSession;
import org.codehaus.tycho.PluginDescription;
import org.codehaus.tycho.TargetEnvironment;
import org.codehaus.tycho.model.PluginRef;
import org.codehaus.tycho.osgitools.BundleReader;

public class ProductAssembler
    extends UpdateSiteAssembler
{

    private final TargetEnvironment environment;

    private boolean includeSources;

    public ProductAssembler( MavenSession session, BundleReader manifestReader, File target, TargetEnvironment environment )
    {
        super( session, target, manifestReader );
        setUnpackPlugins( true );
        setUnpackFeatures( true );
        this.environment = environment;
    }
    
    @Override
    public void visitPlugin( PluginDescription plugin )
    {
        if ( !matchEntivonment( plugin ) )
        {
            return;
        }

        if ( !includeSources && isSourceBundle( plugin ) )
        {
            return;
        }

        super.visitPlugin( plugin );
    }

    @Override
    protected boolean isDirectoryShape( PluginDescription plugin, File location )
    {
        if ( super.isDirectoryShape( plugin, location ) )
        {
            return true;
        }
        
        Manifest mf = manifestReader.loadManifest( location );

        return manifestReader.isDirectoryShape( mf );
    }

    protected boolean matchEntivonment( PluginDescription plugin )
    {
        PluginRef ref = plugin.getPluginRef();
        return ref == null || environment == null || environment.match( ref.getOs(), ref.getWs(), ref.getArch() );
    }

    public void setIncludeSources( boolean includeSources )
    {
        this.includeSources = includeSources;
    }

}
