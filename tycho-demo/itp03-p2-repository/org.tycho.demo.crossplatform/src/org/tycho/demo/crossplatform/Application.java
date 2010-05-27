package org.tycho.demo.crossplatform;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.osgi.framework.BundleContext;

public class Application
    implements IApplication
{
    public Object start( IApplicationContext context )
        throws Exception
    {
        context.applicationRunning();

        Display display = Display.getCurrent();
        if ( display == null )
        {
            display = new Display();
        }

        final Shell shell = new Shell( display );

        display.syncExec( new Runnable()
        {
            public void run()
            {
                MessageBox dialog = new MessageBox( shell );

                BundleContext ctx = Activator.context;

                StringBuilder sb = new StringBuilder();
                sb.append( "osgi.os=" ).append( ctx.getProperty( "osgi.os" ) ).append( "; " );
                sb.append( "osgi.ws=" ).append( ctx.getProperty( "osgi.ws" ) ).append( "; " );
                sb.append( "osgi.arch=" ).append( ctx.getProperty( "osgi.arch" ) );

                dialog.setText( "OSGi runtime environment properties" );
                dialog.setMessage( sb.toString() );

                dialog.open();
            }
        } );

        return null;
    }

    public void stop()
    {
        // TODO Auto-generated method stub

    }

}
