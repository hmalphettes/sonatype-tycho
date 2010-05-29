package org.sonatype.tycho.p2.publisher;

import java.net.URISyntaxException;
import java.util.ArrayList;

import org.eclipse.equinox.p2.publisher.IPublisherAction;
import org.eclipse.equinox.p2.publisher.PublisherInfo;

/**
 * Support for download stats and jre
 */
public class CategoryPublisherApplication extends org.eclipse.equinox.internal.p2.updatesite.CategoryPublisherApplication
{
	
	private String statsURI = null;
	private String statsTrackedBundles = null;
	private String statsPrefix = null;
	private String statsSuffix = null;

	@Override
	protected IPublisherAction[] createActions()
	{
		IPublisherAction[] actions = super.createActions();
		ArrayList<IPublisherAction> newActions = new ArrayList<IPublisherAction>();
		for (IPublisherAction action : actions)
		{
			newActions.add(action);
		}
		if (statsURI != null)
		{
			newActions.add(new DownloadStatsAction(statsURI, statsTrackedBundles, statsPrefix, statsSuffix, true));
		}
		return newActions.toArray(new IPublisherAction[newActions.size()]);
	}
	
	/**
	 * Support for the download stats properties.
	 * See https://bugs.eclipse.org/bugs/show_bug.cgi?id=310132
	 * 
	 */
	protected void processParameter(String arg, String parameter, PublisherInfo pinfo) throws URISyntaxException {
		if (arg.equalsIgnoreCase("-p2.statsURI")) { //$NON-NLS-1$
			statsURI = parameter;
			return;
		}

		if (arg.equalsIgnoreCase("-p2.statsTrackedBundles")) {//$NON-NLS-1$
			statsTrackedBundles = parameter;
			return;
		}
		
		if (arg.equalsIgnoreCase("-p2.statsPrefix")) {//$NON-NLS-1$
			statsPrefix = parameter;
			return;
		}
				
		if (arg.equalsIgnoreCase("-p2.statsSuffix")) {//$NON-NLS-1$
			statsSuffix = parameter;
			return;
		}
		super.processParameter(arg, parameter, pinfo);
				
		
	}



}
