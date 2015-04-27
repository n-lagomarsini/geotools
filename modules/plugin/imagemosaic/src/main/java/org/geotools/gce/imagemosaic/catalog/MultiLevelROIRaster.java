/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2015, Open Source Geospatial Foundation (OSGeo)
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
package org.geotools.gce.imagemosaic.catalog;

import it.geosolutions.imageio.maskband.DatasetLayout;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageInputStreamSpi;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.media.jai.ROI;

import org.geotools.data.DataUtilities;
import org.geotools.gce.imagemosaic.ReadType;
import org.geotools.gce.imagemosaic.Utils;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.image.ImageWorker;
import org.geotools.util.logging.Logging;
import org.opengis.feature.simple.SimpleFeature;

import com.sun.media.jai.operator.ImageReadDescriptor;
import com.vividsolutions.jts.geom.Geometry;

/**
 * {@link MultiLevelROI} implementation supporting Raster masking. Notice that actually it does not support Inset definition.
 * 
 * @author Nicola Lagomarsini GeoSolutions
 */
public class MultiLevelROIRaster implements MultiLevelROI {

    /** {@link Logger} used for logging exceptions */
    private final static Logger LOGGER = Logging.getLogger(MultiLevelROIRaster.class);

    /** Extension for the external mask files */
    private static final String MSK_EXTENSION = ".msk";

    /** Extension for the external overviews files */
    private static final String OVR_EXTENSION = ".ovr";

    /** DatasetLayout object containing information about the file internal structure */
    private DatasetLayout layout;

    /** Input File from where we load internal Masks */
    private File file;

    /** Bounding Box of the mask */
    private Geometry footprint;

    /** Envelope of the mask */
    private ReferencedEnvelope env;

    public MultiLevelROIRaster(DatasetLayout layout, File file, SimpleFeature sf) {
        // Initialization
        this.layout = layout;
        this.file = file;
        // this.feature = sf;
        // Getting Feature Geometry
        Geometry geo = (Geometry) sf.getDefaultGeometry();
        // Getting as envelope
        env = JTS.toEnvelope(geo);
        // Save envelope as Geometry
        footprint = JTS.toGeometry(env);
    }

