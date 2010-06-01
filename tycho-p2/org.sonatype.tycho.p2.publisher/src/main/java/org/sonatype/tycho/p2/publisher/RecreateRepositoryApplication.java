package org.sonatype.tycho.p2.publisher;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.internal.p2.repository.helpers.RepositoryHelper;
import org.eclipse.equinox.p2.internal.repository.tools.RepositoryDescriptor;

/**
 * This application is able to fix the checksums after a pack and sign has been run
 * and generate the descriptors for the pack.gz files.
 * 
 * It expects a single argument: -artifactRepository
 * 
 */
public class RecreateRepositoryApplication extends org.eclipse.equinox.p2.internal.repository.tools.RecreateRepositoryApplication
implements IApplication {

	public Object start(IApplicationContext context) throws Exception {
		initializeFromArguments((String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS));
		run(new NullProgressMonitor());
		return IApplication.EXIT_OK;
	}

	public void stop() {
		//nothing to do.
	}
	
	public void initializeFromArguments(String[] args) throws Exception {
		if (args == null)
		{
			throw new IllegalArgumentException("The argument -artifactRepository is mandatory");
		}

		RepositoryDescriptor sourceRepo = new RepositoryDescriptor();
		sourceRepo.setKind(RepositoryDescriptor.KIND_ARTIFACT);

		boolean sourceRepoSet = false;
		for (int i = 0; i < args.length; i++) {
			if (i == args.length - 1 || args[i + 1].startsWith("-")) //$NON-NLS-1$
				continue;

			String arg = args[++i];

			try
			{
				if (args[i - 1].equalsIgnoreCase("-artifactRepository") 
						|| args[i - 1].equalsIgnoreCase("-a"))
				{ //$NON-NLS-1$
					URI uri = RepositoryHelper.localRepoURIHelper(URIUtil.fromString(arg));
					sourceRepo.setLocation(uri);
					super.setArtifactRepository(sourceRepo);
					sourceRepoSet = true;
				}
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException("invalid URL: " + arg);
			}
		}
		if (!sourceRepoSet)
		{
			throw new IllegalArgumentException("The argument -artifactRepository is mandatory.");
		}
	}


	
	
}
