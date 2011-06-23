/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.observation.remote.internal.jgroups;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.management.MBeanServer;

import org.jgroups.ChannelException;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.conf.ConfiguratorFactory;
import org.jgroups.conf.ProtocolStackConfigurator;
import org.jgroups.conf.XmlConfigurator;
import org.jgroups.jmx.JmxConfigurator;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.container.ApplicationContext;
import org.xwiki.container.Container;
import org.xwiki.observation.remote.NetworkAdapter;
import org.xwiki.observation.remote.RemoteEventData;
import org.xwiki.observation.remote.RemoteEventException;
import org.xwiki.observation.remote.jgroups.JGroupsReceiver;

/**
 * JGroups based implementation of {@link NetworkAdapter}.
 * 
 * @version $Id$
 * @since 2.0RC1
 */
@Component
@Named("jgroups")
@Singleton
public class JGroupsNetworkAdapter implements NetworkAdapter
{
    /**
     * Relative path where to find jgroups channels configurations.
     */
    public static final String CONFIGURATION_PATH = "observation/remote/jgroups/";

    /**
     * Used to lookup the receiver corresponding to the channel identifier.
     */
    @Inject
    private ComponentManager componentManager;

    /**
     * The logger to log.
     */
    @Inject
    private Logger logger;

    /**
     * The network channels.
     */
    private Map<String, JChannel> channels = new ConcurrentHashMap<String, JChannel>();

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.observation.remote.NetworkAdapter#send(org.xwiki.observation.remote.RemoteEventData)
     */
    public void send(RemoteEventData remoteEvent)
    {
        this.logger.debug("Send JGroups remote event [" + remoteEvent + "]");

        // Send the message to the whole group
        Message message = new Message(null, null, remoteEvent);

        // Send message to jgroups channels
        for (Map.Entry<String, JChannel> entry : this.channels.entrySet()) {
            try {
                entry.getValue().send(message);
            } catch (Exception e) {
                this.logger.error("Failed to send message [" + remoteEvent + "] to the channel [" + entry.getKey()
                    + "]", e);
            }
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.observation.remote.NetworkAdapter#startChannel(java.lang.String)
     */
    public void startChannel(String channelId) throws RemoteEventException
    {
        if (this.channels.containsKey(channelId)) {
            throw new RemoteEventException(MessageFormat.format("Channel [{0}] already started", channelId));
        }

        JChannel channel;
        try {
            channel = createChannel(channelId);
            channel.connect("event");

            this.channels.put(channelId, channel);
        } catch (Exception e) {
            throw new RemoteEventException("Failed to create channel [" + channelId + "]", e);
        }

        // Register the channel against the JMX Server
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            JmxConfigurator.registerChannel(channel, mbs, channel.getClusterName());
        } catch (Exception e) {
            this.logger.warn("Failed to register channel [" + channelId + "] against the JMX Server", e);
        }

        this.logger.info(MessageFormat.format("Channel [{0}] started", channelId));
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.observation.remote.NetworkAdapter#stopChannel(java.lang.String)
     */
    public void stopChannel(String channelId) throws RemoteEventException
    {
        JChannel channel = this.channels.get(channelId);

        if (channel == null) {
            throw new RemoteEventException(MessageFormat.format("Channel [{0}] is not started", channelId));
        }

        channel.close();

        this.channels.remove(channelId);

        // Unregister the channel from the JMX Server
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            JmxConfigurator.unregister(channel, mbs, channel.getClusterName());
        } catch (Exception e) {
            this.logger.warn("Failed to unregister channel [" + channelId + "] from the JMX Server", e);
        }

        this.logger.info(MessageFormat.format("Channel [{0}] stopped", channelId));
    }

    /**
     * Create a new channel.
     * 
     * @param channelId the identifier of the channel to create
     * @return the new channel
     * @throws ComponentLookupException failed to get default {@link JGroupsReceiver}
     * @throws ChannelException failed to create channel
     */
    private JChannel createChannel(String channelId) throws ComponentLookupException, ChannelException
    {
        // load configuration
        ProtocolStackConfigurator channelConf;
        try {
            channelConf = loadChannelConfiguration(channelId);
        } catch (IOException e) {
            throw new ChannelException("Failed to load configuration for the channel [" + channelId + "]", e);
        }

        // get Receiver
        JGroupsReceiver channelReceiver;
        try {
            channelReceiver = this.componentManager.lookup(JGroupsReceiver.class, channelId);
        } catch (ComponentLookupException e) {
            channelReceiver = this.componentManager.lookup(JGroupsReceiver.class);
        }

        // create channel
        JChannel channel = new JChannel(channelConf);

        channel.setReceiver(channelReceiver);
        channel.setOpt(JChannel.LOCAL, false);

        return channel;
    }

    /**
     * Load channel configuration.
     * 
     * @param channelId the identifier of the channel
     * @return the channel configuration
     * @throws IOException failed to load configuration file
     * @throws ChannelException failed to creation channel configuration
     */
    private ProtocolStackConfigurator loadChannelConfiguration(String channelId) throws IOException, ChannelException
    {
        String channelFile = channelId + ".xml";
        String path = "/WEB-INF/" + CONFIGURATION_PATH + channelFile;

        InputStream is = null;
        try {
            Container container = this.componentManager.lookup(Container.class);
            ApplicationContext applicationContext = container.getApplicationContext();
           
            if (applicationContext != null) {
                is = applicationContext.getResourceAsStream(path);
            }
        } catch (ComponentLookupException e) {
            this.logger.debug("Failed to lookup Container component.");
        }

        if (is == null) {
            // Fallback on JGroups standard configuraton locations
            is = ConfiguratorFactory.getConfigStream(channelFile);

            if (is == null && !JChannel.DEFAULT_PROTOCOL_STACK.equals(channelFile)) {
                // Fallback on default JGroups configuration
                is = ConfiguratorFactory.getConfigStream(JChannel.DEFAULT_PROTOCOL_STACK);
            }
        }

        return XmlConfigurator.getInstance(is);
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.observation.remote.NetworkAdapter#stopAllChannels()
     */
    public void stopAllChannels() throws RemoteEventException
    {
        for (Map.Entry<String, JChannel> channelEntry : this.channels.entrySet()) {
            channelEntry.getValue().close();
        }

        this.channels.clear();

        this.logger.info("All channels stopped");
    }
}
