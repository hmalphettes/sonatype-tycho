package org.sonatype.tycho.test.mngeclipse1031;

import static org.sonatype.tycho.test.util.EnvironmentUtil.isEclipse32Platform;

import java.io.File;

import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.Test;
import org.sonatype.tycho.test.AbstractTychoIntegrationTest;

public class Junit4TestBundleTest extends AbstractTychoIntegrationTest {
	
	@Test
	public void test() throws Exception {

		if(isEclipse32Platform()) {
			// there is no junit4 support in eclipse 3.2
			return;
		}

        Verifier verifier = getVerifier("MNGECLIPSE1031/bundle.test");

        verifier.executeGoal("integration-test");
        verifier.verifyErrorFreeLog();
		
        File testReport = new File(verifier.getBasedir(), "target/surefire-reports/TEST-bundle.test.BundleTest.xml");
        Assert.assertTrue(testReport.exists());
	}

}
