package org.sonatype.tycho.plugins.p2;

/**
 * @goal feature-p2-metadata
 */
public class FeatureP2MetadataMojo
    extends AbstractP2MetadataMojo
{
	public static String FEATURES_AND_BUNDLES_PUBLISHER_APP_NAME = "org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher";
	
    @Override
    protected String getPublisherApplication()
    {
        return FEATURES_AND_BUNDLES_PUBLISHER_APP_NAME;
    }
}
