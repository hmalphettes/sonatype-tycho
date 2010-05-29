package org.sonatype.tycho.p2.publisher;

import java.net.URISyntaxException;
import java.util.ArrayList;

import org.eclipse.equinox.internal.p2.updatesite.UpdateSitePublisherApplication;
import org.eclipse.equinox.p2.publisher.IPublisherAction;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.actions.JREAction;

/**
 * 2 additions to the original UpdateSitePublisherApplication:
 * <ol>
 * <li>Support for the generation of </li>
 * <li></li>
 * </ol>
 * 
 * The UpdateSitePublisherApplication does not declare the dependency on the JREIU.
 * This causes the bug: https://bugs.eclipse.org/bugs/show_bug.cgi?id=311795
 * This extended UpdateSitePublisherApplication also publishes the jre IU to solve the problem.
 * This is a workaround until we know better.
 * <p>
 * This new behavior is triggered if and only if the flag '-addJREIU' is part of the 
 * command line parameters to invoke the application.
 * </p>
 */
public class UpdateSitePublisherWithJREApplication extends UpdateSitePublisherApplication
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
		}
		else
		{
			super.processFlag(flag, publisherInfo);
		}
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
