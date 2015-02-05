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
package org.geotools.coverage;

import java.awt.Color;
import java.io.Serializable;
import java.util.Arrays;
import java.util.logging.Logger;

import org.geotools.referencing.operation.transform.LinearTransform1D;
import org.geotools.resources.Classes;
import org.geotools.resources.i18n.ErrorKeys;
import org.geotools.resources.i18n.Errors;
import org.geotools.resources.i18n.Vocabulary;
import org.geotools.resources.i18n.VocabularyKeys;
import org.geotools.util.NumberRange;
import org.geotools.util.SimpleInternationalString;
import org.geotools.util.Utilities;
import org.geotools.util.logging.Logging;
import org.opengis.referencing.operation.MathTransform1D;
import org.opengis.util.InternationalString;


/**
 * A category delimited by a range of sample values. A categogy may be either
 * <em>qualitative</em> or <em>quantitative</em>.   For example, a classified
 * image may have a qualitative category defining sample value {@code 0}
 * as water. An other qualitative category may defines sample value {@code 1}
 * as forest, etc.  An other image may define elevation data as sample values
 * in the range {@code [0..100]}.   The later is a <em>quantitative</em>
 * category, because sample values are related to some measurement in the real
 * world. For example, elevation data may be related to an altitude in metres
 * through the following linear relation:
 *
 * <var>altitude</var>&nbsp;=&nbsp;<var>sample&nbsp;value</var>&times;100.
 *
 * Some image mixes both qualitative and quantitative categories. For example,
 * images of Sea Surface Temperature  (SST)  may have a quantitative category
 * for temperature with values ranging from 2 to 35°C,  and three qualitative
 * categories for cloud, land and ice.
 * <p>
 * All categories must have a human readable name. 
 *
 * <p>
 * All {@code Category} objects are immutable and thread-safe.
 *
 * @since 2.1
 *
 *
 * @source $URL$
 * @version $Id$
 * @author Martin Desruisseaux (IRD)
 *
 * @see GridSampleDimension
 */
public class Category implements Serializable {
    /**
     * Serial number for interoperability with different versions.
     */
    private static final long serialVersionUID = 6215962897884256696L;

    /**
     * The logger for category.
     */
    public static final Logger LOGGER = Logging.getLogger("org.geotools.coverage");

    /**
     * The 0 value as a byte. Used for {@link #FALSE} categories.
     */
    private static final NumberRange<Byte> BYTE_0;
    static {
        final Byte index = 0;
        BYTE_0 = NumberRange.create(index, index);
    }

    /**
     * The 1 value as a byte. Used for {@link #TRUE} categories.
     */
    private static final NumberRange<Byte> BYTE_1;
    static {
        final Byte index = 1;
        BYTE_1 = NumberRange.create(index, index);
    }

    /**
     * A transparent color for missing data.
     */
    private static final Color TRANSPARENT = new Color(0,0,0,0);

    /**
     * A default category for "no data" values. This default qualitative category use
     * sample value 0, which is mapped to geophysics value {@link Float#NaN} for those who work
     * with floating point images. The rendering color default to a fully transparent color and
     * the name is "no data" localized to the requested locale.
     */
    public static final Category NODATA = new Category(
            Vocabulary.formatInternational(VocabularyKeys.NODATA), TRANSPARENT, 0, false);

    /**
     * A default category for the boolean "{@link Boolean#FALSE false}" value. This default
     * identity category uses sample value 0, the color {@linkplain Color#BLACK black} and
     * the name "false" localized to the specified locale.
     */
    public static final Category FALSE = new Category(
            Vocabulary.formatInternational(VocabularyKeys.FALSE), Color.BLACK, false);

    /**
     * A default category for the boolean "{@link Boolean#TRUE true}" value. This default
     * identity category uses sample value 1, the color {@linkplain Color#WHITE white}
     * and the name "true" localized to the specified locale.
     */
    public static final Category TRUE = new Category(
            Vocabulary.formatInternational(VocabularyKeys.TRUE), Color.WHITE, true);

    /**
     * The category name.
     */
    private final InternationalString name;

    /**
     * The minimal sample value (inclusive). This category is made of all values
     * in the range {@code minimum} to {@code maximum} inclusive.
     *
     * If this category is an instance of {@code GeophysicsCategory},
     * then this field is the minimal geophysics value in this category.
     * For qualitative categories, the geophysics value is one of {@code NaN} values.
     */
    final double minimum;

