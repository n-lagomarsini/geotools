<StyledLayerDescriptor version="1.0.0"
  xmlns="http://www.opengis.net/sld" xmlns:gml="http://www.opengis.net/gml"
  xmlns:ogc="http://www.opengis.net/ogc" xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://www.opengis.net/sld ./StyledLayerDescriptor.xsd">
    <NamedLayer>
        <Name>Wind</Name>
        <UserStyle>
            <Title>Wind</Title>
            <FeatureTypeStyle>
            
                <Transformation>
                    <ogc:Function name="ras:RasterAsPointCollection">
                        <ogc:Function name="parameter">
                            <ogc:Literal>data</ogc:Literal>
                        </ogc:Function>
                        <!-- Activate the logic to recognize the emisphere -->
                        <ogc:Function name="parameter">
                          <ogc:Literal>emisphere</ogc:Literal>
                          <ogc:Literal>True</ogc:Literal>
                        </ogc:Function>   
                        <ogc:Function name="parameter">
                          <ogc:Literal>targetCRS</ogc:Literal>
                          <ogc:Literal>EPSG:3003</ogc:Literal>
                        </ogc:Function>                          
                        <ogc:Function name="parameter">
                          <ogc:Literal>interpolation</ogc:Literal>
                          <ogc:Literal>InterpolationBilinear</ogc:Literal>
                        </ogc:Function>                         
                        <ogc:Function name="parameter">
                          <ogc:Literal>scale</ogc:Literal>
                          <ogc:Literal>0.05</ogc:Literal>
                          			<!-- We can use the categorize in GeoServer to change the subsampling based on the actual scale denominator -->
									<!--ogc:Function name="Categorize"-->	
							         <!-- Value to transform -->
							         <!--ogc:PropertyName>
							         	<ogc:Function name="env">
   											<ogc:Literal>wms_scale_denominator</ogc:Literal>
										</ogc:Function>
								     </ogc:PropertyName-->
							
							         <!-- Output values and thresholds 
							         <ogc:Literal>1</ogc:Literal>
							         <ogc:Literal>32</ogc:Literal>					
							 </ogc:Function>         -->                    
                        </ogc:Function>                        
                    </ogc:Function>
                </Transformation>                 

                <Rule>
                    <PointSymbolizer>
                        <Graphic>
                            <Mark>
                              <WellKnownName>windbarbs://default(<ogc:PropertyName>Band1</ogc:PropertyName>)[m/s]?emisphere=<ogc:PropertyName>emisphere</ogc:PropertyName></WellKnownName>
                                <Stroke>
                                    <CssParameter name="stroke">000000</CssParameter>
                                    <CssParameter name="stroke-width">1</CssParameter>
                                </Stroke>   
                            </Mark>
                            <Size>
								<!-- Trick to make calm symbol have different size depending on  wind module -->
								<ogc:Function name="Categorize">
							         <!-- Value to transform -->
							         <ogc:PropertyName>Band1</ogc:PropertyName>
							
							         <!-- Output values and thresholds -->
							         <ogc:Literal>8</ogc:Literal>
							         <ogc:Literal>1.543333332</ogc:Literal>
							         <ogc:Literal>32</ogc:Literal>					
						       </ogc:Function>                            
                            </Size>
                            <Rotation>
								<!-- Trick to make calm symbol ignore rotation as it can be not 0-->
								<ogc:Function name="Categorize">
							         <!-- Value to transform -->
							         <ogc:PropertyName>Band1</ogc:PropertyName>
							
							         <!-- Output values and thresholds -->
							         <ogc:Literal>0</ogc:Literal>
							         <ogc:Literal>1.543333332</ogc:Literal>
							         <ogc:PropertyName>Band2</ogc:PropertyName>					
						       </ogc:Function> 
                            </Rotation>
                        </Graphic>
                    </PointSymbolizer>
                    <PointSymbolizer>
                        <Graphic>
                            <Mark>
                              <WellKnownName>circle</WellKnownName>
                                <Fill>
                                    <CssParameter name="fill">
                                        <ogc:Literal>#ff0000</ogc:Literal>
                                    </CssParameter>
                                </Fill>
                            </Mark>
                            <Size>3</Size>
                        </Graphic>
                    </PointSymbolizer>                    
					<!--TextSymbolizer>
         				<Label><ogc:PropertyName>Band1</ogc:PropertyName></Label>       				
         			</TextSymbolizer-->                    
                </Rule>

               
            </FeatureTypeStyle>
        </UserStyle>
    </NamedLayer>
</StyledLayerDescriptor>