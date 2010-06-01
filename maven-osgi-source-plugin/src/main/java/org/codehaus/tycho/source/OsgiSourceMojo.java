package org.codehaus.tycho.source;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.tycho.ArtifactKey;
import org.codehaus.tycho.TychoProject;
import org.codehaus.tycho.utils.SourceBundleUtils;
import org.osgi.framework.Version;

/**
 * Goal to create a JAR-package containing all the source files of a osgi
 * project.
 * 
 * @extendsPlugin source
 * @extendsGoal jar
 * @goal plugin-source
 * @phase package
 */
public class OsgiSourceMojo extends AbstractSourceJarMojo {
    
    private static final String MANIFEST_HEADER_BUNDLE_MANIFEST_VERSION = "Bundle-ManifestVersion";
    private static final String MANIFEST_HEADER_BUNDLE_SYMBOLIC_NAME = "Bundle-SymbolicName";
    private static final String MANIFEST_HEADER_BUNDLE_VERSION = "Bundle-Version";
    private static final String MANIFEST_HEADER_ECLIPSE_SOURCE_BUNDLE = "Eclipse-SourceBundle";
    private static final String VERSION_QUALIFIER = "qualifier";

	/**
	 * If set to true, compiler will use source folders defined in
	 * build.properties file and will ignore
	 * ${project.compileSourceRoots}/${project.testCompileSourceRoots}.
	 * 
	 * Compilation will fail with an error, if this parameter is set to true but
	 * the project does not have valid build.properties file.
	 * 
	 * @parameter default-value="true"
	 */
	private boolean usePdeSourceRoots;

    /**
     * Whether the source jar should be an Eclipse source bundle.
     * 
     * @parameter default-value="true"
     */
    private boolean sourceBundle;
    
	/**
	 * The suffix to be added to the symbolic name of the bundle to construct
	 * the symbolic name of the Eclipse source bundle.
	 * 
	 * @parameter expression="${sourceBundleSuffix}" default-value=".source"
	 */
	private String sourceBundleSuffix;
	
    /**
     * Build qualifier. Recommended way to set this parameter is using
     * build-qualifier goal. Only used when creating a source bundle. 
     * 
     * @parameter expression="${buildQualifier}"
     */
    private String qualifier;

    /**
     * @component role="org.codehaus.tycho.TychoProject"
     */
    private Map<String, TychoProject> projectTypes;

	/** {@inheritDoc} */
	@SuppressWarnings("unchecked")
	protected List<String> getSources(MavenProject p)
			throws MojoExecutionException {
		if (usePdeSourceRoots) {
			Properties props = getBuildProperties();
			List<String> sources = new ArrayList<String>();
			for (Entry<Object, Object> entry : props.entrySet()) {
				if (((String) entry.getKey()).startsWith("source.")) {
					sources.addAll(getSourceDirs((String) entry.getValue()));
				}
			}
			if (sources.isEmpty()) {
				throw new MojoExecutionException(
						"no source folders found in build.properties");
			}
			return sources;
		} else {
			return p.getCompileSourceRoots();
		}
	}

	private List<String> getSourceDirs(String sourceRaw) {
		List<String> sources = new ArrayList<String>();
		for (String source : sourceRaw.split(",")) {
			sources.add(new File(project.getBasedir(), source.trim())
					.getAbsolutePath());
		}
		return sources;
	}

	/** {@inheritDoc} */
	@SuppressWarnings("unchecked")
	protected List getResources(MavenProject p) {
		if (excludeResources) {
			return Collections.EMPTY_LIST;
		}
		if (usePdeSourceRoots) {
			return Collections.EMPTY_LIST;
		}

		return p.getResources();
	}

	/** {@inheritDoc} */
	protected String getClassifier() {
		return SourceBundleUtils.ARTIFACT_CLASSIFIER;
	}

	// TODO check how to fix this code duplicated
	private Properties getBuildProperties() throws MojoExecutionException {
		File file = new File(project.getBasedir(), "build.properties");
		if (!file.canRead()) {
			throw new MojoExecutionException(
					"Unable to read build.properties file");
		}

		Properties buildProperties = new Properties();
		try {
			InputStream is = new FileInputStream(file);
			try {
				buildProperties.load(is);
			} finally {
				is.close();
			}
		} catch (IOException e) {
			throw new MojoExecutionException(
					"Exception reading build.properties file", e);
		}
		return buildProperties;
	}

    @Override
    protected void updateSourceManifest(MavenArchiveConfiguration mavenArchiveConfiguration) {
        super.updateSourceManifest(mavenArchiveConfiguration);

        if (sourceBundle)
        {
            addSourceBundleManifestEntries(mavenArchiveConfiguration);
        }
    }

    private void addSourceBundleManifestEntries(MavenArchiveConfiguration mavenArchiveConfiguration)
    {
        TychoProject projectType = projectTypes.get( project.getPackaging() );
        ArtifactKey artifactKey = projectType.getArtifactKey( project );
        String symbolicName = artifactKey.getId();
        String version = artifactKey.getVersion();

        if ( symbolicName != null && version != null )
        {
            mavenArchiveConfiguration.addManifestEntry( MANIFEST_HEADER_BUNDLE_MANIFEST_VERSION, "2" );

            mavenArchiveConfiguration.addManifestEntry( MANIFEST_HEADER_BUNDLE_SYMBOLIC_NAME,
            	    symbolicName + sourceBundleSuffix);

            Version expandedVersion = getExpandedVersion(version);

            mavenArchiveConfiguration.addManifestEntry( MANIFEST_HEADER_BUNDLE_VERSION, expandedVersion.toString() );

            mavenArchiveConfiguration.addManifestEntry( MANIFEST_HEADER_ECLIPSE_SOURCE_BUNDLE, symbolicName
            	    + ";version=\"" + expandedVersion + "\";roots:=\".\"" );
        }
        else
        {
            getLog().info("NOT adding source bundle manifest entries. Incomplete or no bundle information available.");
        }
    }

    private Version getExpandedVersion(String versionStr)
    {
        Version version = Version.parseVersion( versionStr );
        if ( VERSION_QUALIFIER.equals(version.getQualifier()) )
        {
            return new Version(version.getMajor(), version.getMinor(), version.getMicro(), qualifier);
        }
        return version;
    }

	@Override
	protected boolean isRelevantProject(MavenProject project) {
		String packaging = project.getPackaging();
		return TychoProject.ECLIPSE_PLUGIN.equals(packaging)
				|| TychoProject.ECLIPSE_TEST_PLUGIN.equals(packaging);
	}

}
