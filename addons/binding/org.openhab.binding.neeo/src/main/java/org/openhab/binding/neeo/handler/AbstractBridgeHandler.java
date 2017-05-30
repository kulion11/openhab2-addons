/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.neeo.handler;

import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.BridgeHandler;
import org.openhab.binding.neeo.internal.NeeoBrainApi;

/**
 * An abstract extension of {@link BaseBridgeHandler} that will add utility functions to all subclasses.
 *
 * @author Tim Roberts - initial contribution
 */
abstract class AbstractBridgeHandler extends BaseBridgeHandler {

    /**
     * Instantiates a new abstract bridge handler from the given {@link Bridge}
     *
     * @param bridge the bridge
     */
    public AbstractBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    /**
     * Returns the {@link NeeoBrainApi} associated with this {@link Bridge}. Will iterate through
     * the parent handlers until one returns a {@link NeeoBrainApi} or null if not found
     *
     * @return the {@link NeeoBrainApi} or null if not found
     */
    public NeeoBrainApi getNeeoBrainApi() {
        final Bridge bridge = getBridge();
        if (bridge != null) {
            final BridgeHandler handler = bridge.getHandler();
            if (handler instanceof AbstractBridgeHandler) {
                return ((AbstractBridgeHandler) handler).getNeeoBrainApi();
            }
        }
        return null;
    }

    /**
     * Returns the brain ID associated with this {@link Bridge}. Will iterate through
     * the parent handlers until one returns an ID or null if not found
     *
     * @return the brain ID or null if not found
     */
    public String getNeeoBrainId() {
        final Bridge bridge = getBridge();
        if (bridge != null) {
            final BridgeHandler handler = bridge.getHandler();
            if (handler instanceof AbstractBridgeHandler) {
                return ((AbstractBridgeHandler) handler).getNeeoBrainId();
            }
        }
        return null;
    }
}
