/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.io.neeo.internal.serialization;

import java.lang.reflect.Type;
import java.util.Objects;

import org.openhab.io.neeo.internal.models.NeeoDeviceChannelKind;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * Implementation of {@link JsonSerializer} and {@link JsonDeserializer} to serialize/deserial
 * {@link NeeoDeviceChannelKind}
 *
 * @author Tim Roberts - Initial contribution
 */
public class NeeoDeviceChannelKindSerializer
        implements JsonSerializer<NeeoDeviceChannelKind>, JsonDeserializer<NeeoDeviceChannelKind> {
    @Override
    public NeeoDeviceChannelKind deserialize(JsonElement elm, Type type, JsonDeserializationContext jsonContext)
            throws JsonParseException {
        Objects.requireNonNull(elm, "elm cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(jsonContext, "jsonContext cannot be null");

        if (elm.isJsonNull()) {
            throw new JsonParseException("NeeoDeviceChannelKind could not be parsed from null");
        }

        final NeeoDeviceChannelKind ndck = NeeoDeviceChannelKind.parse(elm.getAsString());
        if (ndck == null) {
            throw new JsonParseException("Unknown NeeoDeviceChannelKind: " + elm.getAsString());
        }
        return ndck;
    }

    @Override
    public JsonElement serialize(NeeoDeviceChannelKind ndck, Type type, JsonSerializationContext jsonContext) {
        Objects.requireNonNull(ndck, "nct cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(jsonContext, "jsonContext cannot be null");

        return new JsonPrimitive(ndck.toString());
    }
}
