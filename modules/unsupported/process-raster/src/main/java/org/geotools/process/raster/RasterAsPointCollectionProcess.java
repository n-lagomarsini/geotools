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
package org.geotools.process.raster;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.NoSuchElementException;

import javax.media.jai.Interpolation;
import javax.media.jai.InterpolationNearest;
import javax.media.jai.PlanarImage;
import javax.media.jai.iterator.RectIter;
import javax.media.jai.iterator.RectIterFactory;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.processing.CoverageProcessor;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.GeoTools;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.collection.AdaptorFeatureCollection;
import org.geotools.feature.collection.BaseSimpleFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.process.ProcessException;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.resources.geometry.XRectangle2D;
import org.geotools.util.Utilities;
import org.opengis.coverage.processing.Operation;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.metadata.spatial.PixelOrientation;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.cs.AxisDirection;
import org.opengis.referencing.cs.CoordinateSystem;
import org.opengis.referencing.cs.CoordinateSystemAxis;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.MathTransform2D;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

/**
 * A process that wraps a {@link GridCoverage2D} as a collection of point feature.
 * 
 * @author Simone Giannecchini, GeoSolutions
 * 
 *
 * @source $URL$
 */
@DescribeProcess(title = "Raster As Point Collection", description = "Returns a collection of point features for the pixels of a raster.  The band values are provided as attributes.")
public class RasterAsPointCollectionProcess implements RasterProcess {
    
    protected static final Operation AFFINE = CoverageProcessor.getInstance().getOperation("Affine");
    
    @DescribeResult(name = "result", description = "Point features")
    public SimpleFeatureCollection execute(
            @DescribeParameter(name = "data", description = "Input raster") GridCoverage2D gc2d,
            @DescribeParameter(name = "scale", description = "scale",min=0, defaultValue="1.0f") Float scaleFactor,
            @DescribeParameter(name = "interpolation", description = "interpolation",min=0, defaultValue="InterpolationNearest") Interpolation interpolation,
            @DescribeParameter(name = "emisphere", description = "Add Emishpere",min=0, defaultValue="False" ) Boolean emisphere)
            throws ProcessException {
        if (gc2d ==null) {
            throw new ProcessException("Invalid input, source grid coverage should be not null");
        }
        
        if(scaleFactor!=null&&Math.abs(scaleFactor-1.0f)>1E-12){
            
            final ParameterValueGroup param = AFFINE.getParameters();
            param.parameter("Source").setValue(gc2d);
            param.parameter("transform").setValue(AffineTransform.getScaleInstance(scaleFactor, scaleFactor));
            param.parameter("Interpolation").setValue(new InterpolationNearest());

            gc2d = (GridCoverage2D) CoverageProcessor.getInstance().doOperation(param);
        }
        
        //return value
        try {
			return new RasterAsPointFeatureCollection(gc2d,emisphere);
		} catch (IOException e) {
			 throw new ProcessException("Unable to wrap provided grid coverage",e);
		}

    }

    /**
     * TODO @see {@link AdaptorFeatureCollection}
     * TODO @ee {@link DefaultFeatureCollection}
     * @author simboss
     *
     */
    private final static class RasterAsPointFeatureCollection extends BaseSimpleFeatureCollection {
    	
    	/**
    	 * The {@link GeometryFactory} cached here for building points inside iterators
    	 */
        static final GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory( GeoTools.getDefaultHints() );
        
    	/**
    	 * The {@link GridCoverage2D} that we want to expose as a point feature collection.
    	 */
		final GridCoverage2D gc2d;
		
		/**
		 * Number of points in this collections is as many as width*height.
		 */
		final  int size;

		/**
		 * Grid to world transformation at the upper left corner of the raster space.
		 */
		final MathTransform2D mt2D;

		/**
		 * The bounding box for this feature collection
		 */
		private ReferencedEnvelope bounds;

		/**
		 * Raster bounds for this coverage
		 */
		final  Rectangle rasterBounds;

		/**
		 * Number of bands
		 */
		final int numBands;

        private boolean emisphere;

        private MathTransform transformToWGS84;

        private int northDimension=-1;
		
