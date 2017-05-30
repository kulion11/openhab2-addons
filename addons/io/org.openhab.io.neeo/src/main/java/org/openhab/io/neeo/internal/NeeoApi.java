/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.io.neeo.internal;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.smarthome.core.common.ThreadPoolManager;
import org.eclipse.smarthome.core.id.InstanceUUID;
import org.eclipse.smarthome.core.net.HttpServiceUtil;
import org.openhab.io.neeo.NeeoConstants;
import org.openhab.io.neeo.internal.models.NeeoAdapterRegistration;
import org.openhab.io.neeo.internal.models.NeeoRecipe;
import org.openhab.io.neeo.internal.models.NeeoRecipeUrls;
import org.openhab.io.neeo.internal.models.NeeoSystemInfo;
import org.openhab.io.neeo.internal.net.HttpRequest;
import org.openhab.io.neeo.internal.net.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * The class provides the API for communicating with a NEEO brain
 *
 * @author Tim Roberts - Initial contribution
 */
public class NeeoApi implements AutoCloseable {

    /** The property name used for connection change notifications */
    public static final String CONNECTED = "connected";

    /** The logger */
    private final Logger logger = LoggerFactory.getLogger(NeeoApi.class);

    /** The gson used in communications */
    private static final Gson gson = NeeoUtil.createGson();

    /** The brain's IP address */
    private final String brainIpAddress;

    /** The URL of the brain */
    private final String brainUrl;

    /** The brain identifier */
    private final String brainId;

    /** The {@link ServiceContext} to use */
    private final ServiceContext context;

    /** The {@link HttpRequest} used for making requests */
    private final AtomicReference<HttpRequest> request = new AtomicReference<>(null);

    /** The known device keys on the brain. */
    private final NeeoDeviceKeys deviceKeys;

    /** The scheduler used to schedule tasks */
    private final ScheduledExecutorService scheduler = ThreadPoolManager
            .getScheduledPool(NeeoConstants.THREAD_POOL_NAME);

    /** The connection task (not-null when connecting, null otherwise) */
    private final AtomicReference<Future<?>> connect = new AtomicReference<>(null);

    /** The check status task (not-null when connecting, null otherwise) */
    private final AtomicReference<ScheduledFuture<?>> checkStatus = new AtomicReference<>(null);

    /** Whether the brain is currently connected */
    private final AtomicBoolean connected = new AtomicBoolean(false);

    /** The callback url registered with the brain */
    private final AtomicReference<URL> callbackUrl = new AtomicReference<>(null);

    /** The property change support */
    private final PropertyChangeSupport propertySupport = new PropertyChangeSupport(this);

    /** The interval, in seconds, to do a check status call */
    private final int checkStatusInterval;

    /** The ansi escape color conversion */
    private static final Map<Integer, String> ansiColors = new HashMap<Integer, String>() {
        {
            put(0, "black");
            put(1, "red");
            put(2, "green");
            put(3, "yellow");
            put(4, "blue");
            put(5, "magenta");
            put(6, "cyan");
            put(7, "white");
        }
    };

    /**
     * Constructs the APi from the given IP address, brain identifier and {@link ServiceContext}
     *
     * @param ipAddress the non-empty ip address
     * @param brainId the non-empty brain id
     * @param context the non-null {@link ServiceContext}
     */
    public NeeoApi(String ipAddress, String brainId, ServiceContext context) {
        NeeoUtil.requireNotEmpty(ipAddress, "ipAddress cannot be empty");
        NeeoUtil.requireNotEmpty(brainId, "brainId cannot be empty");
        Objects.requireNonNull(context, "context cannot be null");

        this.context = context;
        this.brainIpAddress = ipAddress;
        this.brainId = brainId;
        this.brainUrl = NeeoConstants.PROTOCOL + (ipAddress.startsWith("/") ? ipAddress.substring(1) : ipAddress) + ":"
                + NeeoConstants.DEFAULT_BRAIN_PORT;
        deviceKeys = new NeeoDeviceKeys(brainUrl);

        final Object statusCheck = context.getComponentContext().getProperties()
                .get(NeeoConstants.CFG_CHECKSTATUSINTERVAL);
        int checkStatus = 10;
        if (statusCheck != null) {
            try {
                checkStatus = Integer.parseInt(statusCheck.toString());
            } catch (NumberFormatException e) {
                logger.debug("{} was not a valid integer, defaulting to 10: {}", NeeoConstants.CFG_CHECKSTATUSINTERVAL,
                        statusCheck);
            }
        }
        checkStatusInterval = checkStatus;
    }

