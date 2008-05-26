package org.codehaus.tycho.osgitools;

import java.io.File;

import org.apache.maven.artifact.Artifact;
import org.eclipse.osgi.service.resolver.ResolverError;

public interface OsgiDependencyVerifier
{
	static final String ROLE = OsgiDependencyVerifier.class.getName();
	
	ResolverError[] resolve(File outputDir, File[] bundles);
	ResolverError[] resolve(File outputDir, Artifact[] artifacts);
	
}