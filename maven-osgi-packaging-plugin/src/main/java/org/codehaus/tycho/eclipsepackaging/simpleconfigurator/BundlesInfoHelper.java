package org.codehaus.tycho.eclipsepackaging.simpleconfigurator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.tycho.PluginDescription;
import org.codehaus.tycho.model.BundleConfiguration;

/**
 * Generates a bundles.info file.
 * Similar to what the PDEBuild does.
 * Ported from org.eclipse.equinox.internal.simpleconfigurator.manipulator.SimpleConfiguratorManipulatorUtils
 * 
 * TODO: reuse directly the simpleconfigurator.manipulator plugin.
 */
public class BundlesInfoHelper {
	
	private static final String VERSION_PREFIX = "#version=";
	private static final String VERSION_1 = "1";
	public static final String ENCODING_UTF8 = "#encoding=UTF-8";


	public static void writeBundlesInfo(File currentEclipse, Map<String, BundleConfiguration> bundlesToStart,
			Map<String, PluginDescription> bundles, File bundlesInfo, boolean osIsWin)
	throws MojoExecutionException, MojoFailureException
	{
		ArrayList<PluginDescription> list = new ArrayList<PluginDescription>(bundles.values());
		Collections.sort(list, new Comparator<PluginDescription>() {
			public int compare(PluginDescription o1, PluginDescription o2) {
				return ((PluginDescription) o1).getKey().getId().compareTo(((PluginDescription) o2).getKey().getId());
			}
		});
		BufferedWriter writer = null;
		try {
			if (!bundlesInfo.exists())
			{
				bundlesInfo.getParentFile().mkdirs();
				bundlesInfo.createNewFile();
			}
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(bundlesInfo), "UTF-8"));
			
			writer.write(ENCODING_UTF8);
			writeNewline(writer, osIsWin);
			writer.write(createVersionLine());
			writeNewline(writer, osIsWin);
			for (PluginDescription pd : list)
			{
				writer.write(createBundleInfoLine(currentEclipse, pd, bundlesToStart.get(pd.getKey().getId())));
				writeNewline(writer, osIsWin);
			}
		}
		catch (IOException e)
		{
			throw new MojoExecutionException("Unable to output the bundles.info file", e);
		}
		finally
		{
			if (writer != null) IOUtil.close(writer);
		}
	}
	
	private static void writeNewline(Writer writer, boolean osIsWin) throws IOException
	{
		writer.append(osIsWin ? "\n\r" : "\n");
	}

	public static String createVersionLine() {
		return VERSION_PREFIX + VERSION_1;
	}

	public static String createBundleInfoLine(File currentEclipse,
			PluginDescription bundleInfo, BundleConfiguration bundleConfig) {
		// symbolicName,version,location,startLevel,markedAsStarted
		StringBuffer buffer = new StringBuffer();
		buffer.append(bundleInfo.getKey().getId());
		buffer.append(',');
		buffer.append(bundleInfo.getKey().getVersion());
		buffer.append(',');
		
		String name = bundleInfo.getKey().getId() + "_" + bundleInfo.getKey().getVersion();
		File jarBundle = new File(currentEclipse, "plugins/" + name + ".jar");
		if (jarBundle.exists())
		{
			name = "plugins/" + name + ".jar";
		}
		else
		{
			jarBundle = new File(currentEclipse, "plugins/");
			if (jarBundle.exists())
			{
				name = "plugins/" + name + "/";
			}
			else
			{
				throw new IllegalArgumentException("Unable to find a jar or folder" +
						" for the plugin " + name + " inside the plugins " +
						"folder of " + currentEclipse.getAbsolutePath());
			}
		}
		buffer.append(name);
		
		buffer.append(',');
		buffer.append(bundleConfig != null 
					? bundleConfig.getStartLevel() : BundleConfiguration.NO_STARTLEVEL);
		buffer.append(',');
		buffer.append(bundleConfig != null ? bundleConfig.isAutoStart() : "false");
		return buffer.toString();
	}
	
}
