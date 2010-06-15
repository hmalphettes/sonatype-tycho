package org.codehaus.tycho.buildnumber.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.tycho.eclipsepackaging.PackagePluginMojo;
import org.codehaus.tycho.testing.AbstractTychoMojoTestCase;

public class PackagePluginMojoTest extends AbstractTychoMojoTestCase {

	public void testBinIncludesNoDot() throws Exception {
		File basedir = getBasedir("projects/binIncludesNoDot");
		basedir = new File(basedir, "p001");
		
		PackagePluginMojo mojo = execMaven(basedir);
		createDummyClassFile(basedir);
		mojo.execute();
		JarFile pluginJar = new JarFile(new File(basedir,
				"target/test.jar"));
		try {
			assertNull(
					"class files from target/classes must not be included in plugin jar if no '.' in bin.includes",
					pluginJar.getEntry("TestNoDot.class"));
		} finally {
			pluginJar.close();
		}
	}
	
	public void testOutputClassesInANestedFolder() throws Exception {
		File basedir = getBasedir("projects/outputClassesInANestedFolder");
		//Copy the hello.properties to simulate the compiler and resource mojos
		File classes = new File(basedir, "target/classes/");
		classes.mkdirs();
		FileUtils.copyFileToDirectory(new File(basedir, "src/main/resources/hello.properties"), classes);
		System.err.println(classes.getAbsolutePath());
		PackagePluginMojo mojo = execMaven(basedir);
		createDummyClassFile(basedir);
		mojo.execute();
		JarFile pluginJar = new JarFile(new File(basedir,
				"target/test.jar"));
		try {
			//make sure we can find the WEB-INF/classes/hello.properties
			//and no hello.properties.
			assertNotNull(pluginJar.getEntry("WEB-INF/classes/hello.properties"));
			assertNull(pluginJar.getEntry("hello.properties"));
		} finally {
			pluginJar.close();
		}
	}

	public void testBinIncludesSpaces() throws Exception {
        File basedir = getBasedir( "projects/binIncludesSpaces" );
        File classes = new File( basedir, "target/classes" );
        classes.mkdirs();
        FileUtils.fileWrite( new File( classes, "foo.bar" ).getCanonicalPath(), "foobar" );
        PackagePluginMojo mojo = execMaven( basedir );
        mojo.execute();

        JarFile pluginJar = new JarFile( new File( basedir, "target/test.jar" ) );
        try
        {
            assertNotNull( pluginJar.getEntry( "foo.bar" ) );
        }
        finally
        {
            pluginJar.close();
        }
	}

	public void testNoManifestVersion() throws Exception {
        File basedir = getBasedir( "projects/noManifestVersion" );
        PackagePluginMojo mojo = execMaven(basedir);
        mojo.execute();

        Manifest mf;
        InputStream is = new FileInputStream( new File( basedir, "target/MANIFEST.MF" ) );
        try
        {
            mf = new Manifest( is );
        }
        finally
        {
            IOUtil.close( is );
        }

        String symbolicName = mf.getMainAttributes().getValue( "Bundle-SymbolicName" );
        
        assertEquals( "bundle;singleton:=true", symbolicName );
	}

	private PackagePluginMojo execMaven(File basedir) throws Exception {
		File pom = new File(basedir, "pom.xml");
		MavenExecutionRequest request = newMavenExecutionRequest(pom);
		request.getProjectBuildingRequest().setProcessPlugins(false);
		MavenExecutionResult result = maven.execute(request);
		MavenProject project = result.getProject();
		ArrayList<MavenProject> projects = new ArrayList<MavenProject>();
		projects.add(project);
		MavenSession session = new MavenSession(getContainer(), request,
				result, projects);
		PackagePluginMojo mojo = getMojo(project, session);
		return mojo;
	}

	private void createDummyClassFile(File basedir) throws IOException {
		File classFile = new File(basedir,
				"target/classes/TestNoDot.class");
		classFile.getParentFile().mkdirs();
		classFile.createNewFile();
	}

	private PackagePluginMojo getMojo(MavenProject project, MavenSession session)
			throws Exception {
		PackagePluginMojo mojo = (PackagePluginMojo) lookupMojo(
				"package-plugin", project.getFile());
		setVariableValueToObject(mojo, "project", project);
		setVariableValueToObject(mojo, "session", session);
		return mojo;
	}

}
