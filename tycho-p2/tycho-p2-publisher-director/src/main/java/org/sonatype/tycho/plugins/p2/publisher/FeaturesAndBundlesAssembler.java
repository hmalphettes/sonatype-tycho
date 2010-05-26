package org.sonatype.tycho.plugins.p2.publisher;

import java.io.File;

import org.apache.maven.execution.MavenSession;
import org.codehaus.tycho.eclipsepackaging.UpdateSiteAssembler;

/**
 * Similar to the UpdateSiteAssembler. In fact at this point it does differently really.
 */
public class FeaturesAndBundlesAssembler extends UpdateSiteAssembler {

	public FeaturesAndBundlesAssembler(MavenSession session, File target) {
		super(session, target);
	}

}
