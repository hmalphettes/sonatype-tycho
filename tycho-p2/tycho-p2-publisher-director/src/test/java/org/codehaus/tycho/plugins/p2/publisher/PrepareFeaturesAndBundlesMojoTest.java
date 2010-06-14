package org.codehaus.tycho.plugins.p2.publisher;

import java.io.File;

import junit.framework.Assert;

public class PrepareFeaturesAndBundlesMojoTest
    extends AbstractP2MojoTestCase
{

    protected void setUp()
        throws Exception
    {
        super.setUp("projects/repositoryPackaging");
    }

    public void testAssembly()
        throws Exception
    {
    	assemblemojo.execute();

        File resultplugins = new File( targetFolder, "repository/plugins" );
        Assert.assertTrue( resultplugins.exists() );
        Assert.assertEquals( resultplugins.list().length, 1 );
        
        File resultfeatures = new File( targetFolder, "repository/features" );
        Assert.assertTrue( resultfeatures.exists() );
        Assert.assertEquals( resultfeatures.list().length, 1 );
        
        publishmojo.execute();
        
        File resultartifacts = new File( targetFolder, "repository/artifacts.xml" );
        File resultcontent = new File( targetFolder, "repository/content.xml" );
        
        Assert.assertTrue( resultartifacts.exists() );
        Assert.assertTrue( resultcontent.exists() );
        
    }


}
