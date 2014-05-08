package org.geotools.renderer.windbarbs;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * A WindBarb object made of reference speed in knots, and related number of longBarbs (10 kts), 
 * shortBarbs (5 kts) and pennants (50 kts).
 * 
 * @author Daniele Romagnoli, GeoSolutions SAS
 */
class WindBarb {
    static class Pennant{
        final int pennantBaseLength;
        
        final int pennantEdgeLength;
        
        Pennant(final int pennantBaseLength,final int pennantEdgeLength){
            this.pennantBaseLength=pennantBaseLength;
            this.pennantEdgeLength=pennantEdgeLength;
        }
        
        public Shape getShape(){
            Path2D path = new Path2D.Double();
            path.moveTo(0, 0);
            
            path.lineTo(0, pennantBaseLength);
            path.lineTo(pennantEdgeLength, 0);
            path.closePath();
            return path;
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
    
        public WindBarbDefinition(final int vectorLength, final int basePennantLength, final int elementsSpacing,
                final int longBarbLength, final int zeroWindRadius) {
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

    int knots;

    int pennants;

    int longBarbs;

    int shortBarbs;


    static int DEFAULT_BARB_LENGTH = 10;

    static int DEFAULT_BASE_PENNANT_LENGTH = 5;

    static int DEFAULT_ELEMENTS_SPACING = 5;

    static int DEFAULT_VECTOR_LENGTH = 40;

    static int DEFAULT_ZERO_WIND_RADIUS = 10;
    
    /** A {@link WindBarbDefinition} instance reporting structural values for a WindBarb (vector length, sizes, ...) */
    WindBarbDefinition windBarbDefinition;

    static WindBarbDefinition DEFAULT_WINDBARB_DEFINITION = new WindBarbDefinition(
            WindBarb.DEFAULT_VECTOR_LENGTH,
            WindBarb.DEFAULT_BASE_PENNANT_LENGTH, 
            WindBarb.DEFAULT_ELEMENTS_SPACING, 
            WindBarb.DEFAULT_BARB_LENGTH, 
            WindBarb.DEFAULT_ZERO_WIND_RADIUS);

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
    Shape build() {
        int positionOnPath = 0;

        // Base barb
        Path2D path = new Path2D.Double();

        // Initialize Barb
        if (knots < 5) {
            // let's use a circle for Calm
            final Area output= new Area(new Ellipse2D.Float(0, 0, windBarbDefinition.zeroWindRadius, windBarbDefinition.zeroWindRadius));
            return output;
        } else {
            
            // draw wind barb line
            path.moveTo(0, 0);
            path.lineTo(0, windBarbDefinition.vectorLength);
        }

        // pennants management
        if (pennants > 0) {
            positionOnPath = drawPennants(path, positionOnPath);
            positionOnPath+=windBarbDefinition.elementsSpacing; // add spacing
        }

        // long barbs management
        if (longBarbs > 0) {
            positionOnPath = drawLongBarbs(path, positionOnPath);
            positionOnPath+=windBarbDefinition.elementsSpacing; // add spacing
        }

        // short barbs management
        if (shortBarbs > 0) {
            positionOnPath = drawShortBarb(path, positionOnPath);
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
    private int drawShortBarb(Path2D path, int positionOnPath) {
        
        final int basePennantLength = windBarbDefinition.basePennantLength;
        final int shortBarbLength = windBarbDefinition.shortBarbLength;
        
        if (pennants == 0 && longBarbs == 0) {
            positionOnPath = DEFAULT_ELEMENTS_SPACING;
        } 

        path.moveTo(0, positionOnPath);
        path.lineTo(shortBarbLength, positionOnPath - basePennantLength / 4.0 );
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
        
        final int basePennantLength = windBarbDefinition.basePennantLength;
        final int longBarbLength = windBarbDefinition.longBarbLength;
        final int elementsSpacing = windBarbDefinition.elementsSpacing;
        
        
        int appendedLongBarbs = 0;
        while (appendedLongBarbs < longBarbs) {
            
            if(appendedLongBarbs>=1){
                // spacing
                positionOnPath += elementsSpacing;
            }
            // draw long barb
            path.moveTo(0,positionOnPath);
            path.lineTo(longBarbLength,positionOnPath - basePennantLength / 2.0 );
            appendedLongBarbs++;
            
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
        if(pennants<=0){
            return positionOnPath;
        }
        
        final int basePennantLength = windBarbDefinition.basePennantLength;
        final int longBarbLength = windBarbDefinition.longBarbLength;
        
        int appendedPennants = 0;
        while (appendedPennants < pennants) {
            // move forward one pennant at a time
            // draw pennant
            path.moveTo(0, positionOnPath);
            positionOnPath  +=basePennantLength / 2.0;
            path.lineTo(longBarbLength , positionOnPath) ; // first edge
            positionOnPath += basePennantLength / 2.0;
            path.lineTo(0,positionOnPath); //second edge
            path.closePath();
            
            // move
            appendedPennants++;
        }
        return positionOnPath;
    }
}