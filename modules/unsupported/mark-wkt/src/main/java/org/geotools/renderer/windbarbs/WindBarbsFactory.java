package org.geotools.renderer.windbarbs;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.geotools.referencing.operation.transform.AffineTransform2D;
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

    /** WINDBARB_DEFINITION */
    private static final String WINDBARB_DEFINITION = "windbarbs://.*\\(.{1,}\\)\\[.{1,5}\\]\\??.*";//"windbarbs://.*\\(\\d+\\.?\\d*\\)\\[.{1,5}\\]\\??.*";

    /** SOUTHERN_EMISPHERE_FLIP */
    public static final AffineTransform SOUTHERN_EMISPHERE_FLIP = new AffineTransform2D(AffineTransform.getScaleInstance(-1, 1));

    /** The loggermodule. */
    private static final Logger LOGGER = org.geotools.util.logging.Logging.getLogger(WindBarbsFactory.class);

    public static final String WINDBARBS_PREFIX = "windbarbs://";

    private static final String DEFAULT_NAME = "default";

    private static Pattern SPEED_PATTERN = Pattern.compile("(.*?)\\((.{1,})\\)(.*)");//Pattern.compile("(.*?)(\\d+\\.?\\d*)(.*)");
    
    private static Pattern WINDBARB_SET_PATTERN = Pattern.compile("(.*?)://(.*)\\((.*)");

    private static Pattern UNIT_PATTERN = Pattern.compile("(.*?)\\[(.*)\\](.*)");

    private static List<Shape> DEFAULT_CACHED_BARBS;

    static {
        DEFAULT_CACHED_BARBS = new ArrayList<Shape>();
        for (int i = 0; i <= 100; i += 5) {
            DEFAULT_CACHED_BARBS.add(new WindBarb(i).build());
        }
        
        //no module x----- symbol
        DEFAULT_CACHED_BARBS.add(new WindBarb(-1).build());
    }

    

    /**
     * Return a shape with the given url.
     * 
     * @see org.geotools.renderer.style.MarkFactory#getShape(java.awt.Graphics2D, org.opengis.filter.expression.Expression,
     *      org.opengis.feature.Feature)
     */
    public Shape getShape(Graphics2D graphics, Expression symbolUrl, Feature feature){

        // CHECKS
        // cannot handle a null url
        if (symbolUrl == null){
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Provided null symbol to the WindBarbs Factory");
            }            
            return null;
        }
        // cannot handle a null feature
        if (feature == null){
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Provided null feature to the WindBarbs Factory");
            }            
            return null;
        }

        //
        // START PARSING CODE
        //
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Trying to resolve symbol:" + symbolUrl.toString());
        }

        // evaluate string from feature to extract all values
        final String wellKnownName = symbolUrl.evaluate(feature, String.class);
        if (wellKnownName == null || wellKnownName.length()<=0) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Unable to resolve symbol provided to WindBarbs Factory");
            }
            return null;
        }
        
        ////
        //
        // Basic Syntax
        //
        ////    
        if (!wellKnownName.matches(WindBarbsFactory.WINDBARB_DEFINITION)) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Unable to resolve symbol: "+wellKnownName);
            }
            return null;
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Resolved symbol "+wellKnownName);
        }
        
        // ok from now on we should have a real windbarb, let's lower the log level
        
        ////
        //
        // WindBarbs set
        //
        ////    
        String windBarbName = null; 
        Matcher matcher = WINDBARB_SET_PATTERN.matcher(wellKnownName);
        if (matcher.matches()) {
            try {
                windBarbName = matcher.group(2);
            }catch (Exception e) {
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.log(Level.INFO,"Unable to parse windbarb set from string: "+wellKnownName,e);
                }

                return null;
            }
        } 
        if(windBarbName==null||windBarbName.length()<=0){
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.fine("Unable to parse windBarbName from string: "+wellKnownName);
            }
            return null;
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Resolved windBarbName "+windBarbName);
        }        
            
        ////
        //
        // Looking for speed
        //
        ////        
        matcher = SPEED_PATTERN.matcher(wellKnownName);
        double speed = Double.NaN;
        if (matcher.matches()) {
            String speedString="";
            try {
                speedString = matcher.group(2);
                speed = Double.parseDouble(speedString);
            }catch (Exception e) {
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.log(Level.INFO,"Unable to parse speed from string: "+speedString,e);
                }
                return null;
            }
        }else{
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.fine("Unable to parse speed from string: "+wellKnownName);
            }
            return null;
        } 

        ////
        //
        // Looking for unit value 
        //
        ////
        String uom = null;// no default
        matcher = UNIT_PATTERN.matcher(wellKnownName);
        if (matcher.matches()) {
            uom = matcher.group(2);
        }
        if(uom==null||uom.length()<=0){
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Unable to parse UoM from "+ wellKnownName);
            }
            return null;
        }
        
        // so far so good
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("WindBarbs name "+windBarbName+"with Speed " + speed + "[" + uom+ "]");
        }
        
        ////
        //
        // Params
        //
        ////
        int index=wellKnownName.indexOf('?');
        if( index>0){
            final Map<String,String> params= new HashMap<String, String>();
            final String kvp=wellKnownName.substring(index+1);
            String[] pairs = kvp.split("&");
            if(pairs!=null&&pairs.length>0){
                for(String pair:pairs){
                    // split
                    String[] splitPair = pair.split("=");
                    if(splitPair!=null&&splitPair.length>0){
                        params.put(splitPair[0].toLowerCase(),splitPair[1]);
                    }else{
                        if(LOGGER.isLoggable(Level.FINE)){
                            LOGGER.fine("Skipping pair "+pair);
                        }
                    }
                }
                                
                // checks
                if(!params.isEmpty()){
                    return getWindBarb(windBarbName, speed, uom,params);
                }
            }
        }
        
        ////
        //
        // Get shape if possible
        //
        ////
        return getWindBarb(windBarbName, speed, uom);


    }

    /**
     * @param windBarbName
     * @param speed
     * @param units
     * @param params
     * @return
     */
    private Shape getWindBarb(String windBarbName, double speed, String units, Map<String,String> params) {
        // speed
        try{
            double knots = SpeedConverter.toKnots(speed, units);
            
            // shape
            return getWindBarbForKnots(windBarbName, knots,params);
        }catch (Exception e) {
            if(LOGGER.isLoggable(Level.INFO)){
                LOGGER.log(Level.INFO,e.getLocalizedMessage(),e);
            }
            return null;
        }
    }

    /**
     * Get the proper WindBarb related to the referred speed 
     * @param speed
     * @param units
     * @return
     */
    private Shape getWindBarb(final String windBarbName, final double speed, final String units) {
        return getWindBarb(windBarbName, speed, units, null);
    }

    private Shape getWindBarbForKnots(final String windBarbName, final double knots, Map<String, String> params) {
        // No module is signalled by NaN       
        // checking the barbs using our own limits
        int index = -1;
        if(!Double.isNaN(knots)){
            if(knots<3){
                index=0;
            } else {
                index=(int)((knots-3.0)/5.0+1);
            }   
        }else{
            index=DEFAULT_CACHED_BARBS.size()-1;// no wind module is the last symbol
        }
        if(DEFAULT_CACHED_BARBS.size()<=index||index<-1){
            throw new IllegalArgumentException("Unable to find windbarb symbol for speed "+knots+ " kn");
        }
        
        // get the barb
        if (windBarbName.equalsIgnoreCase(DEFAULT_NAME)) {

            final Shape shp=DEFAULT_CACHED_BARBS.get(index);
            if(params==null||params.isEmpty()){
                return shp;
            }

            if(params.containsKey("emisphere")&&params.get("emisphere").equalsIgnoreCase("s")){
                // flip shape on Y axis
                return SOUTHERN_EMISPHERE_FLIP.createTransformedShape(shp);
            }
            if(params.containsKey("hemisphere")&&params.get("hemisphere").equalsIgnoreCase("s")){
                // flip shape on Y axis
                return SOUTHERN_EMISPHERE_FLIP.createTransformedShape(shp);
            }
            return shp;
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
        return DEFAULT_CACHED_BARBS.get(index);
    }
}
