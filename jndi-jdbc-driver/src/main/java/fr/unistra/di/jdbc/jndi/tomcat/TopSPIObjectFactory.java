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

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.ServiceLoader;

import javax.naming.ConfigurationException;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.spi.ObjectFactory;
import javax.sql.DataSource;

/**
 * @author Léa Рая DÉCORNOD <decornod@unistra.fr>
 */
public class TopSPIObjectFactory implements ObjectFactory {

    private static final String[] WELL_KNOWN_FACTORIES = new String[] {
        "org.apache.tomcat.jdbc.pool.DataSourceFactory",
        "org.apache.tomcat.dbcp.dbcp.BasicDataSourceFactory",
        //"org.springframework.jdbc.datasource.SimpleDriverDataSource",
    };

    private static ServiceLoader<ObjectFactory> spiLoader = ServiceLoader.load(ObjectFactory.class);
    private static List<ObjectFactory> factories;

    /**
     * Use Java SPI to get a list of {@link ObjectFactory JNDI Object Factories}
     * <p><b>FIXME:</b> Not Thread Safe™ !
     */
    protected List<ObjectFactory> getFactories() throws NamingException {
        if (factories == null) {
            // get Factories list from SPI
            factories = new ArrayList<ObjectFactory>(1);
            for(ObjectFactory spiImpl : spiLoader)
                factories.add(spiImpl);

            // Otherwise provide one by ourselves
            if (factories.isEmpty()) {
                Class<?> factoryClazz = getDefaultFactoryClass();

                // Instantiate one
                ObjectFactory factory;
                if (ObjectFactory.class.isAssignableFrom(factoryClazz))
                    try {
                        factory = (ObjectFactory) factoryClazz.newInstance();
                    } catch (InstantiationException ex) {
                        throw (NamingException) new NamingException().initCause(ex);
                    } catch (IllegalAccessException ex) {
                        throw (NamingException) new NamingException().initCause(ex);
                    }
                //else if (DataSource.class.isAssignableFrom(factoryClazz))
                //    factory = new DataSourceFactory((Class<? extends DataSource>)factoryClazz);
                else
                    throw new ConfigurationException("Unknown Factory Type !");
                factories.add(factory);
            }
        }
        return factories;
    }

    /**
     * Provide the best default {@link ObjectFactory} we can find
     * @return {@link ObjectFactory} or {@link DataSource} class
     */
    protected Class<?> getDefaultFactoryClass() {

        // try -Djavax.sql.DataSource.Factory=my.datasource.factory
        String javaxSqlDataSourceFactoryClazz =
                System.getProperty("javax.sql.DataSource.Factory");
        if (javaxSqlDataSourceFactoryClazz != null)
            try {
                return Class.forName(javaxSqlDataSourceFactoryClazz);
            } catch (ClassNotFoundException e) { }

        // try well known ObjectFactory if available in ClassPath
        for (String clazz : WELL_KNOWN_FACTORIES)
            try {
                return Class.forName(clazz);
            } catch (ClassNotFoundException e) { }

        // or use our Last-Resort one
        return DriverDataSourceFactory.class; // TODO™
    }

    /**
     * Top Level {@link ObjectFactory}
     * @see javax.naming.spi.ObjectFactory#getObjectInstance(java.lang.Object, javax.naming.Name, javax.naming.Context, java.util.Hashtable)
     */
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
          Hashtable<?, ?> environment) throws Exception {

        // use reference if possible
        Reference ref = null;
        if (obj instanceof Reference)
            /* simple cast if possible */
            ref = (Reference) obj;
        else if (obj instanceof Referenceable)
            /* get from factory method if possible */
            obj = ref = ((Referenceable) (obj)).getReference();

        // use provided factoryClassName exclusively if possible
        if (ref != null && ref.getFactoryClassName() != null) {
            ObjectFactory factory = null;
            try {
                Class<?> factoryClazz = Class.forName(ref.getFactoryClassName());
                factory = (ObjectFactory) factoryClazz.newInstance();
            } catch (ClassNotFoundException ex) {
                //throw (NamingException) new NamingException().initCause(ex);
            } catch (ClassCastException ex) {
                //throw (NamingException) new NamingException().initCause(ex);
            }
            if (factory != null) {
                return factory.getObjectInstance(obj, name, nameCtx, environment);
            }
        }

        // try each registered factory 'til one succeed
        for (ObjectFactory factory : getFactories()) {
            Object instance = factory.getObjectInstance(obj, name, nameCtx, environment);
            if (instance != null)
                return instance;
        }

        // no return as we are the top-level ObjectFactory
        throw new NamingException("Cannot create resource instance");
    }
}