    /**
     * The maximal sample value (inclusive). This category is made of all values
     * in the range {@code minimum} to {@code maximum} inclusive.
     *
     * If this category is an instance of {@code GeophysicsCategory},
     * then this field is the maximal geophysics value in this category.
     * For qualitative categories, the geophysics value is one of {@code NaN} values.
     */
    final double maximum;

    /**
     * The range of values {@code [minimum..maximum]}.
     * May be computed only when first requested, or may be
     * user-supplied (which is why it must be serialized).
     */
    NumberRange<? extends Number> range;

    final boolean isQuantitative;

    /**
     * ARGB codes of category colors. The colors by default will be a 
     * gradient going from black to opaque white.
     */
    private final int[] ARGB;

    /**
     * Default ARGB codes. 
     */
    private static final int[] DEFAULT = {0xFF000000, 0xFFFFFFFF};

    /**
     * A set of default category colors.
     */
    private static final Color[] CYCLE = {
        Color.BLUE,    Color.RED,   Color.ORANGE, Color.YELLOW,     Color.PINK,
        Color.MAGENTA, Color.GREEN, Color.CYAN,   Color.LIGHT_GRAY, Color.GRAY
    };

    /**
     * Constructs a qualitative category for a boolean value.
     *
     * @param  name    The category name as a {@link String} or {@link InternationalString} object.
     * @param  color   The category color, or {@code null} for a default color.
     * @param  sample  The sample value as a boolean.
     */
    public Category(final CharSequence name,
                    final Color        color,
                    final boolean      sample)
    {
        this(name, toArray(color), sample ? BYTE_0 : BYTE_1, false);
    }

   /**
     * Constructs a qualitative category for sample value {@code sample}.
     *
     * @param  name    The category name as a {@link String} or {@link InternationalString} object.
     * @param  color   The category color, or {@code null} for a default color.
     * @param  sample  The sample value as an integer, usually in the range 0 to 255.
     */
    public Category(final CharSequence name,
                    final Color        color,
                    final int          sample)
    {
        this(name, toARGB(color, sample), Integer.valueOf(sample), false);
        assert minimum == sample : minimum;
        assert maximum == sample : maximum;
    }

    /**
     * Constructs a qualitative category for sample value {@code sample}.
     *
     * @param  name    The category name as a {@link String} or {@link InternationalString} object.
     * @param  color   The category color, or {@code null} for a default color.
     * @param  sample  The sample value as an integer, usually in the range 0 to 255.
     */
    public Category(final CharSequence name,
                    final Color        color,
                    final int          sample,
                    final boolean isQuantitative)
    {
        this(name, toARGB(color, sample), Integer.valueOf(sample), isQuantitative);
        assert minimum == sample : minimum;
        assert maximum == sample : maximum;
    }

    /**
     * Constructs a qualitative category for sample value {@code sample}.
     *
     * @param  name    The category name as a {@link String} or {@link InternationalString} object.
     * @param  color   The category color, or {@code null} for a default color.
     * @param  sample  The sample value as a double. May be one of {@code NaN} values.
     */
    public Category(final CharSequence name,
                    final Color        color,
                    final double       sample)
    {
        this(name, toARGB(color, (int) sample), Double.valueOf(sample), false);
        assert Double.doubleToRawLongBits(minimum) == Double.doubleToRawLongBits(sample) : minimum;
        assert Double.doubleToRawLongBits(maximum) == Double.doubleToRawLongBits(sample) : maximum;
    }


    /**
     * Constructs a category for sample value {@code sample}.
     */
    private Category(final CharSequence name,
                     final int[]        ARGB,
                     final Number       sample,
                     final boolean isQuantitative)
    {
        this(name, ARGB, new NumberRange(sample.getClass(), sample, sample), isQuantitative);
    }

    /**
     * Constructs a quantitative category for samples in the specified range.
     *
     * @param  name    The category name as a {@link String} or {@link InternationalString} object.
     * @param  color   The category color, or {@code null} for a default color.
     * @param  sampleValueRange The range of sample values for this category. Element class
     *                 is usually {@link Integer}, but {@link Float} and {@link Double} are
     *                 accepted as well.
     * @throws IllegalArgumentException If the given range is invalid.
     */
    public Category(final CharSequence name, final Color color,
                    final NumberRange<?> sampleValueRange) throws IllegalArgumentException
    {
        this(name, toArray(color), sampleValueRange, true);
    }

