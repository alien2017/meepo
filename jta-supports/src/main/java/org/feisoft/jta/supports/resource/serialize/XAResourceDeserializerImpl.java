/**
 * Copyright 2014-2018 yangming.liu<bytefox@126.com>.
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, see <http://www.gnu.org/licenses/>.
 */

package org.feisoft.jta.supports.resource.serialize;

import org.feisoft.jta.supports.resource.CommonResourceDescriptor;
import org.feisoft.transaction.supports.resource.XAResourceDescriptor;
import org.feisoft.transaction.supports.serialize.XAResourceDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAResource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class XAResourceDeserializerImpl implements XAResourceDeserializer, ApplicationContextAware {

    static final Logger logger = LoggerFactory.getLogger(XAResourceDeserializerImpl.class);

    private Map<String, XAResourceDescriptor> cachedResourceMap = new ConcurrentHashMap<String, XAResourceDescriptor>();

    private ApplicationContext applicationContext;

    public XAResourceDescriptor deserialize(String identifier) {

        XAResourceDescriptor cachedResource = this.cachedResourceMap.get(identifier);
        if (cachedResource != null) {
            return cachedResource;
        }

        try {
            Object bean = this.applicationContext.getBean(identifier);
            XAResourceDescriptor resolvedResource = this.deserializeResource(identifier, bean);
            if (resolvedResource == null) {
                logger.error("can not find a matching xa-resource(identifier= {})!", identifier);
                return null;
            }

            this.cachedResourceMap.put(identifier, resolvedResource);
            return resolvedResource;
        } catch (BeansException bex) {
            logger.error("can not find a matching xa-resource(identifier= {})!", identifier);
            return null;
        } catch (Exception ex) {
            logger.error("can not find a matching xa-resource(identifier= {})!", identifier, ex);
            return null;
        }

    }

    private XAResourceDescriptor deserializeResource(String identifier, Object bean) throws Exception {
        if (XADataSource.class.isInstance(bean)) {
            XADataSource xaDataSource = (XADataSource) bean;
            XAConnection xaConnection = xaDataSource.getXAConnection();
            java.sql.Connection connection = null;
            try {
                connection = xaConnection.getConnection();
                XAResource xares = xaConnection.getXAResource();

                CommonResourceDescriptor descriptor = new CommonResourceDescriptor();
                descriptor.setDelegate(xares);
                descriptor.setIdentifier(identifier);
                descriptor.setManaged(xaConnection);

                return descriptor;
            } catch (Exception ex) {
                logger.warn(ex.getMessage(), ex);

                XAResource xares = xaConnection.getXAResource();

                CommonResourceDescriptor descriptor = new CommonResourceDescriptor();
                descriptor.setDelegate(xares);
                descriptor.setIdentifier(identifier);
                descriptor.setManaged(xaConnection);

                return descriptor;
            } finally {
                this.closeQuietly(connection);
            }
        } else if (ManagedConnectionFactory.class.isInstance(bean)) {
            ManagedConnectionFactory connectionFactory = (ManagedConnectionFactory) bean;
            ManagedConnection managedConnection = connectionFactory.createManagedConnection(null, null);
            javax.resource.cci.Connection connection = null;
            try {
                connection = (javax.resource.cci.Connection) managedConnection.getConnection(null, null);
                XAResource xares = managedConnection.getXAResource();

                CommonResourceDescriptor descriptor = new CommonResourceDescriptor();
                descriptor.setDelegate(xares);
                descriptor.setIdentifier(identifier);
                descriptor.setManaged(managedConnection);

                return descriptor;
            } catch (Exception ex) {
                logger.warn(ex.getMessage(), ex);

                XAResource xares = managedConnection.getXAResource();

                CommonResourceDescriptor descriptor = new CommonResourceDescriptor();
                descriptor.setDelegate(xares);
                descriptor.setIdentifier(identifier);
                descriptor.setManaged(managedConnection);

                return descriptor;
            } finally {
                this.closeQuietly(connection);
            }
        } else {
            return null;
        }

    }

    protected void closeQuietly(javax.resource.cci.Connection closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ex) {
                logger.debug(ex.getMessage());
            }
        }
    }

    protected void closeQuietly(java.sql.Connection closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ex) {
                logger.debug(ex.getMessage());
            }
        }
    }



    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

}
