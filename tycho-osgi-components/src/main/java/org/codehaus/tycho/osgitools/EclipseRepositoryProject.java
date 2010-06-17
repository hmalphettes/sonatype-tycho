package org.codehaus.tycho.osgitools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifact;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.tycho.ArtifactDependencyVisitor;
import org.codehaus.tycho.ArtifactDependencyWalker;
import org.codehaus.tycho.ArtifactDescription;
import org.codehaus.tycho.ArtifactKey;
import org.codehaus.tycho.FeatureDescription;
import org.codehaus.tycho.PluginDescription;
import org.codehaus.tycho.TargetEnvironment;
import org.codehaus.tycho.TargetPlatform;
import org.codehaus.tycho.TychoProject;
import org.codehaus.tycho.model.Feature;
import org.codehaus.tycho.model.PluginRef;
import org.codehaus.tycho.model.ProductConfiguration;
import org.codehaus.tycho.model.UpdateSite;
import org.codehaus.tycho.utils.SourceBundleUtils;
import org.eclipse.osgi.util.ManifestElement;

/**
 * An eclipse repository project produces a p2 repository where a set of products
 * and or categories (aka sites) are published.
 */
@Component( role = TychoProject.class, hint = TychoProject.ECLIPSE_REPOSITORY )
public class EclipseRepositoryProject extends AbstractArtifactBasedProject
{
	
	/**
	 * The published repository is always under the id of the maven project:
	 * this published repository can contain multiple products or sites.
	 */
    public ArtifactKey getArtifactKey( MavenProject project )
    {
        String id = project.getArtifactId();
        String version = getOsgiVersion( project );

        return new ArtifactKey( TychoProject.ECLIPSE_REPOSITORY, id, version );
    }

	@Override
	protected ArtifactDependencyWalker newDependencyWalker(MavenProject project, TargetEnvironment environment)
	{
		List<UpdateSite> sites = loadCategoriesDefinitions(project);
		List<ProductConfiguration> products = loadProducts(project);
		return new EclipseRepositoryProjectArtifactDependencyWalker(
				this.getLogger(), project, this, 
        		getTargetPlatform( project, environment ),
                getEnvironments( project, environment ),
                sites, products);
	}
	
	/**
	 * Parses the product configuration files
	 * @param project
	 * @return
	 */
    protected List<ProductConfiguration> loadProducts( final MavenProject project )
    {
    	List<ProductConfiguration> products = new ArrayList<ProductConfiguration>();
    	for (File file : getProductFiles(project))
    	{
    		try
	        {
	        	products.add(ProductConfiguration.read( file ));
	        }
	        catch ( IOException e )
	        {
	            throw new RuntimeException( "Could not read product configuration file " + file.getAbsolutePath(), e );
	        }
    	}
        return products;
    }

    protected List<UpdateSite> loadCategoriesDefinitions( MavenProject project )
    {
    	List<UpdateSite> sites = new ArrayList<UpdateSite>();
    	for (File file : getCategoriesFiles(project))
    	{
	        try
	        {
	        	sites.add(UpdateSite.read( file ));
	        }
	        catch ( IOException e )
	        {
	            throw new RuntimeException( "Could not read a categories definition (aka site.xml) file: " + file.getAbsolutePath(), e );
	        }
    	}
        return sites;
    }

    /**
     * Looks for all files at the base of the project that extension is ".product"
     * Duplicated in the P2GeneratorImpl
     * @param project
     * @return The list of product files to parse for an eclipse-repository project
     */
    public List<File> getProductFiles(MavenProject project)
    {
   	 	File projectLocation = project.getBasedir();
    	List<File> res = new ArrayList<File>();
    	for (File f : projectLocation.listFiles())
    	{
    		if (f.isFile() && f.getName().endsWith(".product"))
    		{
    			res.add(f);
    		}
    	}
    	return res;
    }
    
    /**
     * Looks for files at the base of the project that contain *site*.xml or *categor*.xml
     * Duplicated in the P2GeneratorImpl
     * TODO: better.
     * @param project
     * @return The list of site and categories files to parse for an eclipse-repository project
     */
    public List<File> getCategoriesFiles(MavenProject project)
    {
   	 	File projectLocation = project.getBasedir();
    	List<File> res = new ArrayList<File>();
    	for (File f : projectLocation.listFiles())
    	{
    		if (f.isFile() && f.getName().endsWith(".xml") && 
    				(f.getName().indexOf("categor") != -1 ||
    				 f.getName().indexOf("site") != -1))
    		{
    			res.add(f);
    		}
    	}
    	return res;
    }

}

class EclipseRepositoryProjectArtifactDependencyWalker extends AbstractArtifactDependencyWalker
{
	private TargetPlatform platform;
	private MavenProject mavenProject;
	private List<UpdateSite> sites;
	private List<ProductConfiguration> products;
	private AbstractArtifactBasedProject tychoProject;
	private Logger logger;

	public EclipseRepositoryProjectArtifactDependencyWalker(
			Logger logger,
			MavenProject mavenProject,
			AbstractArtifactBasedProject tychoProject,
			TargetPlatform platform, TargetEnvironment[] environments,
			List<UpdateSite> sites, List<ProductConfiguration> products)
	{
		super(platform, environments);
		this.logger = logger;
		this.platform = platform;
		this.sites = sites;
		this.products = products;
		this.mavenProject = mavenProject;
		this.tychoProject = tychoProject;
	}
	