		public RasterAsPointFeatureCollection(final GridCoverage2D gc2d) throws IOException {
        	this(gc2d,false);
            
        }
        
        /**
         * @param gc2d2
         * @param emisphere
         */
        public RasterAsPointFeatureCollection(GridCoverage2D gc2d, boolean emisphere)throws IOException {
            super(modify(CoverageUtilities.createFeatureType(gc2d, Point.class),emisphere));
            this.gc2d=gc2d;
            this.emisphere=emisphere;
            
            //
            // get various elements from this coverage
            //
            // SIZE
            final RenderedImage raster=gc2d.getRenderedImage();
            size=raster.getWidth()*raster.getHeight();
            
            // GRID TO WORLD
            mt2D= gc2d.getGridGeometry().getGridToCRS2D(PixelOrientation.UPPER_LEFT);
            
            // BOUNDS take into account that we want to map center coordinates
            rasterBounds = PlanarImage.wrapRenderedImage(raster).getBounds();
            final XRectangle2D rasterBounds_=new XRectangle2D(raster.getMinX()+0.5, raster.getMinY()+0.5, raster.getWidth()-1,  raster.getHeight()-1);
            try {
                                bounds = new ReferencedEnvelope(CRS.transform(mt2D, rasterBounds_, null),gc2d.getCoordinateReferenceSystem2D());
                        } catch (MismatchedDimensionException e) {
                                final IOException ioe= new IOException();
                                ioe.initCause(e);
                                throw ioe;
                        } catch (TransformException e) {
                                final IOException ioe= new IOException();
                                ioe.initCause(e);
                                throw ioe;
                        }
                        
                        // BANDS
                        numBands = gc2d.getNumSampleDimensions();
            
            //
            // Emisphere management
            //
            CoordinateReferenceSystem coverageCRS = gc2d.getCoordinateReferenceSystem2D();
            if(!CRS.equalsIgnoreMetadata(DefaultGeographicCRS.WGS84, coverageCRS)){
                try {
                    
                    // save transform
                    final MathTransform transform = CRS.findMathTransform(coverageCRS, DefaultGeographicCRS.WGS84,true);
                    if(!transform.isIdentity()){
                        this.transformToWGS84=transform;
                    }
                    
                    final CoordinateSystem coordinateSystem = coverageCRS.getCoordinateSystem();
                    // save also latitude axis
                    final int dimension=coordinateSystem.getDimension();
                    for( int i=0;i<dimension;i++){
                        CoordinateSystemAxis axis = coordinateSystem.getAxis(i);
                        if(axis.getDirection().absolute().compareTo(AxisDirection.NORTH)==0){
                            this.northDimension=i;
                            break;
                        }
                    }
                    
                    if(northDimension<0){
                        final IOException ioe= new IOException("Unable to find nort dimension in the coverage CRS+ "+coverageCRS.toWKT());
                        throw ioe;
                    }
                } catch (FactoryException e) {
                    throw new IOException(e);
                }
            }
        }

        /**
         * @param createFeatureType
         * @param emisphere
         * @return
         */
        private static SimpleFeatureType modify(SimpleFeatureType featureType,
                boolean emisphere) {
            if(!emisphere){
                return featureType;
            }
            
            //  add emishpere attribute
            final SimpleFeatureTypeBuilder ftBuilder= new SimpleFeatureTypeBuilder();
            ftBuilder.setName(featureType.getName());
            
            // CRS
            ftBuilder.setCRS(featureType.getCoordinateReferenceSystem());
            
            // TYPE is as follows the_geom | band
            ftBuilder.setDefaultGeometry(featureType.getGeometryDescriptor().getLocalName());

            //copy attributes
            for(AttributeDescriptor atd:featureType.getAttributeDescriptors()){
                ftBuilder.add(atd);
            }
            
            // add emisphere
            ftBuilder.add("emisphere",String.class); //valid values N/S
            return ftBuilder.buildFeatureType();
        }

        @Override
        public SimpleFeatureIterator features() {
            return new RasterAsPointFeatureIterator(this);
        }

		@Override
		public int size() {
			return size;
		}

