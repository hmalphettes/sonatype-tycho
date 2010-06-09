package org.sonatype.tycho.plugins.p2.publisher;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.tycho.ArtifactDependencyVisitor;
import org.codehaus.tycho.FeatureDescription;
import org.codehaus.tycho.PluginDescription;
import org.codehaus.tycho.buildversion.VersioningHelper;
import org.codehaus.tycho.eclipsepackaging.UpdateSiteAssembler;
import org.codehaus.tycho.model.FeatureRef;
import org.codehaus.tycho.model.PluginRef;
import org.codehaus.tycho.model.UpdateSite;
import org.codehaus.tycho.model.UpdateSite.SiteFeatureRef;
import org.sonatype.tycho.plugins.p2.AbstractP2Mojo;

/**
 * Reads product file(s), site(s) files and categories definition files
 * finds the corresponding features and bundles; places them in the target/eclipse folder.
 * The p2 publisher application can start working on that eclipse folder later.
 * 
 * @goal prepare-features-and-bundles
 */
public class PrepareFeaturesAndBundlesMojo extends AbstractP2Mojo {
	
	public void execute() throws MojoExecutionException, MojoFailureException {
		
		targetRepository.mkdirs();
    	FeaturesAndBundlesAssembler assembler = new FeaturesAndBundlesAssembler(session, targetRepository);
        // expandVersion();
    	getDependencyWalker().walk( assembler );
        
    	//also update the features versions.
    	for (File categoryDef : getCategoriesFiles())
		{
			try
			{
				UpdateSite site = UpdateSite.read(categoryDef);
				
				//set the versions to their exact value.
				//otherwise P2 won't publish them at all.
				getDependencyWalker().traverseUpdateSite( site, new ArtifactDependencyVisitor()
				{
	                @Override
	                public boolean visitFeature( FeatureDescription feature )
	                {
	                    FeatureRef featureRef = feature.getFeatureRef();
	                    String id = featureRef.getId();
	                    MavenProject otherProject = feature.getMavenProject();
	                    String version;
	                    if ( otherProject != null )
	                    {
	                        version = VersioningHelper.getExpandedVersion( otherProject, featureRef.getVersion() );
	                    }
	                    else
	                    {
	                        version = feature.getKey().getVersion();
	                    }
	                    String url = UpdateSiteAssembler.FEATURES_DIR + id + "_" + version + ".jar";
	                    ( (SiteFeatureRef) featureRef ).setUrl( url );
	                    featureRef.setVersion( version );
	                    return false; // don't traverse included features
	                }

					@Override
					public void visitPlugin(PluginDescription plugin) {
						PluginRef pluginRef = plugin.getPluginRef();
	                    String id = pluginRef.getId();
	                    MavenProject otherProject = plugin.getMavenProject();
	                    String version;
	                    if ( otherProject != null )
	                    {
	                        version = VersioningHelper.getExpandedVersion( otherProject, pluginRef.getVersion() );
	                    }
	                    else
	                    {
	                        version = plugin.getKey().getVersion();
	                    }
	                    //String url = UpdateSiteAssembler.PLUGINS_DIR + id + "_" + version + ".jar";
	                    //( (SiteFeatureRef) featureRef ).setUrl( url );
	                    pluginRef.setVersion( version );
					}
	                
	            } );
			}
            catch ( Exception e )
            {
                throw new MojoExecutionException( e.getMessage(), e );
            }
        }
    }

}