    /**
     * Helper method to retrieve the {@link NeeoSystemInfo} from the given ip address
     *
     * @param ipAddress the non-empty ip address
     * @return the non-null {@link NeeoSystemInfo} for the address
     * @throws IOException Signals that an I/O exception has occurred or the URL is not a brain
     */
    public static NeeoSystemInfo getSystemInfo(String ipAddress) throws IOException {
        NeeoUtil.requireNotEmpty(ipAddress, "ipAddress cannot be empty");
        final String sysInfo = NeeoConstants.PROTOCOL + (ipAddress.startsWith("/") ? ipAddress.substring(1) : ipAddress)
                + ":" + NeeoConstants.DEFAULT_BRAIN_PORT + NeeoConstants.SYSTEMINFO;

        try (HttpRequest req = new HttpRequest()) {
            final HttpResponse res = req.sendGetCommand(sysInfo);
            if (res.getHttpCode() == HttpStatus.OK_200) {
                return gson.fromJson(res.getContent(), NeeoSystemInfo.class);
            } else {
                throw res.createException();
            }
        }
    }

    /**
     * Helper method to blink the LED on the given brain IP address
     *
     * @throws IOException Signals that an I/O exception has occurred or the URL is not a brain
     */
    public void blinkLed() throws IOException {
        final String identBrain = NeeoConstants.PROTOCOL
                + (brainIpAddress.startsWith("/") ? brainIpAddress.substring(1) : brainIpAddress) + ":"
                + NeeoConstants.DEFAULT_BRAIN_PORT + NeeoConstants.IDENTBRAIN;

        try (HttpRequest req = new HttpRequest()) {
            final HttpResponse res = req.sendGetCommand(identBrain);
            if (res.getHttpCode() != HttpStatus.OK_200) {
                throw res.createException();
            }
        }
    }

    /**
     * Helper method to get the log file from the brain, convert the ANSI escaped result to HTML and return it
     *
     * @throws IOException Signals that an I/O exception has occurred or the URL is not a brain
     */
    public String getLog() throws IOException {
        final String logUrl = NeeoConstants.PROTOCOL
                + (brainIpAddress.startsWith("/") ? brainIpAddress.substring(1) : brainIpAddress) + ":"
                + NeeoConstants.DEFAULT_BRAIN_PORT + NeeoConstants.GETLOG;

        try (HttpRequest req = new HttpRequest()) {
            final HttpResponse res = req.sendGetCommand(logUrl);
            if (res.getHttpCode() != HttpStatus.OK_200) {
                throw res.createException();
            }

            final StringBuilder bld = new StringBuilder(1000);
            bld.append("<pre><div><span>");

            final char[] resp = res.getContent().toCharArray();
            for (int x = 0; x < resp.length; x++) {
                final char ch = resp[x];
                final char nx = x + 1 == resp.length ? '-' : resp[x + 1];

                if ((ch == '\n' || ch == '\r')) {
                    if (x + 1 < resp.length) {
                        bld.append("</span></div><div><span>");
                    }
                } else if (ch == 27 && nx == '[') {
                    bld.append("</span>");
                    x++;
                    String codes = "";
                    while (x + 1 < resp.length && resp[++x] != 'm') {
                        codes += resp[x];
                    }

                    String style = "";
                    for (String code : codes.split(";")) {
                        int cint = Integer.parseInt(code);
                        if (cint == 0) {
                            style = "";
                        } else if (cint == 1) {
                            style += "font-weight:bold;";
                        } else if (cint == 4) {
                            style += "font-style:italic;";
                        } else if (cint >= 30 && cint <= 37) {
                            style += "color:" + ansiColors.get(cint - 30);
                        } else if (cint >= 40 && cint <= 47) {
                            style += "background-color:" + ansiColors.get(cint - 40);
                        } else {
                            // don't worry about it
                        }
                    }
                    bld.append("<span style='" + style + "'>");
                } else {
                    bld.append(ch);
                }
                if (x + 1 == resp.length) {
                    bld.append("</span></div></pre>");
                }
            }
            return bld.toString();
        }
    }

    /**
     * Start the API by scheduling the connection via {@link #scheduleConnect()}
     */
    public void start() {
        scheduleConnect();
    }

    /**
     * Checks to see if the API is currently connected or not
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Schedules a connection attempt (will cancel any prior connection attempt if active)l
     */
    private void scheduleConnect() {
        NeeoUtil.cancel(connect.getAndSet(scheduler.schedule(() -> {
            connect();
        }, 5, TimeUnit.SECONDS)));
    }

    /**
     * Schedules a check status attempt (will cancel any prior connection attempt if active)l
     */
    private void scheduleCheckStatus() {
        NeeoUtil.cancel(checkStatus.getAndSet(scheduler.scheduleWithFixedDelay(() -> {
            checkStatus();
        }, checkStatusInterval, checkStatusInterval, TimeUnit.SECONDS)));
    }

