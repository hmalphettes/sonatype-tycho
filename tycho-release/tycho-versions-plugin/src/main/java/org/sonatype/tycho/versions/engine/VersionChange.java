package org.sonatype.tycho.versions.engine;

import org.sonatype.tycho.versions.pom.MutablePomFile;

public class VersionChange
{
    protected final String newVersion;

    private final MutablePomFile pom;

    private final String version;

    public VersionChange( MutablePomFile pom, String newVersion )
    {
        this( pom, pom.getEffectiveVersion(), newVersion );
    }

    public VersionChange( MutablePomFile pom, String version, String newVersion )
    {
        this.pom = pom;
        this.version = VersionsEngine.toCanonicalVersion( version );
        this.newVersion = VersionsEngine.toCanonicalVersion( newVersion );
    }

    public String getGroupId()
    {
        return pom.getEffectiveGroupId();
    }

    public String getArtifactId()
    {
        return pom.getArtifactId();
    }

    public String getVersion()
    {
        return version;
    }

    public MutablePomFile getProject()
    {
        return pom;
    }

    public String getNewVersion()
    {
        return newVersion;
    }

    @Override
    public int hashCode()
    {
        int hash = version.hashCode();
        hash = 17 * hash + newVersion.hashCode();
        hash = 17 * hash + pom.getEffectiveGroupId().hashCode();
        hash = 17 * hash + pom.getArtifactId().hashCode();
        return hash;
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( obj == this )
        {
            return true;
        }

        if ( !( obj instanceof VersionChange ) )
        {
            return false;
        }

        VersionChange other = (VersionChange) obj;

        return version.equals( other.version ) && newVersion.equals( other.newVersion )
            && pom.getEffectiveGroupId().equals( other.pom.getEffectiveGroupId() )
            && pom.getArtifactId().equals( other.getArtifactId() );
    }
}
