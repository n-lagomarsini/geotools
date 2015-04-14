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
package org.geotools.imageio.netcdf.cv;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.measure.quantity.Angle;
import javax.measure.quantity.Length;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.geotools.factory.GeoTools;
import org.geotools.factory.Hints;
import org.geotools.imageio.netcdf.utilities.NetCDFUtilities;
import org.geotools.referencing.CRS;
import org.geotools.referencing.ReferencingFactoryFinder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.referencing.crs.DefaultProjectedCRS;
import org.geotools.referencing.cs.DefaultCartesianCS;
import org.geotools.referencing.cs.DefaultEllipsoidalCS;
import org.geotools.referencing.datum.DefaultEllipsoid;
import org.geotools.referencing.datum.DefaultGeodeticDatum;
import org.geotools.referencing.datum.DefaultPrimeMeridian;
import org.geotools.referencing.factory.AllAuthoritiesFactory;
import org.geotools.referencing.operation.DefaultOperationMethod;
import org.geotools.referencing.operation.DefiningConversion;
import org.geotools.referencing.operation.projection.MapProjection;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.GeographicCRS;
import org.opengis.referencing.datum.DatumFactory;
import org.opengis.referencing.datum.Ellipsoid;
import org.opengis.referencing.datum.GeodeticDatum;
import org.opengis.referencing.datum.PrimeMeridian;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.MathTransformFactory;

import ucar.nc2.Attribute;
import ucar.nc2.Variable;
import ucar.nc2.constants.CF;
import ucar.nc2.dataset.NetcdfDataset;

/** 
 * Class used to properly setup NetCDF CF Projection parameters. Given a known Projection, 
 * it will take care of remap the Projection's parameters to NetCDF CF GridMapping parameters.
 * 
 * @see <a href="http://cfconventions.org/Data/cf-conventions/cf-conventions-1.6/build/cf-conventions.html#appendix-grid-mappings">NetCDF CF, Appendix
 *      F: Grid Mappings</a>
 */
public class NetCDFProjection {

    private final static java.util.logging.Logger LOGGER = Logger.getLogger(NetCDFProjection.class.toString());
    
    public static final String STANDARD_PARALLEL_1 = MapProjection.AbstractProvider.STANDARD_PARALLEL_1
            .getName().getCode();

    public static final String STANDARD_PARALLEL_2 = MapProjection.AbstractProvider.STANDARD_PARALLEL_2
            .getName().getCode();

    public static final String CENTRAL_MERIDIAN = MapProjection.AbstractProvider.CENTRAL_MERIDIAN
            .getName().getCode();

    public static final String LATITUDE_OF_ORIGIN = MapProjection.AbstractProvider.LATITUDE_OF_ORIGIN
            .getName().getCode();

    public static final String SCALE_FACTOR = MapProjection.AbstractProvider.SCALE_FACTOR.getName()
            .getCode();

    public static final String FALSE_EASTING = MapProjection.AbstractProvider.FALSE_EASTING
            .getName().getCode();

    public static final String FALSE_NORTHING = MapProjection.AbstractProvider.FALSE_NORTHING
            .getName().getCode();

    /**
     * Cached {@link MathTransformFactory} for building {@link MathTransform}
     * objects.
     */
    private static final MathTransformFactory mtFactory;
    
    /** EPSG factories for various purposes. */
    private static final AllAuthoritiesFactory allAuthoritiesFactory;

    private static final DatumFactory datumObjFactory;

    public NetCDFProjection(String projectionName, Map<String, String> parametersMapping) {
        this.name = projectionName;
        this.netCDFParametersMapping = Collections.unmodifiableMap(parametersMapping);
    }

    private Map<String, String> netCDFParametersMapping;

    private String name; 

    /**
     * Returns the underlying unmodifiable Referencing to NetCDF parameters mapping.
     * 
     * @return
     */
    public Map<String, String> getParameters() {
        return netCDFParametersMapping;
    }

    public String getName() {
        return name;
    }

