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
package org.geotools.coverage.processing.operation;

import it.geosolutions.jaiext.JAIExt;
import it.geosolutions.jaiext.affine.AffineDescriptor;
import it.geosolutions.jaiext.range.Range;
import it.geosolutions.jaiext.range.RangeFactory;

import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;
import java.util.HashMap;
import java.util.Map;

import javax.media.jai.Interpolation;
import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.PropertyGenerator;
import javax.media.jai.ROI;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.processing.BaseScaleOperationJAI;
import org.geotools.factory.GeoTools;
import org.geotools.image.ImageWorker;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.util.InternationalString;

/**
 * This operation is simply a wrapper for the JAI Affine operation
 * 
 * @source $URL$
 * @version $Id$
 * @author Simone Giannecchini
 * @since 12.0
 * 
 * @see javax.media.jai.operator.AffineDescriptor
 */
public class Affine extends BaseScaleOperationJAI {

    /** serialVersionUID */
    private static final long serialVersionUID = 1699623079343108288L;

    /**
     * Default constructor.
     */
    public Affine() {
        super("Affine");

    }

    @Override
    protected RenderedImage createRenderedImage(ParameterBlockJAI parameters, RenderingHints hints) {
        final RenderedImage source = (RenderedImage) parameters.getSource(0);
        if(hints== null){
            hints= GeoTools.getDefaultHints().clone();
        }
                
        ////
        //
        // Interpolation
        //
        ////
        final Interpolation interpolation;
        if (parameters.getObjectParameter("interpolation") != null){
            interpolation = (Interpolation) parameters.getObjectParameter("interpolation");
        }else if (hints.get(JAI.KEY_INTERPOLATION) != null){
            interpolation = (Interpolation) hints.get(JAI.KEY_INTERPOLATION);
        }
        else {
            // I am pretty sure this should not happen. However I am not sure we should throw an error
            interpolation = null;
        }
        
        
        ////
        //
        // ImageWorker
        //
        ////
        final ImageWorker worker= new ImageWorker(source);
        worker.setRenderingHints(hints);
        worker.affine(
                (AffineTransform)parameters.getObjectParameter("transform") , 
                interpolation, 
                (double[])parameters.getObjectParameter("backgroundValues"));
        return worker.getRenderedImage();
    }
    
	protected void handleNoDataROI(ParameterBlockJAI parameters,
			GridCoverage2D sourceCoverage){
		// Getting the internal ROI property
		Object roiProp = sourceCoverage.getProperty("GC_ROI");
		ROI innerROI = (ROI) ((roiProp != null && roiProp instanceof ROI) ? roiProp : null);  
		
		if(JAIExt.isJAIExtOperation("Affine")){
			ROI roiParam = (ROI) parameters.getObjectParameter(3);
			ROI newROI = null;
			if(innerROI == null ){
				newROI = roiParam;
			} else {
				newROI = roiParam != null ? innerROI.add(roiParam) : innerROI;
			}
			parameters.set(newROI, 3);
		}
		
		
		Object nodataProp = sourceCoverage.getProperty("GC_NODATA");
		Range innerNodata = (Range) ((nodataProp != null && nodataProp instanceof Range) ? nodataProp : null);  
		if(JAIExt.isJAIExtOperation("Affine")){
			Range noDataParam = (Range) parameters.getObjectParameter(6);
			if(noDataParam == null ){
				parameters.set(innerNodata, 6);
			}
		}
	}
	
	protected Map<String, ?> getProperties(RenderedImage data,
			CoordinateReferenceSystem crs, InternationalString name,
			MathTransform gridToCRS, GridCoverage2D[] sources,
			Parameters parameters) {
		Map props = sources[PRIMARY_SOURCE_INDEX].getProperties();
		
		Map properties = new HashMap<>();
		if(props != null){
			properties.putAll(props);
		}
		
		// Setting NoData property if needed
		double[] background = (double[]) parameters.parameters.getObjectParameter(2);
		if(background != null){
			properties.put("GC_NODATA", RangeFactory.create(background[0], background[0]));
		}
		
		// Setting ROI if present
		PropertyGenerator propertyGenerator = new AffineDescriptor().getPropertyGenerators()[0];
		Object roiProp = propertyGenerator.getProperty("roi", data);
		if(roiProp != null && roiProp instanceof ROI){
			properties.put("GC_ROI", roiProp);
		}
		
		return properties;
	}

}
