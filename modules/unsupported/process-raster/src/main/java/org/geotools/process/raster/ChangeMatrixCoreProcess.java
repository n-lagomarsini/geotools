/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2011-2012, Open Source Geospatial Foundation (OSGeo)
 *    (C) 2007 TOPP - www.openplans.org.
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
package org.geotools.process.raster;

import it.geosolutions.jaiext.changematrix.ChangeMatrixDescriptor;
import it.geosolutions.jaiext.changematrix.ChangeMatrixDescriptor.ChangeMatrix;

import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.ROI;
import javax.media.jai.RenderedOp;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.factory.GeoTools;
import org.geotools.factory.Hints;
import org.geotools.geometry.jts.JTS;
import org.geotools.process.ProcessException;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeResult;
import org.geotools.referencing.operation.transform.ProjectiveTransform;
import org.jaitools.imageutils.ROIGeometry;

import com.vividsolutions.jts.geom.Geometry;

/**
 * Calculates the "ChangeMatrix" from 2 input coverages (Reference and Current) and for each image pixel calculates the sum of the
 * reference(multiplied per a constant) and current value. ChangeMatrix is an matrix which counts how many times the occurrences between 2 classes
 * occurs. 
 * 
 * 
 * 
 * @author Nicola Lagomarsini, GeoSolutions SAS.
 * 
 */
public class ChangeMatrixCoreProcess implements RasterProcess {

    /** Default multiplier */
    private static final int PIXEL_MULTY_ARG_INDEX = 100;

    /** Default number of threads */
    private static final int DEFAULT_THREAD_NUM = 1;

    /**
     * @param rasterT0 that is the reference Image (Mandatory)
     * @param rasterT1 rasterT1 that is the update situation (Mandatory)
     * @param roi that identifies the optional Geometry used as ROI (so that could be null)
     * @param ChangeMatrix representing matrix where the class changes are stored (Mandatory)
     * @param Integer coefficient to use for multiplying the input reference pixel (Default 100)
     * @param GridToWorld transformation, for transforming input ROI from Model to Raster space(Mandatory only when ROI is present)
     * @param equalCRS boolean indicating if the input ROI has the same CRS of the Coverages(Mandatory only when ROI is present)
     * @param reference coverage name (Mandatory)
     * @param ThreadPoolExecutor used for calculating the changematrix in parallel(could be null)
     * @return
     * @throws Exception
     */
    @DescribeResult(name = "changeMatrixProcess", description = "output coverage", type = GridCoverage2D.class)
    public GridCoverage2D execute(
            @DescribeParameter(name = "reference", description = "Input reference coverage") GridCoverage2D referenceCoverage,
            @DescribeParameter(name = "current", description = "Input current coverage") GridCoverage2D nowCoverage,
            @DescribeParameter(name = "ROI", min = 0, description = "Region Of Interest in GridCoverage CRS") Geometry roi,
            @DescribeParameter(name = "ChangeMatrix", min = 1, description = "ChangeMatrix object used in calculations") ChangeMatrix cm,
            @DescribeParameter(name = "Multiplier", min = 0, description = "Coefficient used for multiplying the pixels when calculating the output coverage") Integer multiplier,
            @DescribeParameter(name = "GridToWorld", min = 0, description = "GridToWorld transformation") AffineTransform gridToWorldCorner,
            @DescribeParameter(name = "equalCRS", min = 0, description = "Boolean indicating if the input ROI has the same CRS of the input Coverages") boolean equalCRS,
            @DescribeParameter(name = "refName", min = 1, description = "Name associated to the reference coverage") String referenceName,
            @DescribeParameter(name = "Executor", min = 0, description = "ThreadPoolExecutor used for calculating changematrix in a multithreaded environment") ThreadPoolExecutor executor)
            throws Exception {

        if (nowCoverage == null || referenceCoverage == null) {
            throw new ProcessException("Input Coverages not found");
        }

        // Input RenderedImages
        RenderedImage reference = referenceCoverage.getRenderedImage();

        RenderedImage now = nowCoverage.getRenderedImage();

        // ParameterBlock to use
        final ParameterBlockJAI pbj = new ParameterBlockJAI("ChangeMatrix");

        // Setting of the sources
        pbj.addSource(reference);
        pbj.addSource(now);

        // Parameters to set
        pbj.setParameter("result", cm);

        // Setting of the pixel multiplier if present
        if (multiplier != null) {
            pbj.setParameter(
                    ChangeMatrixDescriptor.PARAM_NAMES[ChangeMatrixDescriptor.PIXEL_MULTY_ARG_INDEX],
                    multiplier);
        } else {
            pbj.setParameter(
                    ChangeMatrixDescriptor.PARAM_NAMES[ChangeMatrixDescriptor.PIXEL_MULTY_ARG_INDEX],
                    PIXEL_MULTY_ARG_INDEX);
        }

        // ROI setting if present
        if (roi != null) {
            if (equalCRS) {
                pbj.setParameter("ROI", CoverageUtilities.prepareROI(roi, gridToWorldCorner));
            } else {
                pbj.setParameter("ROI", prepareROIGeometry(roi, gridToWorldCorner));
            }
        }

        // //////////////////////////////////////////////////////////////////////
        // Compute the Change Matrix ...
        // //////////////////////////////////////////////////////////////////////
        RenderedOp result = JAI.create("ChangeMatrix", pbj, null);

        // check if the ThreadPoolExecutor is null, in that case only a single thread is used
        if (executor == null) {
            executor = new ThreadPoolExecutor(DEFAULT_THREAD_NUM, DEFAULT_THREAD_NUM, 60,
                    TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1000000));
        }
        //
        // result computation for each tile
        //
        final int numTileX = result.getNumXTiles();
        final int numTileY = result.getNumYTiles();
        final int minTileX = result.getMinTileX();
        final int minTileY = result.getMinTileY();
        final List<Point> tiles = new ArrayList<Point>(numTileX * numTileY);
        for (int i = minTileX; i < minTileX + numTileX; i++) {
            for (int j = minTileY; j < minTileY + numTileY; j++) {
                tiles.add(new Point(i, j));
            }
        }
        final CountDownLatch sem = new CountDownLatch(tiles.size());
        // how many JAI tiles do we have?
        final RenderedOp temp = result;
        for (final Point tile : tiles) {

            executor.execute(new Runnable() {

                @Override
                public void run() {
                    temp.getTile(tile.x, tile.y);
                    sem.countDown();
                }
            });
        }
        try {
            sem.await();
        } catch (InterruptedException e) {
            throw new ProcessException(e);
        }
        // computation done!
        cm.freeze();

        /**
         * create the final coverage using final envelope
         */
        // hints for tiling
        final Hints hints = GeoTools.getDefaultHints().clone();

        final String rasterName = referenceName + "_cm_" + System.nanoTime();
        // Final Coverage creation
        final GridCoverage2D retValue = new GridCoverageFactory(hints).create(rasterName, result,
                referenceCoverage.getEnvelope());
        return retValue;
    }

    /**
     * Transform the provided {@link Geometry} in world coordinates into a ROI object in raster coordinates
     * 
     * @param roi
     * @param gridToWorld
     * @return
     * @throws Exception
     */
    private static ROI prepareROIGeometry(Geometry roi, AffineTransform gridToWorld)
            throws Exception {

        Geometry projected = JTS.transform(roi, ProjectiveTransform.create(gridToWorld).inverse());

        return new ROIGeometry(projected);
    }

}