	public void walk(ArtifactDependencyVisitor visitor) {
		Map<ArtifactKey, File> visited = new HashMap<ArtifactKey, File>();
		for (UpdateSite site : sites)
		{
			traverseUpdateSite(site, visitor, visited);
		}
		for (ProductConfiguration product : products)
		{
			traverseProduct(product, visitor, visited);
		}
		traversePom(visitor, visited);
	}
    protected void traverseProduct( ProductConfiguration product, ArtifactDependencyVisitor visitor, Map<ArtifactKey, File> visited )
    {
    	if (product.useFeatures())
    	{
    		//this is arguable:
    		//visit the plugins anyways. we want them to be published in the repository.
        	for ( PluginRef ref : product.getPlugins() )
            {
            	traversePlugin( ref, visitor, visited );
            }
    	}
    	super.traverseProduct(product, visitor, visited);
    }
    
	/**
	 * 
	 */
	protected void traversePom( ArtifactDependencyVisitor visitor, Map<ArtifactKey, File> visited )
	{
		if (mavenProject.getDependencyArtifacts() == null)
		{
			getLogger().warn("Dependencies not resolved, skipping the pom from the ArtifactDependencyVisitor");
			return;
		}
		for (Artifact artifact : mavenProject.getDependencyArtifacts())
		{ 
			File location = artifact.getFile();
			if (location == null || !location.exists())
			{
				if (artifact instanceof ProjectArtifact)
				{
					getLogger().info(artifact.getId() + " is not built yet");
					continue;
				}
				getLogger().warn("Unresolved dependency " + artifact.getId());
				continue;
			}
			ArtifactDescription artifactDesc = platform.getArtifact(location);
			if (artifactDesc == null)
			{
				//humf! not sure why this is happening. It should be resolved in the target platform?
				if (artifact instanceof ProjectArtifact)
				{
					MavenProject project = ((ProjectArtifact) artifact).getProject();
					String packaging = project.getPackaging();
					String classifier = artifact.getClassifier();
					if (packaging.equals(TychoProject.ECLIPSE_PLUGIN))
					{
						if (classifier == null)
						{
							artifactDesc = new DefaultPluginDescription(
								new ArtifactKey(TychoProject.ECLIPSE_PLUGIN, project.getArtifactId(),
										tychoProject.getOsgiVersion(project)), location, project, null );
						}
						else if (classifier.equals(SourceBundleUtils.ARTIFACT_CLASSIFIER))
						{//TODO: better.
							artifactDesc = new DefaultPluginDescription(
									new ArtifactKey(TychoProject.ECLIPSE_PLUGIN, project.getArtifactId() + SourceBundleUtils.SOURCE_BUNDLE_SUFFIX,
											tychoProject.getOsgiVersion(project)), location, project, null );
						}
					}
					else if (packaging.equals(TychoProject.ECLIPSE_FEATURE))
					{
						if (classifier == null)
						{	
							Feature feature = null;
							try
							{
								feature = Feature.loadFeature(location);
							} catch (Throwable t) {
								throw new RuntimeException("Unable to parse the feature.xml inside " + location.getAbsolutePath(), t);
							}
							artifactDesc = new DefaultFeatureDescription(
									new ArtifactKey(TychoProject.ECLIPSE_FEATURE, artifact.getArtifactId(),
											tychoProject.getOsgiVersion(project)), location, project, feature, null );
						}
						else if (classifier.equals(SourceBundleUtils.ARTIFACT_CLASSIFIER))
						{//TODO: better.
							Feature feature = null;
							try
							{
								feature = Feature.loadFeature(location);
							} catch (Throwable t) {
								throw new RuntimeException("Unable to parse the feature.xml inside " + location.getAbsolutePath(), t);
							}
							artifactDesc = new DefaultFeatureDescription(
									new ArtifactKey(TychoProject.ECLIPSE_FEATURE, artifact.getArtifactId() + SourceBundleUtils.SOURCE_BUNDLE_SUFFIX,
											tychoProject.getOsgiVersion(project)), location, project, feature, null );
						}
					}
				}
				else
				{
					Feature feature = null;
					try
					{
						feature = Feature.loadFeature(location);
						artifactDesc = new DefaultFeatureDescription(
								new ArtifactKey(TychoProject.ECLIPSE_FEATURE, artifact.getArtifactId(),
										feature.getVersion()), location, null, feature, null );
					} catch (Throwable t) {
						
					}
					if (artifactDesc == null)
					{
						DefaultBundleReader read = new DefaultBundleReader();
						Manifest man = read.loadManifest(location);
						if (man != null)
						{
							ManifestElement[] elms = read.parseHeader("Bundle-SymbolicName", man);
							if (elms.length == 1)
							{
								String bundleId = elms[0].getValueComponents()[0];
								String bundleVersion = man.getMainAttributes().getValue("Bundle-Version");
								if (bundleVersion != null)
								{
									artifactDesc = new DefaultPluginDescription(
											new ArtifactKey( TychoProject.ECLIPSE_PLUGIN, bundleId, bundleVersion ), location, null, null );
								}
							}
						}
					}
				}
			}
			
			if (artifactDesc == null)
			{
				getLogger().warn("Unable to make a plugin or feature out of the declared artifact " + artifact.getDependencyConflictId());
			}
			else if (artifactDesc.getKey().getType().equals(TychoProject.ECLIPSE_FEATURE))
			{
				visitor.visitFeature((FeatureDescription)artifactDesc);
			}
			else if (artifactDesc.getKey().getType().equals(TychoProject.ECLIPSE_PLUGIN))
			{
				visitor.visitPlugin((PluginDescription)artifactDesc);
			}
			
		}
	}
	
	protected Logger getLogger()
	{
		return logger;
	}
	
}
