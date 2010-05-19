package org.sonatype.tycho.p2.updatesite;

import org.eclipse.equinox.internal.p2.updatesite.UpdateSitePublisherApplication;
import org.eclipse.equinox.p2.publisher.IPublisherAction;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.actions.JREAction;

/**
 * The UpdateSitePublisherApplication does not declare the dependency on the JREIU.
 * This causes the bug: https://bugs.eclipse.org/bugs/show_bug.cgi?id=311795
 * This extended UpdateSitePublisherApplication also publishes the jre IU to solve the problem.
 * This is a workaround until we know better.
 * 
 * This new behavior is triggered if and only if the flag '-addJREIU' is part of the 
 * command line parameters to invoke the application.
 */
public class UpdateSitePublisherWithJREApplication extends UpdateSitePublisherApplication
{
	
	private boolean addJRE = false;

	@Override
	protected IPublisherAction[] createActions()
	{
		if (!addJRE)
		{
			return super.createActions();
		}
		
		IPublisherAction[] actions = super.createActions();
		IPublisherAction[] newActions = new IPublisherAction[actions.length + 1];
		for (int i = 0; i < actions.length; i++)
		{
			IPublisherAction action = actions[i];
			if (action instanceof JREAction)
			{
				return actions;
			}
			newActions[i] = action;
		}
		//let's append the JREAction here.
		newActions[actions.length] = new JREAction((String)null);
		return newActions;
	}
	
	/**
	 * Detect the flag -addJREIU to turn on the generation of the JREIU.
	 */
	protected void processFlag(String flag, PublisherInfo publisherInfo) {
		super.processFlag(flag, publisherInfo);
		if (flag.equalsIgnoreCase("-addJREIU"))//$NON-NLS-1$
		{
			addJRE = true;
		}
	}

}
