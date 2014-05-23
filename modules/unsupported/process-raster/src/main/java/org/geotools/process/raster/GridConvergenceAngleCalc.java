package org.geotools.process.raster;


import org.geotools.geometry.DirectPosition2D;
import org.geotools.referencing.CRS;
import org.geotools.referencing.GeodeticCalculator;
import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.cs.AxisDirection;
import org.opengis.referencing.cs.CoordinateSystemAxis;


/**
 * Used to calculate an estimate for the Grid Convergence Angle at a point 
 * within a 2D Coordinate Reference System.  The Grid Convergence Angle at
 * any point on a projected 2D map is the difference between "up" or "grid 
 * north" on the map at that point and True North.  Since the map is projected,
 * the Grid Convergence Angle can change at each point on the map; True North 
 * can be in a different Cartesian direction on the flat map for every point.
 * One example impact of this is that vectors cannot be accurately drawn on 
 * the screen by simply rotating them a certain amount in "screen degrees."
 * This class, then, is used to estimate the Grid Convergence Angle at those 
 * points.  Though the underlying meaning is the same, different mapping 
 * conventions define the angle's direction (+/-) and beginning point / 
 * ending point differently.  Therefore, for the purposes of this code, the 
 * Grid Convergence Angle is defined as the angle (C) FROM true north TO 
 * grid north with 0 deg up and angles increasing positively clockwise.  So, 
 * for the example below, C would be approximately -33deg, since the angle FROM
 * true north TO grid north is Counter-Clockwise. 
 *<pre>
 * 
 *                         Up
 *                     Grid North
 *                         0 deg   True North
 *                         |       .
 *                         |  C  .
 *                         |   .
 *                         | . 
 *       270 deg ----------O---------90 deg
 *                         |
 *                         |
 *                         |
 *                         |
 *                         |
 *                         180 deg
 * 
 * </pre>
 * This class uses the GeoTools GeodeticCalculator for performing underlying 
 * calculations. 
 * <br><br>
 * Some literature (don't have a link) says that the Grid Covergence Angle is 
 * really the angle between a line extending toward grid north and one
 * extending toward true north THAT HAVE BEEN PROJECTED into the map projection,
 * but suggests the difference between this angle and the way the angle is 
 * being estimated here is small.  May want some verification of that. 
 * <br><br>
 * @author Mike Grogan, WeatherFlow, Inc., Synoptic
 * <br><br>
 * @see http://www.threelittlemaids.co.uk/magdec/explain.html
 * @see http://www.bluemarblegeo.com/knowledgebase/calculator/Scale_Factor_and_Convergence.htm
 * 
 */
final class GridConvergenceAngleCalc 
{

	private final CoordinateReferenceSystem crs;
	private GeodeticCalculator geoCalc;
	private final int upAxisDimension; 
	
	/**
	 * Constructs a new GridConvergenceAngleCalc for a given
	 * CoordinateReferenceSystem
	 * @param crs CoordinateReferenceSystem for which to construct a new 
	 * GridConvergenceAngleCalc. 
	 */
	public GridConvergenceAngleCalc(CoordinateReferenceSystem crs)
	{
		this.crs = crs; 
		this.geoCalc = new GeodeticCalculator(this.crs);
		this.upAxisDimension = determineUpAxisDimension();
	}
	