    /**
     * Currently supported NetCDF projections. TODO: Add more. Check the CF Document
     * 
     * @see <a href="http://cfconventions.org/Data/cf-conventions/cf-conventions-1.6/build/cf-conventions.html#appendix-grid-mappings">NetCDF CF,
     *      Appendix F: Grid Mappings</a>
     */
    public final static NetCDFProjection LAMBERT_AZIMUTHAL_EQUAL_AREA;
    public final static NetCDFProjection TRANSVERSE_MERCATOR;
    public final static NetCDFProjection LAMBERT_CONFORMAL_CONIC_1SP;
    public final static NetCDFProjection LAMBERT_CONFORMAL_CONIC_2SP;

    private final static Map<String, NetCDFProjection> supportedProjections = new HashMap<String, NetCDFProjection>();

    private static final int METER_UNIT_CODE = 9001;
    
    private static final String UNKNOWN = "unknown";

    static {
        Hints hints = GeoTools.getDefaultHints().clone();

        // various authority related factories
        allAuthoritiesFactory = new AllAuthoritiesFactory(hints);

        // various factories
        datumObjFactory = ReferencingFactoryFinder.getDatumFactory(hints);
        mtFactory = ReferencingFactoryFinder.getMathTransformFactory(hints);

        // Setting up Lambert Azimuthal equal area
        Map<String, String> lazeq_mapping = new HashMap<String, String>();
        lazeq_mapping.put(CENTRAL_MERIDIAN, CF.LONGITUDE_OF_PROJECTION_ORIGIN);
        lazeq_mapping.put(LATITUDE_OF_ORIGIN, CF.LATITUDE_OF_PROJECTION_ORIGIN);
        lazeq_mapping.put(FALSE_EASTING, CF.FALSE_EASTING);
        lazeq_mapping.put(FALSE_NORTHING, CF.FALSE_NORTHING);
        LAMBERT_AZIMUTHAL_EQUAL_AREA = new NetCDFProjection(CF.LAMBERT_AZIMUTHAL_EQUAL_AREA, lazeq_mapping);

        // Setting up Transverse Mercator
        Map<String, String> tm_mapping = new HashMap<String, String>();
        tm_mapping.put(SCALE_FACTOR, CF.SCALE_FACTOR_AT_CENTRAL_MERIDIAN);
        tm_mapping.put(CENTRAL_MERIDIAN, CF.LONGITUDE_OF_CENTRAL_MERIDIAN);
        tm_mapping.put(LATITUDE_OF_ORIGIN, CF.LATITUDE_OF_PROJECTION_ORIGIN);
        tm_mapping.put(FALSE_EASTING, CF.FALSE_EASTING);
        tm_mapping.put(FALSE_NORTHING, CF.FALSE_NORTHING);
        TRANSVERSE_MERCATOR = new NetCDFProjection(CF.TRANSVERSE_MERCATOR, tm_mapping);

        Map<String, String> lcc_mapping = new HashMap<String, String>();

        lcc_mapping.put(CENTRAL_MERIDIAN, CF.LONGITUDE_OF_CENTRAL_MERIDIAN);
        lcc_mapping.put(LATITUDE_OF_ORIGIN, CF.LATITUDE_OF_PROJECTION_ORIGIN);
        lcc_mapping.put(FALSE_EASTING, CF.FALSE_EASTING);
        lcc_mapping.put(FALSE_NORTHING, CF.FALSE_NORTHING);

        // Setting up Lambert Conformal Conic 1SP
        Map<String, String> lcc_1sp_mapping = new HashMap<String, String>();
        lcc_1sp_mapping.putAll(lcc_mapping);
        lcc_1sp_mapping.put(LATITUDE_OF_ORIGIN, CF.STANDARD_PARALLEL);
        LAMBERT_CONFORMAL_CONIC_1SP = new NetCDFProjection(CF.LAMBERT_CONFORMAL_CONIC + "_1SP", lcc_1sp_mapping);

        // Setting up Lambert Conformal Conic 2SP
        Map<String, String> lcc_2sp_mapping = new HashMap<String, String>();
        lcc_2sp_mapping.putAll(lcc_mapping);
        lcc_2sp_mapping.put(STANDARD_PARALLEL_1, CF.STANDARD_PARALLEL);
        lcc_2sp_mapping.put(STANDARD_PARALLEL_2, CF.STANDARD_PARALLEL);
        LAMBERT_CONFORMAL_CONIC_2SP = new NetCDFProjection(CF.LAMBERT_CONFORMAL_CONIC + "_2SP", lcc_2sp_mapping);

        supportedProjections.put(TRANSVERSE_MERCATOR.name, TRANSVERSE_MERCATOR);
        supportedProjections.put(LAMBERT_CONFORMAL_CONIC_1SP.name, LAMBERT_CONFORMAL_CONIC_1SP);
        supportedProjections.put(LAMBERT_AZIMUTHAL_EQUAL_AREA.name, LAMBERT_AZIMUTHAL_EQUAL_AREA);
//        supportedProjections.put(LAMBERT_CONFORMAL_CONIC_2SP.name, LAMBERT_CONFORMAL_CONIC_2SP);
    }