    /**
     * Constructs a quantitative category for sample values ranging from {@code lower}
     * inclusive to {@code upper} exclusive. Sample values are converted into geophysics
     * values using the following linear equation:
     *
     * <center><var>x</var><code>&nbsp;=&nbsp;{@linkplain GridSampleDimension#getOffset()
     * offset}&nbsp;+&nbsp;{@linkplain GridSampleDimension#getScale()
     * scale}&times;</code><var>s</var></center>
     *
     * @param  name    The category name as a {@link String} or {@link InternationalString} object.
     * @param  colors  A set of colors for this category. This array may have any length;
     *                 colors will be interpolated as needed. An array of length 1 means
     *                 that an uniform color should be used for all sample values. An array
     *                 of length 0 or a {@code null} array means that some default colors
     *                 should be used (usually a gradient from opaque black to opaque white).
     * @param  lower   The lower sample value, inclusive.
     * @param  upper   The upper sample value, exclusive.
     * @param  scale   The {@link GridSampleDimension#getScale() scale} value which is
     *                 multiplied to sample values for this category.
     * @param  offset  The {@link GridSampleDimension#getOffset() offset} value to add
     *                 to sample values for this category.
     *
     * @throws IllegalArgumentException if {@code lower} is not smaller than {@code upper},
     *         or if {@code scale} or {@code offset} are not real numbers.
     * @deprecated. 
     */
    public Category(final CharSequence name,
                    final Color[]      colors,
                    final int          lower,
                    final int          upper,
                    final double       scale,
                    final double       offset) throws IllegalArgumentException
    {
        this(name, colors, NumberRange.create(lower, true, upper, false), scale, offset);
    }

    /**
     * Constructs a quantitative category for sample values ranging from {@code lower}
     * inclusive to {@code upper} exclusive. 
     * 
     * @param  name    The category name as a {@link String} or {@link InternationalString} object.
     * @param  colors  A set of colors for this category. This array may have any length;
     *                 colors will be interpolated as needed. An array of length 1 means
     *                 that an uniform color should be used for all sample values. An array
     *                 of length 0 or a {@code null} array means that some default colors
     *                 should be used (usually a gradient from opaque black to opaque white).
     * @param  lower   The lower sample value, inclusive.
     * @param  upper   The upper sample value, exclusive.
     *
     * @throws IllegalArgumentException if {@code lower} is not smaller than {@code upper},
     *         or if {@code scale} or {@code offset} are not real numbers.
     */
    public Category(final CharSequence name,
                    final Color[]      colors,
                    final int          lower,
                    final int          upper) throws IllegalArgumentException
    {
        this(name, colors, NumberRange.create(lower, true, upper, false));
    }

    /**
     * Constructs a quantitative category for sample values in the specified range.
     * Sample values are converted into geophysics values using the following linear
     * equation:
     *
     * <center><var>x</var><code>&nbsp;=&nbsp;{@linkplain GridSampleDimension#getOffset()
     * offset}&nbsp;+&nbsp;{@linkplain GridSampleDimension#getScale()
     * scale}&times;</code><var>s</var></center>
     *
     * @param  name    The category name as a {@link String} or {@link InternationalString} object.
     * @param  colors  A set of colors for this category. This array may have any length;
     *                 colors will be interpolated as needed. An array of length 1 means
     *                 that an uniform color should be used for all sample values. An array
     *                 of length 0 or a {@code null} array means that some default colors
     *                 should be used (usually a gradient from opaque black to opaque white).
     * @param  sampleValueRange The range of sample values for this category. Element class
     *                 is usually {@link Integer}, but {@link Float} and {@link Double} are
     *                 accepted as well.
     * @param  scale   The {@link GridSampleDimension#getScale() scale} value which is
     *                 multiplied to sample values for this category.
     * @param  offset  The {@link GridSampleDimension#getOffset() offset} value to add
     *                 to sample values for this category.
     * 
     * @throws IllegalArgumentException if {@code lower} is not smaller than {@code upper},
     *         or if {@code scale} or {@code offset} are not real numbers.
     * @deprecated
     */
    public Category(final CharSequence name,
                    final Color[]     colors,
                    final NumberRange sampleValueRange,
                    final double      scale,
                    final double      offset) throws IllegalArgumentException
    {
        this(name, colors, sampleValueRange);
    }


