/*******************************************************************************
 *  Copyright (c) 2000, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *      IBM Corporation - initial API and implementation
 *******************************************************************************/
package forked.org.eclipse.pde.internal.build;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

/**
 * Copied and pasted from the pde build source code: the org.eclipse.pde.internal.build.Utils#processRootProperties
 * Inlined the necessary constants to not depend on the rest of the pde build.
 * <p>
 * This class supports the following format for the properties file:
 * http://help.eclipse.org/galileo/index.jsp?topic=/org.eclipse.pde.doc.user/tasks/pde_rootfiles.htm
 * </p>
 */
public class RootPropertiesHelper {

	// from org.eclipse.pde.internal.build.IXMLConstants
	// regex expressions and keys for parsing feature root properties
	private static final String REGEX_ROOT_CONFIG = "^root((\\.[\\w-\\*]+){3})$"; //$NON-NLS-1$
	private static final String REGEX_ROOT_CONFIG_FOLDER = "^root((\\.[\\w-\\*]+){3})?\\.folder\\.(.*)$"; //$NON-NLS-1$
	private static final String REGEX_ROOT_CONFIG_PERMISSIONS = "^root((\\.[\\w-\\*]+){3})?\\.permissions\\.(.*)$"; //$NON-NLS-1$
	private static final String REGEX_ROOT_CONFIG_LINK = "^root((\\.[\\w-\\*]+){3})?\\.link$"; //$NON-NLS-1$
	public static final String ROOT_PERMISSIONS = "!!ROOT.PERMISSIONS!!"; //$NON-NLS-1$
	public static final String ROOT_LINK = "!!ROOT.LINK!!"; //$NON-NLS-1$
	public static final String ROOT_COMMON = "!!COMMON!!"; //$NON-NLS-1$

	//from org.eclipse.pde.internal.build.IBuildPropertiesConstants
	public final static String PERMISSIONS = "permissions"; //$NON-NLS-1$
	public final static String LINK = "link"; //$NON-NLS-1$
	public final static String EXECUTABLE = "executable"; //$NON-NLS-1$
	public final static String ROOT_PREFIX = "root."; //$NON-NLS-1$
	public final static String ROOT = "root"; //$NON-NLS-1$
	public final static String ROOT_FOLDER_PREFIX = ROOT_PREFIX + "folder."; //$NON-NLS-1$
	public final static String FOLDER_INFIX = ".folder."; //$NON-NLS-1$
	public final static String PERMISSIONS_INFIX = ".permissions."; //$NON-NLS-1$
	public final static String LINK_SUFFIX = ".link"; //$NON-NLS-1$

	//from org.eclipse.pde.internal.build.Utils
	/**
	 * Process root file properties.  
	 * Resulting map is from config string to a property map.  The format of the property map is:
	 * 1) folder -> fileset to copy.  folder can be "" (the root) or an actual folder
	 * 2) ROOT_PERMISSIONS + rights -> fileset to set rights for
	 * 3) ROOT_LINK -> comma separated list: (target, link)*
	 * 
	 * Properties that are common across all configs are available under the ROOT_COMMON key.
	 * They are also optionally merged into each individual config.

	 * @param properties - build.properties for a feature
	 * @param mergeCommon - whether or not to merge the common properties into each config
	 * @return Map
	 */
	static public Map processRootProperties(Properties properties, boolean mergeCommon) {
		Map map = new HashMap();
		Map common = new HashMap();
		for (Enumeration keys = properties.keys(); keys.hasMoreElements();) {
			String entry = (String) keys.nextElement();
			String config = null;
			String entryKey = null;

			if (entry.equals(ROOT) || entry.matches(REGEX_ROOT_CONFIG)) {
				config = entry.length() > 4 ? entry.substring(5) : ""; //$NON-NLS-1$
				entryKey = ""; //$NON-NLS-1$
			} else if (entry.matches(REGEX_ROOT_CONFIG_FOLDER)) {
				int folderIdx = entry.indexOf(FOLDER_INFIX);
				config = (folderIdx > 5) ? entry.substring(5, folderIdx) : ""; //$NON-NLS-1$
				entryKey = entry.substring(folderIdx + 8);
			} else if (entry.matches(REGEX_ROOT_CONFIG_PERMISSIONS)) {
				int permissionIdx = entry.indexOf(PERMISSIONS_INFIX);
				config = (permissionIdx > 5) ? entry.substring(5, permissionIdx) : ""; //$NON-NLS-1$
				entryKey = ROOT_PERMISSIONS + entry.substring(permissionIdx + 13);
			} else if (entry.matches(REGEX_ROOT_CONFIG_LINK)) {
				int linkIdx = entry.indexOf(LINK_SUFFIX);
				config = (linkIdx > 5) ? entry.substring(5, linkIdx) : ""; //$NON-NLS-1$
				entryKey = ROOT_LINK;
			}

			if (config != null) {
				Map submap = (config.length() == 0) ? common : (Map) map.get(config);
				if (submap == null) {
					submap = new HashMap();
					map.put(config, submap);
				}
				if (submap.containsKey(entryKey)) {
					String existing = (String) submap.get(entryKey);
					submap.put(entryKey, existing + "," + properties.getProperty(entry)); //$NON-NLS-1$
				} else {
					submap.put(entryKey, properties.get(entry));
				}
			}
		}

		//merge the common properties into each of the configs
		if (common.size() > 0 && mergeCommon) {
			for (Iterator iterator = map.keySet().iterator(); iterator.hasNext();) {
				String key = (String) iterator.next();
				Map submap = (Map) map.get(key);
				for (Iterator commonKeys = common.keySet().iterator(); commonKeys.hasNext();) {
					String commonKey = (String) commonKeys.next();
					if (submap.containsKey(commonKey)) {
						String existing = (String) submap.get(commonKey);
						submap.put(commonKey, existing + "," + common.get(commonKey)); //$NON-NLS-1$
					} else {
						submap.put(commonKey, common.get(commonKey));
					}
				}
			}
		}

		//and also add the common properties independently
		if (mergeCommon || common.size() > 0)
			map.put(ROOT_COMMON, common);
		return map;
	}

	
	
}
