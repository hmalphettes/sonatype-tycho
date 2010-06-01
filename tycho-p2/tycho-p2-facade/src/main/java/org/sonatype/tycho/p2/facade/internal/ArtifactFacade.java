package org.sonatype.tycho.p2.facade.internal;

import java.io.File;

import org.apache.maven.artifact.Artifact;

public class ArtifactFacade implements IArtifactFacade {

	private Artifact wrappedArtifact;
	
	public ArtifactFacade(Artifact wrappedArtifact) {
		this.wrappedArtifact = wrappedArtifact;
	}

	public File getLocation() {
		return wrappedArtifact.getFile();
	}

	public String getGroupId() {
		return wrappedArtifact.getGroupId();
	}

	public String getArtifactId() {
		return wrappedArtifact.getArtifactId();
	}

	public String getVersion() {
		return wrappedArtifact.getVersion();
	}

	public String getPackagingType() {
		return wrappedArtifact.getType();
	}

	public String getSourceBundleSuffix() {
		return null;
	}

	public boolean hasSourceBundle() {
		return false;
	}

}
