package org.codehaus.tycho.plugins.p2.publisher;

import java.io.File;

import junit.framework.Assert;

public class PublishCategoriesMojoTest
    extends AbstractP2MojoTestCase
{

    protected void setUp()
	    throws Exception
	{
	    super.setUp("projects/repositoryPackaging");
	}
	
	public void testAllSimple()
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
	    
	    File resultartifacts = new File( targetFolder, "repository/artifacts.jar" );
	    File resultcontent = new File( targetFolder, "repository/content.jar" );
	    
	    Assert.assertTrue( resultartifacts.exists() );
	    Assert.assertTrue( resultcontent.exists() );
	    
	    long artifactsBeforePack = resultartifacts.length();
	    
	    setVariableValueToObject(packAndSignMojo, "enablePackAndSign", Boolean.TRUE);
	    packAndSignMojo.execute();
	    
	    //packing does not update the p2 index files.
	    Assert.assertEquals(resultartifacts.length(), artifactsBeforePack);
	    //1 new file for the pack.gz in the plugins.
	    Assert.assertEquals(resultplugins.list().length, 2);
	    //artifacts.jar and content.jar should not be packed.
	    Assert.assertFalse(new File(targetFolder, "artifacts.jar.pack.gz").exists());
	    
	    
	    //fix the checksums: the artifacts.jar should be modified.
	    fixCheckSumMojo.execute();
	    Assert.assertEquals(resultartifacts.length(), artifactsBeforePack);
	    
	    
	}

}
