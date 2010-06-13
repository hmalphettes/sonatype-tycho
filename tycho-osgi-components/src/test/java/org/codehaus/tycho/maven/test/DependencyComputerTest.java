package org.codehaus.tycho.maven.test;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.project.MavenProject;
import org.codehaus.tycho.TargetPlatform;
import org.codehaus.tycho.TychoConstants;
import org.codehaus.tycho.osgitools.DependencyComputer;
import org.codehaus.tycho.osgitools.DependencyComputer.DependencyEntry;
import org.codehaus.tycho.osgitools.EquinoxResolver;
import org.codehaus.tycho.testing.AbstractTychoMojoTestCase;
import org.codehaus.tycho.utils.MavenSessionUtils;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.State;
import org.junit.Assert;
import org.junit.Test;

public class DependencyComputerTest
    extends AbstractTychoMojoTestCase
{
    private DependencyComputer dependencyComputer;

    protected void setUp()
        throws Exception
    {
        super.setUp();
        dependencyComputer = (DependencyComputer) lookup( DependencyComputer.class );
    }

    @Override
    protected void tearDown()
        throws Exception
    {
        dependencyComputer = null;
        super.tearDown();
    }

    @Test
    public void testExportPackage()
        throws Exception
    {
        File basedir = getBasedir( "projects/exportpackage" );
        File pom = new File( basedir, "pom.xml" );
        MavenExecutionRequest request = newMavenExecutionRequest( pom );
        request.getProjectBuildingRequest().setProcessPlugins( false );
        MavenExecutionResult result = maven.execute( request );

        EquinoxResolver resolver = lookup( EquinoxResolver.class );

        Map<File, MavenProject> basedirMap = MavenSessionUtils.getBasedirMap( result.getTopologicallySortedProjects() );

        MavenProject project = basedirMap.get( new File( basedir, "bundle" ) );
        TargetPlatform platform = (TargetPlatform) project.getContextValue( TychoConstants.CTX_TARGET_PLATFORM );

        State state = resolver.newResolvedState( project, platform );
        BundleDescription bundle = state.getBundleByLocation( project.getBasedir().getAbsolutePath() );

        List<DependencyEntry> dependencies = dependencyComputer.computeDependencies( state.getStateHelper(), bundle );
        Assert.assertEquals( 2, dependencies.size() );
        Assert.assertEquals( "dep", dependencies.get( 0 ).desc.getSymbolicName() );
        Assert.assertEquals( "dep2", dependencies.get( 1 ).desc.getSymbolicName() );
    }

    @Test
    public void testTYCHO0378unwantedSelfDependency()
        throws Exception
    {
        File basedir = getBasedir( "projects/TYCHO0378unwantedSelfDependency" );
        File pom = new File( basedir, "pom.xml" );
        MavenExecutionRequest request = newMavenExecutionRequest( pom );
        request.getProjectBuildingRequest().setProcessPlugins( false );
        MavenExecutionResult result = maven.execute( request );

        Assert.assertEquals( 0, result.getProject().getDependencies().size() );
    }
}