    /**
     * Checks the status of the brain via a quick socket connection. If the status is unavailable, we schedule a
     * connection attempt via {@link #scheduleConnect()} and end this task
     */
    private void checkStatus() {
        try {
            try (Socket soc = new Socket()) {
                soc.connect(new InetSocketAddress(brainIpAddress, NeeoConstants.DEFAULT_BRAIN_PORT), 5000);
            }
            logger.debug("Checking connectivity to {}:{} - successful", brainIpAddress,
                    NeeoConstants.DEFAULT_BRAIN_PORT);
        } catch (IOException ex) {
            logger.debug("Checking connectivity to {}:{} - unsuccessful - going offline: {}", brainIpAddress,
                    NeeoConstants.DEFAULT_BRAIN_PORT, ex.getMessage(), ex);
            setConnected(false);
            NeeoUtil.cancel(checkStatus.getAndSet(null));
            scheduleConnect();
        }
    }

    /**
     * Attempts to connect to the brain by first registering our API ({@link #registerApi()} and then refreshes our
     * device keys found on the brain.
     * Any IOException will be caught and another attempt will be scheduled
     */
    private void connect() {
        logger.debug("Starting connect for {} at {}", brainId, brainIpAddress);
        try {
            setConnected(false);

            NeeoUtil.close(request.getAndSet(new HttpRequest()));

            final URL url = createCallbackUrl();
            if (url == null) {
                logger.debug(
                        "Unable to create a callback URL because there is no primary address specified (please set the primary address in the configuration)");
                return; // end connection
            }
            callbackUrl.set(url);

            NeeoUtil.checkInterrupt();
            registerApi();

            NeeoUtil.checkInterrupt();
            deviceKeys.refresh();

            NeeoUtil.checkInterrupt();
            setConnected(true);
            logger.debug("Connection successful for {} at {}", brainId, brainIpAddress);

            NeeoUtil.checkInterrupt();
            scheduleCheckStatus();
        } catch (IOException e) {
            logger.debug("Connection failed {} at {} (scheduling another connect): {} ", brainId, brainIpAddress,
                    e.getMessage(), e);
            scheduleConnect();
        } catch (InterruptedException e) {
            logger.debug("Connect was interrupted", e);
        } catch (Throwable t) {
            logger.error("Connect had an exception", t);
            scheduleConnect();
        }
    }

    /**
     * Register our API with the brain
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    private void registerApi() throws IOException {
        deregisterApi();

        final URL url = callbackUrl.get();
        if (url == null) {
            throw new IOException("callbackUrl has not been set yet");
        }

        final HttpRequest rqst = request.get();
        if (rqst == null) {
            throw new IOException("request cannot be null");
        }

        logger.debug("Registering {} for {}{} using callback {}", brainId, brainUrl, NeeoConstants.REGISTER_SDK_ADAPTER,
                url);
        final String register = gson.toJson(new NeeoAdapterRegistration(
                NeeoConstants.ADAPTER_NAME + "-" + InstanceUUID.get(), url.toExternalForm()));
        final HttpResponse resp = rqst.sendPostJsonCommand(brainUrl + NeeoConstants.REGISTER_SDK_ADAPTER, register);
        if (resp.getHttpCode() != HttpStatus.OK_200) {
            throw resp.createException();
        }
    }

    /**
     * Get's the brain identifier for this API
     *
     * @return the brain id
     */
    public String getBrainId() {
        return brainId;
    }

    /**
     * Gets the brain URL for this API
     *
     * @return the brain url
     */
    public String getBrainUrl() {
        return brainUrl;
    }

    /**
     * Gets the {@link NeeoDeviceKeys}
     *
     * @return the device keys
     */
    public NeeoDeviceKeys getDeviceKeys() {
        return deviceKeys;
    }

