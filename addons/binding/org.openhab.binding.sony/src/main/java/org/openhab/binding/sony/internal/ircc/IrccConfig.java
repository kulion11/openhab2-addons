/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.sony.internal.ircc;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.sony.internal.simpleip.SimpleIpHandler;

// TODO: Auto-generated Javadoc
/**
 * Configuration class for the {@link SimpleIpHandler}.
 *
 * @author Tim Roberts - Initial contribution
 */
public class IrccConfig {

    /** The Constant AccessCode. */
    public static final String AccessCode = "accessCode";

    /** The access code. */
    private String accessCode;

    /** The Constant DeviceMacAddress. */
    public static final String DeviceMacAddress = "deviceMacAddress";

    /** The device mac address. */
    private String deviceMacAddress;

    /** The network interface the system listens on (eth0 or wlan0). */
    public static final String IrccUri = "irccUri";

    /** The ircc uri. */
    private String irccUri;

    /** The Constant CommandsMapFile. */
    public static final String CommandsMapFile = "commandsMapFile";

    /** The commands map file. */
    private String commandsMapFile;

    /** Refresh time (in seconds) to refresh attributes from the system. */
    public static final String Refresh = "refresh";

    /** The refresh. */
    private int refresh;

    /** The retry polling. */
    private int retryPolling;

    /**
     * Returns the IP address or host name.
     *
     * @return the IP address or host name
     */
    public String getIpAddress() {
        try {
            return new URI(irccUri).getHost();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * Returns the refresh interval (in seconds).
     *
     * @return the refresh interval (in seconds)
     */
    public int getRefresh() {
        return refresh;
    }

    /**
     * Sets the refresh interval (in seconds).
     *
     * @param refresh the refresh interval (in seconds)
     */
    public void setRefresh(int refresh) {
        this.refresh = refresh;
    }

    /**
     * Gets the access code.
     *
     * @return the access code
     */
    public String getAccessCode() {
        return accessCode;
    }

    /**
     * Gets the access code nbr.
     *
     * @return the access code nbr
     */
    public Integer getAccessCodeNbr() {
        if (StringUtils.isEmpty(accessCode)) {
            return null;
        }

        try {
            return Integer.parseInt(accessCode);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Sets the access code.
     *
     * @param accessCode the new access code
     */
    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }

    /**
     * Gets the device mac address.
     *
     * @return the device mac address
     */
    public String getDeviceMacAddress() {
        return deviceMacAddress;
    }

    /**
     * Sets the device mac address.
     *
     * @param deviceMacAddress the new device mac address
     */
    public void setDeviceMacAddress(String deviceMacAddress) {
        this.deviceMacAddress = deviceMacAddress;
    }

    /**
     * Gets the ircc uri.
     *
     * @return the ircc uri
     */
    public String getIrccUri() {
        return irccUri;
    }

    /**
     * Sets the ircc uri.
     *
     * @param irccUrl the new ircc uri
     */
    public void setIrccUri(String irccUrl) {
        this.irccUri = irccUrl;
    }

    /**
     * Gets the commands map file.
     *
     * @return the commands map file
     */
    public String getCommandsMapFile() {
        return commandsMapFile;
    }

    /**
     * Sets the commands map file.
     *
     * @param commandsMapFile the new commands map file
     */
    public void setCommandsMapFile(String commandsMapFile) {
        this.commandsMapFile = commandsMapFile;
    }

    /**
     * Checks if is wol.
     *
     * @return true, if is wol
     */
    public boolean isWOL() {
        return !StringUtils.isEmpty(deviceMacAddress);
    }

    /**
     * Gets the retry polling.
     *
     * @return the retry polling
     */
    public int getRetryPolling() {
        return retryPolling;
    }

    /**
     * Sets the retry polling.
     *
     * @param retry the new retry polling
     */
    public void setRetryPolling(int retry) {
        this.retryPolling = retry;
    }
}
