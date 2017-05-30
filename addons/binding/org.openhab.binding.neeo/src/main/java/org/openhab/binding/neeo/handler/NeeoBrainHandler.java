/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.neeo.handler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.core.net.NetworkAddressService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.neeo.NeeoConstants;
import org.openhab.binding.neeo.NeeoUtil;
import org.openhab.binding.neeo.internal.NeeoBrainApi;
import org.openhab.binding.neeo.internal.NeeoBrainConfig;
import org.openhab.binding.neeo.internal.models.NeeoAction;
import org.openhab.binding.neeo.internal.models.NeeoBrain;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * A subclass of {@link AbstractBridgeHandler} is responsible for handling commands and discovery for a
 * {@link NeeoBrain}
 *
 * @author Tim Roberts - Initial contribution
 */
public class NeeoBrainHandler extends AbstractBridgeHandler {

    /** The logger */
    private final Logger logger = LoggerFactory.getLogger(NeeoBrainHandler.class);

    /**
     * The initialization task (null until set by {@link #initializeTask()} and set back to null in {@link #dispose()}
     */
    private final AtomicReference<Future<?>> initializationTask = new AtomicReference<>(null);

    /** The {@link NeeoBrainApi} (null until set by {@link #initializationTask}) */
    private final AtomicReference<NeeoBrainApi> neeoBrainApi = new AtomicReference<>(null);

    /** The {@link NeeoBrain} (null until set by {@link #initializationTask}) */
    private final AtomicReference<NeeoBrain> neeoBrain = new AtomicReference<>(null);

    /** The {@link HttpService} to register callbacks */
    private final HttpService httpService;

    /** The {@link NetworkAddressService} to use */
    private final NetworkAddressService networkAddressService;

    /** The path to the forward action servlet - will be null if not enabled */
    private final AtomicReference<String> servletPath = new AtomicReference<>(null);

    /** The servlet for forward actions - will be null if not enabled */
    private final AtomicReference<NeeoForwardActionsServlet> forwardActionServlet = new AtomicReference<>(null);

    /** The check status task (not-null when connecting, null otherwise) */
    private final AtomicReference<ScheduledFuture<?>> checkStatus = new AtomicReference<>(null);

    /** GSON implementation - only used to deserialize {@link NeeoAction} */
    private final Gson gson = new Gson();

    /** The port the HTTP service is listening on */
    private final int servicePort;

    /**
     * Instantiates a new neeo brain handler from the {@link Bridge}, service port, {@link HttpService} and
     * {@link NetworkAddressService}.
     *
     * @param bridge the non-null {@link Bridge}
     * @param servicePort the service port the http service is listening on
     * @param httpService the non-null {@link HttpService}
     * @param networkAddressService the non-null {@link NetworkAddressService}
     */
    NeeoBrainHandler(Bridge bridge, int servicePort, HttpService httpService,
            NetworkAddressService networkAddressService) {
        super(bridge);

        Objects.requireNonNull(bridge, "bridge cannot be null");
        Objects.requireNonNull(httpService, "httpService cannot be null");
        Objects.requireNonNull(networkAddressService, "networkAddressService cannot be null");

        this.servicePort = servicePort;
        this.httpService = httpService;
        this.networkAddressService = networkAddressService;
    }

    /**
     * Handles any {@Commands} sent - this bridge has no commands and does nothing
     *
     * @see
     *      org.eclipse.smarthome.core.thing.binding.ThingHandler#handleCommand(org.eclipse.smarthome.core.thing.ChannelUID,
     *      org.eclipse.smarthome.core.types.Command)
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    /**
     * Simply cancels any existing initialization tasks and schedules a new task
     *
     * @see org.eclipse.smarthome.core.thing.binding.BaseThingHandler#initialize()
     */
    @Override
    public void initialize() {
        NeeoUtil.cancel(initializationTask.getAndSet(scheduler.submit(() -> {
            initializeTask();
        })));
    }

