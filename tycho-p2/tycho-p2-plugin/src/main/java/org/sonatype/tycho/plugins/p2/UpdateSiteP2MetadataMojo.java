package org.sonatype.tycho.plugins.p2;


/**
 * @goal update-site-p2-metadata
 */
public class UpdateSiteP2MetadataMojo
    extends AbstractP2MetadataMojo
{
	public static String UPDATE_SITE_PUBLISHER_APP_NAME = "org.eclipse.equinox.p2.publisher.UpdateSitePublisher";
	public static String UPDATE_SITE_WITH_JRE_PUBLISHER_APP_NAME = "org.sonatype.tycho.p2.updatesite.UpdateSitePublisherWithJRE";
	
	/**
	 * @return by default the traditional p2 publisher's app for an UpdateSite
	 * If the property 'tycho.updatesite.with.jre' is set to true
	 * then return the extended application that adds the a.jre.javase IU
	 * to workaround the bug 
	 */
    protected String getPublisherApplication()
    {
        if ("true".equals(super.project.getProperties().get("tycho.updatesite.with.jre"))
        		|| super.project.getProperties().get("tycho.updatesite.with.statsUri") != null)
       	{
        	return UPDATE_SITE_WITH_JRE_PUBLISHER_APP_NAME;
       	}
        return UPDATE_SITE_PUBLISHER_APP_NAME;
    }
    
    /**
     * By default returns null.
     * @return some more arguments added to the command line to invoke the publisher.
     * For example the product needs to be passed the config argument.
     */
    protected String[] getOtherPublisherArguments()
    {
    	if ("true".equals(super.project.getProperties().get("tycho.updatesite.with.jre"))) {
        	return new String[] {"-addJREIU", "-consoleLog"};
    	}
    	return null;
    }
    
}
