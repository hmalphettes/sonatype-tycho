package org.sonatype.tycho.p2.publisher;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Properties;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.p2.publisher.IPublisherAdvice;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.IPublisherResult;
import org.eclipse.equinox.p2.publisher.eclipse.Feature;
import org.eclipse.equinox.p2.publisher.eclipse.FeaturesAction;

import forked.org.eclipse.buckminster.pde.tasks.FeatureRootAdviceFactory;

/**
 * Support for root IUs.
 * They are defined and published for a given feature via this file name ${featureId}.build.properties
 * The format of the properties file is defined here:
 * http://help.eclipse.org/galileo/index.jsp?topic=/org.eclipse.pde.doc.user/tasks/pde_rootfiles.htm
 * <p>
 * If there is only a build.properties file, we associate it to the first feature and publish it
 * which is what the pde build does (?)
 * </p>
 */
public class FeaturesActionWithRootIUs extends FeaturesAction {

	public static FeaturesActionWithRootIUs createFromOriginal(FeaturesAction ori)
	{
		File[] locations = getLocationsFieldValue(ori);
		Feature[] features = getFeaturesFieldValue(ori);
		FeaturesActionWithRootIUs res = new FeaturesActionWithRootIUs(locations);
		res.features = features;
		return res;
	}
	
	private File baseDirectory;
	
	private IPublisherInfo __publisherInfo;
	private IStatus _statusOfGenerateIUs;
	
	public FeaturesActionWithRootIUs(File[] locations) {
		super(locations);
		if (locations == null || locations.length == 0)
		{
			//how do we look for the build.properties file?
		}
		else
		{
			//go up the filesystem looking for the first build.properties file.
			File current = locations[0];
			while (current != null)
			{
				if (current.isDirectory())
				{
					File dotProject = new File(current, ".project");
					if (dotProject.exists())
					{
						baseDirectory = current;
						break;
					}
					else if (new File(current, "pom.xml").exists())
					{
						baseDirectory = current;
						break;
					}
				}
				current = current.getParentFile();
			}
			if (baseDirectory == null)
			{
				System.err.println("Warn: unable to find the base project directory from " + locations[0]);
			}
		}
	}
	
	
	@Override
	public IStatus perform(IPublisherInfo publisherInfo, IPublisherResult results, IProgressMonitor monitor) {
		try {
			//trick to access the publisherInfo object during the execution of generateFeatureIUs
			//p2 execution is synchronized so we are fine.
			__publisherInfo = publisherInfo;
			IStatus status = super.perform(publisherInfo, results, monitor);
			if (!status.isOK())
				return status;
		} finally {
			__publisherInfo = null;
		}
		return _statusOfGenerateIUs;
	}




	/**
	 * Invoke the FeatureRootAdviceFactory for the root IUs
	 */
	@Override
	public void generateFeatureIUs(Feature[] featureList, IPublisherResult result) {
		_statusOfGenerateIUs = generateFeatureRootAdvices(featureList, __publisherInfo, result);
		//__generateFeatureIUs(featureList, result);
		super.generateFeatureIUs(featureList, result);
	}
	
	private IStatus generateFeatureRootAdvices(Feature[] featureList, IPublisherInfo publisherInfo, IPublisherResult result) {
		if (baseDirectory == null || !baseDirectory.isDirectory())
		{
			return Status.OK_STATUS;
		}
		//somewhat support for the standard pde build with a single product and single build.properties file.
		File defaultBuildProperties = new File(baseDirectory, "build.properties");
		
		//todo: use a single feature...
		//right now we are selecting the first one...
		boolean isFirst = true;
		for (Feature feature : featureList) {
			File buildProperties = new File(baseDirectory, feature.getId() + ".build.properties");
			if (!buildProperties.canRead())
			{
				if (isFirst)
				{
					isFirst = false;
					if (defaultBuildProperties.canRead())
					{
						IStatus s = generateFeatureRootAdvice(feature, publisherInfo, result, buildProperties);
						if (!s.isOK())
						{
							return s;
						}
					}
				}
				continue;
			}
			else
			{
				IStatus s = generateFeatureRootAdvice(feature, publisherInfo, result, buildProperties);
				if (!s.isOK())
				{
					return s;
				}
			}
			isFirst = false;
		}
		return Status.OK_STATUS;
	}
	
	private IStatus generateFeatureRootAdvice(Feature feature, IPublisherInfo publisherInfo, IPublisherResult result, File buildProperties) {
		System.err.println("Generating the FeatureRootAdvice for " + feature.getId() + " defined in " + buildProperties.getAbsolutePath());
		InputStream input = null;
		Properties props = new Properties();
		try {
			input = new BufferedInputStream(new FileInputStream(buildProperties));
			props.load(input);
			IPublisherAdvice rootAdvice = FeatureRootAdviceFactory.createRootAdvice(
					feature.getId(), props, baseDirectory, publisherInfo.getConfigurations());
			if (rootAdvice != null)
				__publisherInfo.addAdvice(rootAdvice);
		} catch (IOException e) {
			return new Status(IStatus.ERROR, "", "Failure to create the root advice", e);
		} finally {
			if (input != null) try  { input.close(); } catch (IOException ioe) {}
		}
		return Status.OK_STATUS;
	}


//introspection trick to access the locations and features fields
	private static Field LOCATIONS_FIELD;
	public static final  File[] getLocationsFieldValue(FeaturesAction featuresAction) {
		try {
			if (LOCATIONS_FIELD == null)
			{
				LOCATIONS_FIELD = FeaturesAction.class.getDeclaredField("locations");
				LOCATIONS_FIELD.setAccessible(true);
			}
			return (File[]) LOCATIONS_FIELD.get(featuresAction);
		}
		catch (Throwable t)
		{
			throw new IllegalStateException("Unable to access the field 'locations' on FeaturesAction", t);
		}
	}
	private static Field FEATURES_FIELD;
	public static final  Feature[] getFeaturesFieldValue(FeaturesAction featuresAction) {
		try {
			if (FEATURES_FIELD == null)
			{
				FEATURES_FIELD = FeaturesAction.class.getDeclaredField("features");
				FEATURES_FIELD.setAccessible(true);
			}
			return (Feature[]) FEATURES_FIELD.get(featuresAction);
		}
		catch (Throwable t)
		{
			throw new IllegalStateException("Unable to access the field 'features' on FeaturesAction", t);
		}
	}
	
}