	/**
	 * Estimates the grid convergence angle at a position within a Coordinate
	 * Reference System.  The angle returned is as described in the 
	 * documentation for the class.  The Coordinate Reference System of the 
	 * supplied position must be the same as was used when constructing the 
	 * calculator, because using anything else would not make sense as 
	 * convergence angle depends on projection.  
	 * 
	 * @param position DirectPosition2D at which we want to estimate the 
	 * grid convergence angle 
	 * @return double containing grid convergence angle, as described in 
	 * documentation for the class.
	 * @throws Exception
	 */
	public double getConvergenceAngle(DirectPosition2D position) throws Exception
	{
		//
		// If we could not find the "up" axis ... meaning up on the map/screen
		// not in the vertical ... then throw an exception
		//
		
		if (this.upAxisDimension < 0 )
		{
			throw new Exception("Up Axis can not be determined.");
		}
		
		//
		// Check to make sure the coordinate reference system for the 
		// argument is the same as the calculator. 
		//
		
		CoordinateReferenceSystem positionCRS = 
				position.getCoordinateReferenceSystem();
		
		if (!positionCRS.equals(crs))
		{
			throw new Exception("Position CRS does not match Calculator CRS");
		}

		// 
		// We will use the Geotools Geodetic calculator to estimate the 
		// convergence angle.  We estimate this by taking the supplied point, 
		// moving "upward" along the proper upward map axis by 1 unit, and 
		// then having the Geodetic calculator tell us the azimuth from the 
		// starting point to the ending point.  Since the azimuth is relative
		// to true north ... and we are "walking" along a grid north 
		// parallel, the azimuth then essentially tells us the angle from 
		// true north to grid north, or the local grid convergence angle.
		//
		//
		
		//
		// Get the "up" axis 
		// 
		
		CoordinateSystemAxis upAxis = 
				crs.getCoordinateSystem().getAxis(upAxisDimension);
		
		//
		// Need to make sure we're not going to go out of bounds along the 
		// axis by going up a little bit.
		//
		// Determine the maximum value along that axis
		// 
		
		double upAxisMax = upAxis.getMaximumValue();
	
		// 
		// Get the starting value along the up axis
		// 
		
		double startValueUp = position.getOrdinate(upAxisDimension); 
		
		//
		// If adding 1 to the up axis is going to push us out of bounds, then 
		// first subtract 1 from the starting position ... the estimate should
		// still be close if units are close. 
		// 
		
		if ((startValueUp + 1) > upAxisMax)
		{
			position.setOrdinate(upAxisDimension, 
					position.getOrdinate(upAxisDimension) - 1);
		}

		//
		// Set the starting position for the geodetic calculator to position. 
		//

		geoCalc.setStartingPosition(position);
		
		//
		// Set the ending position to be the same as the starting position, 
		// except move "up" 1 unit along the "up" axis. 
		//
		

		DirectPosition2D endingPosition = 
				new DirectPosition2D((DirectPosition) position);
		endingPosition.setOrdinate(upAxisDimension, 
				position.getOrdinate(upAxisDimension)+1);
		geoCalc.setDestinationPosition(endingPosition);
		
		//
		// Now just ask for the azimuth, which is our convergence angle 
		// estimate. 
		//
		return geoCalc.getAzimuth();
	}
	
	/**
	 * Determines which axis in the calculator's Coordinate Reference 
	 * System is "up" 
	 * @return int with up axis dimension, or -1 if up axis cannot be 
	 * found.  
	 */
	private int determineUpAxisDimension()
	{
		//
		// Grab the number of dimensions.  We only can deal with a 2D 
		// projection here.  Set to -1 if not a 2D system ... and let 
		// other code throw errors. 
		//
		
		int numDimensions = crs.getCoordinateSystem().getDimension();
		
		if (numDimensions > 2)
		{
			return -1;
		}
		
		//
		// Loop through all of the axes until you find the one that is 
		// probably the upward axis. 
		//
		
		for (int i=0; i < numDimensions; i++)
		{
			CoordinateSystemAxis axis = crs.getCoordinateSystem().getAxis(i);
			AxisDirection axisDirection = axis.getDirection();
			
			if (axisDirection.equals(AxisDirection.DISPLAY_UP) || 
					axisDirection.equals(AxisDirection.EAST_NORTH_EAST) || 
					axisDirection.equals(AxisDirection.NORTH) || 
					axisDirection.equals(AxisDirection.NORTH_EAST) || 
					axisDirection.equals(AxisDirection.NORTH_NORTH_EAST) ||
					axisDirection.equals(AxisDirection.NORTH_NORTH_WEST) || 
					axisDirection.equals(AxisDirection.NORTH_WEST) || 
					axisDirection.equals(AxisDirection.ROW_POSITIVE) ||
					axisDirection.equals(AxisDirection.UP) ||
					axisDirection.equals(AxisDirection.WEST_NORTH_WEST))
			{
				return i; 
			}
		}
	
		//
		// If not found yet, find one with name Northing or y and assume 
		// it is up. 
		// 
		
		for (int i=0; i < numDimensions; i++)
		{
			CoordinateSystemAxis axis = crs.getCoordinateSystem().getAxis(i);
			String axisName = axis.getName().toString().toUpperCase();
			if (axisName.equals("Y") || 
					axisName.equals("NORTHING") || 
					axisName.contains("NORTHING"))
			{
				return i;
			}
		}
		
		//
		// If the up axis still hasn't been found, then signify we can't 
		// find it with a -1. 
		//
		
		return -1;
	}

