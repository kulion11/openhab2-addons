/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.neeo.internal.models;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * The implementation of {@link JsonDeserializer} to deserialize a {@link NeeoDevices} class
 *
 * @author Tim Roberts - Initial contribution
 */
public class NeeoDevicesDeserializer implements JsonDeserializer<NeeoDevices> {
    @Override
    public NeeoDevices deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context)
            throws JsonParseException {
        if (jsonElement instanceof JsonObject) {
            final List<NeeoDevice> scenarios = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : ((JsonObject) jsonElement).entrySet()) {
                final NeeoDevice device = context.deserialize(entry.getValue(), NeeoDevice.class);
                scenarios.add(device);
            }

            return new NeeoDevices(scenarios.toArray(new NeeoDevice[0]));
        }
        return null;
    }

}