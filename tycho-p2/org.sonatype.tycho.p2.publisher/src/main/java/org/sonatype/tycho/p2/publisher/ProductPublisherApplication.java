package org.sonatype.tycho.p2.publisher;

import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.equinox.internal.p2.updatesite.UpdateSitePublisherApplication;
import org.eclipse.equinox.p2.publisher.AbstractPublisherApplication;
import org.eclipse.equinox.p2.publisher.IPublisherAction;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.actions.JREAction;

/**
 * Support for download stats
 */
public class ProductPublisherApplication extends org.eclipse.equinox.p2.publisher.eclipse.ProductPublisherApplication
{
	
	private String statsURI = null;
	private String statsTrackedBundles = null;
	private String statsPrefix = null;
	private String statsSuffix = null;

	public ProductPublisherApplication()
	{
		try {
	    // TODO HACK to work around Eclipse bug #315757 so that context repositories can be set
	         final Method setupAgentMethod = AbstractPublisherApplication.class.getDeclaredMethod("setupAgent"); //$NON-NLS-1$
	         setupAgentMethod.setAccessible(true);
	         setupAgentMethod.invoke(this);
	     } catch (final Exception e) {
	         e.printStackTrace();
	         throw new RuntimeException(e);
	     }
	}
		
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
