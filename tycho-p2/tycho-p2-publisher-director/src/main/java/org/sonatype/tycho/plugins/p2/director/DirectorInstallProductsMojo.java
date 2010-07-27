package org.sonatype.tycho.plugins.p2.director;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.AbstractScanner;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.tycho.TargetEnvironment;
import org.codehaus.tycho.buildversion.VersioningHelper;
import org.codehaus.tycho.model.ProductConfiguration;
import org.codehaus.tycho.p2.P2ArtifactRepositoryLayout;
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
	
	/**
	 * Set to false to use the remote repositories when invoking director.
	 * TODO: find a way to use the tycho cache.
	 * @parameter default-value="true"
	 */
	private boolean standaloneRepository;
	
	/**
	 * String that defines the environments to archive as roaming products during the build.
	 * if null, then none are archived.
	 * The string look like this: os=antPattern;ws=antPattern;arch=antPattern
	 * @parameter
	 */
	private String environmentsToArchive;
	
	/**
	 * String that defines the product file names to archive as roaming products during the build.
	 * if undefined then a wildcard is used so all of them are archived. The pattern is a usual ant pattern.
	 * null or empty will archive none of them.
	 * @parameter default-value="*"
	 */
	private String productsToArchive;
	
	private Map<String,File> _profiles = new HashMap<String,File>();

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
			File already = _profiles.put(profile, productFile);
			if (already != null && !already.equals(productFile))
			{//profile need to be a little unique if not very unique.
				throw new MojoFailureException("There are at least 2 product files with the same profile: " +
						profile + " is the profile name for " + already.getName() + " and " + productFile.getName());
			}
			File currentTarget = getTarget(env, profile);
			//see http://wiki.eclipse.org/Equinox_p2_director_application
			Commandline cli = super.getCommandLine(DIRECTOR_APP_NAME);
			cli.addArguments(new String[] {
					"-installIU", productConfiguration.getId(),//
					"-repository", getRepositories(), //
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
			
			//zip for windows, tar.gz for everyone else.
			String version = productConfiguration.getVersion();
	        version = version.replace(VersioningHelper.QUALIFIER, qualifier);

			String topLevelDirectory = profile + "-" + version;
			super.createArchive(currentTarget, toString(env),
					env.getOs().indexOf("win") != -1 ? true: false,
							topLevelDirectory, topLevelDirectory);
		}
		catch (IOException ioe)
		{
			throw new MojoExecutionException("Unable to execute the publisher", ioe);
		}
		
	}
	
	private String getProfileValue(ProductConfiguration productConfiguration)
	{
		String prodName = productConfiguration.getName();
    	if (prodName == null || prodName.length() == 0)
    	{
    		return getProfileValueFromProductId(productConfiguration);
    	}
    	StringBuilder profile = new StringBuilder();
    	char[] chars = prodName.toCharArray();
    	boolean upperCaseOnNext = true;
    	for (int i = 0; i < chars.length; i++)
    	{
    		char c = chars[i];
    		switch (c)
    		{
    		case ' ':
    			upperCaseOnNext = true;
    			break;
    		default:
    			profile.append(upperCaseOnNext ? Character.toUpperCase(c) : c);
    			upperCaseOnNext = false;
    		}
    	}
    	getLog().info("Computed profile " + profile + " for the product " + productConfiguration.getId());
    	return profile.toString();
	}

	
	private String getProfileValueFromProductId(ProductConfiguration productConfiguration)
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
    	getLog().info("Computed profile from the productId " + profile + " for the product " + productId);
    	return profile;
	}
	

	/**
	 * @param environment
	 * @return The built folder where the product is installed and later archived.
	 */
    private File getTarget( TargetEnvironment environment, String profile )
    {
		String version = getTychoProjectFacet().getArtifactKey( project ).getVersion();
		version = VersioningHelper.getExpandedVersion(project, version);
        version = version.replace(VersioningHelper.QUALIFIER, qualifier);

        File target = new File( project.getBuild().getDirectory(), toString( environment ) + "/" + profile + "-" + version );
        target.mkdirs();

        return target;
    }

    /**
     * @return The list of environments for which an archive of a product is generated.
     */
    protected List<TargetEnvironment> getTargetEnvironmentsToArchive()
    {
    	if (environmentsToArchive == null) {
    		return Collections.EMPTY_LIST;
    	}
        StringTokenizer tokenizer = new StringTokenizer(environmentsToArchive, " \n\t\r", false);
    	if (!tokenizer.hasMoreTokens()) {
    		getLog().error("Illegal value for the parameter <environmentsToArchive>" + environmentsToArchive + "</environmentsToArchive>"
    				+ " Expecting <environmentsToArchive>os=antPattern;ws=antPattern;arch=antPattern" +
    						"   os=antPattern;ws=antPattern;arch=antPattern</environmentsToArchive>");
    		return Collections.EMPTY_LIST;
    	}
    	List<String[]> patterns = new ArrayList<String[]>();
        while (tokenizer.hasMoreTokens()) {
            String tok = tokenizer.nextToken();
    		String[] t = tok.split(";");
    		if (t.length != 3) {
    			getLog().error("Illegal value for one of the patterns.'" + tok + "'"
        				+ " Expecting 'os=antPattern;ws=antPattern;arch=antPattern'");
        		return Collections.EMPTY_LIST;
    		}
        	String os = null;
        	String ws = null;
        	String arch = null;
        	for (String s : t) {
        		if (s.startsWith("os=")) {
        			os = s.substring(3);
        		} else if (s.startsWith("ws=")) {
        			ws = s.substring(3);
        		} else if (s.startsWith("arch=")) {
        			arch = s.substring(5);
        		}
        	}
        	patterns.add(new String[] {os, ws, arch});
    	}
    	List<TargetEnvironment> res = new ArrayList<TargetEnvironment>();
    	StringBuilder log = null;
    	if (getLog().isInfoEnabled()) {
    		log = new StringBuilder("Environments for which a product archive is generated: "+environmentsToArchive+" =>  \n  ");
    	}
    	for (TargetEnvironment te : super.getEnvironments()) {
    		for (String[] pattern : patterns) {
	    		if (AbstractScanner.match(pattern[0], te.getOs())
	    				&& AbstractScanner.match(pattern[1], te.getWs())
	    				&& AbstractScanner.match(pattern[2], te.getArch())) {
	    			res.add(te);
	    			log.append("os="+te.getOs()+";ws="+te.getWs()+";arch="+te.getArch()+"\n  ");
	    			break;
	    		}
	    	}
    	}
    	if (getLog().isInfoEnabled()) {
    		getLog().info(log.toString().trim());
    	}
    	return res;
    }

    /**
     * @return The list of product files for which an archive of the product is generated.
     */
    protected List<File> getProductFilesToArchive()
    {
    	if (productsToArchive == null) {
    		return Collections.EMPTY_LIST;
    	}
    	productsToArchive = productsToArchive.trim();
    	if (productsToArchive.length() == 0) {
    		return Collections.EMPTY_LIST;
    	} else if (productsToArchive.equals("*")) {
    		return super.getProductFiles();
    	}
    	StringBuilder log = new StringBuilder();
    	if (getLog().isInfoEnabled()) {
    		log = new StringBuilder("Product files for which an archive is generated: "+productsToArchive+" =>  \n  ");
    	}
    	List<File> res = new ArrayList<File>();
    	boolean isFirst = true;
    	for (File p : super.getProductFiles()) {
    		if (AbstractScanner.match(productsToArchive, p.getName())) {
    			res.add(p);
    			if (isFirst) {
    				isFirst = false;
    			} else {
    				log.append(", ");
    			}
    			log.append(p.getName());
    		}
    	}
    	if (getLog().isInfoEnabled()) {
    		getLog().info(log.toString().trim());
    	}
    	return res;
    }
    
    /**
     * @return The repositories used by director to install the app.
     */
    private String getRepositories()
    {
    	String thisRepo = null;
		try {
			thisRepo = targetRepository.toURI().toURL().toExternalForm();
		} catch (MalformedURLException e) {
			//will never happen but hey...
			getLog().warn("unable to make a url", e);
			thisRepo = "file:" + targetRepository.getAbsolutePath();
		}
    	if (standaloneRepository)
    	{
    		return thisRepo;
    	}
    	StringBuilder sb = new StringBuilder(thisRepo);
    	
    	//get the various p2 repositories and add them to the list:
    	Set<String> urls = new HashSet<String>();
    	for (Repository repo : project.getRepositories())
    	{
    		if (P2ArtifactRepositoryLayout.ID.equals(repo.getLayout()))
    		{
    			String url = repo.getUrl();
    			if (urls.add(url))
    			{
    				sb.append(",");
    				sb.append(url);
    			}
    		}
    	}
    	return sb.toString();
    }
    
}
