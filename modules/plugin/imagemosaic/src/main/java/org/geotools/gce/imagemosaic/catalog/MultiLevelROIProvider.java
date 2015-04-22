package org.geotools.gce.imagemosaic.catalog;

import java.io.IOException;

import org.opengis.feature.simple.SimpleFeature;

public interface MultiLevelROIProvider {

    public MultiLevelROI getMultiScaleROI(SimpleFeature sf) throws IOException;

    public void dispose();

}
