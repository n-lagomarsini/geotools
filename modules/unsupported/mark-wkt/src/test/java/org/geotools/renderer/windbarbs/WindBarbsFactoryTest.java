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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;

import javax.swing.JFrame;
import javax.swing.JPanel;

import junit.framework.TestCase;

import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.GeoTools;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.expression.Expression;

import com.vividsolutions.jts.geom.LineString;

/**
 * Unit tests for WindBarbs factory
 * 
 * @author Daniele Romagnoli, GeoSolutions SAS
 * 
 */
public class WindBarbsFactoryTest extends TestCase {
    public class ShapePanel extends JPanel {

        private Shape shp;

        public void paint(Graphics g) {
            super.paint(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setColor(Color.black);
            g2d.setTransform(AffineTransform.getTranslateInstance(-shp.getBounds().getMinX(), -shp.getBounds().getMinY()));
            g2d.draw(shp);
        }
    }
    
    private SimpleFeature feature;

    private Expression exp;

    private FilterFactory ff;

    {
        try {
            ff = CommonFactoryFinder.getFilterFactory(GeoTools.getDefaultHints());
            SimpleFeatureTypeBuilder featureTypeBuilder = new SimpleFeatureTypeBuilder();
            featureTypeBuilder.setName("TestType");
            featureTypeBuilder.add("geom", LineString.class, DefaultGeographicCRS.WGS84);
            SimpleFeatureType featureType = featureTypeBuilder.buildFeatureType();
            SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);
            this.feature = featureBuilder.buildFeature(null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void testWellKnownTextLineString() {
        WindBarbsFactory wbf = new WindBarbsFactory();
        try {
            this.exp = ff.literal(WindBarbsFactory.WINDBARBS_PREFIX + "default(145)[kts]");
            Shape shp = (Shape) wbf.getShape(null, this.exp, this.feature);
            System.out.println(shp.getBounds());
            ShapePanel p = new ShapePanel();
            p.shp=shp;
            
            JFrame frame = new JFrame("Draw Shapes") ;
            frame.getContentPane().add( p );
            frame.setSize(600, 600);
            frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE);
            frame.setVisible( true );
            System.in.read();
            
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
            return;
        }

        assertTrue(true);
    }

}
