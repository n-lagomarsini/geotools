/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2002-2011, Open Source Geospatial Foundation (OSGeo)
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
package org.geotools.renderer.windbarbs;

import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.measure.converter.UnitConverter;
import javax.measure.unit.NonSI;
import javax.measure.unit.Unit;
import javax.measure.unit.UnitFormat;

import org.geotools.util.Utilities;

/**
 * Utility class doing speed conversion since windBarbs are based on knots
 * 
 * @author Daniele Romagnoli, GeoSolutions SAS
 */
class SpeedConverter {

    /** The logger. */
    private static final Logger LOGGER = org.geotools.util.logging.Logging.getLogger(SpeedConverter.class);
    
    /** UCUM_FORMAT_INSTANCE */
    private static final UnitFormat UCUM_FORMAT_INSTANCE = UnitFormat.getUCUMInstance();

    private static final double SECONDS_IN_HOUR = 3600d;

    private static final double METERS_IN_KILOMETER = 1000d;

    private static final double METERS_IN_NAUTICAL_MILE = 1852d;

    private static final double METERS_PER_SECOND_TO_KNOTS = SECONDS_IN_HOUR / METERS_IN_NAUTICAL_MILE;

    private static final double KILOMETERS_PER_HOUR_TO_KNOTS = METERS_IN_KILOMETER / METERS_IN_NAUTICAL_MILE;

    private static final String METER_PER_SECOND = "m/s";

    private static final String KILOMETER_PER_HOUR = "km/h";
    
    private static final String MILE_PER_HOUR = "mph";
    
    private static final double MILES_PER_HOUR_TO_KNOTS = 0.868976d;

    private static final String KNOTS = "knots";

    private static final String KTS = "kts";
    
    private static final String KN = NonSI.KNOT.toString();

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static int toKnots(double speed, String uom) {
        // checks
        Utilities.ensureNonNull("uom", uom);
        
        // most common cases first
        if (uom.equalsIgnoreCase(KNOTS)|| uom.equalsIgnoreCase(KTS)|| uom.equalsIgnoreCase(KN)){
            return (int) speed;
        }
        if (uom.equalsIgnoreCase(METER_PER_SECOND)) {
            return (int)( speed * METERS_PER_SECOND_TO_KNOTS);
        } 
        if (uom.equalsIgnoreCase(KILOMETER_PER_HOUR)) {
            return (int)( speed * KILOMETERS_PER_HOUR_TO_KNOTS);
        } 
        if (uom.equalsIgnoreCase(MILE_PER_HOUR)) {
            return (int)( speed * MILES_PER_HOUR_TO_KNOTS);
        } 
        
        // ok let's try harder --> this is going to be slower
        try {
            Unit  unit = (Unit) SpeedConverter.UCUM_FORMAT_INSTANCE.parseObject(uom);                
            UnitConverter converter = unit.getConverterTo(NonSI.KNOT);
            return (int)converter.convert(speed);
        } catch (ParseException e) {
            LOGGER.log(Level.FINER, e.getMessage(), e);
        }
        throw new IllegalArgumentException("The supplied units isn't currently supported:"+ uom);
    }
}