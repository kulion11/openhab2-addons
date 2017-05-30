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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.type.ThingType;
import org.openhab.io.neeo.NeeoConstants;
import org.openhab.io.neeo.NeeoService;
import org.openhab.io.neeo.NeeoServlet;
import org.openhab.io.neeo.internal.NeeoDeviceKeys;
import org.openhab.io.neeo.internal.NeeoUtil;
import org.openhab.io.neeo.internal.ServiceContext;
import org.openhab.io.neeo.internal.models.NeeoDevice;
import org.openhab.io.neeo.internal.models.NeeoDeviceChannel;
import org.openhab.io.neeo.internal.models.NeeoDeviceTiming;
import org.openhab.io.neeo.internal.models.NeeoDeviceType;
import org.openhab.io.neeo.internal.models.NeeoThingUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * Implementation of {@link JsonSerializer} and {@link JsonDeserializer} to serialize/deserial
 * {@link NeeoDevice}. This implementation should NOT be used in communications with the NEEO brain (use
 * {@link NeeoBrainDeviceSerializer} instead)
 *
 * @author Tim Roberts - Initial contribution
 */
public class NeeoDeviceSerializer implements JsonSerializer<NeeoDevice>, JsonDeserializer<NeeoDevice> {

    /** The service */
    private final NeeoService service;

    /** The serivce context */
    private final ServiceContext context;

    /**
     * Constructs the object with no service or context
     */
    public NeeoDeviceSerializer() {
        this(null, null);
    }

    /**
     * Constructs the object from the service and context. A null service or context will suppress certain values on the
     * returned json object
     *
     * @param service the possibly null service
     * @param context the possibly null context
     */
    public NeeoDeviceSerializer(NeeoService service, ServiceContext context) {
        this.service = service;
        this.context = context;
    }

    @Override
    public NeeoDevice deserialize(JsonElement elm, Type type, JsonDeserializationContext jsonContext)
            throws JsonParseException {
        Objects.requireNonNull(elm, "elm cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(jsonContext, "jsonContext cannot be null");

        if (!(elm instanceof JsonObject)) {
            throw new JsonParseException("Element not an instance of JsonObject: " + elm);
        }

        final JsonObject jo = (JsonObject) elm;
        final NeeoThingUID uid = jsonContext.deserialize(jo.get("uid"), NeeoThingUID.class);
        final NeeoDeviceType devType = jsonContext.deserialize(jo.get("type"), NeeoDeviceType.class);
        final String manufacturer = NeeoUtil.getString(jo, "manufacturer");
        final String name = NeeoUtil.getString(jo, "name");
        final NeeoDeviceChannel[] channels = jsonContext.deserialize(jo.get("channels"), NeeoDeviceChannel[].class);
        final NeeoDeviceTiming timing = jo.has("timing")
                ? jsonContext.deserialize(jo.get("timing"), NeeoDeviceTiming.class)
                : null;

        final String[] deviceCapabilities = jo.has("deviceCapabilities")
                ? jsonContext.deserialize(jo.get("deviceCapabilities"), String[].class)
                : null;

        try {
            return new NeeoDevice(uid, devType, manufacturer, name, Arrays.asList(channels), timing,
                    deviceCapabilities == null ? null : Arrays.asList(deviceCapabilities));
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new JsonParseException(e);
        }
    }

    @Override
    public JsonElement serialize(NeeoDevice device, Type deviceType, JsonSerializationContext jsonContext) {
        Objects.requireNonNull(device, "device cannot be null");
        Objects.requireNonNull(deviceType, "deviceType cannot be null");
        Objects.requireNonNull(jsonContext, "jsonContext cannot be null");

        final JsonObject jsonObject = new JsonObject();

        final NeeoThingUID uid = device.getUid();
        jsonObject.add("uid", jsonContext.serialize(uid));
        jsonObject.add("type", jsonContext.serialize(device.getType()));
        jsonObject.addProperty("manufacturer", device.getManufacturer());
        jsonObject.addProperty("name", device.getName());

        final JsonArray channels = (JsonArray) jsonContext.serialize(device.getChannels());

        final NeeoDeviceTiming timing = device.getDeviceTiming();
        jsonObject.add("timing", jsonContext.serialize(timing == null ? new NeeoDeviceTiming() : timing));

        jsonObject.add("deviceCapabilities", jsonContext.serialize(device.getDeviceCapabilities()));

        jsonObject.addProperty("thingType", uid.getThingType());

        if (StringUtils.equalsIgnoreCase(NeeoConstants.NEEOIO_BINDING_ID, uid.getBindingId())) {
            jsonObject.addProperty("thingStatus", uid.getThingType().toUpperCase());
        }

        if (context != null) {
            if (!StringUtils.equalsIgnoreCase(NeeoConstants.NEEOIO_BINDING_ID, uid.getBindingId())) {
                final Thing thing = context.getThingRegistry().get(device.getUid().asThingUID());
                jsonObject.addProperty("thingStatus",
                        thing == null ? ThingStatus.UNKNOWN.name() : thing.getStatus().name());

                if (thing != null) {
                    final ThingType thingType = context.getThingTypeRegistry().getThingType(thing.getThingTypeUID());

                    if (thingType != null) {
                        for (JsonElement chnl : channels) {
                            JsonObject jo = (JsonObject) chnl;
                            if (jo.has("groupId") && jo.has("itemLabel")) {
                                final String groupId = jo.get("groupId").getAsString();
                                final String groupLabel = NeeoUtil.getGroupLabel(thingType, groupId);
                                if (StringUtils.isNotEmpty(groupLabel)) {
                                    final JsonElement itemLabel = jo.remove("itemLabel");
                                    jo.addProperty("itemLabel", groupLabel + "#" + itemLabel.getAsString());
                                } else if (StringUtils.isNotEmpty("groupId")) {
                                    // have a groupid but no group definition found (usually error on binding)
                                    // just default to "Others" like the Paperui does.
                                    final JsonElement itemLabel = jo.remove("itemLabel");
                                    jo.addProperty("itemLabel", "Others#" + itemLabel.getAsString());
                                }
                            }
                        }
                    }
                }
            }
        }

        jsonObject.add("channels", channels);

        if (service != null) {
            List<String> foundKeys = new ArrayList<>();
            for (final NeeoServlet servlet : service.getServlets()) {
                final NeeoDeviceKeys servletKeys = servlet.getDeviceKeys();
                final Set<String> keys = servletKeys == null ? null : servletKeys.get(device.getUid());
                foundKeys.addAll(keys);
            }
            jsonObject.add("keys", jsonContext.serialize(foundKeys));
        }

        return jsonObject;
    }
}