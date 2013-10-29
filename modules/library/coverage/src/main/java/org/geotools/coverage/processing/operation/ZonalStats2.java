/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2011, Open Source Geospatial Foundation (OSGeo)
 *    (C) 2001-2007 TOPP - www.openplans.org.
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
package org.geotools.coverage.processing.operation;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.ROI;
import javax.media.jai.ROIShape;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.processing.BaseStatisticsOperationJAI;
import org.geotools.coverage.processing.CoverageProcessingException;
import org.geotools.geometry.Envelope2D;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.parameter.ImagingParameters;
import org.geotools.referencing.operation.transform.ProjectiveTransform;
import org.geotools.util.logging.Logging;
import org.jaitools.imageutils.ROIGeometry;
import org.opengis.coverage.processing.OperationNotFoundException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.metadata.spatial.PixelOrientation;
import org.opengis.parameter.ParameterNotFoundException;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.util.AffineTransformation;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;

/**
 * This operation is similar to the {@link ZonalStats} operation but implements a new version of the "ZonalStats" operation. The main difference
 * between the two operations is that inside this version multiple geometries are handled, instead of the old version which supports only one geometry
 * per time.
 * 
 * @author geosolutions
 * 
 */

public class ZonalStats2 extends BaseStatisticsOperationJAI {

    /** {@link Logger} for this class. */
    public final static Logger LOGGER = Logging
            .getLogger("org.geotools.coverage.processing.operation");

    /**
     * This operation MUST be performed on the geophysics data for this {@link GridCoverage2D}.
     * 
     * @param parameters {@link ParameterValueGroup} that describes this operation
     * @return always true.
     */
    protected boolean computeOnGeophysicsValues(ParameterValueGroup parameters) {
        return true;
    }

    /**
     * Constructs a default {@code "ZonalStats2"} operation.
     */
    public ZonalStats2() throws OperationNotFoundException {
        super(getOperationDescriptor("Zonal"));
    }

