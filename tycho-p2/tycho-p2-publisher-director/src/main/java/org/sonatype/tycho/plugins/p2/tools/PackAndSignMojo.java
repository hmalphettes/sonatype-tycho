package org.sonatype.tycho.plugins.p2.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.sonatype.tycho.plugins.p2.AbstractP2AppInvokerMojo;

/**
 * Invokes the org.eclipse.equinox.p2.jarprocessor on the commandline.
 * 
 * @goal pack-and-sign
 */
public class PackAndSignMojo extends AbstractP2AppInvokerMojo {

	/**
	 * @parameter default-value="true"
	 */
	private boolean packVerbose;
	
	/**
	 * Highly recommended to specify the pack200 location. and to use a jdk5 as we speak.
	 * Lots of bad things are said at eclipse about pack200 and jdk6.
	 * If null use ${java.home}/bin
	 * 
	 * @parameter
	 */
	private String pack200ExecParentFolder;

    /**
     * List of patterns for a directory scanner to select the jars that must not be packed.
     * Not all jars survive the pack200 conditioning.
     * @parameter
     */
    protected String packExclude;
    
    /**
     * The command to sign the jars.
     * When it is not specified, no signing takes place.
     * @parameter
     */
    protected String signCmd;
    
    /**
     * The path to a pack.properties file.
     * @parameter 
     */
    protected String packPropertiesSource;

	public void execute() throws MojoExecutionException, MojoFailureException
	{
		//creates the pack.properties file in the repository.
		//that never hurts even if we won't do the packing ourselves.
		setupPackProperties(targetRepository, packPropertiesSource != null ? new File(packPropertiesSource) : null);
		
		if (!enablePackAndSign)
		{
			getLog().info("Skip the pack and sign");
			return;
		}
		try
		{
			
			if (pack200ExecParentFolder == null)
			{
				pack200ExecParentFolder = System.getProperty( "java.home" ) + File.separator + "bin";
			}
			
			Commandline cli = getCommandLine();
	        //http://wiki.eclipse.org/index.php/Pack200
	    	/*
	  3.6 syle:
	   java -jar org.eclipse.equinox.p2.jarprocessor_1.0.200.v20100123-1019.jar
	 -processAll -repack -sign signing-script.sh -pack -outputDir ./out eclipse-SDK.zip
	    	 */
			cli.addArguments(new String[] {
					"-Dorg.eclipse.update.jarprocessor.pack200=" + pack200ExecParentFolder,
					"-processAll", "-pack", "-repack" });
			
			if (signCmd != null)
			{
				cli.addArguments(new String[] {"-sign", signCmd});
			}
			if (packVerbose)
			{
				cli.addArguments(new String[] {"-verbose"});
			}
			
			
			File outputDirectory = targetRepository; //new File(targetRepository.getParentFile(), targetRepository.getName() + "-packed");
			outputDirectory.mkdirs();
			cli.addArguments(new String[] {
					"-outputDir", outputDirectory.getCanonicalPath(), targetRepository.getCanonicalPath() });
			
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
		            throw new MojoFailureException( "P2 jarprocessor return code was " + result );
		        }
	        }
	        catch (CommandLineException cle) 
	        {
	        	throw new MojoExecutionException( "P2 jarprocessor failed to be executed ", cle );
	        }
	        
