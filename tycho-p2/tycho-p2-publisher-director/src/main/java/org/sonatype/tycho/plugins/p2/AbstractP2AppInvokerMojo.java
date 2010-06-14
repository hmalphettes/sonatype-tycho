package org.sonatype.tycho.plugins.p2;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.sonatype.tycho.osgi.DefaultEquinoxEmbedder;
import org.sonatype.tycho.osgi.EquinoxEmbedder;

/**
 * Common objects to be able to invoke a p2 application on the command line.
 */
public abstract class AbstractP2AppInvokerMojo extends AbstractP2Mojo {

	/**
	 * Set to true to enable the packing.
	 * @parameter default-value="false"
	 */
	protected boolean enablePackAndSign;

	
    /** @component */
    protected EquinoxEmbedder p2;
    
    /**
     * Kill the forked test process after a certain number of seconds. If set to 0, wait forever for the process, never
     * timing out.
     * 
     * @parameter expression="${p2.timeout}"
     */
    protected int forkedProcessTimeoutInSeconds;
    
    /**
     * Set to false to invoke the tycho runtime on the command line.
     * By default true to invoke tycho's P2 embedder instead of starting a new process
     * on the command-line.
     * <p>
     * For now: false, it does not work yet.
     * </p>
     * @parameter default-value="false"
     */
    protected boolean invokeThroughEmbedder;

	/**
	 * @return The -consoleLog to enable the log or an empty string if not configured
	 * to do so.
	 */
    protected String[] getConsoleLogFlag()
    {
    	return new String[] {"-consoleLog"};
    }
    
	/**
	 * @return The -application to enable the log or an empty string if not configured
	 * to do so.
	 */
    protected String[] getApplicationParameter(String application)
    {
    	return new String[] {/*invokeThroughEmbedder ? "-Declipse.appplication" : */"-application", application};
    }
    
    /**
     * Returns a command line where the equinox launcher is set and the usual parameters for
     * invoking a eclipse application in headless mode are set.
     * @param application
     * @return
     * @throws MojoFailureException
     * @throws IOException
     */
    protected Commandline getCommandLine(String application)
    throws MojoFailureException, IOException {
        Commandline cli = new Commandline();

        cli.setWorkingDirectory( project.getBasedir() );

        String executable = System.getProperty( "java.home" ) + File.separator + "bin" + File.separator + "java";
        if ( File.separatorChar == '\\' )
        {
            executable = executable + ".exe";
        }
        cli.setExecutable( executable );
        if (!invokeThroughEmbedder)
        {
        	cli.addArguments( new String[] { "-jar", getEquinoxLauncher().getCanonicalPath(), } );
        }
        cli.addArguments(getApplicationParameter(application));
        cli.addArguments(new String[] {"-nosplash"});
        cli.addArguments(getConsoleLogFlag());
        
        return cli;
	}

    /**
     * Execute 
     * @param cli
     * @param vmArgs
     * @throws MojoFailureException
     * @throws MojoExecutionException
     */
    protected void execute(Commandline cli, String vmArgs)
    throws MojoFailureException, MojoExecutionException {
    	
    	if (!invokeThroughEmbedder)
    	{
	        //last argument is traditionally for the vm:
	        if (vmArgs != null && vmArgs.length() != 0)
	        {
	        	cli.addArguments(new String[] {"-vmargs", vmArgs});
	        }
    	}
        
    	if (!invokeThroughEmbedder)
    	{
    		getLog().info( "Command line:\n\t" + cli.toString() );
    	}
    	else
    	{
    		StringBuilder sb = new StringBuilder();
    		for (String arg : cli.getArguments())
    		{
    			sb.append(" ");
    			sb.append(arg);
    		}
    		getLog().info( "Equinox Embedder arguments:\n\t" + sb.toString() );
    	}
        if (invokeThroughEmbedder)
        {
        	try
        	{
        		((DefaultEquinoxEmbedder)p2).setNonFrameworkArgs(cli.getArguments());
				((DefaultEquinoxEmbedder)p2).start();
			}
        	catch (Exception e)
        	{
				throw new MojoExecutionException( "P2 publisher failed to be executed ", e );
			}
        	return;
        }
        
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

        try
        {
	        int result = CommandLineUtils.executeCommandLine( cli, out, err, forkedProcessTimeoutInSeconds );
	        if ( result != 0 )
	        {
	            throw new MojoFailureException( "P2 publisher return code was " + result );
	        }
        }
        catch (CommandLineException cle) 
        {
        	throw new MojoExecutionException( "P2 publisher failed to be executed ", cle );
        }

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
