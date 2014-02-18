package org.geotools.process.raster;

import it.geosolutions.jaiext.changematrix.ChangeMatrixDescriptor.ChangeMatrix;

import java.util.Set;
import java.util.TreeSet;

import org.geotools.coverage.grid.GridCoverage2D;
import org.junit.BeforeClass;
import org.junit.Test;

public class ChangeMatrixTest {

    private static GridCoverage2D referenceCoverage;

    private static GridCoverage2D currentCoverage;

    private static Set<Integer> classes;

    private static ChangeMatrix cm;


    @BeforeClass
    public static void prepareValues(){
        
        referenceCoverage = null;
        currentCoverage = null;
        
        
        classes = new TreeSet<Integer>();
        classes.add(4);
        classes.add(5);
        
        cm = new ChangeMatrix(classes);
        
    }
    
    
    
    @Test
    public void test() {
        
    }

}