    public ROI getTransformedROI(AffineTransform at, int imageIndex, Rectangle imgBounds,
            ImageReadParam params, ReadType readType) {
        // Check if ROI must be taken from internal or external masks
        int numInternalMasks = layout.getNumInternalMasks();
        int numExternalMasks = layout.getNumExternalMasks();
        int numExternalMaskOverviews = layout.getNumExternalMaskOverviews();
        // Getting FileHelper
        FileHelper h = findBestMatch(imgBounds, imageIndex, numInternalMasks, numExternalMasks,
                numExternalMaskOverviews, params);
        // Define which File must be used for reading mask info
        File inFile = h.file;
        // Defining imageIndex based on the imageIndex
        int index = h.index;

        // No file found?
        if (inFile == null) {
            throw new IllegalArgumentException("Unable to load Raster Footprint for granule: "
                    + file.getAbsolutePath());
        }
        URL granuleUrl = DataUtilities.fileToURL(inFile);
        // Getting input stream and reader from File
        ImageInputStream inStream = null;
        ImageReader reader = null;
        try {
            // Getting input Stream
            ImageInputStreamSpi inStreamSpi = Utils.getInputStreamSPIFromURL(granuleUrl);
            inStream = inStreamSpi.createInputStreamInstance(granuleUrl, ImageIO.getUseCache(),
                    ImageIO.getCacheDirectory());
            // Getting Reader
            ImageReaderSpi readerSpi = Utils.getReaderSpiFromStream(null, inStream);
            reader = readerSpi.createReaderInstance();
            // Setting input
            reader.setInput(inStream, false, false);
            // Reading file
            RenderedImage raster = null;
            if (readType.equals(ReadType.DIRECT_READ)) {
                // read data directly
                raster = reader.read(index, h.readParameters);
            } else {
                // read data
                inStream.seek(0);
                raster = ImageReadDescriptor.create(inStream, index, false, false, false, null,
                        null, h.readParameters, reader, null);
            }
            // Check bounds
            double scaleX = imgBounds.width / (1.0d * raster.getWidth());
            double scaleY = imgBounds.height / (1.0d * raster.getHeight());
            AffineTransform scale = AffineTransform.getScaleInstance(scaleX, scaleY);
            // Apply translation if needed
            int transX = imgBounds.x;
            int transY = imgBounds.y;
            AffineTransform translate = AffineTransform.getTranslateInstance(transX, transY);
            translate.concatenate(scale);
            // Wrapping Raster with ImageWorker
            ImageWorker worker = new ImageWorker(raster);
            // Scaling ROI if necessary
            if (!scale.isIdentity()) {
                worker.affine(translate, null, null).getRenderedImage();
            }
            // Creating ROI
            return worker.binarize(1).getImageAsROI();
        } catch (IOException e) {
            if (LOGGER.isLoggable(Level.SEVERE)) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
            }
        } finally {
            if (readType != ReadType.JAI_IMAGEREAD && reader != null) {
                try {
                    reader.dispose();
                } catch (Exception e) {
                    if (LOGGER.isLoggable(Level.SEVERE)) {
                        LOGGER.log(Level.SEVERE, e.getMessage(), e);
                    }
                }
            }
            if (readType != ReadType.JAI_IMAGEREAD && inStream != null) {
                try {
                    inStream.close();
                } catch (Exception e) {
                    if (LOGGER.isLoggable(Level.SEVERE)) {
                        LOGGER.log(Level.SEVERE, e.getMessage(), e);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public boolean isEmpty() {
        return env.isEmpty();
    }

    @Override
    public Geometry getFootprint() {
        return footprint;
    }

    /**
     * Method for searching the best mask image index based on the overview image index. Also this method searches for the correct mask file to use
     * for reading the mask and also sets the {@link ImageReadParam}s
     * 
     * @param imgBounds
     * @param imageIndex
     * @param numInternalMasks
     * @param numExternalMasks
     * @param numExternalMaskOverviews
     * @param params 
     * @return
     */
    private FileHelper findBestMatch(Rectangle imgBounds, int imageIndex, int numInternalMasks,
            int numExternalMasks, int numExternalMaskOverviews, ImageReadParam params) {
        int index = -1;
        File inFile = null;
        // ImageReadParameters definition
        ImageReadParam readParameters = new ImageReadParam();
        readParameters.setSourceRegion(params.getSourceRegion());
        readParameters.setSourceSubsampling(1, 1, 0, 0);
        int totalExternalMask = numExternalMasks + numExternalMaskOverviews;
        if (imageIndex < numInternalMasks) {
            inFile = file;
            index = layout.getInternalMaskImageIndex(imageIndex);
        } else if (imageIndex < (numExternalMasks)) {
            inFile = new File(file.getAbsolutePath() + MSK_EXTENSION);
            index = imageIndex;
        } else if (imageIndex < totalExternalMask) {
            inFile = new File(file.getAbsolutePath() + MSK_EXTENSION + OVR_EXTENSION);
            index = imageIndex - numExternalMasks;
        } else {
            // Reset ImageParameter Definition
            //readParameters = new ImageReadParam();
            readParameters.setSourceSubsampling(1, 1, 0, 0);
            // Getting last available mask value
            if (numInternalMasks > 0) {
                inFile = file;
                index = numInternalMasks - 1;
            } else if (numExternalMaskOverviews > 0) {
                inFile = new File(file.getAbsolutePath() + MSK_EXTENSION + OVR_EXTENSION);
                index = numExternalMaskOverviews - 1;
            } else if (numExternalMasks > 0) {
                inFile = new File(file.getAbsolutePath() + MSK_EXTENSION);
                index = numExternalMasks - 1;
            }
        }
        // Returning output
        FileHelper helper = new FileHelper();
        helper.file = inFile;
        helper.index = index;
        helper.readParameters = readParameters;
        return helper;
    }

    /**
     * Helper class used for storing information to use for read the mask data.
     * 
     * @author Nicola Lagomarsini GeoSolutions
     */
    static class FileHelper {

        File file;

        int index;

        ImageReadParam readParameters;

    }
}
