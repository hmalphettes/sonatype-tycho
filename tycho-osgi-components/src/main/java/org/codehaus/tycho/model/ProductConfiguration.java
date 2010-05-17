package org.codehaus.tycho.model;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.plexus.util.IOUtil;

import de.pdark.decentxml.Attribute;
import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import de.pdark.decentxml.XMLIOSource;
import de.pdark.decentxml.XMLParser;
import de.pdark.decentxml.XMLWriter;

/**
 * As of eclipse 3.5.1, file format does not seem to be documented. There are most likely multiple parser
 * implementations. org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile
 */
public class ProductConfiguration
{
    private static XMLParser parser = new XMLParser();

    public static ProductConfiguration read( File file )
        throws IOException
    {
        InputStream is = new BufferedInputStream( new FileInputStream( file ) );
        return read( is ); // closes the stream
    }

    public static ProductConfiguration read( InputStream input )
        throws IOException
    {
        try
        {
            return new ProductConfiguration( parser.parse( new XMLIOSource( input ) ) );
        }
        finally
        {
            IOUtil.close( input );
        }
    }

    public static void write( ProductConfiguration product, File file )
        throws IOException
    {
        OutputStream os = new BufferedOutputStream( new FileOutputStream( file ) );

        Document document = product.document;
        try
        {
            Writer w =
                document.getEncoding() != null ? new OutputStreamWriter( os, document.getEncoding() )
                                : new OutputStreamWriter( os );
            XMLWriter xw = new XMLWriter( w );
            try
            {
                document.toXML( xw );
            }
            finally
            {
                xw.flush();
            }
        }
        finally
        {
            IOUtil.close( os );
        }
    }

    private Element dom;

    private Document document;

    public ProductConfiguration( Document document )
    {
        this.document = document;
        this.dom = document.getRootElement();
    }

    public String getProduct()
    {
        return dom.getAttributeValue( "id" );
    }

    public String getApplication()
    {
        return dom.getAttributeValue( "application" );
    }

    public List<FeatureRef> getFeatures()
    {
        Element featuresDom = dom.getChild( "features" );
        if ( featuresDom == null )
        {
            return Collections.emptyList();
        }

        ArrayList<FeatureRef> features = new ArrayList<FeatureRef>();
        for ( Element pluginDom : featuresDom.getChildren( "feature" ) )
        {
            features.add( new FeatureRef( pluginDom ) );
        }
        return Collections.unmodifiableList( features );
    }

    public String getId()
    {
        return dom.getAttributeValue( "uid" );
    }

    public Launcher getLauncher()
    {
        Element domLauncher = dom.getChild( "launcher" );
        if ( domLauncher == null )
        {
            return null;
        }
        return new Launcher( domLauncher );
    }

    public String getName()
    {
        return dom.getAttributeValue( "name" );
    }

    public List<PluginRef> getPlugins()
    {
        Element pluginsDom = dom.getChild( "plugins" );
        if ( pluginsDom == null )
        {
            return Collections.emptyList();
        }

        ArrayList<PluginRef> plugins = new ArrayList<PluginRef>();
        for ( Element pluginDom : pluginsDom.getChildren( "plugin" ) )
        {
            plugins.add( new PluginRef( pluginDom ) );
        }
        return Collections.unmodifiableList( plugins );
    }

    public boolean useFeatures()
    {
        return Boolean.parseBoolean( dom.getAttributeValue( "useFeatures" ) );
    }

    public boolean includeLaunchers()
    {
        String attribute = dom.getAttributeValue( "includeLaunchers" );
        return attribute == null ? true : Boolean.parseBoolean( attribute );
    }

    public String getVersion()
    {
        return dom.getAttributeValue( "version" );
    }

    public void setVersion( String version )
    {
        dom.setAttribute( "version", version );
    }

