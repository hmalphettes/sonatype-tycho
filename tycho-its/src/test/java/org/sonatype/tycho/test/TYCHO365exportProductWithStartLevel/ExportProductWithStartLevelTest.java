package org.sonatype.tycho.test.TYCHO365exportProductWithStartLevel;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import junit.framework.Assert;

import org.apache.maven.it.Verifier;
import org.junit.Test;
import org.sonatype.tycho.test.AbstractTychoIntegrationTest;

public class ExportProductWithStartLevelTest extends AbstractTychoIntegrationTest {


	@Test
	public void exportPluginRcpApplication() throws Exception {
		Verifier verifier = getVerifier("/TYCHO365exportProductWithStartLevel/plugin-rcp");
		verifier.executeGoal("package");
		verifier.verifyErrorFreeLog();
		
		File configFile = new File(verifier.getBasedir(), "HeadlessProduct/target/linux.gtk.x86_64/eclipse/configuration/config.ini");
		Assert.assertTrue(configFile.canRead());
		Properties configIni = new Properties();
		FileInputStream fis = new FileInputStream(configFile);
		configIni.load(fis);
		fis.close();

		String osgiBundles = configIni.getProperty("osgi.bundles");
		Assert.assertEquals("HeadlessPlugin@7:start,org.eclipse.equinox.app@6,org.eclipse.equinox.launcher,org.eclipse.equinox.launcher.gtk.linux.x86_64", osgiBundles);
	}

	@Test
	public void exportFeatureRCPApplication() throws Exception {
		Verifier verifier = getVerifier("/TYCHO365exportProductWithStartLevel/feature-rcp");
		verifier.executeGoal("package");
		verifier.verifyErrorFreeLog();
		
		File configFile = new File(verifier.getBasedir(), "HeadlessProduct/target/linux.gtk.x86_64/eclipse/configuration/config.ini");
		Assert.assertTrue(configFile.canRead());
		Properties configIni = new Properties();
		FileInputStream fis = new FileInputStream(configFile);
		configIni.load(fis);
		fis.close();

		String osgiBundles = configIni.getProperty("osgi.bundles");
        Assert.assertEquals("HeadlessPlugin@7:start,org.eclipse.equinox.app@6,org.eclipse.equinox.launcher,org.eclipse.equinox.launcher.gtk.linux.x86_64", osgiBundles);
	}
}
