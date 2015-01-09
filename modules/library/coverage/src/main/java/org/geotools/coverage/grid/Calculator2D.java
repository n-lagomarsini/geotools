/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 * 
 *    (C) 2001-2008, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.coverage.grid;


/**
 * A grid coverage backed by the same {@linkplain #image image}, {@linkplain #gridGeometry grid
 * geometry} and {@linkplain #getSampleDimensions sample dimension} than an other coverage, but
 * performing some additional calculation in its {@code evaluate} methods.
 *
 * @since 2.5
 *
 *
 * @source $URL$
 * @version $Id$
 * @author Martin Desruisseaux (IRD)
 */
public abstract class Calculator2D extends GridCoverage2D {
    /**
     * For cross-version compatibility.
     */
    private static final long serialVersionUID = -6287856672249003253L;

    /**
     * The source grid coverage which was specified at construction time (never {@code null}).
     *
     * @serial This field duplicate the value obtained by <code>{@linkplain #getSources()}(0)</code>
     *         except if this coverage has been deserialized. The source is required in order to get
     *         the {@link #view} method to work. Because the {@linkplain GridCoverage2D#image image}
     *         contained in the source is the same one than in this {@link Calculator2D}, there is
     *         few cost in keeping it.
     */
    protected final GridCoverage2D source;

    /**
     * Constructs a new grid coverage with the same parameter than the specified coverage.
     *
     * @param name The name for this coverage, or {@code null} for the same than {@code coverage}.
     * @param coverage The source grid coverage.
     */
    protected Calculator2D(final CharSequence name, final GridCoverage2D coverage) {
        super(name, coverage);
        this.source = coverage;
    }
}