    /**
     * Initializes the bridge by connecting to the configuration ipaddress and parsing the results. Properties will be
     * set and the thing will go online. Rooms will then be discovered via {@link #startScan()}
     */
    private void initializeTask() {
        try {
            NeeoUtil.checkInterrupt();

            final NeeoBrainConfig config = getBrainConfig();
            final NeeoBrainApi api = new NeeoBrainApi(config.getIpAddress());
            final NeeoBrain brain = api.getBrain();
            final String brainId = getNeeoBrainId();

            NeeoUtil.checkInterrupt();
            neeoBrainApi.getAndSet(api);
            neeoBrain.set(brain);

            final Map<String, String> properties = new HashMap<>();
            properties.put("Name", brain.getName());
            properties.put("Version", brain.getVersion());
            properties.put("Label", brain.getLabel());
            properties.put("Is Configured", String.valueOf(brain.isConfigured()));
            properties.put("Key", brain.getKey());
            properties.put("AirKey", brain.getAirkey());
            properties.put("Last Change", String.valueOf(brain.getLastchange()));
            updateProperties(properties);

            if (config.isEnableForwardActions()) {
                NeeoUtil.checkInterrupt();
                final String path = NeeoConstants.WEBAPP_FORWARDACTIONS.replace("{brainid}", brainId);

                final NeeoForwardActionsServlet servlet = new NeeoForwardActionsServlet(
                        new NeeoForwardActionsServlet.Callback() {
                            @Override
                            public void post(String json) {
                                triggerChannel(NeeoConstants.CHANNEL_BRAIN_FOWARDACTIONS, json);

                                final NeeoAction action = gson.fromJson(json, NeeoAction.class);

                                for (final Thing child : getThing().getThings()) {
                                    final ThingHandler th = child.getHandler();
                                    if (th instanceof NeeoRoomHandler) {
                                        ((NeeoRoomHandler) th).processAction(action);
                                    }
                                }
                            }

                        }, config.getForwardChain());

                final String oldPath = servletPath.getAndSet(path);
                forwardActionServlet.set(servlet);

                NeeoUtil.checkInterrupt();
                try {
                    if (StringUtils.isNotEmpty(oldPath)) {
                        try {
                            httpService.unregister(oldPath);
                        } catch (IllegalArgumentException e) {
                            logger.debug("IAE throw when unregistering '{}' - ignoring: {}", oldPath, e.getMessage(),
                                    e);
                        }
                    }

                    httpService.registerServlet(path, servlet, new Hashtable<>(),
                            httpService.createDefaultHttpContext());

                    final URL callbackURL = createCallbackUrl(brainId, config);
                    if (callbackURL == null) {
                        logger.debug(
                                "Unable to create a callback URL because there is no primary address specified (please set the primary address in the configuration)");
                    } else {
                        final URL url = new URL(callbackURL, path);
                        api.registerForwardActions(url);
                    }
                } catch (NamespaceException | ServletException e) {
                    logger.debug("Error registering forward actions to {}: {}", path, e.getMessage(), e);
                }
            }

            NeeoUtil.checkInterrupt();
            updateStatus(ThingStatus.ONLINE);

            NeeoUtil.checkInterrupt();
            if (config.getCheckStatusInterval() > 0) {
                NeeoUtil.cancel(checkStatus.getAndSet(scheduler.scheduleAtFixedRate(() -> {
                    try {
                        NeeoUtil.checkInterrupt();
                        checkStatus(config.getIpAddress());
                    } catch (InterruptedException e) {
                        // do nothing - we were interrupted and should stop
                    }
                }, config.getCheckStatusInterval(), config.getCheckStatusInterval(), TimeUnit.SECONDS)));
            }
        } catch (IOException e) {
            logger.debug("Exception occurred connecting to brain: {}", e.getMessage(), e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Exception occurred connecting to brain: " + e.getMessage());
        } catch (InterruptedException e) {
            logger.debug("Initializtion was interrupted", e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR,
                    "Initialization was interrupted");
        }
    }

    /**
     * Gets the {@link NeeoBrainApi} used by this bridge
     *
     * @return a possibly null {@link NeeoBrainApi}
     */
    @Override
    public NeeoBrainApi getNeeoBrainApi() {
        return neeoBrainApi.get();
    }

    /**
     * Gets the brain id used by this bridge
     *
     * @return a non-null, non-empty brain id
     */
    @Override
    public String getNeeoBrainId() {
        return getThing().getUID().getId();
    }

    /**
     * Helper method to get the {@link NeeoBrainConfig}
     *
     * @return the {@link NeeoBrainConfig}
     */
    private NeeoBrainConfig getBrainConfig() {
        return getConfigAs(NeeoBrainConfig.class);
    }

    /**
     * Checks the status of the brain via a quick socket connection. If the status is unavailable and we are
     * {@link ThingStatus#ONLINE}, then we go {@link ThingStatus#OFFLINE}. If the status is available and we are
     * {@link ThingStatus#OFFLINE}, we go {@link ThingStatus#ONLINE}.
     *
     * @param ipAddress a non-null, non-empty IP address
     */
    private void checkStatus(String ipAddress) {
        NeeoUtil.requireNotEmpty(ipAddress, "ipAddress cannot be empty");

        try {
            try (Socket soc = new Socket()) {
                soc.connect(new InetSocketAddress(ipAddress, NeeoConstants.DEFAULT_BRAIN_PORT), 5000);
            }
            logger.debug("Checking connectivity to {}:{} - successful", ipAddress, NeeoConstants.DEFAULT_BRAIN_PORT);

            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }
        } catch (IOException e) {
            if (getThing().getStatus() == ThingStatus.ONLINE) {
                logger.debug("Checking connectivity to {}:{} - unsuccessful - going offline: {}", ipAddress,
                        NeeoConstants.DEFAULT_BRAIN_PORT, e.getMessage(), e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Exception occurred connecting to brain: " + e.getMessage());
            } else {
                logger.debug("Checking connectivity to {}:{} - unsuccessful - still offline", ipAddress,
                        NeeoConstants.DEFAULT_BRAIN_PORT);
            }
        }
    }

    /**
     * Disposes of the bridge by closing/removing the {@link #neeoBrainApi} and canceling/removing any pending
     * {@link #initializeTask()}
     *
     * @see org.eclipse.smarthome.core.thing.binding.BaseThingHandler#dispose()
     */
    @Override
    public void dispose() {
        final NeeoBrainApi api = neeoBrainApi.getAndSet(null);
        neeoBrain.set(null);

        NeeoUtil.cancel(initializationTask.getAndSet(null));
        NeeoUtil.cancel(checkStatus.getAndSet(null));

        final NeeoForwardActionsServlet servlet = forwardActionServlet.getAndSet(null);
        if (servlet != null) {
            try {
                api.deregisterForwardActions();
            } catch (IOException e) {
                logger.debug("IOException occurred deregistering the forward actions: {}", e.getMessage(), e);
            }

            final String path = servletPath.getAndSet(null);
            if (path != null) {
                httpService.unregister(path);
            }
        }

        NeeoUtil.close(api);
    }

    /**
     * Creates the URL the brain should callback. Note: if there is multiple interfaces, we try to prefer the one on the
     * same subnet as the brain
     *
     * @param brainId the non-null, non-empty brain identifier
     * @param config the non-null brain configuration
     * @return the callback URL
     * @throws MalformedURLException if the URL is malformed
     */
    private URL createCallbackUrl(String brainId, NeeoBrainConfig config) throws MalformedURLException {
        NeeoUtil.requireNotEmpty(brainId, "brainId cannot be empty");
        Objects.requireNonNull(config, "config cannot be null");

        final String ipAddress = networkAddressService.getPrimaryIpv4HostAddress();
        if (ipAddress == null) {
            logger.debug("No network interface could be found.");
            return null;
        }

        return new URL("http://" + ipAddress + ":" + servicePort);
    }
}
