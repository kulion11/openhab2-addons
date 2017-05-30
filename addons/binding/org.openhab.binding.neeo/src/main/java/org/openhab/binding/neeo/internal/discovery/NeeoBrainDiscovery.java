/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.neeo.internal.discovery;

import static org.openhab.binding.neeo.NeeoConstants.BRIDGE_TYPE_BRAIN;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.jmdns.ServiceInfo;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.io.transport.mdns.discovery.MDNSDiscoveryParticipant;
import org.openhab.binding.neeo.NeeoConstants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link MDNSDiscoveryParticipant} that will discover NEEO brain(s).
 *
 * @author Tim Roberts - initial contribution
 */
@Component(service = MDNSDiscoveryParticipant.class, immediate = true)
public class NeeoBrainDiscovery implements MDNSDiscoveryParticipant {

    /** The logger */
    private Logger logger = LoggerFactory.getLogger(NeeoBrainDiscovery.class);

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Collections.singleton(BRIDGE_TYPE_BRAIN);
    }

    @Override
    public String getServiceType() {
        return NeeoConstants.NEEO_MDNS_TYPE;
    }

    @Override
    public DiscoveryResult createResult(ServiceInfo service) {
        final ThingUID uid = getThingUID(service);
        if (uid == null) {
            return null;
        }
        logger.debug("createResult is evaluating: {}", service);

        final Map<String, Object> properties = new HashMap<>(2);

        final InetAddress ip = getIpAddress(service);
        if (ip == null) {
            logger.debug("Application not 'neeo' in MDNS serviceinfo: {}", service);
            return null;
        }
        final String inetAddress = ip.getHostAddress();

        final String id = uid.getId();
        final String label = service.getName() + " (" + id + ")";

        properties.put(NeeoConstants.CONFIG_IPADDRESS, inetAddress);
        properties.put(NeeoConstants.CONFIG_ENABLEFORWARDACTIONS, true);

        logger.debug("Adding NEEO Brain to inbox: {} at {}", id, inetAddress);
        return DiscoveryResultBuilder.create(uid).withProperties(properties).withLabel(label).build();
    }

    @Override
    public ThingUID getThingUID(ServiceInfo service) {
        logger.debug("getThingUID is evaluating: {}", service);
        if (!StringUtils.equals("neeo", service.getApplication())) {
            logger.debug("Application not 'neeo' in MDNS serviceinfo: {}", service);
            return null;
        }

        if (getIpAddress(service) == null) {
            logger.debug("No IP address found in MDNS serviceinfo: {}", service);
            return null;
        }

        String model = service.getPropertyString("hon"); // model
        if (model == null) {
            final String server = service.getServer(); // NEEO-xxxxx.local.
            if (server != null) {
                final int idx = server.indexOf(".");
                if (idx >= 0) {
                    model = server.substring(0, idx);
                }
            }
        }
        if (model == null || model.length() <= 5 || !model.toLowerCase().startsWith("neeo")) {
            logger.debug("No 'hon' found in MDNS serviceinfo: {}", service);
            return null;
        }

        final String id = model.substring(5);
        logger.debug("NEEO Brain Found: {}", id);

        return new ThingUID(BRIDGE_TYPE_BRAIN, id);
    }

    /**
     * Gets the ip address found in the {@link ServiceInfo}
     *
     * @param service a non-null service
     * @return the ip address of the service or null if none found.
     */
    private InetAddress getIpAddress(ServiceInfo service) {
        Objects.requireNonNull(service);

        for (String addr : service.getHostAddresses()) {
            try {
                return InetAddress.getByName(addr);
            } catch (UnknownHostException e) {
                // ignore
            }
        }

        for (InetAddress addr : service.getInet4Addresses()) {
            return addr;
        }
        // Fallback for Inet6addresses
        for (InetAddress addr : service.getInet6Addresses()) {
            return addr;
        }
        return null;
    }
}
