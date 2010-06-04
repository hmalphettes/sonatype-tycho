/*******************************************************************************
 *  Copyright (c) 2008, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *      IBM Corporation - initial API and implementation
 *******************************************************************************/
package forked.org.eclipse.buckminster.pde.tasks;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.PatternSet.NameEntry;
import org.apache.tools.ant.types.selectors.FilenameSelector;
import org.apache.tools.ant.types.selectors.OrSelector;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.equinox.internal.p2.core.helpers.StringHelper;
import org.eclipse.equinox.internal.p2.publisher.FileSetDescriptor;

import forked.org.eclipse.buckminster.pde.tasks.FeatureRootAdvice.ConfigAdvice;
import forked.org.eclipse.pde.internal.build.RootPropertiesHelper;

/**
 * <p>
 * Notes by hmalphettes:
 * Static methods copied from buckminster's code.
 * From the org.eclipse.buckminster.pde.tasks.FeaturesAction class:
 * http://dev.eclipse.org/viewsvn/index.cgi/trunk/org.eclipse.buckminster.pde/src/java/org/eclipse/buckminster/pde/tasks/FeaturesAction.java?root=Tools_BUCKMINSTER&view=markup
 * <br/>
 * Using {@link RootPropertiesHelper} 
 * <br/>
 * It would be nice to redo the directory scanner outside of ant.
 * Currently this little piece of code is the only reason why we need ant.
 * </p>
 */
public class FeatureRootAdviceFactory {
	
	private static final Project PROPERTY_REPLACER = new Project();

	public static FeatureRootAdvice createRootAdvice(String featureId, Properties buildProperties, File baseDirectory, String[] configs) {
		@SuppressWarnings("unchecked")
		Map<String, Map<String, String>> configMap = RootPropertiesHelper.processRootProperties(buildProperties, true);
		if (configMap.size() == 1) {
			Map<String, String> entry = configMap.get(RootPropertiesHelper.ROOT_COMMON);
			if (entry != null && entry.isEmpty())
				return null;
		}

		FeatureRootAdvice advice = new FeatureRootAdvice(featureId);
		for (Map.Entry<String, Map<String, String>> entry : configMap.entrySet()) {
			String config = entry.getKey();
			Map<String, String> rootMap = entry.getValue();
			populateConfigAdvice(advice, config, rootMap, baseDirectory, configs);
		}
		return advice;
	}
	
