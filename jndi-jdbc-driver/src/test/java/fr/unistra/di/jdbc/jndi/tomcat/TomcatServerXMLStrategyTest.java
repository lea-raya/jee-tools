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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.sql.SQLException;
import java.util.Properties;
import java.util.ServiceLoader;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;
import javax.sql.DataSource;
import javax.xml.xpath.XPathExpressionException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Léa Рая DÉCORNOD <decornod@unistra.fr>
 *
 */
public class TomcatServerXMLStrategyTest {

    private TomcatServerXMLStrategy instance;

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        instance = new TomcatServerXMLStrategy();
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
    }

    /**
     * Test method for {@link fr.unistra.di.jdbc.jndi.tomcat.TomcatServerXMLStrategy#fetchServerXML()}.
     * @throws FileNotFoundException
     */
    @Test
    public void testFetchServerXML() throws Exception {
        URL resource = TomcatServerXMLStrategy.class.getResource("/server.xml");
        File serverXML = new File(resource.getFile());
        setFinalStatic(TomcatServerXMLStrategy.class.getField("SERVER_XML_LOCATIONS"),
                new String[] { "/bad/path/${CATALINA_BASE}/to/no/file", serverXML.getPath() });
        assertEquals(serverXML, instance.fetchServerXML());
    }

    private static void setFinalStatic(Field field, Object value) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        field.setAccessible(true);
        // remove final modifier from field
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, value);
    }

    /**
     * Test method for {@link fr.unistra.di.jdbc.jndi.tomcat.TomcatServerXMLStrategy#parseServerXML(java.io.File, java.lang.String)}.
     * @throws XPathExpressionException
     * @throws FileNotFoundException
     * @throws NameNotFoundException
     */
    @Test
    public void testParseServerXML() throws FileNotFoundException, XPathExpressionException, NameNotFoundException {
        File serverXML = new File(getClass().getResource("/server.xml").getFile());
        Reference ref = instance.parseServerXML(serverXML, "jdbc/testDB");
        assertEquals("javax.sql.DataSource", ref.getClassName());
        assertEquals("org.hsqldb.jdbc.JDBCDriver", ref.get("driverClassName").getContent().toString());
        assertTrue(ref.get("url").getContent().toString().startsWith("jdbc:hsqldb:"));
        assertEquals("sa", ref.get("username").getContent().toString());
    }

    /**
     * Test method for {@link fr.unistra.di.jdbc.jndi.tomcat.TomcatServerXMLStrategy#getDataSource(java.lang.String, java.util.Properties)}.
     * @throws SQLException
     */
    @Test
    public void testGetDataSource() throws SQLException {
        File serverXML = new File(getClass().getResource("/server.xml").getFile());
        try {
            // override server.xml location tester
            setFinalStatic(TomcatServerXMLStrategy.class.getField("SERVER_XML_LOCATIONS"),
                    new String[] { serverXML.getPath() });
            // trick SPI to skip application Classpath
            setFinalStatic(TopSPIObjectFactory.class.getDeclaredField("spiLoader"),
                    ServiceLoader.loadInstalled(ObjectFactory.class));
            // suppress Classpath scanning of known ObjectFactories implementations
            setFinalStatic(TopSPIObjectFactory.class.getDeclaredField("WELL_KNOWN_FACTORIES"),
                    new String[] { });
        } catch (Exception e) {}
        Properties props = new Properties();
        props.setProperty("username", "testUser");
        props.setProperty("password", "secret");
        DataSource dataSource = null;
        dataSource = instance.getDataSource("jdbc/testDB", props);
        assertNotNull(dataSource);
        assertThat(dataSource, instanceOf(DriverDatasource.class));
        assertThat(((DriverDatasource)dataSource).getDriver(), instanceOf(org.hsqldb.jdbc.JDBCDriver.class));
    }

}