		@Override
		public ReferencedEnvelope getBounds() {
			return new ReferencedEnvelope(bounds);
		}
    }

    private final static class RasterAsPointFeatureIterator implements SimpleFeatureIterator {
    	
    	private final double[] temp;

    	private final SimpleFeatureBuilder fb;
    	
    	private final RasterAsPointFeatureCollection fc;
        
        private int index=0;
        
        private final int size;

		private final RectIter iterator;

		private final Coordinate coord= new Coordinate();

        public RasterAsPointFeatureIterator(final RasterAsPointFeatureCollection fc) {

        	//checks
        	Utilities.ensureNonNull("fc", fc);
        	
        	//get elements
        	this.fc= fc;
            this.fb = new SimpleFeatureBuilder(fc.getSchema());
            this.size=fc.size;
            
            // create an iterator that only goes forward, it is the fastest one
            iterator= RectIterFactory.create(fc.gc2d.getRenderedImage(), null);

            //
            //start the iterator
            //
            iterator.startLines();
            if(iterator.finishedLines())
            	throw new NoSuchElementException("Index beyond size:"+index+">"+size);
            iterator.startPixels();
            if(iterator.finishedPixels())
            	throw new NoSuchElementException("Index beyond size:"+index+">"+size);   
            
            // appo
            temp= new double[fc.numBands];
        }

        /**
         * Closes this iterator
         */
        public void close() {
        	// NO OP
        }

        /**
         * Tells us whether or not we have more elements to iterate on.
         */
        public boolean hasNext() {
            return index<size;
        }

        public SimpleFeature next() throws NoSuchElementException {
            if(!hasNext())
            	throw new NoSuchElementException("Index beyond size:"+index+">"+size);
            
            // iterate
            if(iterator.finishedPixels())
            	throw new NoSuchElementException("Index beyond size:"+index+">"+size);
            if(iterator.finishedLines())
            	throw new NoSuchElementException("Index beyond size:"+index+">"+size);                    
            
            // ID
            final int id=index;
            
            // POINT
            // can we reuse the coord?
            coord.x= 0.5+fc.rasterBounds.x+index%fc.rasterBounds.width;
            coord.y=0.5+ fc.rasterBounds.y+index/fc.rasterBounds.width ;
            final Point point = RasterAsPointFeatureCollection.geometryFactory.createPoint( coord );
            try {
				fb.add(JTS.transform(point, fc.mt2D));
			} catch (MismatchedDimensionException e) {
				final NoSuchElementException nse= new NoSuchElementException();
				nse.initCause(e);
				throw nse;
			} catch (TransformException e) {
				final NoSuchElementException nse= new NoSuchElementException();
				nse.initCause(e);
				throw nse;
			}
            
			// VALUES
            // loop on bands
			iterator.getPixel(temp);
			for(double d:temp){
				// I exploit the internal converters to go from double to whatever the type is
				// TODO is this correct or we can do more.
				fb.add(d);
			}
			
			// emisphere??
			if(fc.emisphere){
			    if(fc.transformToWGS84==null){
			       if(point.getY()>=0){
			           fb.add("N");
			       }else {
			           fb.add("S");
			       }
			    }else{
			            try {
			                Point wgs84Point = (Point) JTS.transform(point, fc.transformToWGS84);
                                        if (wgs84Point.getCoordinate().getOrdinate(fc.northDimension) >= 0) {
                                            fb.add("N");
                                        } else {
                                            fb.add("S");
                                        }			                
	                        } catch (MismatchedDimensionException e) {
	                                final NoSuchElementException nse= new NoSuchElementException();
	                                nse.initCause(e);
	                                throw nse;
	                        } catch (TransformException e) {
	                                final NoSuchElementException nse= new NoSuchElementException();
	                                nse.initCause(e);
	                                throw nse;
	                        }			        
			    }
			}
			
            
            // do we need to wrap the line??
            if(iterator.nextPixelDone()){
            	if(!iterator.nextLineDone())
            		iterator.startPixels();
            }
            
            // return
            final SimpleFeature returnValue= fb.buildFeature(String.valueOf(id));
            
            // increase index and iterator
            index++;
            
            return returnValue;
        }

    }
}
