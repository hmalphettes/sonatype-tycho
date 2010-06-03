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

package org.eclipse.buckminster.pde.tasks;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.equinox.internal.p2.core.helpers.FileUtils.IPathComputer;
import org.eclipse.equinox.internal.p2.publisher.FileSetDescriptor;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.publisher.AbstractAdvice;
import org.eclipse.equinox.p2.publisher.actions.IFeatureRootAdvice;

@SuppressWarnings("restriction")
public class FeatureRootAdvice extends AbstractAdvice implements IFeatureRootAdvice {
	
	private static final String Config_ANY = "*";
	
	public static class ConfigAdvice implements IPathComputer {
		private Map<File, String> files;

		private final FileSetDescriptor descriptor;

		ConfigAdvice(String config) {
			descriptor = (config.length() == 0 || config.equals(Config_ANY)) ? new FileSetDescriptor("root", null) //$NON-NLS-1$
					: new FileSetDescriptor("root." + config, config); //$NON-NLS-1$
		}

		public void addRootfile(File sourceFile, String destDir) {
			if (files == null)
				files = new HashMap<File, String>();
			files.put(sourceFile, destDir);
		}

		public IPath computePath(File source) {
			if (files != null) {
				String destDir = files.get(source);
				if (destDir != null)
					return Path.fromPortableString(destDir).append(source.getName());
			}
			return null;
		}

		public FileSetDescriptor getDescriptor() {
			return descriptor;
		}

		public File[] getFiles() {
			if (files != null) {
				Set<File> fileSet = files.keySet();
				return fileSet.toArray(new File[fileSet.size()]);
			}
			return noFiles;
		}

		public void reset() {
		}
	}

	private static final File[] noFiles = new File[0];

	private final Map<String, ConfigAdvice> advice = new HashMap<String, ConfigAdvice>();

	private final String featureId;

	public FeatureRootAdvice(String featureId) {
		this.featureId = featureId;
	}

	public ConfigAdvice getConfigAdvice(String config) {
		ConfigAdvice configAdvice = advice.get(config);
		if (configAdvice == null) {
			configAdvice = new ConfigAdvice(config);
			advice.put(config, configAdvice);
		}
		return configAdvice;
	}

	public String[] getConfigs() {
		return advice.keySet().toArray(new String[advice.size()]);
	}
	
	public boolean isApplicable(String configSpec, boolean includeDefault, String id, Version version) {
		return (configSpec == null || advice.containsKey(configSpec)) && (id == null || featureId.equals(id));
	}

	public String[] getConfigurations() {
		return getConfigs();
	}

	public IPathComputer getRootFileComputer(String configSpec) {
		return getConfigAdvice(configSpec);
	}

	public FileSetDescriptor getDescriptor(String configSpec) {
		ConfigAdvice configAdvice = getConfigAdvice(configSpec);
		if (configAdvice != null)
		{
			if (configAdvice.getDescriptor().getFiles() == null
					|| configAdvice.getDescriptor().size() == 0)
			{
				configAdvice.getDescriptor().addFiles(configAdvice.getFiles());
			}
		}
		return configAdvice != null ? configAdvice.getDescriptor() : null;
	}
}