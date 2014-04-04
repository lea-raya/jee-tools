/**
 * Copyright (C) 2014 Université de Strasbourg (di-dip@unistra.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.unistra.di.jdbc.jndi.tomcat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLSyntaxErrorException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Properties;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;
import javax.sql.DataSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import fr.unistra.di.jdbc.jndi.JNDIDecoratorProxy;
import fr.unistra.di.jdbc.jndi.LazyDataSourceFetcher;
import fr.unistra.di.jdbc.jndi.fallback.FallbackStrategy;

/**
 * @author Léa Рая DÉCORNOD <decornod@unistra.fr>
 *
 */
public class TomcatServerXMLStrategy implements FallbackStrategy {

    public static String[] SERVER_XML_LOCATIONS = new String[] {
        "${CATALINA_BASE}/conf/server.xml",
        "${CATALINA_HOME}/conf/server.xml",
    };
    public static String[] ENV_PROPS = new String[] {
        "CATALINA_HOME", "CATALINA_BASE"
    };
    private static final ObjectFactory factory = new TopSPIObjectFactory();

    /* (non-Javadoc)
     * @see fr.unistra.di.dip.tools.jdbc.jndi.FallbackStrategy#getDataSource(java.lang.String, java.util.Properties)
     */
    public DataSource getDataSource(String name, Properties info)
            throws SQLException {

        // ignore non JEE standard JNDI resource name
        if (!name.startsWith("java:comp/env/"))
            return null;
        name = name.substring("java:comp/env/".length());

        Reference dsRef;
        try {
            File tomcatConf = fetchServerXML();
            dsRef = parseServerXML(tomcatConf, name);
        } catch (FileNotFoundException ex) {
            throw new SQLNonTransientConnectionException(ex);
        } catch (NamingException ex) {
            throw new SQLNonTransientConnectionException(ex);
        } catch (XPathExpressionException ex) {
            throw new SQLSyntaxErrorException(ex);
        }
        try {
            return (DataSource) factory.getObjectInstance(dsRef, null, null, null);
        } catch (Exception ex) {
            throw (SQLException) new SQLException().initCause(ex);
        }
    }

    protected File fetchServerXML() throws FileNotFoundException {
        File serverXmlFile = null;
        String[] locations = SERVER_XML_LOCATIONS;
        for (int i = 0; i < locations.length; i++)
            for (String env : ENV_PROPS)
                if (System.getenv(env) != null)
                    locations[i] = locations[i].replace("${"+env+"}", System.getenv(env));

        for (String location : locations) {
            serverXmlFile = new File(location);
            if (serverXmlFile.canRead())
                return serverXmlFile;
        }
        throw new FileNotFoundException("Did not find tomcat's server.xml {" + Arrays.asList(locations).toString() + "}");
    }

    protected Reference parseServerXML(File file, String name)
            throws FileNotFoundException, XPathExpressionException, NameNotFoundException  {
        XPath xpath = XPathFactory.newInstance().newXPath();
        InputSource serverXML = new InputSource(new FileInputStream(file));
        serverXML.setPublicId(file.getPath());
        Node resource = (Node) xpath.evaluate(
                "/Server/GlobalNamingResources/Resource[@name='"+name+"']",
                serverXML,
                XPathConstants.NODE);
        if (resource == null)
            throw new NameNotFoundException(MessageFormat.format(
                    "Could not find ressource {1} in {0}",
                    new Object[] { file.getPath(), name }
                    ));
        NamedNodeMap attributes = resource.getAttributes();
        return attributesToReference(attributes);
    }

    /**
     * @param attributes
     */
    private Reference attributesToReference(NamedNodeMap attributes) {
        Attr typeN = (Attr) attributes.getNamedItem("type");
        String type = typeN == null ? null : typeN.getValue();
        Attr factoryN = (Attr) attributes.getNamedItem("factory");
        String factory = factoryN == null ? null : factoryN.getValue();

        Reference ref = new Reference(type, factory, null);
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attribute = (Attr) attributes.item(i);
            String name = attribute.getName();
            if ("name".equals(name) || "type".equals(name) || "factory".equals(name))
                continue;
            String value = attribute.getValue();
            ref.add(new StringRefAddr(name, value));
        }
        return ref;
    }

}
