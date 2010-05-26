package org.codehaus.tycho.osgitools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.tycho.ArtifactDependencyVisitor;
import org.codehaus.tycho.ArtifactDependencyWalker;
import org.codehaus.tycho.ArtifactKey;
import org.codehaus.tycho.TargetEnvironment;
import org.codehaus.tycho.TychoProject;
import org.codehaus.tycho.model.ProductConfiguration;
import org.codehaus.tycho.model.UpdateSite;

/**
 * An eclipse repository project produces a p2 repository where a set of products
 * and or categories (aka sites) are published.
 */
@Component( role = TychoProject.class, hint = TychoProject.ECLIPSE_REPOSITORY )
public class EclipseRepositoryProject extends AbstractArtifactBasedProject {

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
		final List<UpdateSite> sites = loadCategoriesDefinitions(project);
		final List<ProductConfiguration> products = loadProducts(project);
        return new AbstractArtifactDependencyWalker( getTargetPlatform( project, environment ),
                									  getEnvironments( project, environment ) )
        {
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
			}
		};
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