	/**
	 * Runs some test points and CRS through the calculator. 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception
	{
		//
		// Test some points within a custom Lambert Conformal Conic projection
		// used by the HRRR forecast model. 
		//
		
		String wktString="PROJCS[\"Lambert_Conformal_Conic\"," +
				"GEOGCS[\"GCS_unknown\",DATUM[\"D_unknown\"," +
				"SPHEROID[\"Sphere\",6367470,0]],PRIMEM[\"Greenwich\",0]," +
				"UNIT[\"Degree\",0.017453292519943295]]," +
				"PROJECTION[\"Lambert_Conformal_Conic_1SP\"]," +
				"PARAMETER[\"latitude_of_origin\",38.5]," +
				"PARAMETER[\"central_meridian\",-97.5]," +
				"PARAMETER[\"scale_factor\",1]," +
				"PARAMETER[\"false_easting\",0]," +
				"PARAMETER[\"false_northing\",0],UNIT[\"m\",1.0]]";
		CoordinateReferenceSystem crs = CRS.parseWKT(wktString);
		GridConvergenceAngleCalc angleCalc = new GridConvergenceAngleCalc(crs);
		DirectPosition2D position = 
				new DirectPosition2D(crs,2626.018310546785*1000,-1118.3695068359375*1000);
		System.out.println("Testing "+position);
		System.out.println("Should get approx 16.0573598047079");
		System.out.println("Grid convergence angle: "+angleCalc.getConvergenceAngle(position));
		System.out.println();
		position = 
				new DirectPosition2D(crs,-1201.9818115234375*1000,-1172.3695068359375*1000);
		System.out.println("Testing "+position);
		System.out.println("Should get approx -7.461565880473206");
		System.out.println("Grid convergence angle: "+angleCalc.getConvergenceAngle(position));
		System.out.println();
		//
		// Test Oklahoma in EPSG:3411 Northern Hemis. Sea Ice Polar Stereo.
		//
		crs = CRS.decode("EPSG:3411");
		angleCalc = new GridConvergenceAngleCalc(crs);
		position = new DirectPosition2D(crs,-5050427.62537,-3831167.39071);
		System.out.println("Testing "+position);
		System.out.println("Should get approx -52.81667373163404");
		System.out.println("Grid convergence angle: "+angleCalc.getConvergenceAngle(position));
		System.out.println();
		//
		// Test Antarctic Polar Stereo EPSG:3031
		// 
		//
		crs = CRS.decode("EPSG:3031");
		angleCalc = new GridConvergenceAngleCalc(crs);
		for (int i=0; i < 5000; i++)
		{
		position = new DirectPosition2D(crs,5450569.17764,-5333348.64467);
		System.out.println("Testing "+position);
		System.out.println("Should get approx -134.37722187798775");
		System.out.println("Grid convergence angle: "+angleCalc.getConvergenceAngle(position));
		System.out.println();
		}
	}


//	/*
//	 * A couple of very early and rough ways of trying this are below.  
//	 * Leaving in and commenting out for now in case of future need to 
//	 * reference ... or try these ways of doing it ... 
//	public double doIt(DirectPosition2D sourcePoint, CoordinateReferenceSystem sourceCRS) throws Exception
//	{
//
//		if (sourceCRS == null)
//		{
//			sourceCRS = sourcePoint.getCoordinateReferenceSystem();
//		}
//
//		System.out.println("Source Point:"+sourcePoint.toString());
//		//System.out.println("Coordinate Reference System: "+sourceCRS.toWKT());
//
//
//		CoordinateReferenceSystem latLonCRS = DefaultGeographicCRS.WGS84;
//		//				CoordinateReferenceSystem latLonCRS = CRS.decode("EPSG:4326");
//		MathTransform data2world = CRS.findMathTransform(sourceCRS, latLonCRS, true); //lenient Bursa Wolf see http://docs.geotools.org/stable/userguide/faq.html#q-bursa-wolf-parameters-required
//		MathTransform world2data = data2world.inverse();
//
//		DirectPosition2D sourcePointLatLon = new DirectPosition2D(); 
//
//		data2world.transform(sourcePoint, sourcePointLatLon);
//		System.out.println("sourcePointLatLon: "+sourcePointLatLon);
//		System.out.println();
//
//		//
//		// Get a new gridPoint in source coordinates slightly more positive.
//		// 
//		DirectPosition2D newGridPointSourceCoords = new DirectPosition2D(sourceCRS,sourcePoint.getX(),sourcePoint.getY() + 0.001);
//		System.out.println("newGridPointSourceCoords: "+newGridPointSourceCoords);
//		System.out.println();
//		//
//		// Get the latitude of that point.  This will be the latitude of a new point
//		// on the meridian. 
//		//
//
//		DirectPosition2D newGridPointLatLonCoords = new DirectPosition2D(); 
//		data2world.transform(newGridPointSourceCoords, newGridPointLatLonCoords);
//		System.out.println("newGridPointLatLonCoords: "+newGridPointLatLonCoords);
//		System.out.println();
//		double meridianPointLat = newGridPointLatLonCoords.getY();
//		//
//		// Make a new point along the same meridian as our source point but at 
//		// same latitude as our new grid point. 
//		//
//		DirectPosition2D newMeridianPointLatLon = new DirectPosition2D(latLonCRS, sourcePointLatLon.getX(),meridianPointLat); 
//		System.out.println("newMeridianPointLatLonCoords: "+newMeridianPointLatLon);
//
//		//
//		// Get that position now back in our grid coordinates
//		//
//		DirectPosition2D newMeridianPointSourceCoords = new DirectPosition2D(); 
//		world2data.transform(newMeridianPointLatLon, newMeridianPointSourceCoords);
//
//		//
//		// Print that out
//		//
//		System.out.println("newMeridianPointSourceCoords: "+newMeridianPointSourceCoords);
//		System.out.println();
//		//
//		// Determine difference between points
//		//
//		//		double dX = newMeridianPointSourceCoords.getX() - newGridPointSourceCoords.getX();
//		//		double dY = newMeridianPointSourceCoords.getY() - newGridPointSourceCoords.getY();
//		//		double dX = newMeridianPointSourceCoords.getX() - sourcePoint.getX();
//		//		double dY = newMeridianPointSourceCoords.getY() - sourcePoint.getY();
//		//		double dX = -newMeridianPointSourceCoords.getX() + sourcePoint.getX();
//		//		double dY = -newMeridianPointSourceCoords.getY() + sourcePoint.getY();
//
//		double dYnewMeridianPoint = newMeridianPointSourceCoords.getY() - sourcePoint.getY();
//		double dXnewMeridianPoint = newMeridianPointSourceCoords.getX() - sourcePoint.getX();
//		double dYnewGridPoint = newGridPointSourceCoords.getY() - sourcePoint.getY(); 
//		double dXnewGridPoint = newGridPointSourceCoords.getX() - sourcePoint.getX(); 
//
//		double angle1 = Math.atan2(dYnewMeridianPoint, dXnewMeridianPoint);
//		double angle2 = Math.atan2(dYnewGridPoint, dXnewGridPoint);
//		//        System.out.println("angle2: "+angle2);
//
//		//        double angle = angle1 - angle2;
//		double angle = angle2 - angle1;
//		//		System.out.println("dX, dY:"+dX+", "+dY);
//		//		System.out.println("Angle: "+Math.toDegrees(Math.atan2(dX, dY)));
//		System.out.println("Angle: "+Math.toDegrees(angle));
//		return 0d;
//	}
//
//	
//
//	public double geodeticCalcScreen(DirectPosition2D origin, CoordinateReferenceSystem originCRS) throws Exception
//	{
//		//
//		// http://docs.geotools.org/latest/userguide/library/referencing/axis.html
//		// http://docs.geotools.org/stable/userguide/tutorial/affinetransform.html
//		if (originCRS == null)
//		{
//			originCRS = origin.getCoordinateReferenceSystem();
//		}
//
//
//		GridToEnvelopeMapper gtm = new GridToEnvelopeMapper();
//
//
//
//		MathTransform data2world = CRS.findMathTransform( originCRS, DefaultGeographicCRS.WGS84, true);
//		AffineTransform2D world2screen = new AffineTransform2D(null); // start with identity transform
//		world2screen.translate( -180, 90); // relocate to the viewport
//		world2screen.scale( 360 / 360, 180 / 180); // scale to fit
//		world2screen.scale( 1.0, -1.0 ); // flip to match screen
//		DefaultMathTransformFactory dmtf = new DefaultMathTransformFactory();
//		MathTransform transform = dmtf.createConcatenatedTransform(data2world, world2screen);
//		MathTransform screen2World = world2screen.inverse();
//
//		originCRS.getCoordinateSystem().getDimension();
//		System.out.println(originCRS);
//		System.out.println(originCRS.getCoordinateSystem().getAxis(0).getName());
//
//		GeodeticCalculator gc = new GeodeticCalculator(DefaultGeographicCRS.WGS84);
//
//		DirectPosition2D startingPoint = new DirectPosition2D(DefaultGeographicCRS.WGS84);
//		transform.transform(origin, startingPoint);
//		gc.setStartingPosition(startingPoint);
//
//		DirectPosition2D endingPoint = new DirectPosition2D(DefaultGeographicCRS.WGS84);
//		endingPoint.setLocation(startingPoint.getX(), startingPoint.getY()+0.001);
//		screen2World.transform(endingPoint, endingPoint);
//
//
//		gc.setDestinationPosition(new DirectPosition2D(origin.getX(),origin.getY()+1));
//		double angle = gc.getAzimuth();
//		System.out.println("Azimuth Angle: "+angle);
//		return angle;
//	}
//	*/
}