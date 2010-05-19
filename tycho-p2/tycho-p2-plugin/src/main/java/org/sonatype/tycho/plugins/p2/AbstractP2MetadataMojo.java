package org.sonatype.tycho.plugins.p2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.sonatype.tycho.osgi.EquinoxEmbedder;

public abstract class AbstractP2MetadataMojo
    extends AbstractMojo
{
    /**
     * @parameter expression="${project}"
     * @required
     */
    protected MavenProject project;

    /**
     * Metadata repository name
     * 
     * @parameter default-value="${project.name}"
     * @required
     */
    protected String metadataRepositoryName;

    /**
     * Generated update site location (must match update-site mojo configuration)
     * 
     * @parameter expression="${project.build.directory}/site"
     */
    private File target;

    /**
     * Artifact repository name
     * 
     * @parameter default-value="${project.name} Artifacts"
     * @required
     */
    protected String artifactRepositoryName;

    /**
     * Kill the forked test process after a certain number of seconds. If set to 0, wait forever for the process, never
     * timing out.
     * 
     * @parameter expression="${p2.timeout}"
     */
    private int forkedProcessTimeoutInSeconds;

    /**
     * Arbitrary JVM options to set on the command line.
     * 
     * @parameter
     */
    protected String argLine;

    /** 
     * @parameter default-value="true" 
     */
    protected boolean generateP2Metadata;
    
    /**
     * Enable outputting the logs of the p2 apps on the console.
     * 
     * @parameter expression="${p2.consoleLog}" default-value="false" 
     */
    private boolean p2ConsoleLog;

    /** @component */
    private EquinoxEmbedder p2;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( !generateP2Metadata )
        {
            return;
        }

        try
        {
            if ( getUpdateSiteLocation().isDirectory() )
            {
                generateMetadata();
            }
            else
            {
                getLog().warn( getUpdateSiteLocation().getAbsolutePath() + " does not exist or is not a directory" );
            }
        }
        catch ( MojoFailureException e )
        {
            throw e;
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Cannot generate P2 metadata", e );
        }
    }

    private void generateMetadata()
        throws Exception
    {   
        Commandline cli = new Commandline();

        cli.setWorkingDirectory( project.getBasedir() );

        String executable = System.getProperty( "java.home" ) + File.separator + "bin" + File.separator + "java";
        if ( File.separatorChar == '\\' )
        {
            executable = executable + ".exe";
        }
        cli.setExecutable( executable );

        cli.addArguments( new String[] { "-jar", getEquinoxLauncher().getCanonicalPath(), } );
        
        if ( p2ConsoleLog )
        {
        	cli.addArguments( new String[] { "-consoleLog" } );
        }
        cli.addArguments( getDefaultPublisherArguments() );
                
        String[] otherArgs = getOtherPublisherArguments();
        if (otherArgs != null && otherArgs.length > 0)
        {
        	cli.addArguments(otherArgs);
        }
        
        String[] downloadStats = getDownloadStatsPublisherArguments();
        if (downloadStats != null) {
        	cli.addArguments(downloadStats);
        }
        
        
        //last argument is traditionally for the vm:
        String vmArg = internalGetVmArgLine();
        if (vmArg != null && vmArg.length() != 0)
        {
        	cli.addArguments(new String[] {"-vmargs", internalGetVmArgLine()});
        }
        
        getLog().info( "Command line:\n\t" + cli.toString() );

        StreamConsumer out = new StreamConsumer()
        {
            public void consumeLine( String line )
            {
                System.out.println( line );
            }
        };

        StreamConsumer err = new StreamConsumer()
        {
            public void consumeLine( String line )
            {
                System.err.println( line );
            }
        };

        int result = CommandLineUtils.executeCommandLine( cli, out, err, forkedProcessTimeoutInSeconds );
        if ( result != 0 )
        {
            throw new MojoFailureException( "P2 publisher return code was " + result );
        }
    }
    
    /**
     * @return The vm arg line passed to the publisher app.
     */
    protected String internalGetVmArgLine()
    {
    	return argLine;
    }
    
    protected String[] getDefaultPublisherArguments() throws IOException
    {
    	 return new String[] { "-nosplash", // 
    	            "-application", getPublisherApplication(), // 
    	            "-source", getSourceLocation().getCanonicalPath(), //
    	            "-metadataRepository", getUpdateSiteLocation().toURL().toExternalForm(), //
    	            "-metadataRepositoryName", metadataRepositoryName, //
    	            "-artifactRepository", getUpdateSiteLocation().toURL().toExternalForm(), //
    	            "-artifactRepositoryName", artifactRepositoryName, //
    	            "-noDefaultIUs", //
//    	    		"-console", "-consolelog"
    	            };
    }
    
    /**
     * If the publisher application supports it these arguments will generate
     * the hooks for the eclipse download stats.
     * See http://wiki.eclipse.org/Equinox_p2_download_stats
     * and https://bugs.eclipse.org/bugs/show_bug.cgi?id=302160
     * 
     * @return The download stats arguments
     */
    protected String[] getDownloadStatsPublisherArguments() {
    	String statsURI = (String) project.getProperties().get("tycho.publisher.with.statsUri");
    	String statsTrackedBundles = (String) project.getProperties().get("tycho.publisher.with.statsTrackedBundleIDs");
    	if (statsURI != null && statsTrackedBundles != null)
    	{
    		return new String[] { "-p2.statsURI", statsURI, "-p2.statsTrackedBundles", statsTrackedBundles };
    	}
    	return null;
    }
    
    /**
     * By default returns null.
     * @return some more arguments added to the command line to invoke the publisher.
     * For example the product needs to be passed the config argument.
     */
    protected String[] getOtherPublisherArguments()
    {
    	return null;
    }

    protected abstract String getPublisherApplication();

    protected File getUpdateSiteLocation()
    {
        return target;
    }
    
    protected File getSourceLocation() {
    	return target;
    }

    private File getEquinoxLauncher()
        throws MojoFailureException
    {
        // XXX dirty hack
        File p2location = p2.getRuntimeLocation();
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir( p2location );
        ds.setIncludes( new String[] { "plugins/org.eclipse.equinox.launcher_*.jar" } );
        ds.scan();
        String[] includedFiles = ds.getIncludedFiles();
        if ( includedFiles == null || includedFiles.length != 1 )
        {
            throw new MojoFailureException( "Can't locate org.eclipse.equinox.launcher bundle in " + p2location );
        }
        return new File( p2location, includedFiles[0] );
    }

}