	private static void populateConfigAdvice(FeatureRootAdvice advice, String config, Map<String, String> rootMap, File baseDirectory,
			String[] configs) {
		if (config.equals(RootPropertiesHelper.ROOT_COMMON))
			config = ""; //$NON-NLS-1$
		else {
			config = reorderConfig(config);
			int idx = configs.length;
			while (--idx >= 0)
				if (config.equals(configs[idx]))
					break;

			if (idx < 0) {
				System.err.println("No FeatureRootAdvice for " + config + ": was not on the list");
				// Config was not on the list
				return;
			}
		}

		ConfigAdvice configAdvice = advice.getConfigAdvice(config);
		FileSetDescriptor descriptor = configAdvice.getDescriptor();
		List<String> permissionsKeys = new ArrayList<String>();
		for (Map.Entry<String, String> rootEntry : rootMap.entrySet()) {
			String key = rootEntry.getKey();
			if (key.equals(RootPropertiesHelper.ROOT_LINK)) {
				descriptor.setLinks(rootEntry.getValue());
				continue;
			}

			if (key.startsWith(RootPropertiesHelper.ROOT_PERMISSIONS)) {
				permissionsKeys.add(key);
				continue;
			}

			for (String rootValue : StringHelper.getArrayFromString(rootEntry.getValue(), ',')) {
				String rootName = rootValue;
				boolean isAbsolute = rootName.startsWith("absolute:"); //$NON-NLS-1$
				if (isAbsolute)
					rootName = rootName.substring(9);

				boolean isFile = rootName.startsWith("file:"); //$NON-NLS-1$
				if (isFile)
					rootName = rootName.substring(5);

				if (rootName.length() == 0)
					continue;

				IPath basePath;
				String pattern;

				// Base path cannot contain wild card characters
				IPath rootPath = Path.fromPortableString(rootName);
				int firstStar = -1;
				int numSegs = rootPath.segmentCount();
				for (int idx = 0; idx < numSegs; ++idx)
					if (rootPath.segment(idx).indexOf('*') >= 0) {
						firstStar = idx;
						break;
					}

				if (firstStar == -1) {
					if (isFile) {
						pattern = rootPath.lastSegment();
						basePath = rootPath.removeLastSegments(1);
					} else {
						pattern = "**"; //$NON-NLS-1$
						basePath = rootPath;
					}
				} else {
					basePath = rootPath.removeLastSegments(rootPath.segmentCount() - (firstStar + 1));
					pattern = rootPath.removeFirstSegments(firstStar).toPortableString();
				}

				if (!isAbsolute) {
					try {
						basePath = new Path(baseDirectory.getCanonicalPath()).append(basePath.makeRelative());
					} catch (IOException e) {
						basePath = new Path(baseDirectory.getAbsolutePath()).append(basePath.makeRelative());
					}
				}
				FileSet fileset = new FileSet();
				fileset.setProject(PROPERTY_REPLACER);
				fileset.setErrorOnMissingDir(false);
				File base = basePath.toFile();
				fileset.setDir(base);
				NameEntry include = fileset.createInclude();
				include.setName(pattern);
				System.err.println("Scanning inside " + basePath.toString() + " for " + pattern + " for the config '" + config + "'.");
				String[] files = fileset.getDirectoryScanner().getIncludedFiles();
				if (files.length == 0) {
					System.err.println("The build.properties point to a root file that does not exist: " + config + " " + baseDirectory.getAbsolutePath());
//					PDEPlugin.getLogger().warning(
//							NLS.bind(Messages.rootAdviceForConfig_0_in_1_at_2_does_not_appoint_existing_artifacts, new Object[] { config,
//									IPDEBuildConstants.PROPERTIES_FILE, baseDirectory.toOSString() }));
					continue;
				}

				IPath destBaseDir = Path.fromPortableString(key);
				for (String found : files) {
					IPath foundFile = Path.fromPortableString(found);
					String destDir = destBaseDir.append(foundFile.removeLastSegments(1)).toPortableString();
//System.err.println("Found " + foundFile.toString() + " and placing it in " + destBaseDir.toString());
					configAdvice.addRootfile(new File(base, found), destDir);
				}
			}
		}

		for (String permissionKey : permissionsKeys) {
			String permissionString = rootMap.get(permissionKey);
			String[] names = StringHelper.getArrayFromString(permissionString, ',');
//do we need this?
//this looks like it applies only for the executables.
			OrSelector orSelector = new OrSelector();
			orSelector.setProject(PROPERTY_REPLACER);
			for (String name : names) {
				// Workaround for bogus entries in the equinox executable
				// feature
				if ("${launcherName}.app/Contents/MacOS/${launcherName}".equals(name)) //$NON-NLS-1$
					name = "Eclipse.app/Contents/MacOS/launcher"; //$NON-NLS-1$

				FilenameSelector nameSelector = new FilenameSelector();
				nameSelector.setProject(PROPERTY_REPLACER);
				nameSelector.setName(name);
				orSelector.addFilename(nameSelector);
			}

			permissionKey = permissionKey.substring(RootPropertiesHelper.ROOT_PERMISSIONS.length());
			for (File file : configAdvice.getFiles()) {
				IPath finalFilePath = configAdvice.computePath(file);
//do we need this?
				if (orSelector.isSelected(null, finalFilePath.toOSString(), null))
					descriptor.addPermissions(new String[] { permissionKey, finalFilePath.toPortableString() });
			}
		}
	}

	private static String reorderConfig(String config) {
		String[] parsed = StringHelper.getArrayFromString(config, '.');
		return parsed[1] + '.' + parsed[0] + '.' + parsed[2];
	}

	
}
