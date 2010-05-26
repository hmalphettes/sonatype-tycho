package org.sonatype.tycho.plugins.p2.director;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.tycho.TargetEnvironment;
import org.codehaus.tycho.model.ProductConfiguration;
import org.sonatype.tycho.plugins.p2.AbstractP2AppInvokerMojo;

/**
 * Invokes p2 director to install a product archive for each environment specified
 * into a folder.
 * <p>
 * @see http://wiki.eclipse.org/Equinox_p2_director_application
 * </p>
 * 
 * @goal archive-products
 */
public class DirectorInstallProductsMojo extends AbstractP2AppInvokerMojo {
	
	public static final String DIRECTOR_APP_NAME = "org.eclipse.equinox.p2.director";

	public void execute() throws MojoExecutionException, MojoFailureException {
		for (File productFile : getProductFilesToArchive())
		{
	    	for (TargetEnvironment env : getTargetEnvironmentsToArchive())
	    	{
	    		execute(env, productFile);
	    	}
		}
    }
	
	private void execute(TargetEnvironment env, File productFile)
	throws MojoExecutionException, MojoFailureException {
		try
		{
			ProductConfiguration productConfiguration = ProductConfiguration.read(productFile);
			String profile  = getProfileValue(productConfiguration);
			File currentTarget = getTarget(env);
			//see http://wiki.eclipse.org/Equinox_p2_director_application
			Commandline cli = super.getCommandLine(DIRECTOR_APP_NAME);
			cli.addArguments(new String[] {
					"-installIU", productConfiguration.getId(),//
					"-metadataRepository", targetRepository.toURI().toURL().toExternalForm(), //
					"-artifactRepository", targetRepository.toURI().toURL().toExternalForm(), //
					"-destination",	currentTarget.getCanonicalPath(),
					"-profile",	profile,
					"-profileProperties", "org.eclipse.update.install.features=true",
					"-bundlepool", currentTarget.getCanonicalPath(),
					"-p2.os", env.getOs(), 
					"-p2.ws", env.getWs(),
					"-p2.arch",	env.getArch(),
					"-roaming"
					});
			
			super.execute(cli, null);
		}
		catch (IOException ioe)
		{
			throw new MojoExecutionException("Unable to execute the publisher", ioe);
		}
		
	}
	
	private String getProfileValue(ProductConfiguration productConfiguration)
	{
		String profile = null;
    	//let's do something a bit nicer:
    	//the last segment of the product id. unless it is 'product'. then look before.
    	String productId = productConfiguration.getId();
    	StringTokenizer tokenizer = new StringTokenizer(productId, ".");
    	ArrayList<String> toks = new ArrayList<String>();
    	while (tokenizer.hasMoreElements())
    	{
    		toks.add(tokenizer.nextToken());
    	}
    	String[] segs = toks.toArray(new String[toks.size()]);
    	for (int i = segs.length -1; i >= 0; i--)
    	{
    		if (segs[i].toLowerCase().indexOf("prod") == -1)
    		{
    			if (i > 0 && segs[i].toLowerCase().equals("sdk"))
    			{
    				profile = segs[i-1] + "SDK";
    				break;
    			}
    			else
    			{
    				profile = segs[i];
    				break;
    			}
    		}
    	}
    	if (profile == null)
    	{
    		profile = "profile";
    	}
    	getLog().info("Computed profile " + profile);
    	return profile;
	}

	/**
	 * @param environment
	 * @return The built folder where the product is installed and later archived.
	 */
    private File getTarget( TargetEnvironment environment )
    {
        File target = new File( project.getBuild().getDirectory(), toString( environment ) );
        target.mkdirs();

        return target;
    }
    
    /**
     * @return The list of environments for which an archive of a product is generated.
     */
    protected List<TargetEnvironment> getTargetEnvironmentsToArchive()
    {
    	return super.getEnvironments();
    }

    /**
     * @return The list of product files for which an archive of the product is generated.
     */
    protected List<File> getProductFilesToArchive()
    {
    	return super.getProductFiles();
    }
    
}
