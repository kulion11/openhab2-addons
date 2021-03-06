/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.sony.internal.scalarweb.models.api;

// TODO: Auto-generated Javadoc
/**
 * The Class Enabled.
 * 
 * @author Tim Roberts - Initial contribution
 */
public class Enabled {

    /** The enabled. */
    private final boolean enabled;

    /**
     * Instantiates a new enabled.
     *
     * @param enabled the enabled
     */
    public Enabled(boolean enabled) {
        super();
        this.enabled = enabled;
    }

    /**
     * Checks if is enabled.
     *
     * @return true, if is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Enabled [enabled=" + enabled + "]";
    }
}
