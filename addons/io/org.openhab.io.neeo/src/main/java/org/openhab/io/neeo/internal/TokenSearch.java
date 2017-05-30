/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.io.neeo.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.binding.BindingInfo;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.type.ThingType;
import org.openhab.io.neeo.NeeoConstants;
import org.openhab.io.neeo.internal.models.NeeoDevice;
import org.openhab.io.neeo.internal.models.TokenScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class emulates the same search pattern that the NEEO brain uses (https://github.com/neophob/tokensearch.js) on
 * all the exposed things in the registry.
 *
 * @author Tim Roberts - Initial contribution
 */
public class TokenSearch {

    private final Logger logger = LoggerFactory.getLogger(TokenSearch.class);

    /** The service context */
    private final ServiceContext context;

    /** The search threshold */
    private final double threshold;

    /** The search limit */
    private final int searchLimit;

    /**
     * Instantiates a new token search based on the {@link ServiceContext} and threshold
     *
     * @param context the non-null context
     * @param threshold the threshold between 0 and 1
     */
    public TokenSearch(ServiceContext context, double threshold) {
        Objects.requireNonNull(context, "context cannot be null");
        if (threshold < 0 || threshold > 1) {
            throw new IllegalArgumentException("threshold must be between 0 and 1");
        }

        this.threshold = threshold;
        this.context = context;

        final Object searchLimitText = context.getComponentContext().getProperties().get(NeeoConstants.CFG_SEARCHLIMIT);
        int searchLimit = 12;
        if (searchLimitText != null) {
            try {
                searchLimit = Integer.parseInt(searchLimitText.toString());
            } catch (NumberFormatException e) {
                logger.debug("{} was not a valid integer, defaulting to {}: {}", NeeoConstants.CFG_SEARCHLIMIT,
                        searchLimit, searchLimitText);
            }
        }
        this.searchLimit = searchLimit;
    }

    /**
     * Searches the registry for all {@link NeeoDevice} matching the query
     *
     * @param query the non-empty query
     * @return the non-null, possibly empty list of matching things (and their score)
     */
    public List<TokenScore<NeeoDevice>> search(String query) {
        NeeoUtil.requireNotEmpty(query, "query cannot be empty");

        final List<TokenScore<NeeoDevice>> results = new ArrayList<>();

        final String[] needles = StringUtils.split(query, ' ');
        int maxScore = -1;

        for (NeeoDevice device : context.getDefinitions().getExposed()) {
            int score = searchAlgorithm(device.getName(), needles);
            score += searchAlgorithm("openhab", needles);
            // score += searchAlgorithm(thing.getLocation(), needles);
            score += searchAlgorithm(device.getUid().getBindingId(), needles);

            final Thing thing = context.getThingRegistry().get(device.getUid().asThingUID());
            if (thing != null) {
                score += searchAlgorithm(thing.getLocation(), needles);

                final Map<@NonNull String, String> properties = thing.getProperties();
                final String vendor = properties.get(Thing.PROPERTY_VENDOR);
                if (StringUtils.isNotEmpty(vendor)) {
                    score += searchAlgorithm(vendor, needles);
                }

                final ThingType tt = context.getThingTypeRegistry().getThingType(thing.getThingTypeUID());
                if (tt != null) {
                    score += searchAlgorithm(tt.getLabel(), needles);

                    final BindingInfo bi = context.getBindingInfoRegistry().getBindingInfo(tt.getBindingId());
                    if (bi != null) {
                        score += searchAlgorithm(bi.getName(), needles);
                    }
                }
            }

            maxScore = Math.max(maxScore, score);

            results.add(new TokenScore<>(score, device));
        }

        return applyThreshold(results, maxScore, threshold);
    }

    /**
     * The search algorithm (lifted from tokensearch.js)
     *
     * @param haystack the search term
     * @param needles the items to search
     * @return the score of the match
     */
    private int searchAlgorithm(String haystack, String[] needles) {
        Objects.requireNonNull(needles, "needles cannot be null");

        int score = 0;
        int arrayLength = needles.length;
        for (int i = 0; i < arrayLength; i++) {
            String needle = needles[i];
            int stringPos = StringUtils.indexOfIgnoreCase(haystack, needle);
            int tokenScore = 0;
            if (stringPos > -1) {
                if (needle.length() < 2) {
                    tokenScore = 1;
                } else {
                    if (StringUtils.equalsIgnoreCase(haystack, needle)) {
                        tokenScore = 6;
                    } else if (stringPos == 0) {
                        tokenScore = 2;
                    } else {
                        tokenScore = 1;
                    }
                }
            }
            score += tokenScore;
        }
        return score;
    }

    /**
     * Apply threshold to the results (lifted from tokensearch.js)
     *
     * @param collection the collection of items
     * @param maxScore the maximum score
     * @param threshold the threshold
     * @return the list passing the threshold
     */
    private List<TokenScore<NeeoDevice>> applyThreshold(List<TokenScore<NeeoDevice>> collection, int maxScore,
            double threshold) {
        Objects.requireNonNull(collection, "collection cannot be null");

        final double normalizedScore = 1d / maxScore;
        final List<TokenScore<NeeoDevice>> results = new ArrayList<>();

        for (TokenScore<NeeoDevice> ts : collection) {
            double score = 1 - ts.getScore() * normalizedScore;
            if (score <= threshold) {
                results.add(ts);
            }
        }

        // Sort and then limit by search limit
        return results.stream().sorted().limit(searchLimit).collect(Collectors.toList());
    }
}