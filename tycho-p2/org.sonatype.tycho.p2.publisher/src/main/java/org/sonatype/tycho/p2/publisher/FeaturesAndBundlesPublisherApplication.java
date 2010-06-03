package org.sonatype.tycho.p2.publisher;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.equinox.internal.p2.updatesite.UpdateSitePublisherApplication;
import org.eclipse.equinox.p2.publisher.IPublisherAction;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.actions.JREAction;
import org.eclipse.equinox.p2.publisher.eclipse.FeaturesAction;

/**
 * Support for download stats and jre
 */
public class FeaturesAndBundlesPublisherApplication extends org.eclipse.equinox.p2.publisher.eclipse.FeaturesAndBundlesPublisherApplication
{
	
	private boolean addJRE = false;
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
			if (action instanceof FeaturesAction)
			{
				//this action takes care of the root files.
				action = FeaturesActionWithRootIUs.createFromOriginal((FeaturesAction)action);
			}
			newActions.add(action);
		}
		if (statsURI != null)
		{
			newActions.add(new DownloadStatsAction(statsURI, statsTrackedBundles, statsPrefix, statsSuffix, true));
		}
		if (addJRE)
		{
			boolean alreadyThere = false;
			for (int i = 0; i < actions.length; i++)
			{
				IPublisherAction action = actions[i];
				if (action instanceof JREAction)
				{
					alreadyThere = true;
					continue;
				}
			}
			if (!alreadyThere)
			{
				newActions.add(new JREAction((String)null));
			}
		}
		
		return newActions.toArray(new IPublisherAction[newActions.size()]);
	}
	
	/**
	 * Detect the flag -addJREIU to turn on the generation of the JREIU.
	 */
	protected void processFlag(String flag, PublisherInfo publisherInfo) {
		if (flag.equalsIgnoreCase("-addJREIU"))//$NON-NLS-1$
		{
			addJRE = true;
			return;
		}
		super.processFlag(flag, publisherInfo);
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