    /** 
     * Get a NetCDF Projection definition referred by name 
     */
    public static NetCDFProjection getSupportedProjection(String projectionName) {
        if (supportedProjections.containsKey(projectionName)) {
            return supportedProjections.get(projectionName);
        } else {
            LOGGER.warning("The specified projection isn't currently supported: " + projectionName);
            return null;
        }
    }

    /**
     * Extract the GridMapping information from the specified variable and setup a {@link CoordinateReferenceSystem} instance
     * @throws FactoryException 
     * */
    public static CoordinateReferenceSystem parseProjection(Variable var) throws FactoryException {
        // Preliminar check on spatial_ref attribute which may contain a fully defined WKT
        // as an instance, being set from GDAL, or a GeoTools NetCDF ouput 

        Attribute spatialRef = var.findAttribute(NetCDFUtilities.SPATIAL_REF);
        CoordinateReferenceSystem crs = parseSpatialRef(spatialRef);
        if (crs != null) {
            return crs;
        }

        Attribute gridMappingName = var.findAttribute(NetCDFUtilities.GRID_MAPPING_NAME);
        if (gridMappingName == null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("No grid_mapping_name attribute has been found. Unable to parse a CF projection from this variable");
            }
            return null;
        }

        // Preliminar checks on special cases 
        String mappingName = gridMappingName.getStringValue();
        String projectionName = mappingName;
        if (mappingName.equalsIgnoreCase(CF.LAMBERT_CONFORMAL_CONIC)) {
            Attribute standardParallel = var.findAttribute(CF.STANDARD_PARALLEL);
            final int numParallels = standardParallel.getLength();
            projectionName = numParallels == 1 ? LAMBERT_CONFORMAL_CONIC_1SP.name : 
                LAMBERT_CONFORMAL_CONIC_2SP.name;
        }

        // Getting the proper projection and set the projection parameters
        NetCDFProjection projection = supportedProjections.get(projectionName);

        // The GT referencing projection parameters
        ParameterValueGroup parameters = mtFactory.getDefaultParameters(projectionName);

        // The NetCDF projection parameters
        Map<String, String> projectionParams = projection.getParameters();
        Set<String> parameterKeys = projectionParams.keySet();
        for (String parameterKey: parameterKeys) {
            String attributeName = projectionParams.get(parameterKey);
            Attribute attribute = var.findAttribute(attributeName);
            if (attribute != null) {
                // Get the parameter value and handle special management for longitudes outside -180, 180
                double value = attribute.getNumericValue().doubleValue();
                if (attributeName.contains("meridian") || attributeName.contains("longitude")) {
                    value = value - (360) * Math.floor(value / (360) + 0.5);
                }
                parameters.parameter(parameterKey).setValue(value);
            }
        }

        Unit<Length> linearUnit = SI.METER; //allAuthoritiesFactory.createUnit("EPSG:" + METER_UNIT_CODE);
        Unit<Angle> angularUnit = NonSI.DEGREE_ANGLE;

        //TODO: check for custom ellipsoids, prime meridian and datums
        // Ellipsoid and PrimeMeridian
        Ellipsoid ellipsoid = buildEllipsoid(var, linearUnit);
        PrimeMeridian primeMeridian = DefaultPrimeMeridian.GREENWICH;

        // Datum
        final GeodeticDatum datum = new DefaultGeodeticDatum(UNKNOWN, ellipsoid, primeMeridian);

        // Base Geographic CRS
        final Map<String, String> props = new HashMap<String, String>();