    public List<String> getW32Icons()
    {
        Element domLauncher = dom.getChild( "launcher" );
        if ( domLauncher == null )
        {

            return null;
        }
        Element win = domLauncher.getChild( "win" );
        if ( win == null )
        {
            return null;
        }
        List<String> icons = new ArrayList<String>();
        String useIco = win.getAttributeValue( "useIco" );
        if ( Boolean.valueOf( useIco ) )
        {
            // for (Element ico : win.getChildren("ico"))
            {
                Element ico = win.getChild( "ico" );
                // should be only 1
                icons.add( ico.getAttributeValue( "path" ) );
            }
        }
        else
        {
            for ( Element bmp : win.getChildren( "bmp" ) )
            {
                List<Attribute> attibuteNames = bmp.getAttributes();
                if ( attibuteNames != null && attibuteNames.size() > 0 )
                    icons.add( attibuteNames.get( 0 ).getValue() );
            }
        }
        return icons;
    }

    public String getLinuxIcon()
    {
        Element domLauncher = dom.getChild( "launcher" );
        if ( domLauncher == null )
        {

            return null;
        }
        Element linux = domLauncher.getChild( "linux" );
        if ( linux == null )
        {
            return null;
        }

        return linux.getAttributeValue( "icon" );
    }

    public Map<String, BundleConfiguration> getPluginConfiguration()
    {
        Element configurationsDom = dom.getChild( "configurations" );
        if ( configurationsDom == null )
        {
            return null;
        }

        Map<String, BundleConfiguration> configs = new LinkedHashMap<String, BundleConfiguration>();
        for ( Element pluginDom : configurationsDom.getChildren( "plugin" ) )
        {
            configs.put( pluginDom.getAttributeValue( "id" ), new BundleConfiguration( pluginDom ) );
        }
        return Collections.unmodifiableMap( configs );
    }

    public String getMacIcon()
    {
        Element domLauncher = dom.getChild( "launcher" );
        if ( domLauncher == null )
        {

            return null;
        }
        Element linux = domLauncher.getChild( "macosx" );
        if ( linux == null )
        {
            return null;
        }
        return linux.getAttributeValue( "icon" );
    }
    
    /**
     * Return the value of /configIni/$platform/text() or null if there is no such element.
     * @param platform (aka OS) According to the PDE UI for the product file,
     * it should be one of 'linux', 'macosx', 'solaris', 'win32'
     * however any string is accepted here. 
     * @return The path to the config file. According to the PDE UI it is an
     * absolute path where the root is the project.
     */
    public String getConfigIni(String platform)
    {
    	Element configIni = dom.getChild("configIni");
    	if (configIni == null)
    	{
    		return null;
    	}
    	Element platIni = dom.getChild(platform);
    	if (platIni == null)
    	{
    		return null;
    	}
    	return platIni.getText();
    }
    
    /**
     * @param platform (aka OS) or null for the arguments that apply to all platforms.
     * @return the launcher argument for the VM
     */
    private String getLauncherArgs(String platform, boolean forVM)
    {
    	Element launcherArgs = dom.getChild("launcherArgs");
    	if (launcherArgs == null)
    	{
    		return null;
    	}
    	String elemName = forVM ? "vmArgs" : "programArgs";
    	if (platform != null)
    	{
    		if (platform.length() < 2) 
    		{
    			throw new IllegalArgumentException("Invalid platform name " + platform);
    		}
    		elemName = elemName + Character.toUpperCase(platform.charAt(0)) + platform.substring(1, 3);
    	}
    	Element programArgs = launcherArgs.getChild(elemName);
    	return programArgs != null ? programArgs.getText() : null;
    	
    }
    /**
     * Return the value of /launcherArgs/name()[concat('vmArgs',$platform)]/text()
     * or null if there is no such element.
     * 
     * @param platform (aka OS) or null for the arguments that apply to all platforms.
     * @return the launcher argument for the VM
     */
    public String getLauncherArgsForVM(String platform)
    {
    	return getLauncherArgs(platform, true);
    }
    /**
     * Return the value of /launcherArgs/name()[concat('programArgs',$platform)]/text() 
     * or null if there is no such element.
     * 
     * @param platform (aka OS) or null for the arguments that apply to all platforms.
     * @return the launcher argument for the program
     */
    public String getLauncherArgsForProgram(String platform)
    {
    	return getLauncherArgs(platform, false);
    }
}