    /**
     * Constructs a quantitative category for sample values in the specified range.
     * 
     * @param  name    The category name as a {@link String} or {@link InternationalString} object.
     * @param  colors  A set of colors for this category. This array may have any length;
     *                 colors will be interpolated as needed. An array of length 1 means
     *                 that an uniform color should be used for all sample values. An array
     *                 of length 0 or a {@code null} array means that some default colors
     *                 should be used (usually a gradient from opaque black to opaque white).
     * @param  sampleValueRange The range of sample values for this category. Element class
     *                 is usually {@link Integer}, but {@link Float} and {@link Double} are
     *                 accepted as well.
     * 
     * @throws IllegalArgumentException if {@code lower} is not smaller than {@code upper},
     *         or if {@code scale} or {@code offset} are not real numbers.
     */
    public Category(final CharSequence name,
                    final Color[]     colors,
                    final NumberRange sampleValueRange) throws IllegalArgumentException
    {
        this(name, colors, sampleValueRange, true);
    }

    /**
     * Constructs a quantitative category mapping samples to geophysics values in the specified
     * range. Sample values in the {@code sampleValueRange} will be mapped to geophysics
     * values in the {@code geophysicsValueRange} through a linear equation of the form:
     *
     * <center><var>x</var><code>&nbsp;=&nbsp;{@linkplain GridSampleDimension#getOffset()
     * offset}&nbsp;+&nbsp;{@linkplain GridSampleDimension#getScale()
     * scale}&times;</code><var>s</var></center>
     *
     * {@code scale} and {@code offset} coefficients are computed from the ranges supplied in
     * arguments.
     *
     * @param  name    The category name as a {@link String} or {@link InternationalString} object.
     * @param  colors  A set of colors for this category. This array may have any length;
     *                 colors will be interpolated as needed. An array of length 1 means
     *                 that an uniform color should be used for all sample values. An array
     *                 of length 0 or a {@code null} array means that some default colors
     *                 should be used (usually a gradient from opaque black to opaque white).
     * @param  sampleValueRange The range of sample values for this category. Element class
     *                 is usually {@link Integer}, but {@link Float} and {@link Double} are
     *                 accepted as well.
     * @param  geophysicsValueRange The range of geophysics values for this category.
     *                 Element class is usually {@link Float} or {@link Double}.
     *
     * @throws ClassCastException if the range element class is not a {@link Number} subclass.
     * @throws IllegalArgumentException if the range is invalid.
     * @deprecated
     */
    public Category(final CharSequence name,
                    final Color[]     colors,
                    final NumberRange sampleValueRange,
                    final NumberRange geophysicsValueRange) throws IllegalArgumentException
    {
        this(name, colors, sampleValueRange);
    }

   /**
     * Constructs a qualitative or quantitative category for samples in the specified range.
     * Sample values (usually integers) will be converted into geophysics values (usually
     * floating-point) through the {@code sampleToGeophysics} transform.
     *
     * @param  name    The category name as a {@link String} or {@link InternationalString} object.
     * @param  colors  A set of colors for this category. This array may have any length;
     *                 colors will be interpolated as needed. An array of length 1 means
     *                 that an uniform color should be used for all sample values. An array
     *                 of length 0 or a {@code null} array means that some default colors
     *                 should be used (usually a gradient from opaque black to opaque white).
     * @param  sampleValueRange The range of sample values for this category. Element class
     *                 is usually {@link Integer}, but {@link Float} and {@link Double} are
     *                 accepted as well.
     * @param  sampleToGeophysics A transform from sample values to geophysics values,
     *                 or {@code null} if this category is not a quantitative one.
     *
     * @throws ClassCastException if the range element class is not a {@link Number} subclass.
     * @throws IllegalArgumentException if the range is invalid.
     * @deprecated
     */
    public Category(final CharSequence name,
                    final Color[]     colors,
                    final NumberRange sampleValueRange,
                    final MathTransform1D sampleToGeophysics) throws IllegalArgumentException
    {
        this(name, colors, sampleValueRange);
    }

