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

import org.geotools.referencing.operation.projection.MapProjection;

import ucar.nc2.constants.CF;

/** 
 * Class used to properly setup NetCDF CF Projection parameters. Given a known Projection, 
 * it will take care of remap the Projection's parameters to NetCDF CF GridMapping parameters.
 * 
 * @see <a href="http://cfconventions.org/Data/cf-conventions/cf-conventions-1.6/build/cf-conventions.html#appendix-grid-mappings">NetCDF CF, Appendix
 *      F: Grid Mappings</a>
 */
public class NetCDFProjection {

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

    public NetCDFProjection(Map<String, String> parametersMapping) {
        this.netCDFParametersMapping = Collections.unmodifiableMap(parametersMapping);
    }

    private Map<String, String> netCDFParametersMapping;

    /**
     * Returns the underlying unmodifiable Referencing to NetCDF parameters mapping.
     * 
     * @return
     */
    public Map<String, String> getParameters() {
        return netCDFParametersMapping;
    };

    /**
     * Currently supported NetCDF projections. TODO: Add more. Check the CF Document
     * 
     * @see <a href="http://cfconventions.org/Data/cf-conventions/cf-conventions-1.6/build/cf-conventions.html#appendix-grid-mappings">NetCDF CF,
     *      Appendix F: Grid Mappings</a>
     */
    public final static NetCDFProjection TRANSVERSE_MERCATOR_PARAMS;
    public final static NetCDFProjection LAMBERT_CONFORMAL_CONIC_1SP_PARAMS;
    public final static NetCDFProjection LAMBERT_CONFORMAL_CONIC_2SP_PARAMS;

    static {
        Map<String, String> tm_mapping = new HashMap<String, String>();
        tm_mapping.put(SCALE_FACTOR, CF.SCALE_FACTOR_AT_CENTRAL_MERIDIAN);
        tm_mapping.put(CENTRAL_MERIDIAN, CF.LONGITUDE_OF_CENTRAL_MERIDIAN);
        tm_mapping.put(LATITUDE_OF_ORIGIN, CF.LATITUDE_OF_PROJECTION_ORIGIN);
        tm_mapping.put(FALSE_EASTING, CF.FALSE_EASTING);
        tm_mapping.put(FALSE_NORTHING, CF.FALSE_NORTHING);
        TRANSVERSE_MERCATOR_PARAMS = new NetCDFProjection(tm_mapping);

        Map<String, String> lcc_mapping = new HashMap<String, String>();

        lcc_mapping.put(CENTRAL_MERIDIAN, CF.LONGITUDE_OF_CENTRAL_MERIDIAN);
        lcc_mapping.put(LATITUDE_OF_ORIGIN, CF.LATITUDE_OF_PROJECTION_ORIGIN);
        lcc_mapping.put(FALSE_EASTING, CF.FALSE_EASTING);
        lcc_mapping.put(FALSE_NORTHING, CF.FALSE_NORTHING);

        Map<String, String> lcc_1sp_mapping = new HashMap<String, String>();
        lcc_1sp_mapping.putAll(lcc_mapping);
        lcc_1sp_mapping.put(STANDARD_PARALLEL_1, CF.STANDARD_PARALLEL);
        LAMBERT_CONFORMAL_CONIC_1SP_PARAMS = new NetCDFProjection(lcc_1sp_mapping);

        Map<String, String> lcc_2sp_mapping = new HashMap<String, String>();
        lcc_2sp_mapping.putAll(lcc_mapping);
        lcc_2sp_mapping.put(STANDARD_PARALLEL_1, CF.STANDARD_PARALLEL);
        lcc_2sp_mapping.put(STANDARD_PARALLEL_2, CF.STANDARD_PARALLEL);
        LAMBERT_CONFORMAL_CONIC_2SP_PARAMS = new NetCDFProjection(lcc_2sp_mapping);

    }
}