        // make the user defined GCS from all the components...
        props.put("name", UNKNOWN);
        GeographicCRS baseCRS = new DefaultGeographicCRS(props, datum,
                DefaultEllipsoidalCS.GEODETIC_2D.usingUnit(angularUnit));

        double semiMajor = ellipsoid.getSemiMajorAxis();
        double inverseFlattening = ellipsoid.getInverseFlattening();

        // setting missing parameters
        parameters.parameter("semi_minor").setValue(semiMajor * (1 - (1 / inverseFlattening)));
        parameters.parameter("semi_major").setValue(semiMajor); 

        // create math transform
        MathTransform transform = mtFactory.createParameterizedTransform(parameters);

        // create the projection transform
        DefiningConversion conversionFromBase= new DefiningConversion(
                Collections.singletonMap("name", UNKNOWN), 
                new DefaultOperationMethod(transform),
                transform);

        // Create the projected CRS
        return new DefaultProjectedCRS(
                java.util.Collections.singletonMap("name", UNKNOWN),
                conversionFromBase,
                baseCRS,
                transform,
                DefaultCartesianCS.PROJECTED);
    }

    /** 
     * Extract the {@link CoordinateReferenceSystem} from the {@link NetCDFUtilities#SPATIAL_REF}
     * attribute if present.
     * @param spatialRef
     * @return
     */
    private static CoordinateReferenceSystem parseSpatialRef(Attribute spatialRef) {
        CoordinateReferenceSystem crs = null;
        if (spatialRef != null) {
            String wkt = spatialRef.getStringValue();
            try {
                crs = CRS.parseWKT(wkt);
            } catch (FactoryException e) {
                if (LOGGER.isLoggable(Level.WARNING)){ 
                    LOGGER.warning("Unable to setup a CRS from the specified WKT: " + wkt);
                }
            }
        }
        return crs;
    }

    /**
     * Build a custom ellipsoid, looking for definition parameters
     * @param var
     * @param linearUnit
     * @return
     */
    private static Ellipsoid buildEllipsoid(Variable var, Unit<Length> linearUnit) {
        Number semiMajorAxis = null;
        Number semiMinorAxis = null;
        Double inverseFlattening = Double.NEGATIVE_INFINITY;
        Ellipsoid ellipsoid = null;

        // Look for semiMajorAxis first
        Attribute semiMajorAxisAttribute = var.findAttribute(CF.SEMI_MAJOR_AXIS);
        if (semiMajorAxisAttribute != null) {
            semiMajorAxis = semiMajorAxisAttribute.getNumericValue();
        }

        // If not present, maybe it's a sphere. Looking for the radius
        if (semiMajorAxis == null) {
            semiMajorAxisAttribute = var.findAttribute(CF.EARTH_RADIUS);
            if (semiMajorAxisAttribute != null) {
                semiMajorAxis = semiMajorAxisAttribute.getNumericValue();
            }
        }

        // Looking for semiMininorAxis
        Double semiMajor = semiMajorAxis.doubleValue();
        Attribute semiMinorAxisAttribute = var.findAttribute(CF.SEMI_MINOR_AXIS);
        if (semiMinorAxisAttribute != null) {
            semiMinorAxis = semiMinorAxisAttribute.getNumericValue();
        }

        // Create an Ellipsoid in case semiMinorAxis is defined
        if (semiMinorAxis != null) {
            ellipsoid = DefaultEllipsoid.createEllipsoid(UNKNOWN, semiMajor, semiMinorAxis.doubleValue()
                , linearUnit);
        }

        // If not defined yet, looking for other attributes.
        if (ellipsoid == null) {
            Attribute inverseFlatteningAttribute = var.findAttribute(CF.INVERSE_FLATTENING);
            if (inverseFlatteningAttribute != null) {
                inverseFlattening = inverseFlatteningAttribute.getNumericValue().doubleValue();
            }
            ellipsoid = DefaultEllipsoid.createFlattenedSphere(UNKNOWN,semiMajor, inverseFlattening, linearUnit);
        }
        return ellipsoid;
    }

    public static CoordinateReferenceSystem parseProjection(NetcdfDataset dataset) {
        Attribute attribute = dataset.findAttribute(NetCDFUtilities.SPATIAL_REF);
        return parseSpatialRef(attribute);
    }
}