    /**
     * Constructs a qualitative or quantitative category for samples in the specified range.
     *
     * @param  name    The category name as a {@link String} or {@link InternationalString} object.
     * @param  colors  A set of colors for this category. This array may have any length;
     *                 colors will be interpolated as needed. An array of length 1 means
     *                 that an uniform color should be used for all sample values. An array
     *                 of length 0 or a {@code null} array means that some default colors
     *                 should be used (usually a gradient from opaque black to opaque white).
     * @param  sampleValueRange The range of sample values for this category. Element class
     *                 is usually {@link Integer}, but {@link Float} and {@link Double} are
     *                 accepted as well.
     *
     * @throws ClassCastException if the range element class is not a {@link Number} subclass.
     * @throws IllegalArgumentException if the range is invalid.
     */
    public Category(final CharSequence name,
                    final Color[]     colors,
                    final NumberRange sampleValueRange,
                    final boolean isQuantitative) throws IllegalArgumentException
    {
        this(name, toARGB(colors), sampleValueRange, isQuantitative);
    }

    /**
     * Constructs a category.  This private constructor is
     * used for both qualitative and quantitative category constructors.
     * It also used by {@link #recolor} in order to construct a new category 
     * similar to this one except for ARGB codes.
     */
    private Category(final CharSequence name,
                     final int[]        ARGB,
                     final NumberRange  range,
                     final boolean isQuantitative) throws IllegalArgumentException
    {
        ensureNonNull("name", name);
        this.name      = SimpleInternationalString.wrap(name);
        this.ARGB      = ARGB;
        this.range     = range;
        Class<?> type     = range.getElementClass();
        boolean minInc = range.isMinIncluded();
        boolean maxInc = range.isMaxIncluded();
        this.minimum   = doubleValue(type, range.getMinValue(), minInc ? 0 : +1);
        this.maximum   = doubleValue(type, range.getMaxValue(), maxInc ? 0 : -1);
        /*
         * If we are constructing a qualitative category for a single NaN value,
         * accepts it as a valid one.
         */
        if (minInc && maxInc && Double.isNaN(minimum) &&
            Double.doubleToRawLongBits(minimum) == Double.doubleToRawLongBits(maximum))
        {
            this.isQuantitative = false;
            return;
        }
        this.isQuantitative = isQuantitative;
        /*
         * Checks the arguments. Use '!' in compares in order to reject NaN values,
         * except for the legal case catched by the "if" block just above.
         */
        if (!(minimum<=maximum) || Double.isInfinite(minimum) || Double.isInfinite(maximum)) {
            throw new IllegalArgumentException(Errors.format(ErrorKeys.BAD_RANGE_$2,
                                               range.getMinValue(), range.getMaxValue()));
        }
    }


    /**
     * Returns a linear transform with the supplied scale and offset values.
     *
     * @param scale  The scale factor. May be 0 for a constant transform.
     * @param offset The offset value. May be NaN if this method is invoked from a constructor
     *               for initializing {@link #transform} for a qualitative category.
     */
    static MathTransform1D createLinearTransform(final double scale, final double offset) {
        return LinearTransform1D.create(scale, offset);
    }


    /**
     * Returns a {@code double} value for the specified number. If {@code direction}
     * is non-zero, then this method will returns the closest representable number of type
     * {@code type} before or after the double value.
     *
     * @param type      The range element class. {@code number} must be
     *                  an instance of this class (this will not be checked).
     * @param number    The number to transform to a {@code double} value.
     * @param direction -1 to return the previous representable number,
     *                  +1 to return the next representable number, or
     *                   0 to return the number with no change.
     */
    private static double doubleValue(final Class<?>        type,
                                      final Comparable number,
                                      final int     direction)
    {
        assert (direction >= -1) && (direction <= +1) : direction;
        return org.geotools.resources.XMath.rool(type, ((Number)number).doubleValue(), direction);
    }

    /**
     * Returns the given color in an array of length 1, or {@code null} if {@code color} is null.
     */
    private static Color[] toArray(final Color color) {
        return (color != null) ? new Color[] {color} : null;
    }

    /**
     * Convert an array of colors to an array of ARGB values.
     * If {@code colors} is null, then a default array
     * will be returned.
     *
     * @param  colors The array of colors to convert (may be null).
     * @return The colors as ARGB values. Never null.
     */
    private static int[] toARGB(final Color[] colors) {
        final int[] ARGB;
        if (colors!=null && colors.length!=0) {
            ARGB = new int[colors.length];
            for (int i=0; i<ARGB.length; i++) {
                final Color color = colors[i];
                if (color != null) {
                    ARGB[i] = color.getRGB();
                } else {
                    // Left ARGB[i] to its default value (0), which is the transparent color.
                }
            }
        } else {
            ARGB = DEFAULT;
        }
        return ARGB;
    }

