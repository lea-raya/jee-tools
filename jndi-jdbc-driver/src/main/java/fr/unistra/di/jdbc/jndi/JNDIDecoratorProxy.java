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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.util.Arrays;

import javax.naming.NamingException;
import javax.naming.NoInitialContextException;
import javax.sql.DataSource;

/**
 * Proxy around {@link java.sql.Connection}
 * lazily fetched from a JNDI {@link javax.sql.DataSource} object
 *
 * <p> FIXME : not Thread safe !
 *
 * @author Léa Рая DÉCORNOD <decornod@unistra.fr>
 * @see java.lang.reflect.Proxy#newProxyInstance(ClassLoader, Class[], InvocationHandler)
 * @see javax.sql.DataSource
 * @see java.sql.Connection
 */
public class JNDIDecoratorProxy implements InvocationHandler {

    private LazyDataSourceFetcher dataSourceFetcher;

    private Object delegate;
    /** use to provide original exception from a lazy call */
    private final SQLException potentialCause;

    public JNDIDecoratorProxy(LazyDataSourceFetcher dsFetcher) {
        this.dataSourceFetcher = dsFetcher;
        this.potentialCause = new SQLNonTransientConnectionException("could not get DataSource");
        // strip this stackframe element (ie this constructor)
        StackTraceElement[] stackTrace = potentialCause.getStackTrace();
        potentialCause.setStackTrace(Arrays.copyOfRange(stackTrace, 1, stackTrace.length));
    }

    /** lazy fetch a {@link DataSource}
     * to lazy get a {@link java.sql.Connection}
     * to delegate on
     * @throws SQLException rethrowed from creation
     * if some {@link NamingException} or {@link SQLException}
     * occurred (attached as cause)
     */
    private Object getDelegate() throws SQLException  {
        if (delegate == null) {
            DataSource jndiDS;
            try {
                jndiDS = dataSourceFetcher.lookup();
            } catch (NamingException jndiEx) {
                throw (SQLException) potentialCause.initCause(jndiEx);
            } catch (SQLException sqlEx) {
                throw (SQLException) potentialCause.initCause(sqlEx);
            }
            try {
                delegate = jndiDS.getConnection();
            } catch (SQLException sqlEx) {
                throw (SQLException) potentialCause.initCause(sqlEx);
            }
        }
        return delegate;
    }

    /** invoke method on delegate object (lazily fetched)
     * @see java.lang.reflect.InvocationHandler#invoke(java.lang.Object, java.lang.reflect.Method, java.lang.Object[])
     */
    public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
        return method.invoke(getDelegate(), args);
    }
}
