package org.geotools.gce.imagemosaic.catalog;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;

import javax.media.jai.ROI;

import org.geotools.gce.imagemosaic.ReadType;

import com.vividsolutions.jts.geom.Geometry;

public interface MultiLevelROI {
    
    public ROI getTransformedROI(AffineTransform at, int imageIndex, Rectangle imgBounds,
            ReadType readType);

    public boolean isEmpty();

    public Geometry getFootprint();

}