    /**
     * Returns ARGB values for the specified color. If {@code color}
     * is null, a default ARGB code will be returned.
     */
    private static int[] toARGB(Color color, final int sample) {
        if (color == null) {
            color = CYCLE[Math.abs(sample) % CYCLE.length];
        }
        return new int[] {
            color.getRGB()
        };
    }

    /**
     * Returns the category name.
     *
     * @return The category name.
     */
    public InternationalString getName() {
        return name;
    }

    /**
     * Returns the set of colors for this category. Change to the returned array will not affect
     * this category.
     *
     * @return The colors palette for this category.
     *
     * @see GridSampleDimension#getColorModel
     */
    public Color[] getColors() {
        final Color[] colors = new Color[ARGB.length];
        for (int i=0; i<colors.length; i++) {
            colors[i] = new Color(ARGB[i], true);
        }
        return colors;
    }

    /**
     * Returns the range of sample values occurring in this category. Sample values can be
     * transformed into geophysics values using the {@link #getSampleToGeophysics} transform.
     *
     * @return The range of sample values.
     *
     * @see NumberRange#getMinimum(boolean)
     * @see NumberRange#getMaximum(boolean)
     * @see GridSampleDimension#getMinimumValue()
     * @see GridSampleDimension#getMaximumValue()
     */
    public NumberRange<? extends Number> getRange() {
        assert range != null;
        return range;
    }

    /**
     * Returns a transform from sample values to geophysics values. If this category
     * is not a quantitative one, then this method returns {@code null}.
     *
     * @return The transform from sample values to geophysics values.
     * @deprecated
     */
    public MathTransform1D getSampleToGeophysics() {
        LOGGER.fine("Views aren't supported anymore: returning the Identity transform");
        return LinearTransform1D.IDENTITY;
    }
    /**
     * Returns {@code true} if this category is quantitative. A quantitative category
     * has a non-null {@link #getSampleToGeophysics() sampleToGeophysics} transform.
     *
     * @return {@code true} if this category is quantitative, or
     *         {@code false} if this category is qualitative.
     */
    public boolean isQuantitative() {
        return isQuantitative;
    }

    /**
     * Returns a category for the same range of sample values but a different color palette.
     * The array given in argument may have any length; colors will be interpolated as needed.
     * An array of length 1 means that an uniform color should be used for all sample values.
     * An array of length 0 or a {@code null} array means that some default colors should be
     * used (usually a gradient from opaque black to opaque white).
     *
     * @param colors A set of colors for the new category.
     * @return A category with the new color palette, or {@code this}
     *         if the new colors are identical to the current ones.
     *
     * @see org.geotools.coverage.processing.ColorMap#recolor
     */
    public Category recolor(final Color[] colors) {
        final int[] newARGB = toARGB(colors);
        if (Arrays.equals(ARGB, newARGB)) {
            return this;
        }
        // The range can be null only for GeophysicsCategory cases. Because
        // the later override this method, the case below should never occurs.
        assert range != null : this;
        final Category newCategory = new Category(name, newARGB, range, isQuantitative);
        return newCategory;
    }

    /**
     * Changes the mapping from sample to geophysics values. This method returns a category with
     * a "{@linkplain #getSampleToGeophysics sample to geophysics}" transformation set to the
     * specified one. Other properties like the {@linkplain #getRange sample value range}
     * and the {@linkplain #getColors colors} are unchanged.
     * <p>
     * <strong>Note about geophysics categories:</strong> The above rules are straightforward
     * when applied on non-geophysics category, but this method can be invoked on geophysics
     * category (as returned by <code>{@linkplain #geophysics geophysics}(true)</code>) as well.
     * Since geophysics categories are already the result of some "sample to geophysics"
     * transformation, invoking this method on those is equivalent to {@linkplain
     * org.opengis.referencing.operation.MathTransformFactory#createConcatenatedTransform
     * concatenate} this "sample to geophysics" transform with the specified one.
     *
     * @param  sampleToGeophysics The new {@linkplain #getSampleToGeophysics sample to geophysics}
     *         transform.
     * @return A category using the specified transform.
     *
     * @see #getSampleToGeophysics
     * @see GridSampleDimension#rescale
     * @deprecated
     */
    public Category rescale(final MathTransform1D sampleToGeophysics) {
        LOGGER.fine("Views aren't supported anymore: returning \"this\"");
        return this;
    }

