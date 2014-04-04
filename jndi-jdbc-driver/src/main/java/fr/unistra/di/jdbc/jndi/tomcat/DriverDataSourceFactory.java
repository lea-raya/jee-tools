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

import java.sql.Driver;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;

/**
 * @author Léa Рая DÉCORNOD <decornod@unistra.fr>
 *
 */
public class DriverDataSourceFactory implements ObjectFactory {

    /* (non-Javadoc)
     * @see javax.naming.spi.ObjectFactory#getObjectInstance(java.lang.Object, javax.naming.Name, javax.naming.Context, java.util.Hashtable)
     */
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
            Hashtable<?, ?> environment) throws Exception {

        // only handle non-null Reference objects
        if ((obj == null) || !(obj instanceof Reference))
            return null;

        Reference ref = (Reference) obj;
        String driverClassName = getAttr(ref, "driverClassName");
        Driver driver = (Driver) Class.forName(driverClassName).newInstance();
        String url = getAttr(ref, "url");

        Properties props = new Properties();
        Enumeration<RefAddr> attrs = ref.getAll();
        while (attrs.hasMoreElements()) {
            RefAddr attr = (RefAddr) attrs.nextElement();
            String key = attr.getType();
            if ("driverClassName".equals(key) || "url".equals(key))
                continue;
            if ("username".equals(key))
                props.put("user", attr.getContent());
            else
                props.put(key, attr.getContent());
        }

        return new DriverDatasource(driver, url, props);
    }

    public static String getAttr(Reference ref, String key)
    {
        RefAddr value = ref.get(key);
        if (value == null)
            return null;
        if (value instanceof StringRefAddr)
            return (String) value.getContent();
        else
            return (String) value.getContent().toString();
    }
}