	        //now keep track of the repository unpacked.
	        //and put the packed repository in place of the repository.
//	        FileUtils.rename(targetRepository,
//	        		new File(targetRepository.getParentFile(), targetRepository.getName() + "-before-packAndSign"));
//	        FileUtils.rename(outputDirectory, targetRepository);
	        
		}
		catch (IOException ioe)
		{
			throw new MojoExecutionException("Error executing P2 jarprocessor", ioe);
		}
	}
	
    /**
     * Returns a command line where the equinox launcher is set and the usual parameters for
     * invoking a eclipse application in headless mode are set.
     * @param application
     * @return
     * @throws MojoFailureException
     * @throws IOException
     */
    protected Commandline getCommandLine()
    throws MojoFailureException, IOException
    {
        Commandline cli = new Commandline();

        cli.setWorkingDirectory( project.getBasedir() );

        String executable = System.getProperty( "java.home" ) + File.separator + "bin" + File.separator + "java";
        if ( File.separatorChar == '\\' )
        {
            executable = executable + ".exe";
        }
        cli.setExecutable( executable );
        cli.addArguments( new String[] { "-jar", getJarProcessor().getCanonicalPath(), } );
        
        return cli;
	}

	
    private File getJarProcessor()
	    throws MojoFailureException
	{
	    // XXX dirty hack
	    File p2location = p2.getRuntimeLocation();
	    DirectoryScanner ds = new DirectoryScanner();
	    ds.setBasedir( p2location );
	    ds.setIncludes( new String[] { "plugins/org.eclipse.equinox.p2.jarprocessor_*.jar" } );
	    ds.scan();
	    String[] includedFiles = ds.getIncludedFiles();
	    if ( includedFiles == null || includedFiles.length != 1 )
	    {
	        throw new MojoFailureException( "Can't locate org.eclipse.equinox.p2.jarprocessor bundle in " + p2location );
	    }
	    return new File( p2location, includedFiles[0] );
	}
	
    
    /**
     * Some jars must not go through the packer.
     * This is controlled via the pack.properties file at the root of the directory where jars are packed.
     * <p>
     * When the pack.properties file is copied, update the property pack.excludes for example:<br/>
     * pack.excludes=plugins/com.ibm.icu.base_3.6.1.v20070417.jar,plugins/com.ibm.icu_3.6.1.v20070417.jar,plugins/com.jcraft.jsch_0.1.31.jar
     * </p>
     * <p>
     * More doc here: https://bugs.eclipse.org/bugs/show_bug.cgi?id=178723
     * </p>
     */
    private void setupPackProperties(File targetRepo, File packPropertiesSource)
    throws MojoExecutionException {
    	//pack.excludes=com.ibm.icu.base_3.6.1.v20070417.jar,com.ibm.icu_3.6.1.v20070417.jar,com.jcraft.jsch_0.1.31.jar
    	Properties props = new Properties();
    	InputStream inStream = null;
    	OutputStream outStream = null;
    	try
    	{
    		if (packPropertiesSource != null && packPropertiesSource.canRead())
    		{
    			props.load(inStream);
    		}
    		else
    		{
    			//put the default property:
    			props.put("pack200.default.args", "-E4");
    		}
			props.put("pack.excludes", getPackExcluded(targetRepo).toString());

			File out = new File(targetRepo, "pack.properties");
    		if (!out.exists()) out.createNewFile();
    		outStream = new FileOutputStream(out);
    		props.save(outStream, "Pack.properties generated by the packmojo");
    	}
    	catch (IOException ioe)
    	{
    		throw new MojoExecutionException("Unable to setup the pack.properties file", ioe);
    	}
    	finally
    	{
    		if (inStream != null) try { inStream.close(); } catch (IOException ioe) {}
    		if (outStream != null) try { outStream.close(); } catch (IOException ioe) {}
    	}
    }
    
    private String getPackExcluded(File targetRepoDirectory)
    {
    	StringBuilder patterns = new StringBuilder("artifacts.jar,content.jar");
    	if (packExclude != null)
    	{
    		patterns.append("," + packExclude);
    	}
		DirectoryScanner scanner = new DirectoryScanner();
		scanner.setBasedir(targetRepoDirectory);
		getLog().info("Scanning for jars to add to pack.exclude using the patterns " + patterns + " in " + targetRepoDirectory.getAbsolutePath());
		StringTokenizer tokenizer = new StringTokenizer(patterns.toString(), ", \r\n\t",false);
		String[] incls = new String[tokenizer.countTokens()];
		int i = 0;
		while (tokenizer.hasMoreTokens())
		{
			String tok = tokenizer.nextToken();
			incls[i] = tok;
			i++;
		}
		scanner.setIncludes(incls);
		scanner.scan();
		String[] includedFiles = scanner.getIncludedFiles();
		StringBuilder excluded = null;
		for (String incl : includedFiles)
		{
			incl = incl.replace('\\', '/');
			getLog().debug("   added to pack.exclude: " + incl);
			if (excluded == null)
			{
				excluded = new StringBuilder(incl);
			}
			else
			{
				excluded.append("," + incl);
			}
		}
    	return excluded.toString();
    }

}
