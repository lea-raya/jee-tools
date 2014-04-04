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
package fr.unistra.di.jdbc.jndi;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.logging.Logger;

import fr.unistra.di.jdbc.jndi.fallback.FallbackStrategy;

/* (non-Javadoc)
 * @author Léa Рая DÉCORNOD <decornod@unistra.fr>
 */
public class Driver implements java.sql.Driver {

    private static final String JDBC_JNDI_PREFIX = "jdbc:jndi:";

    private LazyDataSourceFetcher fetcher = null;

    /** JDBC Driver Registration */
    static {
        try {
            DriverManager.registerDriver(new Driver());
        } catch (SQLException ex) {
            logError("DriverManager.registerDriver FAILED : " + ex.getMessage());
            throw new ExceptionInInitializerError(ex);
        }
    }

    /** get cached {@link LazyDataSourceFetcher} instance
     * <p><b>FIXME:</b> Not Thread Safe™ !
     * @return
     */
    public LazyDataSourceFetcher getFetcher(String name, Properties info) {
        if (fetcher == null) {
            fetcher = new LazyDataSourceFetcher(name, info);
        }
        return fetcher;
    }

    /**
     * returns {@link Connection} Proxy object delegating calls
     * to a lazily fetched JNDI {@link javax.sql.DataSource}.
     * <p>{@inheritDoc}
     * @see java.sql.Driver#connect(String, Properties)
     */
    public Connection connect(String url, Properties info) throws SQLException {
        String name;
        try {
            name = getNameFromURL(url, info);
        } catch (SQLException ex) {
            logInfo("WARNING" +  ex.getMessage());
            return null;
        }
        Connection proxyed = (Connection) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { Connection.class },
                new JNDIDecoratorProxy(getFetcher(name, info)));
        return proxyed;
    }

    protected String getNameFromURL(String url, Properties info) throws SQLException {
        if (!acceptsURL(url))
            throw new SQLException("Invalid JDBC JNDI URL (expecting jdbc:jndi:java:comp/env/jdbc/...)");
        String jndiName = url.substring(JDBC_JNDI_PREFIX.length());
        if (! jndiName.startsWith("java:comp/env/jdbc/"))
            logInfo(MessageFormat.format("WARNING: JNDI name {0} don't follow JEE convention java:comp/env/jdbc/...", jndiName));

        return jndiName;
    }

    /* (non-Javadoc)
     * @param message
     */
    private static void logInfo(String message) {
        DriverManager.println(message);
    }

    /* (non-Javadoc)
     * @param message
     */
    private static void logError(String message) {
        PrintWriter log = DriverManager.getLogWriter();
        if (log != null) {
            log.println(message);
        }
    }

    /* (non-Javadoc)
     * @see java.sql.Driver#acceptsURL(java.lang.String)
     */
    public boolean acceptsURL(String url) throws SQLException {
        return url != null && url.startsWith(JDBC_JNDI_PREFIX);
    }

    /* (non-Javadoc)
     * @see java.sql.Driver#getPropertyInfo(java.lang.String, java.util.Properties)
     */
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)
            throws SQLException {
        return new DriverPropertyInfo[]{};
    }

    /* (non-Javadoc)
     * @see java.sql.Driver#getMajorVersion()
     */
    public int getMajorVersion() {
        return 1;
    }

    /* (non-Javadoc)
     * @see java.sql.Driver#getMinorVersion()
     */
    public int getMinorVersion() {
        return 0;
    }

    /* (non-Javadoc)
     * @see java.sql.Driver#jdbcCompliant()
     */
    public boolean jdbcCompliant() {
        return false;
    }

    /* (non-Javadoc)
     * @see java.sql.Driver#getParentLogger()
     */
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

}
