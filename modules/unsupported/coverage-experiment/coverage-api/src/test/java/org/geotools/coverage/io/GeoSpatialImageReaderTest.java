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
package org.geotools.coverage.io;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.spi.ImageReaderSpi;

import org.geotools.feature.NameImpl;
import org.geotools.imageio.GeoSpatialImageReader;
import org.opengis.feature.type.Name;

public class GeoSpatialImageReaderTest {

    /**
     * A simple GeoSpatialImageReader only supporting 2 testCoverages: testCoverage1, testCoverage2
     */
    public static class TestGeospatialImageReader extends GeoSpatialImageReader {

        protected TestGeospatialImageReader(ImageReaderSpi originatingProvider) {
            super(originatingProvider);
        }

        private static Map<Name, CoverageSourceDescriptor> descriptors = new HashMap<Name, CoverageSourceDescriptor>();

        private static List<Name> coverageNames = new ArrayList<Name>();

        static {
            coverageNames.add(new NameImpl("testCoverage1"));
            coverageNames.add(new NameImpl("testCoverage2"));
            for (Name coverageName : coverageNames) {
                descriptors.put(coverageName, new TestCoverageSourceDescriptor(coverageName.toString()));
            }
        }

        @Override
        public void addFile(String filePath) {

        }

        @Override
        public List<String> list() {
            return null;
        }

        @Override
        public void removeFile(String filePath) {

        }

        @Override
        public void purge() {

        }

        @Override
        public Collection<Name> getCoveragesNames() {
            return descriptors.keySet();
        }

        @Override
        public int getCoveragesNumber() {
            return descriptors.size();
        }

        @Override
        public CoverageSourceDescriptor getCoverageDescriptor(Name name) {
            if (descriptors.containsKey(name)) {
                return descriptors.get(name);
            }
            return null;
        }

        @Override
        public int getWidth(int imageIndex) throws IOException {
            return RasterLayoutTest.testRasterLayout.getWidth();
        }

        @Override
        public int getHeight(int imageIndex) throws IOException {
            return RasterLayoutTest.testRasterLayout.getHeight();
        }

        @Override
        public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {
            return null;
        }

        @Override
        public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
            return null;
        }

    }

}
