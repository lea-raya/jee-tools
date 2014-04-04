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

import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;
import javax.sql.DataSource;

import fr.unistra.di.jdbc.jndi.fallback.FallbackStrategy;

public class LazyDataSourceFetcher {

    /** JNDI Name to lookup for */
    private String jndiName;
    /** JDBC properties */
    private Properties info;
    /** {@link DataSource} object cache */
    private DataSource dataSource;

    /**
     * @param name JNDI Name to look for
     * @param info JDBC Properties
     * @param fallbackStrategies Strategies to use if no JNDI Context available
     */
    public LazyDataSourceFetcher(String name, Properties info) {
        this.jndiName = name;
        this.info = info;
        this.dataSource = null;
    }

    /**
     * Lazily get a {@link DataSource} object
     * either from JEE Container
     * using a {@link InitialContext#lookup(String) JNDI Lookup},
     * or from the first matching {@link FallbackStrategy}
     * <p><b>FIXME:</b> not Thread Safe !
     * @return lazily fetched {@link DataSource} object
     * @throws NamingException in case of JNDI error from JEE Container
     * @throws SQLException the first exception from FallbackStrategies
     * @throws NoInitialContextException in last resort (no FallbackStrategy for ex)
     * @see InitialContext#lookup(String)
     * @see FallbackStrategy#getDataSource(String, Properties)
     */
    public DataSource getDataSource() throws NamingException, SQLException {
        if (dataSource == null) {
            dataSource = lookup();
        }
        return dataSource;
    }

    /**
     * JNDI lookup for a {@link DataSource} object
     * @throws NamingException
     * @throws NoInitialContextException
     * @throws SQLException
     */
    protected DataSource lookup() throws NamingException, SQLException {
        try {
            // fetch JNDI object from JEE container
            InitialContext ctx = new InitialContext();
            return (DataSource)ctx.lookup(jndiName);
        } catch (NoInitialContextException noContextEx) {
            // try fallbacks strategies
            // and returns the first non-null match
            // or re-throws the first Exception
            // or re-throws NoInitialContextException
            SQLException failure = null;
            ServiceLoader<FallbackStrategy> fallbackStrategies = ServiceLoader.load(FallbackStrategy.class);
            for (FallbackStrategy strategy : fallbackStrategies)
                try {
                    DataSource ds = strategy.getDataSource(jndiName, info);
                    if (ds != null)
                        return ds;
                } catch (SQLException ex) {
                    if (failure == null)
                        failure = ex;
                }
            if (failure != null)
                throw failure;
            else
                throw noContextEx;
        }
    }
}