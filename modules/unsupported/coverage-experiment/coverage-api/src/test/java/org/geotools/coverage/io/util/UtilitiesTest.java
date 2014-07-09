/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2014, Open Source Geospatial Foundation (OSGeo)
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
package org.geotools.coverage.io.util;

import java.io.File;

import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.geotools.data.DataSourceException;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.metadata.iso.citation.Citations;
import org.geotools.referencing.CRS;
import org.geotools.referencing.NamedIdentifier;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.referencing.crs.DefaultProjectedCRS;
import org.geotools.referencing.datum.DefaultGeodeticDatum;
import org.junit.Assert;
import org.junit.Test;
import org.opengis.geometry.Envelope;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.ReferenceIdentifier;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.GeographicCRS;
import org.opengis.referencing.datum.Ellipsoid;
import org.opengis.referencing.datum.PrimeMeridian;
import org.opengis.referencing.operation.TransformException;

/**
 * Class testing the {@link Utilities} methods.
 */
public class UtilitiesTest extends Assert {

    final private static double DELTA = 1E-6;

    @Test
    public void testIdentifiers() {
        ReferenceIdentifier[] identifiers = Utilities.getIdentifiers("WGS84");
        assertNotNull(identifiers);
        assertEquals(7, identifiers.length);
        NamedIdentifier first = (NamedIdentifier) identifiers[0];
        assertEquals(first.getCode(), "WGS84");
        assertEquals(first.getAuthority(), Citations.OGC);
    }

    @Test
    public void testParsing() throws DataSourceException {

        // Test basic units parsing
        Unit unit = Utilities.parseUnit("m");
        assertEquals(unit, SI.METER);

        unit = Utilities.parseUnit("s");
        assertEquals(unit, SI.SECOND);

        unit = Utilities.parseUnit("temp_deg_c");
        assertEquals(unit, SI.CELSIUS);

        // Testinc Datum and Ellipsoid related parsing
        final double inverseFlattening = 298.257223563;
        final double equatorialRadius = 6378137;
        final DefaultGeodeticDatum datum = Utilities.getDefaultGeodeticDatum("WGS84",
                equatorialRadius, inverseFlattening, SI.METER);
        final PrimeMeridian primeMeridian = datum.getPrimeMeridian();
        assertEquals(0, primeMeridian.getGreenwichLongitude(), DELTA);
        final Ellipsoid ellipsoid = datum.getEllipsoid();
        assertEquals(equatorialRadius, ellipsoid.getSemiMajorAxis(), DELTA);
        assertEquals(inverseFlattening, ellipsoid.getInverseFlattening(), DELTA);

        // Testing coordinateReferenceSystem setup
        final GeographicCRS geoCrs = Utilities.getBaseCRS(equatorialRadius, inverseFlattening);
        assertEquals(datum, geoCrs.getDatum());

        CoordinateReferenceSystem mercatorCRS = Utilities.getMercator2SPProjectedCRS(10, 0, 0,
                geoCrs, null);
        assertTrue(mercatorCRS instanceof DefaultProjectedCRS);
        DefaultProjectedCRS mercator = (DefaultProjectedCRS) mercatorCRS;
        assertEquals(datum, mercator.getDatum());
        assertEquals(geoCrs, mercator.getBaseCRS());

    }

    @Test
    public void testCustomFile() {

        // Testing a custom file with suffix for coverageName
        final File file = new File("/sampleFile.ext:variable1");
        final File customFile = Utilities.getFileFromCustomInput(file);
        assertEquals("sampleFile.ext", customFile.getName());
    }

    @Test
    public void testEnvelopes() throws NoSuchAuthorityCodeException, FactoryException,
            TransformException {
        // Setting up an UTM and WGS84 CRSs

        // Setup a 3D envelope and return it as 2D, making sure there is no 3rd dimension anymore
        final GeneralEnvelope envelope3D = new GeneralEnvelope(DefaultGeographicCRS.WGS84_3D);
        envelope3D.setEnvelope(0, 0, 0, 10, 10, 10);
        final Envelope requestedEnvelope = Utilities.getRequestedEnvelope2D(envelope3D);
        assertEquals(0, requestedEnvelope.getMinimum(0), DELTA);
        assertEquals(0, requestedEnvelope.getMinimum(1), DELTA);
        assertEquals(10, requestedEnvelope.getMaximum(0), DELTA);
        assertEquals(10, requestedEnvelope.getMaximum(1), DELTA);

        // 3D envelope has 3rd dimension whilst 2D one hasn't
        assertEquals(10, envelope3D.getMaximum(2), DELTA);
        boolean is3D = true;
        try {
            requestedEnvelope.getMaximum(2);
        } catch (IndexOutOfBoundsException e) {
            is3D = false;
        }
        assertFalse(is3D);

    }

    @Test
    public void testGetEnvelope() throws FactoryException, TransformException {

        // Setup an envelope in WGS84
        GeneralEnvelope envelope = new GeneralEnvelope(DefaultGeographicCRS.WGS84);
        envelope.setEnvelope(0, 0, 10, 10);
        
        GeneralEnvelope wgs84 = new GeneralEnvelope(Utilities.getEnvelopeAsWGS84(envelope, true));
        GeneralEnvelope wgs84_2 = new GeneralEnvelope(Utilities.getEnvelopeAsWGS84(envelope, false));
        
        // Ensure the 2 envelope contain the initial one
        assertFalse(wgs84.isEmpty());
        assertTrue(wgs84.contains(envelope, true));
        assertFalse(wgs84_2.isEmpty());
        assertTrue(wgs84_2.contains(envelope, true));
        
        // Setup an envelope in EPSG:3857
        envelope = new GeneralEnvelope(CRS.decode("EPSG:3857"));
        envelope.setEnvelope(0, 0, 10, 10);
        
        wgs84 = new GeneralEnvelope(Utilities.getEnvelopeAsWGS84(envelope, true));
        wgs84_2 = new GeneralEnvelope(Utilities.getEnvelopeAsWGS84(envelope, false));
        // Ensure the new envelopes are not empty
        assertFalse(wgs84.isEmpty());
        assertFalse(wgs84_2.isEmpty());
    }

}