    /**
     * Send a notification to the brain
     *
     * @param msg the possibly null, possibly empty notification to send
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void notify(String msg) throws IOException {
        if (connected.get()) {
            final HttpRequest rqst = request.get();
            if (rqst == null) {
                throw new IOException("request cannot be null");
            }

            logger.debug("Sending Notification to brain ({}): {}", brainId, msg);
            final HttpResponse resp = rqst.sendPostJsonCommand(brainUrl + NeeoConstants.NOTIFICATION, msg);
            if (resp.getHttpCode() != HttpStatus.OK_200) {
                throw resp.createException();
            }
        } else {
            logger.debug("Notification ignored - brain not connected");
        }
    }

    /**
     * Deregister our API with the brain.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    private void deregisterApi() throws IOException {
        final URL url = callbackUrl.get();
        if (url != null) {
            final HttpRequest rqst = request.get();
            if (rqst == null) {
                throw new IOException("request cannot be null");
            }

            try {
                logger.debug("Deregistering {} for {}{} using callback {}", brainId, brainUrl,
                        NeeoConstants.UNREGISTER_SDK_ADAPTER, url);
                final String deregister = gson.toJson(new NeeoAdapterRegistration(
                        NeeoConstants.ADAPTER_NAME + "-" + InstanceUUID.get(), url.toExternalForm()));
                final HttpResponse resp = rqst.sendPostJsonCommand(brainUrl + NeeoConstants.UNREGISTER_SDK_ADAPTER,
                        deregister);
                if (resp.getHttpCode() != HttpStatus.OK_200) {
                    throw resp.createException();
                }
            } finally {
                NeeoUtil.cancel(checkStatus.getAndSet(null));
            }
        }
    }

    /**
     * Executes a recipe for the given deviceKey
     *
     * @param deviceKey the non-empty device key
     * @param on true to start the recipe, false to stop
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void executeRecipe(String deviceKey, boolean on) throws IOException {
        NeeoUtil.requireNotEmpty(deviceKey, "deviceKey cannot be empty");

        final HttpRequest rqst = request.get();
        if (rqst == null) {
            throw new IOException("request cannot be null");
        }

        final HttpResponse resp = rqst.sendGetCommand(brainUrl + NeeoConstants.RECIPES);
        if (resp.getHttpCode() != HttpStatus.OK_200) {
            throw resp.createException();
        }

        for (NeeoRecipe recipe : gson.fromJson(resp.getContent(), NeeoRecipe[].class)) {
            if (StringUtils.equalsIgnoreCase(recipe.getUid(), deviceKey)) {
                final NeeoRecipeUrls urls = recipe.getUrls();
                final String url = urls == null ? null : (on ? urls.getSetPowerOn() : urls.getSetPowerOff());

                if (StringUtils.isNotEmpty(url)) {
                    final HttpResponse cmdResp = rqst.sendGetCommand(url);
                    if (cmdResp.getHttpCode() != HttpStatus.OK_200) {
                        throw cmdResp.createException();
                    }
                }
                break;
            }
        }
    }

    /**
     * Simply cancels any connection attempt and de-registers the API.
     */
    @Override
    public void close() {
        // kill our threads
        NeeoUtil.cancel(checkStatus.getAndSet(null));
        NeeoUtil.cancel(connect.getAndSet(null));

        try {
            deregisterApi();
        } catch (IOException e) {
            logger.debug("Exception while deregistring api during close - ignoring: {}", e.getMessage(), e);
        } finally {
            // Do this regardless if a runtime exception was thrown
            NeeoUtil.close(request.getAndSet(null));
            callbackUrl.set(null);
            setConnected(false);
        }

    }

    /**
     * Adds a {@link PropertyChangeListener} for the given propertyChange
     *
     * @param propertyName a non-null, non-empty property name
     * @param listener a non-null listener to add
     */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        NeeoUtil.requireNotEmpty(propertyName, "propertyName must not be empty");
        Objects.requireNonNull(listener, "listener cannot be null");
        propertySupport.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Removes the {@link PropertyChangeListener} from property change notifications
     *
     * @param listener a non-null listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        propertySupport.removePropertyChangeListener(listener);
    }

    /**
     * Helper method to set the connected status and fire off change listener
     *
     * @param connected true if connected, false otherwise
     */
    private void setConnected(boolean connected) {
        final boolean oldValue = this.connected.getAndSet(connected);

        if (!connected) {
            callbackUrl.set(null);
        }

        if (oldValue != connected) {
            propertySupport.firePropertyChange(CONNECTED, oldValue, connected);
        }
    }

    /**
     * Creates the URL the brain should callback. Note: if there is multiple interfaces, we try to prefer the one on the
     * same subnet as the brain
     *
     * @return the callback URL
     * @throws MalformedURLException if the URL is malformed
     */
    private URL createCallbackUrl() throws MalformedURLException {
        // Get the port the service is listening on
        final int port = HttpServiceUtil.getHttpServicePort(context.getComponentContext().getBundleContext());
        if (port == -1) {
            logger.debug("Cannot find port of the http service.");
            return null;
        }

        final String ipAddress = context.getNetworkAddressService().getPrimaryIpv4HostAddress();
        if (ipAddress == null) {
            logger.debug("No network interface could be found.");
            return null;
        }

        return new URL("http://" + ipAddress + ":" + port + NeeoUtil.getServletUrl(brainId));
    }
}