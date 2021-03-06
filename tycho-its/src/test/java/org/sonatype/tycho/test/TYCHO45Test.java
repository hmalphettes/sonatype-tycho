package org.sonatype.tycho.test;

import org.apache.maven.it.Verifier;
import org.junit.Test;

public class TYCHO45Test extends AbstractTychoIntegrationTest {

	@Test
	public void test() throws Exception {
		String tychoVersion = getTychoVersion();

		Verifier verifier = getVerifier("TYCHO45");

        // generate poms
        verifier.getCliOptions().add("-DgroupId=tycho45");
        verifier.getCliOptions().add("-DtestSuite=tests.suite");
        verifier.setAutoclean( false );
        verifier.setLogFileName( "log-init.txt" );
        verifier.executeGoal( "org.sonatype.tycho:maven-tycho-plugin:" + tychoVersion + ":generate-poms" );
        verifier.verifyErrorFreeLog();

        // run the build
        verifier.getCliOptions().add("-DtestClass=tests.suite.AllTests");
        verifier.setLogFileName( "log-test.txt" );
        verifier.executeGoal("integration-test");
        verifier.verifyErrorFreeLog();
	}
}