    /**
     * Returns the {@linkplain org.geotools.coverage.grid.ViewType#GEOPHYSICS geophysics} or
     * {@linkplain org.geotools.coverage.grid.ViewType#PACKED packed} of this category.
     * By definition, a <cite>geophysics category</cite> is a category with a
     * {@linkplain #getRange range of sample values} transformed in such a way that the
     * {@linkplain #getSampleToGeophysics sample to geophysics} transform is always the
     * {@linkplain MathTransform1D#isIdentity identity} transform, or {@code null} if no
     * such transform existed in the first place. In other words, the range of sample values
     * in a geophysics category maps directly the "<cite>real world</cite>" values without the
     * need for any transformation.
     * <p>
     * {@code Category} objects live by pair: a
     * {@linkplain org.geotools.coverage.grid.ViewType#GEOPHYSICS geophysics} one (used for
     * computation) and a {@linkplain org.geotools.coverage.grid.ViewType#PACKED packed} one
     * (used for storing data, usually as integers). The {@code geo} argument specifies which
     * object from the pair is wanted, regardless if this method is invoked on the geophysics or
     * packed instance of the pair.
     * <p>
     * Newly constructed categories are {@linkplain org.geotools.coverage.grid.ViewType#PACKED
     * packed} (i.e. a {@linkplain #getSampleToGeophysics sample to geophysics} transform must
     * be applied in order to gets {@linkplain org.geotools.coverage.grid.ViewType#GEOPHYSICS
     * geophysics} values).
     *
     * @param  geo {@code true} to get a category with an identity
     *         {@linkplain #getSampleToGeophysics transform} and a
     *         {@linkplain #getRange range of values} matching the
     *         {@linkplain org.geotools.coverage.grid.ViewType#GEOPHYSICS geophysics} values, or
     *         {@code false} to get the {@linkplain org.geotools.coverage.grid.ViewType#PACKED
     *         packed} category (the one constructed with {@code new Category(...)}).
     * @return The category. Never {@code null}, but may be {@code this}.
     *
     * @see GridSampleDimension#geophysics
     * @see org.geotools.coverage.grid.GridCoverage2D#view
     * @deprecated
     */
    public Category geophysics(final boolean geo) {
        return this;
    }

    /**
     * Returns a hash value for this category. This value need not remain consistent between
     * different implementations of the same class.
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Compares the specified object with this category for equality.
     *
     * @param  object The object to compare with.
     * @return {@code true} if the given object is equals to this category.
     */
    @Override
    public boolean equals(final Object object) {
        if (object == this) {
            // Slight optimization
            return true;
        }
        if (object!=null && object.getClass().equals(getClass())) {
            final Category that = (Category) object;
            if (Double.doubleToRawLongBits(minimum)== Double.doubleToRawLongBits(that.minimum) &&
                Double.doubleToRawLongBits(maximum)== Double.doubleToRawLongBits(that.maximum) &&
                Utilities.equals(this.name,      that.name ) &&
                          Arrays.equals(this.ARGB,      that.ARGB ))
            {
                // Special test for 'range', since 'GeophysicsCategory'
                // computes it only when first needed.
                if (this.range!=null && that.range!=null) {
                    if (!Utilities.equals(this.range, that.range)) {
                        return false;
                    }
                    return true;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a string representation of this category.
     * The returned string is implementation dependent.
     * It is usually provided for debugging purposes.
     */
    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder(Classes.getShortClassName(this));
        buffer.append("(\"").append(name).append("\":[");
            if (Classes.isInteger(getRange().getElementClass())) {
                buffer.append(Math.round(minimum)).append("...")
                      .append(Math.round(maximum)); // Inclusive
            } else {
                buffer.append(minimum).append(" ... ")
                      .append(maximum); // Inclusive
            }
        return buffer.append("])").toString();
    }

    /**
     * Makes sure that an argument is non-null.
     *
     * @param  name   Argument name.
     * @param  object User argument.
     * @throws IllegalArgumentException if {@code object} is null.
     */
    static void ensureNonNull(final String name, final Object object)
        throws IllegalArgumentException
    {
        if (object == null) {
            throw new IllegalArgumentException(Errors.format(ErrorKeys.NULL_ARGUMENT_$1, name));
        }
    }
}
