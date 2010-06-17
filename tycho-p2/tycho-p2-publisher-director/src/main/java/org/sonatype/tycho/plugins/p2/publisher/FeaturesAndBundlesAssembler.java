package org.sonatype.tycho.plugins.p2.publisher;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.codehaus.tycho.ArtifactKey;
import org.codehaus.tycho.FeatureDescription;
import org.codehaus.tycho.PluginDescription;
import org.codehaus.tycho.TargetPlatform;
import org.codehaus.tycho.TychoProject;
import org.codehaus.tycho.buildversion.VersioningHelper;
import org.codehaus.tycho.eclipsepackaging.UpdateSiteAssembler;
import org.codehaus.tycho.model.Feature;
import org.codehaus.tycho.osgitools.DefaultFeatureDescription;
import org.codehaus.tycho.osgitools.DefaultPluginDescription;
import org.sonatype.tycho.plugins.p2.AbstractP2Mojo;

/**
 * Similar to the UpdateSiteAssembler. In fact at this point it does differently really.
 */
public class FeaturesAndBundlesAssembler extends UpdateSiteAssembler {

	private String qualifier;
	private AbstractP2Mojo mojo;
	
	public FeaturesAndBundlesAssembler(AbstractP2Mojo mojo, File target) {
		super(mojo.getSession(), target);
		super.setPack200(false);//we take care of pack200 with the eclipse jarprocessor later.
		this.mojo = mojo;
	}

}
