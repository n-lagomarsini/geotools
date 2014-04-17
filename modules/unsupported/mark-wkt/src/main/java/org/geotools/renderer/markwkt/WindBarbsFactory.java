package org.geotools.renderer.markwkt;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
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

    /** The logger for the rendering module. */
    private static final Logger LOGGER = org.geotools.util.logging.Logging
            .getLogger(WindBarbsFactory.class);

    public static final String WINDBARBS_PREFIX = "windbarbs://";

    private static final String DEFAULT_NAME = "default";

    private static Pattern SPEED_PATTERN = Pattern.compile("\\((.*?)\\)");

    private static Pattern UNIT_PATTERN = Pattern.compile("\\[(.*?)\\]");

    private static List<Shape> DEFAULT_CACHED_BARBS;

    private static int DEFAULT_VECTOR_LENGTH = 20;

    private static int DEFAULT_BASE_PENNANT_LENGTH = 5;

    private static int DEFAULT_BARB_LENGTH = 10;

    private static int DEFAULT_ELEMENTS_SPACING = 5;
    
    private static int DEFAULT_ZERO_WIND_RADIUS = 10;

    private static WindBarbDefinition DEFAULT_WINDBARB_DEFINITION;

    static {
        DEFAULT_WINDBARB_DEFINITION = new WindBarbDefinition(DEFAULT_VECTOR_LENGTH,
                DEFAULT_BASE_PENNANT_LENGTH, DEFAULT_ELEMENTS_SPACING, DEFAULT_BARB_LENGTH, DEFAULT_ZERO_WIND_RADIUS);
        DEFAULT_CACHED_BARBS = new ArrayList<Shape>();
        for (int i = 0; i <= 100; i += 5) {
            DEFAULT_CACHED_BARBS.add(new WindBarb(i).buildWindBarb());
        }
    }

    /**
     * Utility class doing speed conversion since windBarbs are based on knots
     * 
     * @author Daniele Romagnoli, GeoSolutions SAS
     */
    static class SpeedConverter {
        private static double SECONDS_IN_HOUR = 3600d;

        private static double METERS_IN_KILOMETER = 1000d;

        private static double METERS_IN_NAUTICAL_MILE = 1852d;

        private static double METERS_PER_SECOND_TO_KNOTS = SECONDS_IN_HOUR
                / METERS_IN_NAUTICAL_MILE;

        private static double KILOMETERS_PER_HOUR_TO_KNOTS = METERS_IN_KILOMETER
                / METERS_IN_NAUTICAL_MILE;

        private static String METER_PER_SECOND = "m/s";

        private static String KILOMETER_PER_HOUR = "km/h";

        private static String KNOTS = "knots";

        private static String KTS = "kts";

        private static int toKnots(double speed, String units) {
            if (units.equalsIgnoreCase(METER_PER_SECOND)) {
                speed = speed * METERS_PER_SECOND_TO_KNOTS;
            } else if (units.equalsIgnoreCase(KILOMETER_PER_HOUR)) {
                speed = speed * KILOMETERS_PER_HOUR_TO_KNOTS;
            } else if (!units.equalsIgnoreCase(KNOTS) && !units.equalsIgnoreCase(KTS)) {
                throw new IllegalArgumentException("The supplied units isn't currently supported:"
                        + units);
            }
            return (Double.valueOf(speed)).intValue();
        }
    }

    /**
     * A WindBarbDefinition contains parameters used to build the WindBarb, such as the 
     * main vector length, the elements Spacing, the length of long barbs...
     * 
     * @author Daniele Romagnoli, GeoSolutions SAS
     *
     */
    static class WindBarbDefinition {

        public WindBarbDefinition(int vectorLength, int basePennantLength, int elementsSpacing,
                int longBarbLength, int zeroWindRadius) {
            super();
            this.vectorLength = vectorLength;
            this.basePennantLength = basePennantLength;
            this.elementsSpacing = elementsSpacing;
            this.longBarbLength = longBarbLength;
            this.shortBarbLength = longBarbLength / 2;
            this.zeroWindRadius = zeroWindRadius;
        }

        /** The main vector length */
        int vectorLength;

        /** The length of the base of the pennant (the triangle) */
        int basePennantLength;

        /** The distance between multiple barbs, and pennants */
        int elementsSpacing;

        /** The length of a long barb */
        int longBarbLength;

        /** The length of a short barb (is always half the length of a long barb) */
        int shortBarbLength;

        int zeroWindRadius;
    }

    /**
     * A WindBarb object made of reference speed in knots, and related number of longBarbs (10 kts), shortBarbs (5 kts) and pennants (50 kts).
     * 
     * @author Daniele Romagnoli, GeoSolutions SAS
     */
    static class WindBarb {
        int knots;

        int pennants;

        int longBarbs;

        int shortBarbs;

        /** A {@link WindBarbDefinition} instance reporting structural values for a WindBarb (vector length, sizes, ...) */
        WindBarbDefinition windBarbDefinition;

        public WindBarb(final int knots) {
            this(DEFAULT_WINDBARB_DEFINITION, knots);
        }

        public WindBarb(final WindBarbDefinition definition, final int knots) {
            this.windBarbDefinition = definition;
            this.knots = knots;
            pennants = knots / 50;
            longBarbs = (knots - (pennants * 50)) / 10;
            shortBarbs = (knots - (pennants * 50) - (longBarbs * 10)) / 5;
        }

        /**
         * Build a {@Shape} WindBarb
         * 
         * @return
         */
        private Shape buildWindBarb() {
            int positionOnPath = 0;

            // Base barb
            Path2D path = new Path2D.Double();

            // Initialize Barb
            if (knots < 5) {
                // Draw a circle and return (We may consider parametrize this)
                return new Ellipse2D.Float(0, 0, windBarbDefinition.zeroWindRadius, windBarbDefinition.zeroWindRadius);
            } else {
                path.moveTo(0, 0);
                path.lineTo(windBarbDefinition.vectorLength, 0);
            }

            // pennants management
            if (pennants > 0) {
                positionOnPath = drawPennants(path, positionOnPath);
            }

            // long barbs management
            if (longBarbs > 0) {
                positionOnPath = drawLongBarbs(path, positionOnPath);
            }

            // short barbs management
            if (shortBarbs > 0) {
                positionOnPath = drawShortBarbs(path, positionOnPath);
            }

            return path;
        }

        /**
         * Add short barbs to the shape
         * 
         * @param path
         * @param positionOnPath
         * @return
         */
        private int drawShortBarbs(Path2D path, int positionOnPath) {
            int hasFlags = pennants > 0 ? 1 : 0;
            final int vectorLength = windBarbDefinition.vectorLength;
            final int basePennantLength = windBarbDefinition.basePennantLength;
            final int shortBarbLength = windBarbDefinition.shortBarbLength;
            final int elementsSpacing = windBarbDefinition.elementsSpacing;
            if (pennants == 0 && longBarbs == 0) {
                positionOnPath = vectorLength - 5;
                path.moveTo(positionOnPath, 0);
                path.lineTo(positionOnPath + basePennantLength / 4, shortBarbLength);
            } else {
                positionOnPath = vectorLength - (pennants * basePennantLength) - elementsSpacing
                        * (longBarbs + hasFlags);
                path.moveTo(positionOnPath, 0);
                path.lineTo(positionOnPath + basePennantLength / 4, shortBarbLength);
            }
            return positionOnPath;
        }

        /**
         * Add long barbs to the shape
         * 
         * @param path
         * @param positionOnPath
         * @return
         */
        private int drawLongBarbs(Path2D path, int positionOnPath) {
            int hasFlags = pennants > 0 ? 1 : 0;
            final int vectorLength = windBarbDefinition.vectorLength;
            final int basePennantLength = windBarbDefinition.basePennantLength;
            final int longBarbLength = windBarbDefinition.longBarbLength;
            final int elementsSpacing = windBarbDefinition.elementsSpacing;
            int appendedLongBarbs = 0;
            positionOnPath = vectorLength - (pennants * basePennantLength) - elementsSpacing
                    * hasFlags;
            while (appendedLongBarbs < longBarbs) {
                path.moveTo(positionOnPath, 0);
                path.lineTo(positionOnPath + basePennantLength / 2, longBarbLength);
                appendedLongBarbs++;
                positionOnPath -= elementsSpacing;
            }
            return positionOnPath;
        }

        /**
         * add Pennants to the shape
         * 
         * @param path
         * @param positionOnPath
         * @return
         */
        private int drawPennants(Path2D path, int positionOnPath) {
            final int vectorLength = windBarbDefinition.vectorLength;
            final int basePennantLength = windBarbDefinition.basePennantLength;
            final int longBarbLength = windBarbDefinition.longBarbLength;
            int appendedPennants = 0;
            while (appendedPennants < pennants) {
                positionOnPath = vectorLength - (appendedPennants * basePennantLength);
                path.moveTo(positionOnPath, 0);
                path.lineTo(positionOnPath - (basePennantLength / 2), longBarbLength);
                path.lineTo(positionOnPath - basePennantLength, 0);
                path.closePath();
                positionOnPath = 0;
                appendedPennants++;
            }
            return positionOnPath;
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
