package org.codehaus.tycho.plugins.p2.publisher;

import java.io.File;
import java.util.List;
import java.util.zip.ZipFile;

import junit.framework.Assert;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.tycho.eclipsepackaging.PackageUpdateSiteMojo;
import org.codehaus.tycho.testing.AbstractTychoMojoTestCase;
import org.sonatype.tycho.osgi.EquinoxEmbedder;
import org.sonatype.tycho.plugins.p2.director.DirectorInstallProductsMojo;
import org.sonatype.tycho.plugins.p2.publisher.ArchiveRepositoryMojo;
import org.sonatype.tycho.plugins.p2.publisher.FeaturesAndBundlesAssembler;
import org.sonatype.tycho.plugins.p2.publisher.PrepareFeaturesAndBundlesMojo;
import org.sonatype.tycho.plugins.p2.publisher.PublishRepositoryMojo;
import org.sonatype.tycho.plugins.p2.tools.FixCheckSumMojo;
import org.sonatype.tycho.plugins.p2.tools.PackAndSignMojo;

public abstract class AbstractP2MojoTestCase
    extends AbstractTychoMojoTestCase
{

	protected MavenProject project;
	protected MavenSession session;

	protected PrepareFeaturesAndBundlesMojo assemblemojo;
	protected PublishRepositoryMojo publishmojo;
	protected PackAndSignMojo packAndSignMojo;
	protected FixCheckSumMojo fixCheckSumMojo;
	protected ArchiveRepositoryMojo archiveRepositoryMojo;
	protected DirectorInstallProductsMojo archiveProductsMojo;

	protected File targetFolder;
	protected File targetRepositoryFolder;

    protected void setUp(String basedDirArgument)
        throws Exception
    {
        super.setUp();

        File basedir = getBasedir(basedDirArgument);
        File platform = new File( "src/test/resources/eclipse" );

        List<MavenProject> projects = getSortedProjects( basedir, platform );

        project = projects.get( 0 );
        session = newMavenSession(project, projects);
        targetFolder = new File( project.getFile().getParent(), "target" );
        targetRepositoryFolder =  new File(targetFolder, "repository");

        /* plexus:
           	  <process-classes>
                org.sonatype.tycho:tycho-p2-publisher-director:${project.version}:prepare-features-and-bundles,
                org.sonatype.tycho:tycho-p2-publisher-director:${project.version}:publish-repository
              </process-classes>
              <package>
                org.sonatype.tycho:tycho-p2-publisher-director:${project.version}:pack-and-sign,
                org.sonatype.tycho:tycho-p2-publisher-director:${project.version}:fix-checksums,
                org.sonatype.tycho:tycho-p2-publisher-director:${project.version}:archive-repository,
                org.sonatype.tycho:tycho-p2-publisher-director:${project.version}:archive-products
              </package>
         */
        assemblemojo = (PrepareFeaturesAndBundlesMojo) lookupMojo( "prepare-features-and-bundles", project.getFile() );
        publishmojo = (PublishRepositoryMojo) lookupMojo( "publish-repository", project.getFile() );
        
        packAndSignMojo = (PackAndSignMojo) lookupMojo( "pack-and-sign", project.getFile() );
        fixCheckSumMojo = (FixCheckSumMojo) lookupMojo( "fix-checksums", project.getFile() );
        archiveRepositoryMojo = (ArchiveRepositoryMojo) lookupMojo( "archive-repository", project.getFile() );
        archiveProductsMojo = (DirectorInstallProductsMojo) lookupMojo( "archive-products", project.getFile() );
        
        setVariableValueToMojos( "project", project );
        setVariableValueToMojos( "session", session );
        setVariableValueToMojos( "targetRepository", targetRepositoryFolder);
        
        //compress is true by default.
        setVariableValueToObject(publishmojo, "compress", Boolean.TRUE);
        
        //look for the tycho runtime built in the project:
        File tychoPublisherDirector = new File(getBasedir());//project.getFile().getParentFile();
        
        File tychoRuntime = new File(tychoPublisherDirector.getParentFile(), "tycho-p2-runtime/target/product/eclipse");
        if (!tychoRuntime.exists())
        {
        	Assert.fail("Unable to locate the built tycho runtime in " + tychoRuntime.getAbsolutePath());
        }
        System.setProperty("equinox-runtimeLocation" /* org.sonatype.tycho.osgi.DefaultEquinoxEmbedder#SYSPROP_EQUINOX_RUNTIMELOCATION */,
        		tychoRuntime.getAbsolutePath());
    }
    
    private void setVariableValueToMojos(String param, Object value) throws Exception {
    	setVariableValueToObject( assemblemojo, param, value );
    	setVariableValueToObject( publishmojo, param, value );
    	setVariableValueToObject( packAndSignMojo, param, value );
    	setVariableValueToObject( fixCheckSumMojo, param, value );
    	setVariableValueToObject( archiveProductsMojo, param, value );
    }

}