    /**
     * Copies parameter values from the specified {@link ParameterValueGroup} to the {@link ParameterBlockJAI}
     * 
     * @param parameters The {@link ParameterValueGroup} to be copied.
     * @return A copy of the provided {@link ParameterValueGroup} as a JAI block.
     * 
     * @see org.geotools.coverage.processing.OperationJAI#prepareParameters(org.opengis.parameter.ParameterValueGroup)
     */
    @Override
    protected ParameterBlockJAI prepareParameters(final ParameterValueGroup parameters) {
        // /////////////////////////////////////////////////////////////////////
        //
        // Make a copy of the input parameters.
        //
        // ///////////////////////////////////////////////////////////////////
        final ImagingParameters copy = (ImagingParameters) descriptor.createValue();
        org.geotools.parameter.Parameters.copy(parameters, copy);
        final ParameterBlockJAI block = (ParameterBlockJAI) copy.parameters;
        try {

            // /////////////////////////////////////////////////////////////////////
            //
            //
            // Now transcode the parameters as needed by this operation.
            //
            //
            // ///////////////////////////////////////////////////////////////////
            // XXX make it robust
            final GridCoverage2D source = (GridCoverage2D) parameters.parameter(
                    operation.getSourceNames()[PRIMARY_SOURCE_INDEX]).getValue();
            final AffineTransform gridToWorldTransformCorrected = new AffineTransform(
                    (AffineTransform) ((GridGeometry2D) source.getGridGeometry())
                            .getGridToCRS2D(PixelOrientation.UPPER_LEFT));
            final MathTransform worldToGridTransform;
            try {
                worldToGridTransform = ProjectiveTransform.create(gridToWorldTransformCorrected
                        .createInverse());
            } catch (NoninvertibleTransformException e) {
                // //
                //
                // Something bad happened here, namely the transformation to go
                // from grid to world was not invertible. Let's wrap and
                // propagate the error.
                //
                // //
                final CoverageProcessingException ce = new CoverageProcessingException(e);
                throw ce;
            }

            // //
            //
            // get the original envelope and the crs
            //
            // //
            final CoordinateReferenceSystem crs = source.getCoordinateReferenceSystem2D();
            final Envelope2D envelope = source.getEnvelope2D();

            // /////////////////////////////////////////////////////////////////////
            //
            // Transcode the polygons parameter into a roi list. If an old ROI
            // parameter is used, then it is put inside the roi list
            //
            // I am assuming that the supplied values are in the same
            // CRS as the source coverage. We here apply
            //
            // /////////////////////////////////////////////////////////////////////
            // New Geometry list object parameter
            final Object roilist = parameters.parameter("roilist").getValue();
            // Old singular ROI object parameter
            Object o;
            try {
                o = parameters.parameter("roi").getValue();
            } catch (ParameterNotFoundException p) {
                o = null;
            }
            // Output List for storing the geometries inside a ROI List
            List<ROI> outputList = null;

            // Creation of the New RoiList object
            if (roilist != null && roilist instanceof List<?>) {

                List<SimpleFeature> geomList = (List<SimpleFeature>) roilist;
                // Iteration on all the features
                int numGeom = geomList.size();
                Iterator<SimpleFeature> geomIter = geomList.iterator();
                // Output List definition
                outputList = new ArrayList<ROI>(numGeom);
                // Calculation of the source coverage envelope
                ReferencedEnvelope coverageEnvelope = new ReferencedEnvelope(envelope);
                // For each feature, there is the conversion
                while (geomIter.hasNext()) {
                    SimpleFeature zone = geomIter.next();

                    // grab the geometry and eventually reproject it to the
                    Geometry geometry = (Geometry) zone.getDefaultGeometry();

                    // first off, cut the geometry around the coverage bounds if necessary
                    ReferencedEnvelope geometryEnvelope = new ReferencedEnvelope(
                            geometry.getEnvelopeInternal(), crs);

                    if (!coverageEnvelope.intersects((Envelope) geometryEnvelope)) {
                        // no intersection, no stats
                        continue;
                    } else if (!coverageEnvelope.contains((Envelope) geometryEnvelope)) {
                        // the geometry goes outside of the coverage envelope, that makes
                        // the stats fail for some reason
                        geometry = JTS.toGeometry((Envelope) coverageEnvelope).intersection(
                                geometry);
                        geometryEnvelope = new ReferencedEnvelope(geometry.getEnvelopeInternal(),
                                crs);
                    }

                    // transform the geometry to raster space so that we can use it as a ROI source
                    Geometry rasterSpaceGeometry = JTS.transform(geometry, worldToGridTransform);

                    // simplify the geometry so that it's as precise as the coverage, excess coordinates
                    // just make it slower to determine the point in polygon relationship
                    Geometry simplifiedGeometry = DouglasPeuckerSimplifier.simplify(
                            rasterSpaceGeometry, 1);

                    // translation of the selected geometry of 0.5, from the pixel center to the corners.
                    AffineTransformation at = new AffineTransformation();

                    at.setToTranslation(-0.5, -0.5);
                    simplifiedGeometry.apply(at);

                    // build a shape using a fast point in polygon wrapper
                    ROI roi = new ROIGeometry(simplifiedGeometry, false);
                    // addition of the roi object
                    outputList.add(roi);
                }

            } else if (o != null && o instanceof Polygon) {
                // Output List definition
                outputList = new ArrayList<ROI>(1);
                // Selection of the polygon associated with the ROI
                final Polygon roiInput = (Polygon) o;
                // If the input ROI intersects the coverage, then it is added to the list
                if (new ReferencedEnvelope(roiInput.getEnvelopeInternal(),
                        source.getCoordinateReferenceSystem2D())
                        .intersects((Envelope) new ReferencedEnvelope(envelope))) {

                    final java.awt.Polygon shapePolygon = convertPolygon(roiInput,
                            worldToGridTransform);

                    outputList.add(new ROIShape(shapePolygon));

                }
            }
            // Setting of the roilist parameter to the parameterBlock
            block.setParameter("roilist", outputList);

            return block;
        } catch (Exception e) {
            // //
            //
            // Something bad happened here Let's wrap and propagate the error.
            //
            // //
            final CoverageProcessingException ce = new CoverageProcessingException(e);
            throw ce;
        }
    }
}
