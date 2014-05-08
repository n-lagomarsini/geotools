package org.geotools.renderer.windbarbs;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.geotools.renderer.style.MarkFactory;
import org.opengis.feature.Feature;
import org.opengis.filter.expression.Expression;

/**
 * Factory to produce WindBarbs. Urls for wind barbs are in the form:
 * windbarbs://default(speed_value)[units_of_measure]
 * 
 * TODO: We may consider adding a FLAG to say whether the arrows are toward wind (meteo convention)
 * or against wind (ocean convention)
 * 
 * @author Daniele Romagnoli, GeoSolutions SAS
 */
public class WindBarbsFactory implements MarkFactory {

    /** The loggermodule. */
    private static final Logger LOGGER = org.geotools.util.logging.Logging.getLogger(WindBarbsFactory.class);

    public static final String WINDBARBS_PREFIX = "windbarbs://";

    private static final String DEFAULT_NAME = "default";

    private static Pattern SPEED_PATTERN = Pattern.compile("\\((.*?)\\)");

    private static Pattern UNIT_PATTERN = Pattern.compile("\\[(.*?)\\]");

    private static List<Shape> DEFAULT_CACHED_BARBS;

    static {
        DEFAULT_CACHED_BARBS = new ArrayList<Shape>();
        for (int i = 0; i <= 150; i += 5) {
            DEFAULT_CACHED_BARBS.add(new WindBarb(i).build());
        }
    }

    

    /**
     * Return a shape with the given url.
     * 
     * @see org.geotools.renderer.style.MarkFactory#getShape(java.awt.Graphics2D, org.opengis.filter.expression.Expression,
     *      org.opengis.feature.Feature)
     */
    public Shape getShape(Graphics2D graphics, Expression symbolUrl, Feature feature)
            throws Exception {

        // cannot handle a null url
        if (symbolUrl == null)
            return null;

        // see if it's a shape
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Trying to resolve symbol:" + symbolUrl.toString());
        }

        final String wellKnownName = symbolUrl.evaluate(feature, String.class);
        if (wellKnownName == null || !wellKnownName.startsWith(WINDBARBS_PREFIX)) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Unable to resolve symbol");
            }
            return null;
        }

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Resolved symbol");
        }

        String units = "kts";

        String windBarbName = DEFAULT_NAME; 

        if (wellKnownName.contains("(")) {
            windBarbName = wellKnownName.substring(WINDBARBS_PREFIX.length() , wellKnownName.indexOf("("));
        }
        // Looking for speed value
        Matcher matcher = SPEED_PATTERN.matcher(wellKnownName);
        double speed = 0;
        if (matcher.find()) {
            String speedString = matcher.group();
            speed = Double.parseDouble(speedString.substring(1, speedString.length() - 1));
        }

        // Looking for unit value (one of km/h, m/s, kts, knots)
        matcher = UNIT_PATTERN.matcher(wellKnownName);
        if (matcher.find()) {
            String unitString = matcher.group();
            units = unitString.substring(1, unitString.length() - 1);
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Speed value = " + speed + " " + units);
        }

        return getWindBarb(windBarbName, speed, units);
    }

    /**
     * Get the proper WindBarb related to the referred speed 
     * @param speed
     * @param units
     * @return
     */
    private Shape getWindBarb(final String windBarbName, final double speed, final String units) {
        int knots = SpeedConverter.toKnots(speed, units);
        return getWindBarbForKnots(windBarbName, knots);
    }

    private Shape getWindBarbForKnots(final String windBarbName, final int knots) {
        // We may revisit this in case windBarbs validity is across values, using the nearest...
        // as an instance, windBarb for 10 knots is valid for speed between 8 and 12 knots
        if (windBarbName.equalsIgnoreCase(DEFAULT_NAME)) {
            return DEFAULT_CACHED_BARBS.get(knots / 5);
        }
        // TODO:
        // We may refers to a different name such as "custom1",
        // then load the definition of that name from a property file called "custom1.properties"
        // and pre-build shapes for that definition.
        // 
        // A property file may have a CSV similar syntax.
        // VECTOR_LENGTH, FULL_BARB_LENGTH, BASE_PENNANT_LENGTH, ELEMENTS_SPACING, ZERO_RADIUS
        // Where:
        // VECTOR_LENGTH = The length of the main vector
        // FULL_BARB_LENGTH = The length of the long barb (the half barb will have half this size)
        // BASE_PENNANT_LENGTH = The Length of the base of the triangle composing the pennant
        // ELEMENTS_SPACING = The distances between the various barbs composing the shape
        // ZERO_RADIUS = The radius of the circle used to represent air calm.
        // 
        return DEFAULT_CACHED_BARBS.get(knots / 5);
    }
}
