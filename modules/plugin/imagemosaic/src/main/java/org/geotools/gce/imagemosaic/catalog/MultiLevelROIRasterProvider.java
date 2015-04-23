package org.geotools.gce.imagemosaic.catalog;

import it.geosolutions.imageio.maskband.DatasetLayout;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.coverage.grid.io.AbstractGridCoverage2DReader;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.factory.Hints;
import org.geotools.gce.imagemosaic.Utils;
import org.geotools.util.logging.Logging;
import org.opengis.feature.simple.SimpleFeature;

public class MultiLevelROIRasterProvider implements MultiLevelROIProvider {

    static final Logger LOGGER = Logging.getLogger(SidecarFootprintProvider.class);
    
    public static final Hints EXCLUDE_MOSAIC = new Hints(Utils.EXCLUDE_MOSAIC, true);

    private File mosaicFolder;

    public MultiLevelROIRasterProvider(File mosaicFolder) {
        this.mosaicFolder = mosaicFolder;
    }

    @Override
    public MultiLevelROI getMultiScaleROI(SimpleFeature sf) throws IOException {
        if (sf == null) {
            // Feature is not defined
            return null;
        }
        // Extracting File from feature
        Object value = sf.getAttribute("location");
        if (value != null && value instanceof String) {
            String strValue = (String) value;
            File file = getFile(strValue);
            MultiLevelROI result = null;
            if (file.exists() && file.canRead()) {
                try {
                    // When looking for formats which may parse this file, make sure to exclude the ImageMosaicFormat as return
                    AbstractGridFormat format = (AbstractGridFormat) GridFormatFinder.findFormat(file, EXCLUDE_MOSAIC);
                    AbstractGridCoverage2DReader reader = format.getReader(file);
                    // Getting Dataset Layout
                    DatasetLayout layout = reader.getDatasetLayout();
                    // If present use it
                    if(layout != null){
                        // Getting Total Number of masks
                        int numExternalMasks = layout.getNumExternalMasks() > 0 ? layout.getNumExternalMasks() : 0;
                        int numInternalMasks = layout.getNumInternalMasks() > 0 ? layout.getNumInternalMasks() : 0;
                        int numExternalMaskOverviews = layout.getNumExternalMaskOverviews() > 0 ? layout.getNumExternalMaskOverviews() : 0;
                        int totalMasks = numExternalMasks + 
                                numInternalMasks + 
                                numExternalMaskOverviews;
                        // Check if masks are present
                        // NOTE No Mask: Outside ROI
                        if(totalMasks > 0){
                            return new MultiLevelROIRaster(layout, file, sf);
                        }
                    }
                } catch (Exception e) {
                    throw new IOException("Failed to load the footprint for granule " + strValue, e);
                }
            }
            return result;
        } else {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Could not use the location attribute value to search for "
                        + "the file, the value was: " + value);
            }
            return null;
        }
    }

    private File getFile(String strValue) throws IOException {
        File file = new File(strValue);
        if (!file.isAbsolute()) {
            file = new File(mosaicFolder, strValue);
        }

        return file;
    }

    @Override
    public void dispose() {
    }

}
