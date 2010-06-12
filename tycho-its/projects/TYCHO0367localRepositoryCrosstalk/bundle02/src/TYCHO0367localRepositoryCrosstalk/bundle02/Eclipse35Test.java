package TYCHO0367localRepositoryCrosstalk.bundle02;

import junit.framework.TestCase;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.PlatformAdmin;
import org.eclipse.osgi.service.resolver.State;
import org.osgi.framework.ServiceReference;

public class Eclipse35Test
    extends TestCase
{
    public void test()
        throws Exception
    {
        ServiceReference sfr = Activator.context.getServiceReference( PlatformAdmin.class.getName() );

        PlatformAdmin padmin = (PlatformAdmin) Activator.context.getService( sfr );

        State state = padmin.getState();

        BundleDescription equinox = state.getBundle( "org.eclipse.osgi", null );

        assertEquals( 3, equinox.getVersion().getMajor() );
        assertEquals( 5, equinox.getVersion().getMinor() );
    }
}
