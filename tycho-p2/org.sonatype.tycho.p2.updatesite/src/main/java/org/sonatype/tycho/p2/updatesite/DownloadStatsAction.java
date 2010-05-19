package org.sonatype.tycho.p2.updatesite;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.publisher.AbstractPublisherAction;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.IPublisherResult;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.IQueryable;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.artifact.ArtifactDescriptorQuery;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.equinox.p2.repository.artifact.spi.ArtifactDescriptor;

/**
 * See http://wiki.eclipse.org/Equinox_p2_download_stats
 * and https://bugs.eclipse.org/bugs/show_bug.cgi?id=302160
 */
public class DownloadStatsAction extends AbstractPublisherAction {

	private String statsURI;
	private String statsPrefix;
	private String statsSuffix;
	private boolean useArtifactId;
	private boolean useArtifactVersion;
	private boolean useRepositoryVersion;
	private Set<String> statsTrackedBundles;
	
	/**
	 * Generate the following url for download stats:
	 * statsURI/artifactId+.bundle
	 * This matches the current example on the eclipse wiki.
	 * <p>
	 * if statsURI is null or empty then this action does nothing.
	 * </p>
	 * @param statsURI if null this action does nothing
	 * @param statsTrackedBundles
	 * @param statsPrefix
	 * @param statsSuffix
	 * @param useBundleId true to assemble the bundle id, false to not do that.
	 */
	public DownloadStatsAction(String statsURI, String statsTrackedBundles) {
		this(statsURI, split(statsTrackedBundles), null, ".bundle", true, false, false);
	}
	
	private static final Set<String> split(String idsList) {
		Set<String> trackedArtifactIDs = new HashSet<String>();
		StringTokenizer tokenizer = new StringTokenizer(idsList, ",;: ", false);
		while (tokenizer.hasMoreTokens()) {
			trackedArtifactIDs.add(tokenizer.nextToken());
		}
		return trackedArtifactIDs;
	}

	/**
	 * Generate the following url for download stats:
	 * statsURI/statsPrefix+repoVersion+artifactId+_+artifactVersion+statsSuffix
	 * if useArtifactId is false then it is not used.
	 * if useRepositoryVersion is false then it is not used.
	 * </br> On the current wiki page
	 * statsPrefix is null, statsSuffix is 'bundle', useArtifactId is true
	 * and useRepositoryVersion is false.
	 * <p>
	 * if statsURI is null or empty then this action does nothing.
	 * </p>
	 * @param statsURI if null this action does nothing
	 * @param statsTrackedBundles
	 * @param statsPrefix
	 * @param statsSuffix
	 * @param useBundleId true to assemble the bundle id, false to not do that.
	 */
	public DownloadStatsAction(String statsURI, Set<String> statsTrackedBundles,
			String statsPrefix, String statsSuffix, boolean useArtifactId,
			boolean useArtifactVersion, boolean useRepositoryVersion) {
		this.statsURI = statsURI;
		this.statsTrackedBundles = statsTrackedBundles;
		this.statsPrefix = statsPrefix == null ? "" : statsPrefix;
		this.statsSuffix = statsPrefix == null ? "" : statsSuffix;
		this.useArtifactId = useArtifactId;
		this.useArtifactVersion = useArtifactVersion;
		this.useRepositoryVersion = useRepositoryVersion;
	}
		
	@Override
	public IStatus perform(IPublisherInfo publisherInfo,
			IPublisherResult results, IProgressMonitor monitor) {
		
		if (statsURI == null) {
			return Status.OK_STATUS;
		}
		
		publisherInfo.getArtifactRepository().setProperty("p2.statsURI", statsURI);
		
		for (String bundleId : statsTrackedBundles) {
			IQueryable<IArtifactDescriptor> queryable = publisherInfo.getArtifactRepository().descriptorQueryable();
			IQuery<IInstallableUnit> query = QueryUtil.createIUQuery(bundleId); 
			IQueryResult<IArtifactDescriptor> artifacts =
				queryable.query(new ArtifactDescriptorQuery(bundleId, null, null), monitor);
			
			for (IArtifactDescriptor artifact : artifacts.toArray(IArtifactDescriptor.class)) {
				if (artifact instanceof ArtifactDescriptor) {
					//should we check that this root IU is indeed a bundle?
					//(don't know how to do that)
					((ArtifactDescriptor)artifact).setProperty("p2.statsURI", getStatsValue(publisherInfo, artifact));
				}
			}
		}
		return Status.OK_STATUS;
	}
	
	private String getStatsValue(IPublisherInfo publisherInfo, IArtifactDescriptor artifactDesc) {
		StringBuilder sb = new StringBuilder(statsPrefix);
		if (useRepositoryVersion) {
			if (sb.length() > 0) {
				sb.append("_");
			}
			sb.append(publisherInfo.getArtifactRepository().getVersion());
			sb.append("_");
		}
		if (useArtifactId) {
			sb.append(artifactDesc.getArtifactKey().getId());
			if (useArtifactVersion) {
				String version = artifactDesc.getArtifactKey().getVersion().getOriginal();
				if (version != null) {
					sb.append("_");
					sb.append(version);
				}
			}
		}
		sb.append(statsSuffix);
		return sb.toString();
	}

}
