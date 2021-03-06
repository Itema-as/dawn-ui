/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.dawnsci.plotting.draw2d.swtxy;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.dawb.common.ui.macro.TraceMacroEvent;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.dawnsci.analysis.api.dataset.DataEvent;
import org.eclipse.dawnsci.analysis.api.dataset.IDataListener;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.IDynamicDataset;
import org.eclipse.dawnsci.analysis.api.downsample.DownsampleMode;
import org.eclipse.dawnsci.analysis.api.roi.IPolylineROI;
import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.impl.BooleanDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.IndexIterator;
import org.eclipse.dawnsci.analysis.dataset.impl.Maths;
import org.eclipse.dawnsci.analysis.dataset.impl.RGBDataset;
import org.eclipse.dawnsci.analysis.dataset.roi.LinearROI;
import org.eclipse.dawnsci.analysis.dataset.roi.PointROI;
import org.eclipse.dawnsci.analysis.dataset.roi.PolygonalROI;
import org.eclipse.dawnsci.analysis.dataset.roi.PolylineROI;
import org.eclipse.dawnsci.analysis.dataset.roi.RectangularROI;
import org.eclipse.dawnsci.macro.api.MacroEventObject;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.histogram.HistogramBound;
import org.eclipse.dawnsci.plotting.api.histogram.IImageService;
import org.eclipse.dawnsci.plotting.api.histogram.IPaletteService;
import org.eclipse.dawnsci.plotting.api.histogram.ImageServiceBean;
import org.eclipse.dawnsci.plotting.api.histogram.ImageServiceBean.HistoType;
import org.eclipse.dawnsci.plotting.api.histogram.ImageServiceBean.ImageOrigin;
import org.eclipse.dawnsci.plotting.api.preferences.BasePlottingConstants;
import org.eclipse.dawnsci.plotting.api.preferences.PlottingConstants;
import org.eclipse.dawnsci.plotting.api.trace.DownSampleEvent;
import org.eclipse.dawnsci.plotting.api.trace.IDownSampleListener;
import org.eclipse.dawnsci.plotting.api.trace.IImageTrace;
import org.eclipse.dawnsci.plotting.api.trace.IPaletteListener;
import org.eclipse.dawnsci.plotting.api.trace.ITrace;
import org.eclipse.dawnsci.plotting.api.trace.ITraceContainer;
import org.eclipse.dawnsci.plotting.api.trace.PaletteEvent;
import org.eclipse.dawnsci.plotting.api.trace.TraceEvent;
import org.eclipse.dawnsci.plotting.api.trace.TraceUtils;
import org.eclipse.dawnsci.plotting.api.trace.TraceWillPlotEvent;
import org.eclipse.draw2d.Figure;
import org.eclipse.draw2d.Graphics;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.nebula.visualization.widgets.figureparts.ColorMapRamp;
import org.eclipse.nebula.visualization.xygraph.figures.Axis;
import org.eclipse.nebula.visualization.xygraph.figures.IAxisListener;
import org.eclipse.nebula.visualization.xygraph.linearscale.Range;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.function.Downsample;

/**
 * A trace which draws an image to the plot.
 * 
 * @author Matthew Gerring
 *
 */
public class ImageTrace extends Figure implements IImageTrace, IAxisListener, ITraceContainer, IDataListener {
	
	private static final Logger logger = LoggerFactory.getLogger(ImageTrace.class);
	
	private static final int MINIMUM_ZOOM_SIZE  = 4;
	private static final int MINIMUM_LABEL_SIZE = 10;

	private String           name;
	private String           dataName;
	private String           paletteName;
	private Axis             xAxis;
	private Axis             yAxis;
	private ColorMapRamp     intensityScale;
	private Dataset          image;
	private DownsampleType   downsampleType=DownsampleType.MAXIMUM;
	private int              currentDownSampleBin=-1;
	private int              alpha = -1;
	private List<IDataset>   axes;
	private ImageServiceBean imageServiceBean;
	private ImageScaleType dirty = null;
	/**
	 * Used to define if the zoom is at its maximum possible extend
	 */
	private boolean          isMaximumZoom;
	/**
	 * Used to define if the zoom is at an extent large enough to show a 
	 * label grid for the intensity.
	 */
	private boolean          isLabelZoom;

    /**
     * Controls of the image should be downsampled and the ImageData recreated.
     */
	private boolean          imageCreationAllowed = true;

	/**
	 * The parent plotting system for this image.
	 */
	private IPlottingSystem<?> plottingSystem;

	private IImageService service;

	private boolean xTicksAtEnd, yTicksAtEnd;
	
	private double[] globalRange;
		
	public ImageTrace(final String name, 
			          final Axis xAxis, 
			          final Axis yAxis,
			          final ColorMapRamp intensityScale) {
		
		this.name  = name;
		this.xAxis = xAxis;
		this.yAxis = yAxis;
		this.intensityScale = intensityScale;

		this.service = ServiceHolder.getImageService();
		this.imageServiceBean = service.createBeanFromPreferences();
		setPaletteName(getPreferenceStore().getString(BasePlottingConstants.COLOUR_SCHEME));
		
		downsampleType = DownsampleType.forLabel(getPreferenceStore().getString(BasePlottingConstants.DOWNSAMPLE_PREF));

		xAxis.addListener(this);
		yAxis.addListener(this);

		xTicksAtEnd = xAxis.hasTicksAtEnds();
		xAxis.setTicksAtEnds(false);
		yTicksAtEnd = yAxis.hasTicksAtEnds();
		yAxis.setTicksAtEnds(false);
		xAxis.setTicksIndexBased(true);
		yAxis.setTicksIndexBased(true);

		if (xAxis instanceof AspectAxis && yAxis instanceof AspectAxis) {
			
			AspectAxis x = (AspectAxis)xAxis;
			AspectAxis y = (AspectAxis)yAxis;
			x.setKeepAspectWith(y);
			y.setKeepAspectWith(x);		
		}
				
	}

	private IPreferenceStore store;
	private IPreferenceStore getPreferenceStore() {
		if (store!=null) return store;
		store = new ScopedPreferenceStore(InstanceScope.INSTANCE, "org.dawnsci.plotting");
		return store;
	}

	public String getName() {
		return name;
	}

	public void setName(String newName) {
		if (plottingSystem!=null) plottingSystem.moveTrace(this.name, newName);
		this.name = newName;
	}

	public AspectAxis getXAxis() {
		return (AspectAxis)xAxis;
	}

	public void setXAxis(Axis xAxis) {
		this.xAxis = xAxis;
		xAxis.setTicksIndexBased(true);
	}

	public AspectAxis getYAxis() {
		return (AspectAxis)yAxis;
	}

	public void setYAxis(Axis yAxis) {
		this.yAxis = yAxis;
		yAxis.setTicksIndexBased(true);
	}

	public Dataset getImage() {
		return image;
	}

	public PaletteData getPaletteData() {
		if (imageServiceBean==null) return null;
		return imageServiceBean.getPalette();
	}

	public void setPaletteData(PaletteData paletteData) {
		if (paletteData==null)      return;
		if (imageServiceBean==null) return;
		imageServiceBean.setPalette(paletteData);
		createScaledImage(ImageScaleType.FORCE_REIMAGE, null);
		intensityScale.repaint();
		repaint();
		firePaletteDataListeners(paletteData);
	}

	@Override
	public String getPaletteName() {
		return paletteName;
	}

	@Override
	public void setPaletteName(String paletteName) {
		this.paletteName = paletteName;
	}
	
	@Override
	public void setPalette(String paletteName) {
		
		String orig = this.paletteName;
		IPaletteService pservice = ServiceHolder.getPaletteService();
		final PaletteData paletteData = pservice.getDirectPaletteData(paletteName);
        setPaletteName(paletteName);
        setPaletteData(paletteData);
     	
		if (!paletteName.equals(orig) && ServiceHolder.getMacroService()!=null) {
			//
			TraceMacroEvent evt = new TraceMacroEvent(this, "setPalette", paletteName);
			ServiceHolder.getMacroService().publish(evt);
		}
	}

	private enum ImageScaleType {
		// Going up in order of work done
		NO_REIMAGE,
		REIMAGE_ALLOWED,
		FORCE_REIMAGE, 
		REHISTOGRAM;
	}
	

    /**
     * Get the current image which can be used by the composite trace.
     * @return
     * @throws Exception 
     */
	protected ScaledImageData getScaledImageData() {	
		return scaledData;
	}

	private org.eclipse.swt.graphics.Rectangle  screenRectangle;

	/**
	 * Object to hold the current scaled image, the location it should be drawn
	 * and the downsampled image data which it used.
	 */
	private ScaledImageData scaledData = new ScaledImageData();

	/**
	 * number of entries in intensity scale
	 */
	final static int INTENSITY_SCALE_ENTRIES = 256;

	/**
	 * When this is called the SWT image is created
	 * and saved in the swtImage field. The image is downsampled. If rescaleAllowed
	 * is set to false, the current bin is not checked and the last scaled image
	 * is always used.
	 *  
	 * Do not synchronized this method - it can cause a race condition on linux only.
	 * 
	 * @return true if scaledImage created.
	 */
	protected boolean createScaledImage(ImageScaleType rescaleType, final IProgressMonitor monitor) {
			
		if (!imageCreationAllowed) return false;
		
		boolean requireImageGeneration = scaledData==null || 
				                         scaledData.getDownsampledImageData() == null ||
				                         rescaleType==ImageScaleType.FORCE_REIMAGE || 
				                         rescaleType==ImageScaleType.REHISTOGRAM; // We know that it is needed
		
		// If we just changed downsample scale, we force the update.
	    // This allows user resizes of the plot area to be picked up
		// and the larger data size used if it fits.
        if (!requireImageGeneration && rescaleType==ImageScaleType.REIMAGE_ALLOWED && currentDownSampleBin>0) {
        	if (getDownsampleBin()!=currentDownSampleBin) {
        		requireImageGeneration = true;
        	}
        }

		final XYRegionGraph graph  = (XYRegionGraph)getXAxis().getParent();
		final Rectangle     bounds = graph.getRegionArea().getBounds();
		if (bounds.width<1 || bounds.height<1) return false;
		scaledData.setX(bounds.x);
		scaledData.setY(bounds.y);
		scaledData.setXoffset(0);
		scaledData.setYoffset(0);			

		if (!imageCreationAllowed) return false;
		if (monitor!=null && monitor.isCanceled()) return false;

		// 
		if (requireImageGeneration)  {
			boolean ok = createDownsampledImageData(rescaleType, monitor);
			if (!ok) return false;
		}
		
		if (monitor!=null && monitor.isCanceled()) return false;
		if (scaledData.getDownsampledImageData() == null) return false;

		try {
			
			isMaximumZoom = false;
			isLabelZoom   = false;
			
			if (scaledData != null) scaledData.disposeImage();
			
			ImageData imageData = scaledData.getDownsampledImageData();
			if (imageData!=null && imageData.width==bounds.width && imageData.height==bounds.height) { 
				// No slice, faster
				if (monitor!=null && monitor.isCanceled()) return false;
				Image scaledImage  = new Image(Display.getDefault(), imageData);
				scaledData.setScaledImage(scaledImage);
				 
			} else {
				
				if (globalRange != null) {
					return buildImageRelativeToAxes(imageData);
					
				}
				
				// slice data to get current zoom area
				/**     
				 *      x1,y1--------------x2,y2
				 *        |                  |
				 *        |                  |
				 *        |                  |
				 *      x3,y3--------------x4,y4
				 */
				
				ImageData data = imageData;
				ImageOrigin origin = getImageOrigin();
				
				Range xRange = xAxis.getRange();
				Range yRange = yAxis.getRange();
				
				double minX = xRange.getLower()/currentDownSampleBin;
				double minY = yRange.getLower()/currentDownSampleBin;
				double maxX = xRange.getUpper()/currentDownSampleBin;
				double maxY = yRange.getUpper()/currentDownSampleBin;
				int xSize = imageData.width;
				int ySize = imageData.height;
				
				// check as getLower and getUpper don't work as expected
				if(maxX < minX){
					double temp = maxX;
					maxX = minX;
					minX = temp;
				}
				if(maxY < minY){
					double temp = maxY;
					maxY = minY;
					minY = temp;
				}
				
				double xSpread = maxX - minX;
				double ySpread = maxY - minY;
				
				double xScale = bounds.width / xSpread;
				double yScale = bounds.height / ySpread;
//				System.err.println("Area is " + rbounds + " with scale (x,y) " + xScale + ", " + yScale);
				
				// Deliberately get the over-sized dimensions so that the edge pixels can be smoothly panned through.
				int minXI = (int) Math.floor(minX);
				int minYI = (int) Math.floor(minY);
				
				int maxXI = (int) Math.ceil(maxX);
				int maxYI = (int) Math.ceil(maxY);
				
				int fullWidth = (int) (maxXI-minXI);
				int fullHeight = (int) (maxYI-minYI);
				
				// Force a minimum size on the system
				if (fullWidth <= MINIMUM_ZOOM_SIZE) {
					if (fullWidth > imageData.width) fullWidth = MINIMUM_ZOOM_SIZE;
					isMaximumZoom = true;
				}
				if (fullHeight <= MINIMUM_ZOOM_SIZE) {
					if (fullHeight > imageData.height) fullHeight = MINIMUM_ZOOM_SIZE;
					isMaximumZoom = true;
				}
				if (fullWidth <= MINIMUM_LABEL_SIZE && fullHeight <= MINIMUM_LABEL_SIZE) {
					isLabelZoom = true;
				}
				
				int scaleWidth = (int) (fullWidth*xScale);
				int scaleHeight = (int) (fullHeight*yScale);
//				System.err.println("Scaling to " + scaleWidth + "x" + scaleHeight);
				int xPix = (int)minX;
				int yPix = (int)minY;
				
				double xPixD = 0;
				double yPixD = 0;
				
				// These offsets are used when the scaled images is drawn to the screen.
				double xOffset = (minX - Math.floor(minX))*xScale;
				double yOffset = (minY - Math.floor(minY))*yScale;
				// Deal with the origin orientations correctly.
				switch (origin) {
				case TOP_LEFT:
					break;
				case TOP_RIGHT:
					xPixD = xSize-maxX;
					xPix = (int) Math.floor(xPixD);
					xOffset = (xPixD - xPix)*xScale;
					break;
				case BOTTOM_RIGHT:
					xPixD = xSize-maxX;
					xPix = (int) Math.floor(xPixD);
					xOffset = (xPixD - xPix)*xScale;
					yPixD = ySize-maxY;
					yPix = (int) Math.floor(yPixD);
					yOffset = (yPixD - yPix)*yScale;
					break;
				case BOTTOM_LEFT:
					yPixD = ySize-maxY;
					yPix = (int) Math.floor(yPixD);
					yOffset = (yPixD - yPix)*yScale;
					break;
				}
				if (xPix < 0 || yPix < 0 || xPix+fullWidth > xSize || yPix+fullHeight > ySize) {
					return false; // prevent IAE in calling getPixel
				}
				// Slice the data.
				// Pixel slice on downsampled data = fast!
				if (imageData.depth <= 8) {
					// NOTE Assumes 8-bit images
					final int size   = fullWidth*fullHeight;
					final byte[] pixels = new byte[size];
					for (int y = 0; y < fullHeight; y++) {
						imageData.getPixels(xPix, yPix+y, fullWidth, pixels, fullWidth*y);
					}
					data = new ImageData(fullWidth, fullHeight, data.depth, getPaletteData(), 1, pixels);
				} else {
					// NOTE Assumes 24 Bit Images
					final int[] pixels = new int[fullWidth];
					
					data = new ImageData(fullWidth, fullHeight, 24, new PaletteData(0xff0000, 0x00ff00, 0x0000ff));
					for (int y = 0; y < fullHeight; y++) {					
						imageData.getPixels(xPix, yPix+y, fullWidth, pixels, 0);
						data.setPixels(0, y, fullWidth, pixels, 0);
					}
				}
//				data.alpha = imageServiceBean.getAlpha();
				
				// create the scaled image
				// We are suspicious if the algorithm wants to create an image
				// bigger than the screen size and in that case do not scale
				// Fix to http://jira.diamond.ac.uk/browse/SCI-926
				boolean proceedWithScale = true;
				try {
					if (screenRectangle == null) {
						screenRectangle = Display.getCurrent().getPrimaryMonitor().getClientArea();
					}
					if (scaleWidth>screenRectangle.width*2      || 
						scaleHeight>screenRectangle.height*2) {
						
						logger.error("Image scaling algorithm has malfunctioned and asked for an image bigger than the screen!");
						logger.debug("scaleWidth="+scaleWidth);
						logger.debug("scaleHeight="+scaleHeight);
						proceedWithScale = false;
					}
				} catch (Throwable ne) {
					proceedWithScale = true;
				}
				
				Image scaledImage = null;
				if (proceedWithScale) {
					data = data!=null ? data.scaledTo(scaleWidth, scaleHeight) : null;
					scaledImage = data!=null ? new Image(Display.getDefault(), data) : null;
				} else if (scaledImage==null) {
					scaledImage = data!=null ? new Image(Display.getDefault(), data) : null;
				}	
				scaledData.setXoffset(xOffset);
				scaledData.setYoffset(yOffset);
				scaledData.setScaledImage(scaledImage);
				
			}

			return true;
		} catch (IllegalArgumentException ie) {
			logger.error(ie.toString());
			return false;
		} catch (java.lang.NegativeArraySizeException allowed) {
			return false;
			
		} catch (NullPointerException ne) {
			throw ne;
		} catch (Throwable ne) {
			logger.error("Image scale error!", ne);
			return false;
		}
	}
	
	private boolean buildImageRelativeToAxes(ImageData imageData) {
		
		// slice data to get current zoom area
		/**     
		 *      x1,y1--------------x2,y2
		 *        |                  |
		 *        |                  |
		 *        |                  |
		 *      x3,y3--------------x4,y4
		 */
		
		ImageData data = imageData;
		
		//Get the axes coodinates visible on screen
		double[] da = getImageCoords(1, false);
		double minX = da[0];
		double minY = da[1];
		double maxX = da[2];
		double maxY = da[3];
		
		//Get the data points per axis step
		double xAxValPerPoint =  getAxisValuePerDataPoint(minX,maxX,getAxes().get(0));
		double yAxValPerPoint =  getAxisValuePerDataPoint(minY,maxY,getAxes().get(1));
		
		if (Double.isNaN(xAxValPerPoint)|| Double.isNaN(yAxValPerPoint)) {
			return false;
		}
		
		//get x and y start position in data array (floored)
		int xPix = getPositionInAxis(minX,(Dataset)getAxes().get(0),true)/currentDownSampleBin;
		int yPix = getPositionInAxis(minY,(Dataset)getAxes().get(1),true)/currentDownSampleBin;
		
		//determine sub pixel offset in data frame
		double xdiff = (getAxes().get(0).getDouble(xPix)-minX);
		double ydiff = (getAxes().get(1).getDouble(yPix)-minY);
		
		//Determine the corresponding number of data points in x and y (floor min, ceil max)
		int xDataPoints = getNumberOfDataPoints(minX,maxX,getAxes().get(0));
		int yDataPoints = getNumberOfDataPoints(minY,maxY,getAxes().get(1));
		
		int xDataPointsDS = xDataPoints/currentDownSampleBin;
		int yDataPointsDS = yDataPoints/currentDownSampleBin;
		
		//Get matching screen co-ordinates in pixels
		double[] screenCoords = getImageCoords(1, true);
		
		//calculate full number of data points
		double realx = (maxX-minX)/xAxValPerPoint;
		double realy = (maxY-minY)/yAxValPerPoint;
		
		// Ratio of screen pixels to full number of data points
		double xScale = (screenCoords[2]-screenCoords[0]) / (realx);
		double yScale = (screenCoords[3]-screenCoords[1]) / (realy);
		
		//FIXME Handle minimum cases
		
		// Size of image data in index
		final int xSize = imageData.width;
		final int ySize = imageData.height;
		
		IDataset xd = getAxes().get(0);
		IDataset yd = getAxes().get(1);
		
		double xmin = xd.min().doubleValue();
		double xmax = xd.max().doubleValue();
		double ymin = yd.min().doubleValue();
		double ymax = yd.max().doubleValue();
		
		double xLen = xd.getSize();
		double yLen = yd.getSize();
		
		double xp = ((xmax-xmin)/(xLen-1))/2;
		double yp = ((ymax-ymin)/(yLen-1))/2;
		
		double xOffset = (((-xdiff+xp)/xAxValPerPoint))*xScale;
		double yOffset = (((-ydiff+yp)/yAxValPerPoint))*yScale;
//		xOffset = 0;
		

		//FIXME origins
		
		int width  = xPix+xDataPointsDS > xSize  ? Math.min(xSize, xDataPointsDS) : xDataPointsDS;
		int height = yPix+yDataPointsDS > ySize  ? Math.min(ySize, yDataPointsDS) : yDataPointsDS;
		
		int scaleWidth  = Math.max(1, (int) (xDataPoints*xScale));
		int scaleHeight = Math.max(1, (int) (yDataPoints*yScale));
		
//		 Force a minimum size on the system
//		if (width <= MINIMUM_ZOOM_SIZE) {
//			if (width > imageData.width) width = MINIMUM_ZOOM_SIZE;
//			isMaximumZoom = true;
//		}
//		if (height <= MINIMUM_ZOOM_SIZE) {
//			if (height > imageData.height) height = MINIMUM_ZOOM_SIZE;
//			isMaximumZoom = true;
//		}
		
		
		try {
			// Slice the data.
			// Pixel slice on downsampled data = fast!
			if (imageData.depth <= 8) {
				// NOTE Assumes 8-bit images
				final int size   = width*height;
				final byte[] pixels = new byte[size];
				for (int y = 0; y < height && (yPix+y)<ySize ; y++) {
					imageData.getPixels(xPix, yPix+y, width, pixels, width*y);
				}
				data = new ImageData(width, height, data.depth, getPaletteData(), 1, pixels);
			} else {
				// NOTE Assumes 24 Bit Images
				final int[] pixels = new int[width];
				data = new ImageData(width, height, imageData.depth, new PaletteData(0xff0000, 0x00ff00, 0x0000ff));
				for (int y = 0; y < height; y++) {					
					imageData.getPixels(xPix, y+yPix, width, pixels, 0);
					data.setPixels(0, y, width, pixels, 0);
				}
			}
			data.alpha = imageServiceBean.getAlpha();

			// create the scaled image
			// We are suspicious if the algorithm wants to create an image
			// bigger than the screen size and in that case do not scale
			// Fix to http://jira.diamond.ac.uk/browse/SCI-926
			boolean proceedWithScale = true;
			try {
				if (screenRectangle == null) {
					screenRectangle = Display.getCurrent().getPrimaryMonitor().getClientArea();
				}
				if (scaleWidth>screenRectangle.width*2 ||  scaleHeight>screenRectangle.height*2) {
					logger.error("Image scaling algorithm has malfunctioned and asked for an image bigger than the screen!");
					logger.debug("scaleWidth="+scaleWidth);
					logger.debug("scaleHeight="+scaleHeight);
					proceedWithScale = false;
				}
			} catch (Throwable ne) {
				proceedWithScale = true;
			}

			Image scaledImage = null;
			if (proceedWithScale) {
				data = data!=null ? data.scaledTo(scaleWidth, scaleHeight) : null;
				scaledImage = data!=null ? new Image(Display.getDefault(), data) : null;
			} else if (scaledImage==null) {
				scaledImage = data!=null ? new Image(Display.getDefault(), data) : null;
			}
			
			scaledData.setX(screenCoords[0]);
			scaledData.setY(screenCoords[1]);
			scaledData.setXoffset(xOffset);
			scaledData.setYoffset(yOffset);
			scaledData.setScaledImage(scaledImage);
			
		} catch (IllegalArgumentException ne) {
			
			logger.error("Image scaling has malfunctioned");
			logger.debug("Trace name = "+getName());
			logger.debug("scaleWidth = "+scaleWidth);
			logger.debug("scaleHeight = "+scaleHeight);
			logger.debug("width = "+width);
			logger.debug("height = "+height);
			logger.debug("xPix = "+xPix);
			logger.debug("yPix = "+yPix);
			
			throw ne;
		}
		
		
		return true;
		
	}
	
	public void setGlobalRange(double[] globalRange) {
		this.globalRange = globalRange;
		yAxis.setTicksIndexBased(false);
		xAxis.setTicksIndexBased(false);
//		if (xAxis instanceof AspectAxis)((AspectAxis)xAxis).setMaximumRange(globalRange[0], globalRange[1]);
//		if (yAxis instanceof AspectAxis)((AspectAxis)yAxis).setMaximumRange(globalRange[2], globalRange[3]);
//		xAxis.setRange(lower, upper);
		xAxis.setTicksAtEnds(false);
		yAxis.setTicksAtEnds(false);
		updateImageDirty(ImageScaleType.FORCE_REIMAGE);
//		performAutoscale();
	}
	
	private final int getNumberOfDataPoints(double min, double max, IDataset axes) {
		
		int minp = getPositionInAxis(min,axes,true);
		int maxp = getPositionInAxis(max,axes,false);
		
		return maxp-minp+1;
	}
	
	
	private final double getAxisValuePerDataPoint(double min, double max, IDataset axes) {
		
		int minp = getPositionInAxis(min,axes);
		int maxp = getPositionInAxis(max,axes);
		
		return (axes.getDouble(maxp)-axes.getDouble(minp))/(maxp-minp);
	}
	
	private final int getPositionInAxis(double val, IDataset axis) {
		Dataset a = DatasetUtils.convertToDataset(axis.clone());
		return Maths.abs(a.isubtract(val)).minPos()[0];
	}
	
	private final int getPositionInAxis(double val, IDataset axis, boolean floor) {
		int pos = 0;
		double dif = 0;
		double test = Double.MAX_VALUE;
		Dataset a = DatasetUtils.convertToDataset(axis.clone());
		IndexIterator it = a.getIterator();
		while (it.hasNext()) {
			double d = a.getDouble(it.index);
			double ad = Math.abs(d-val);
			if (ad < test) {
				test = ad;
				pos = it.index;
				dif = d-val;
			}
		}
		
		if (floor) {
			if (dif > 0) pos--;
		} else {
			if (dif < 0) pos++;
		}
		
		return pos;
		
	}

	/**
	 * Get the image in data coordinates  or 
	 * pixel coordinates.
	 * 
	 * 
	 * @param bin  - if the datacoordinates are downsampled, optionally set the bin.
	 * @return x1,y1,x2,y2  where x2>x1 and y2>y1
	 */
	private final double[] getImageCoords(int bin, boolean pixels) {
		
		Range xRange = xAxis.getRange();
		Range yRange = yAxis.getRange();

		double minX = xRange.getLower()/bin;
		double minY = yRange.getLower()/bin;
		double maxX = xRange.getUpper()/bin;
		double maxY = yRange.getUpper()/bin;

		// Make sure we have the min and max right
		if(maxX < minX){
			double temp = maxX;
			maxX = minX;
			minX = temp;
		}
		if(maxY < minY){
			double temp = maxY;
			maxY = minY;
			minY = temp;
		}
				
		// Bind the extent of the images to the actual data
		IDataset x = getAxes()!=null ? getAxes().get(0) : null;
		
		double minXData = (x!=null ? x.getDouble(0) : 0d)/bin;
		minX = Math.max(minXData, minX);
		
		double maxXData = (x!=null ? x.getDouble(x.getSize()-1) : image.getShape()[1])/bin;
		maxX = Math.min(maxXData, maxX);
		
		IDataset y = getAxes()!=null ? getAxes().get(1) : null;
		
		double minYData = (y!=null ? y.getDouble(0) : 0d)/bin;
		minY = Math.max(minYData, minY);

		double maxYData = (y!=null ? y.getDouble(y.getSize()-1) : image.getShape()[0])/bin;
		maxY = Math.min(maxYData, maxY);
	
		if (pixels) {
			int x1 = xAxis.getValuePosition(minX, false);
			int y1 = yAxis.getValuePosition(minY, false);
			int x2 = xAxis.getValuePosition(maxX, false);
			int y2 = yAxis.getValuePosition(maxY, false);
			return new double[]{x1, y1, x2, y2};
			
		} else {
		    return new double[]{minX, minY, maxX, maxY};
		}
	}

	private boolean createDownsampledImageData(ImageScaleType rescaleType, IProgressMonitor monitor) {
		
		try {
			imageCreationAllowed = false;
			if (image==null) return false;
			
			IDataset reducedFullImage = getDownsampled(image);

			imageServiceBean.setImage(reducedFullImage);
			imageServiceBean.setMonitor(monitor);
			if (fullMask!=null) {
				// For masks, we preserve the min (the falses) to avoid losing fine lines
				// which are masked.
				imageServiceBean.setMask(getDownsampled(fullMask, DownsampleMode.MINIMUM));
			} else {
				imageServiceBean.setMask(null); // Ensure we lose the mask!
			}
			
			if (rescaleType==ImageScaleType.REHISTOGRAM) { // Avoids changing colouring to 
				                                           // max and min of new selection.
				
				Range xRange = getXAxis().getRange();
				Range yRange = getYAxis().getRange();
				
				if (globalRange != null) {
					xRange = new Range(getPositionInAxis(xRange.getLower(), getAxes().get(0)), getPositionInAxis(xRange.getUpper(), getAxes().get(0)));
					yRange = new Range(getPositionInAxis(yRange.getLower(), getAxes().get(1)), getPositionInAxis(yRange.getUpper(), getAxes().get(1)));
				}
				
				
				Dataset  slice     = slice(yRange, xRange, (Dataset)getData());
				ImageServiceBean histoBean = imageServiceBean.clone();
				histoBean.setImage(slice);
				if (fullMask!=null) histoBean.setMask(slice(getYAxis().getRange(), getXAxis().getRange(), fullMask));
				double[] fa = service.getFastStatistics(histoBean);
				setMin(fa[0]);
				setMax(fa[1]);

			}
							
			this.imageServiceBean.setAlpha(getAlpha());
			ImageData imageData   = service.getImageData(imageServiceBean);
			scaledData.setDownsampledImageData(imageData);
			
			try {
				ImageServiceBean intensityScaleBean = imageServiceBean.clone();
				intensityScaleBean.setOrigin(ImageOrigin.TOP_LEFT);
				// We send the image drawn with the same palette to the 
				// intensityScale
				// TODO FIXME This will not work in log mode
				if (reducedFullImage instanceof RGBDataset) return true;
				final DoubleDataset dds = new DoubleDataset(INTENSITY_SCALE_ENTRIES,1);
				double max = getMax().doubleValue();
				double inc = (max - getMin().doubleValue())/INTENSITY_SCALE_ENTRIES;
				for (int i = 0; i < INTENSITY_SCALE_ENTRIES; i++) {
					dds.set(max - (i*inc), i, 0);
				}
				intensityScaleBean.setImage(dds);
				intensityScaleBean.setMask(null);
				intensityScale.setImageData(service.getImageData(intensityScaleBean));
				intensityScale.setLog10(getImageServiceBean().isLogColorScale());
			} catch (Throwable ne) {
				logger.warn("Cannot update intensity!");
			}

		} catch (Exception e) {
			logger.error("Cannot create image from data!", e);
		} finally {
			imageCreationAllowed = true;
		}
		return true;
	}

	private static final int[] getBounds(Range xr, Range yr) {
		return new int[] {(int) Math.floor(xr.getLower()), (int) Math.floor(yr.getLower()),
				(int) Math.ceil(xr.getUpper()), (int) Math.ceil(yr.getUpper())};
	}

	private Map<Integer, Reference<Object>> mipMap;
	private Map<Integer, Reference<Object>> maskMap;
	private Collection<IDownSampleListener> downsampleListeners;
	
	private IDataset getDownsampled(Dataset image) {
	
		return getDownsampled(image, getDownsampleTypeDiamond());
 	}
	
	/**
	 * Uses caches based on bin, not DownsampleMode.
	 * @param image
	 * @param mode
	 * @return
	 */
	private IDataset getDownsampled(Dataset image, DownsampleMode mode) {
		
		// Down sample, no point histogramming the whole thing
        final int bin = getDownsampleBin();
        
        boolean newBin = false;
        if (currentDownSampleBin!=bin) newBin = true;
        
        try {
	        this.currentDownSampleBin = bin;
			if (bin==1) {
		        logger.trace("No downsample bin (or bin=1)");
				return image; // nothing to downsample
			}
			
			if (image.getDtype()!=Dataset.BOOL) {
				if (mipMap!=null && mipMap.containsKey(bin) && mipMap.get(bin).get()!=null) {
			        logger.trace("Downsample bin used, "+bin);
					return (Dataset)mipMap.get(bin).get();
				}
			} else {
				if (maskMap!=null && maskMap.containsKey(bin) && maskMap.get(bin).get()!=null) {
			        logger.trace("Downsample mask bin used, "+bin);
					return (Dataset)maskMap.get(bin).get();
				}
			}
			
			final Downsample downSampler = new Downsample(mode, new int[]{bin,bin});
			List<? extends IDataset>   sets = downSampler.value(image);
			final IDataset set = sets.get(0);
			
			if (image.getDtype()!=Dataset.BOOL) {
				if (mipMap==null) mipMap = new HashMap<Integer,Reference<Object>>(3);
				mipMap.put(bin, new SoftReference<Object>(set));
		        logger.trace("Downsample bin created, "+bin);
			} else {
				if (maskMap==null) maskMap = new HashMap<Integer,Reference<Object>>(3);
				maskMap.put(bin, new SoftReference<Object>(set));
		        logger.trace("Downsample mask bin created, "+bin);
			}
	      
			return set;
			
        } finally {
        	if (newBin) { // We fire a downsample event.
        		fireDownsampleListeners(new DownSampleEvent(this, bin));
        	}
        }
	}
	
	protected void fireDownsampleListeners(DownSampleEvent evt) {
		if (downsampleListeners==null) return;
		for (IDownSampleListener l : downsampleListeners) l.downSampleChanged(evt);
	}

	@Override
	public int getBin() {
		return currentDownSampleBin;
	}
	
	/**
	 * Add listener to be notifed if the dawnsampling changes.
	 * @param l
	 */
	@Override
	public void addDownsampleListener(IDownSampleListener l) {
		if (downsampleListeners==null) downsampleListeners = new HashSet<IDownSampleListener>(7);
		downsampleListeners.add(l);
	}
	
	/**
	 * Remove listener so that it is not notified.
	 * @param l
	 */
	@Override
	public void removeDownsampleListener(IDownSampleListener l) {
		if (downsampleListeners==null) return;
		downsampleListeners.remove(l);
	}
	
	@Override
	public Dataset getDownsampled() {
		return (Dataset)getDownsampled(getImage());
	}
	
	public IDataset getDownsampledMask() {
		if (getMask()==null) return null;
		return getDownsampled(getMask(), DownsampleMode.MINIMUM);
	}

	/**
	 * Returns the bin for downsampling, either 1,2,4 or 8 currently.
	 * This gives a pixel count of 1,4,16 or 64 for the bin. If 1 no
	 * binning at all is done and no downsampling is being done, getDownsampled()
	 * will return the Dataset ok even if bin is one (no downsampling).
	 * 
	 * @param slice
	 * @param bounds
	 * @return
	 */
	public int getDownsampleBin() {
		
		final XYRegionGraph graph      = (XYRegionGraph)getXAxis().getParent();
		final Rectangle     realBounds = graph.getRegionArea().getBounds();
		
		double rwidth  = getSpan(getXAxis());
		double rheight = getSpan(getYAxis());
 
		int iwidth  = realBounds.width;
		int iheight = realBounds.height;

		int max = 1024;
		int ret = -1;
		for (int i = 2 ; i <= max; i *= 2) {
			if (iwidth>(rwidth/i) || iheight>(rheight/i)) {
				ret = i/2;
				break;
			}
		}
		// We make sure that the bin is no smaller than 1/64 of the shape
		int dataSide  = Math.max(image.getShape()[0], image.getShape()[1]);
		double sixtyF = dataSide/64;
		if (ret>sixtyF) ret = (int)sixtyF; // No need to round, int portion accurate enough
		if (ret<1)      ret = 1;
		return ret;
	}

	private double getSpan(Axis axis) {
		final Range range = axis.getRange();
		return Math.max(range.getUpper(),range.getLower()) - Math.min(range.getUpper(), range.getLower());
	}

	private boolean lastAspectRatio = true;
	private IntensityLabelPainter intensityLabelPainter;
	@Override
	protected void paintFigure(Graphics graphics) {
		
		super.paintFigure(graphics);

		/**
		 * This is not actually needed except that when there
		 * are a number of opens of an image, e.g. when moving
		 * around an h5 gallery with arrow keys, it looks smooth 
		 * with this in.
		 */
		if (scaledData.getScaledImage()==null || !isKeepAspectRatio() || lastAspectRatio!=isKeepAspectRatio()) {
			boolean imageReady = createScaledImage(ImageScaleType.NO_REIMAGE, null);
			if (!imageReady && dirty == null) {
				return;
			}
			lastAspectRatio = isKeepAspectRatio();
		}
		
		if (dirty != null) {
			boolean imageReady = createScaledImage(dirty, null);
			dirty = null;
			if (!imageReady) {
				return;
			}
		}

		graphics.pushState();
		
		// Offsets and scaled image are calculated in the createScaledImage method.
		if (scaledData.getScaledImage()!=null) {
			graphics.drawImage(scaledData.getScaledImage(), scaledData.getXPosition(), scaledData.getYPosition());
		}
		
		boolean isLabelZoomEnabled = getPreferenceStore().getBoolean(BasePlottingConstants.LABEL_ZOOM_ENABLED);
		if (isLabelZoomEnabled && isLabelZoom && scaledData!=null) {
			if (intensityLabelPainter==null) intensityLabelPainter = new IntensityLabelPainter(plottingSystem, this);
			intensityLabelPainter.paintIntensityLabels(graphics);
		}

		graphics.popState();
	}

	private boolean isKeepAspectRatio() {
		return getXAxis().isKeepAspect() && getYAxis().isKeepAspect();
	}
	
	public void sleep() {
		if (mipMap!=null)           mipMap.clear();
		if (maskMap!=null)          maskMap.clear();
		if (scaledData!=null)       scaledData.disposeImage();
	}
	public void remove() {
		
		if (mipMap!=null)           mipMap.clear();
		if (maskMap!=null)          maskMap.clear();
		if (scaledData!=null)       scaledData.disposeImage();
		
		if (paletteListeners!=null) paletteListeners.clear();
		paletteListeners = null;
		if (downsampleListeners!=null) downsampleListeners.clear();
		downsampleListeners = null;
		
        clearAspect(xAxis);
        clearAspect(yAxis);
        
		if (getParent()!=null) getParent().remove(this);
		xAxis.removeListener(this);
		yAxis.removeListener(this);
		xAxis.setTicksAtEnds(xTicksAtEnd);
		yAxis.setTicksAtEnds(yTicksAtEnd);
		xAxis.setTicksIndexBased(false);
		yAxis.setTicksIndexBased(false);
		axisRedrawActive = false;
		if (imageServiceBean!=null) imageServiceBean.dispose();
		
		this.imageServiceBean = null;
		this.service          = null;
		this.intensityScale   = null;
		
		if (image instanceof IDynamicDataset) {
			IDynamicDataset dset = (IDynamicDataset)image;
			dset.removeDataListener(this);
		}
		
		if (scaledData!=null) scaledData.disposeImage();
		this.image            = null;
		this.rgbDataset       = null;
		this.fullMask         = null;
	}
	
	public void dispose() {
		remove();
	}

	private void clearAspect(Axis axis) {
        if (axis instanceof AspectAxis ) {			
			AspectAxis aaxis = (AspectAxis)axis;
			aaxis.setKeepAspectWith(null);
			aaxis.setMaximumRange(null);
		}
	}

	@Override
	public IDataset getData() {
		return image;
	}
	
	@Override
	public IDataset getRGBData() {
		return rgbDataset;
	}


	/**
	 * Create a slice of data from given ranges
	 * @param xr
	 * @param yr
	 * @return
	 */
	private final Dataset slice(Range xr, Range yr, final Dataset data) {
		
		// Check that a slice needed, this speeds up the initial show of the image.
		final int[] shape = data.getShape();
		final int[] imageRanges = getImageBounds(shape, getImageOrigin());
		final int[] bounds = getBounds(xr, yr);
		if (imageRanges!=null && Arrays.equals(imageRanges, bounds)) {
			return data;
		}
		
		int[] xRange = getRange(bounds, shape[0], 0, false);
		int[] yRange = getRange(bounds, shape[1], 1, false);		

		try {
			return data.getSliceView(new int[]{xRange[0],yRange[0]}, new int[]{xRange[1],yRange[1]}, null);
			
		} catch (IllegalArgumentException iae) {
			logger.error("Cannot slice image", iae);
			return data;
		}
	}

	private static final int[] getRange(int[] bounds, int side, int index, boolean inverted) {
		int start = bounds[index];
		if (inverted) start = side-start;
		
		int stop  = bounds[2+index];
		if (inverted) stop = side-stop;

		if (start>stop) {
			start = bounds[2+index];
			if (inverted) start = side-start;
			
			stop  = bounds[index];
			if (inverted) stop = side-stop;
		}
		
		return new int[]{start, stop};
	}

	private boolean axisRedrawActive = true;

	@Override
	public void axisRangeChanged(Axis axis, Range old_range, Range new_range) {
//		createScaledImage(ImageScaleType.REIMAGE_ALLOWED, null);
		updateImageDirty(ImageScaleType.REIMAGE_ALLOWED);
	}

	/**
	 * We do a bit here to ensure that 
	 * not too many calls to createScaledImage(...) are made.
	 */
	@Override
	public void axisRevalidated(Axis axis) {
		if (axis.isYAxis()) updateAxisRange(axis);
	}
	
	private void updateAxisRange(Axis axis) {
		if (!axisRedrawActive) return;				
		updateImageDirty(ImageScaleType.REIMAGE_ALLOWED);
//		createScaledImage(ImageScaleType.REIMAGE_ALLOWED, null);
	}


	private void updateImageDirty(ImageScaleType type) {
		
		if (dirty == null) dirty = type;
		
		if (dirty.ordinal() < type.ordinal()) {
			dirty = type;
		}
		
	}
	
	private void setAxisRedrawActive(boolean b) {
		this.axisRedrawActive = b;
	}


	public void performAutoscale() {
		
		if (globalRange != null) {
			xAxis.setRange(globalRange[0], globalRange[1]);
			yAxis.setRange(globalRange[3], globalRange[2]);
			return;
		}
		
		final int[] shape = image.getShape();
		switch(getImageOrigin()) {
		case TOP_LEFT:
			xAxis.setRange(0, shape[1]);
			yAxis.setRange(shape[0], 0);	
			break;
			
		case BOTTOM_LEFT:
			xAxis.setRange(0, shape[0]);
			yAxis.setRange(0, shape[1]);		
			break;

		case BOTTOM_RIGHT:
			xAxis.setRange(shape[1], 0);
			yAxis.setRange(0, shape[0]);		
			break;

		case TOP_RIGHT:
			xAxis.setRange(shape[0], 0);
			yAxis.setRange(shape[1], 0);		
			break;
		
		}
	}
	
	private static final int[] getImageBounds(int[] shape, ImageOrigin origin) {
		if (origin==null) origin = ImageOrigin.TOP_LEFT; 
		switch (origin) {
		case TOP_LEFT:
			return new int[] {0, shape[0], shape[1], 0};
		case BOTTOM_LEFT:
			return new int[] {0, 0, shape[0], shape[1]};
		case BOTTOM_RIGHT:
			return new int[] {shape[1], 0, 0, shape[0]};
		case TOP_RIGHT:
			return new int[] {shape[0], shape[1], 0, 0};
		}
		return null;
	}

	public void setImageOrigin(ImageOrigin imageOrigin) {
		if (this.mipMap!=null) mipMap.clear();
		imageServiceBean.setOrigin(imageOrigin);
		createAxisBounds();
		performAutoscale();
		createScaledImage(ImageScaleType.FORCE_REIMAGE, null);
		repaint();
		fireImageOriginListeners();
	}


	/**
	 * Creates new axis bounds, updates the label data set
	 */
	private void createAxisBounds() {
		
		if (globalRange != null) {
			((AspectAxis)getXAxis()).setTitle(axes.get(0).getName());
			((AspectAxis)getYAxis()).setTitle(axes.get(1).getName());
			return;
		}
		
		final int[] shape = image.getShape();
		if (getImageOrigin()==ImageOrigin.TOP_LEFT || getImageOrigin()==ImageOrigin.BOTTOM_RIGHT) {
			setupAxis(getXAxis(), new Range(0,shape[1]), axes!=null&&axes.size()>0 ? axes.get(0) : null);
			setupAxis(getYAxis(), new Range(0,shape[0]), axes!=null&&axes.size()>1 ? axes.get(1) : null);
		} else {
			setupAxis(getXAxis(), new Range(0,shape[0]), axes!=null&&axes.size()>1 ? axes.get(1) : null);
			setupAxis(getYAxis(), new Range(0,shape[1]), axes!=null&&axes.size()>0 ? axes.get(0) : null);
		}
	}
	
	private void setupAxis(Axis axis, Range bounds, IDataset labels) {
		((AspectAxis)axis).setMaximumRange(bounds);
		((AspectAxis)axis).setLabelDataAndTitle(labels);
	}

	@Override
	public ImageOrigin getImageOrigin() {
		if (imageServiceBean==null) return ImageOrigin.TOP_LEFT;
		return imageServiceBean.getOrigin();
	}
	
	
	private boolean rescaleHistogram = true;
	
	public boolean isRescaleHistogram() {
		return rescaleHistogram;
	}

	@Override
	public void setRescaleHistogram(boolean rescaleHistogram) {
		this.rescaleHistogram = rescaleHistogram;
		fireSetRescaleListeners();
	}

	private RGBDataset rgbDataset;
	
	@Override
	public boolean setData(IDataset im, List<? extends IDataset> axes, boolean performAuto) {
		
		if (im instanceof IDynamicDataset) {
			IDynamicDataset dset = (IDynamicDataset)im;
			dset.addDataListener(this);
		}
		return setDataInternal(im, axes, performAuto);
	}
	
	/**
	 * Called when the internal data of image has changed.
	 */
	public void dataChangePerformed(final DataEvent evt) {
		
		if (Display.getDefault().getThread()==Thread.currentThread()) {
		    setDataInternal(image, axes, plottingSystem.isRescale());
		    updateImageDirty(ImageScaleType.FORCE_REIMAGE);
		    repaint();
		} else {
			Display.getDefault().syncExec(new Runnable() {
				public void run() {
					setDataInternal(image, axes, plottingSystem.isRescale());
					updateImageDirty(ImageScaleType.FORCE_REIMAGE);
					repaint();
				}
			});
		}
	}
	
	@SuppressWarnings("unchecked")
	private boolean setDataInternal(IDataset im, List<? extends IDataset> axes, boolean performAuto) {

		// We are just assigning the data before the image is live.
		if (getParent()==null && !performAuto) {
			this.image = DatasetUtils.convertToDataset(im);
			this.axes  = (List<IDataset>)axes;
			// is this enough?
			if (imageServiceBean==null) imageServiceBean = new ImageServiceBean();
			imageServiceBean.setImage(im);
			return false;
		}

		if (getPreferenceStore().getBoolean(PlottingConstants.IGNORE_RGB) && im instanceof RGBDataset) {
			RGBDataset rgb = (RGBDataset)im;
			im = rgb.createGreyDataset(Dataset.FLOAT64);
			rgbDataset = rgb;
		} else {
			rgbDataset = null;
		}
		if (plottingSystem!=null) try {
			final TraceWillPlotEvent evt = new TraceWillPlotEvent(this, false);
			evt.setImageData(im, axes);
			evt.setNewImageDataSet(false);
			plottingSystem.fireWillPlot(evt);
			if (!evt.doit) return false;
			if (evt.isNewImageDataSet()) {
				im = evt.getImage();
				axes  = evt.getAxes();
			}
		} catch (Throwable ignored) {
			// We allow things to proceed without a warning.
		}

		// The image is drawn low y to the top left but the axes are low y to the bottom right
		// We do not currently reflect it as it takes too long. Instead in the slice
		// method, we allow for the fact that the dataset is in a different orientation to 
		// what is plotted.
		if (image==null) return false;
		this.image = (Dataset)im;
		if (this.mipMap!=null)  mipMap.clear();
		if (scaledData!=null) scaledData.disposeImage();
		
		if (imageServiceBean==null) imageServiceBean = new ImageServiceBean();
		imageServiceBean.setImage(im);
		
		if (service==null) service = (IImageService)PlatformUI.getWorkbench().getService(IImageService.class);
		if (rescaleHistogram) {
			final double[] fa = service.getFastStatistics(imageServiceBean);
			setMin(fa[0]);
			setMax(fa[1]);
		}
		
		setAxes(axes, performAuto);
       
		if (plottingSystem!=null) try {
			if (plottingSystem.getTraces().contains(this)) {
				plottingSystem.fireTraceUpdated(new TraceEvent(this));
			}
		} catch (Throwable ignored) {
			// We allow things to proceed without a warning.
		}
		
		try {
			final ScopedPreferenceStore store = new ScopedPreferenceStore(InstanceScope.INSTANCE, "org.dawnsci.plotting.system");
			if (store.getBoolean(PlottingConstants.SHOW_INTENSITY)) {
				boolean isRGB = im instanceof RGBDataset;
				if (isRGB && getPlottingSystem().isShowIntensity()) {
					getPlottingSystem().setShowIntensity(false);
				} else if (!isRGB && !getPlottingSystem().isShowIntensity()) {
					getPlottingSystem().setShowIntensity(true);
				}
			}
		} catch (Exception ne) { // Not the end of the world if this fails!
			logger.error("Could not get scoped preference store org.dawnsci.plotting.system!", ne);
		}

		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setAxes(List<? extends IDataset> axes, boolean performAuto) {
		this.axes  = (List<IDataset>) axes;
		createAxisBounds();
		
		if (axes==null) {
			getXAxis().setTitle("");
			getYAxis().setTitle("");
		} else if (axes.get(0)==null) {
			getXAxis().setTitle("");
		} else if (axes.get(1)==null) {
			getYAxis().setTitle("");
		}
		if (performAuto) {
	 		try {
				setAxisRedrawActive(false);
				performAutoscale();
			} finally {
				setAxisRedrawActive(true);
			}
		} else {
//			createScaledImage(ImageScaleType.FORCE_REIMAGE, null);
			updateImageDirty(ImageScaleType.FORCE_REIMAGE);
			repaint();
		}
	}


	public Number getMin() {
		return imageServiceBean.getMin();
	}

	public void setMin(Number min) {
		if (imageServiceBean==null) return;
		
		Number orig = imageServiceBean.getMin();
		imageServiceBean.setMin(min);
		try {
			intensityScale.setMin(min.doubleValue());
		} catch (Exception e) {
			logger.error("Cannot set scale of intensity!",e);
		}
		fireMinDataListeners();
		
		
		if (!min.equals(orig) && ServiceHolder.getMacroService()!=null) {
			TraceMacroEvent evt = new TraceMacroEvent(this, "setMin", min);
			ServiceHolder.getMacroService().publish(evt);
		}

	}

	public Number getMax() {
		return imageServiceBean.getMax();
	}
	
	public void setMax(Number max) {
		
		if (imageServiceBean==null) return;
		Number orig = imageServiceBean.getMax();
		imageServiceBean.setMax(max);
		try {
			intensityScale.setMax(max.doubleValue());
		} catch (Exception e) {
			logger.error("Cannot set scale of intensity!",e);
		}
		fireMaxDataListeners();
		
		
		if (!max.equals(orig) && ServiceHolder.getMacroService()!=null) {
			TraceMacroEvent evt = new TraceMacroEvent(this, "setMax", max);
			ServiceHolder.getMacroService().publish(evt);
		}

	}

	@Override
	public ImageServiceBean getImageServiceBean() {
		return imageServiceBean;
	}

	private Collection<IPaletteListener> paletteListeners;


	@Override
	public void addPaletteListener(IPaletteListener pl) {
		if (paletteListeners==null) paletteListeners = new HashSet<IPaletteListener>(11);
		paletteListeners.add(pl);
	}

	@Override
	public void removePaletteListener(IPaletteListener pl) {
		if (paletteListeners==null) return;
		paletteListeners.remove(pl);
	}
	
	
	private void firePaletteDataListeners(PaletteData paletteData) {
		if (paletteListeners==null) return;
		final PaletteEvent evt = new PaletteEvent(this, getPaletteData()); // Important do not let Mark get at it :)
		for (IPaletteListener pl : paletteListeners) pl.paletteChanged(evt);
	}
	private void fireMinDataListeners() {
		if (paletteListeners==null) return;
		if (!imageCreationAllowed)  return;
		final PaletteEvent evt = new PaletteEvent(this, getPaletteData());
		for (IPaletteListener pl : paletteListeners) pl.minChanged(evt);
	}
	private void fireMaxDataListeners() {
		if (paletteListeners==null) return;
		if (!imageCreationAllowed)  return;
		final PaletteEvent evt = new PaletteEvent(this, getPaletteData());
		for (IPaletteListener pl : paletteListeners) pl.maxChanged(evt);
	}
	private void fireMaxCutListeners() {
		if (paletteListeners==null) return;
		if (!imageCreationAllowed)  return;
		final PaletteEvent evt = new PaletteEvent(this, getPaletteData());
		for (IPaletteListener pl : paletteListeners) pl.maxCutChanged(evt);
	}
	private void fireMinCutListeners() {
		if (paletteListeners==null) return;
		if (!imageCreationAllowed)  return;
		final PaletteEvent evt = new PaletteEvent(this, getPaletteData());
		for (IPaletteListener pl : paletteListeners) pl.minCutChanged(evt);
	}
	private void fireNanBoundsListeners() {
		if (paletteListeners==null) return;
		if (!imageCreationAllowed)  return;
		final PaletteEvent evt = new PaletteEvent(this, getPaletteData());
		for (IPaletteListener pl : paletteListeners) pl.nanBoundsChanged(evt);
	}
	private void fireMaskListeners() {
		if (paletteListeners==null) return;
		if (!imageCreationAllowed)  return;
		final PaletteEvent evt = new PaletteEvent(this, getPaletteData());
		for (IPaletteListener pl : paletteListeners) pl.maskChanged(evt);
	}
	private void fireImageOriginListeners() {
		if (paletteListeners==null) return;
		if (!imageCreationAllowed)  return;
		final PaletteEvent evt = new PaletteEvent(this, getPaletteData());
		for (IPaletteListener pl : paletteListeners) pl.imageOriginChanged(evt);
	}
	
	private void fireSetRescaleListeners() {
		if (paletteListeners==null) return;
		if (!imageCreationAllowed)  return;
		final PaletteEvent evt = new PaletteEvent(this, getPaletteData());
		for (IPaletteListener pl : paletteListeners) pl.rescaleHistogramChanged(evt);
	}
	
	@Override
	public DownsampleType getDownsampleType() {
		return downsampleType;
	}
	
	@Override
	public void setDownsampleType(DownsampleType type) {
		
		DownsampleType orig = this.downsampleType;
		if (this.mipMap!=null)  mipMap.clear();
		if (this.maskMap!=null) maskMap.clear();
		this.downsampleType = type;
//		createScaledImage(ImageScaleType.FORCE_REIMAGE, null);
		updateImageDirty(ImageScaleType.FORCE_REIMAGE);
		getPreferenceStore().setValue(BasePlottingConstants.DOWNSAMPLE_PREF, type.getLabel());
		repaint();
		
		if (type!=orig && ServiceHolder.getMacroService()!=null) {
			TraceMacroEvent evt = new TraceMacroEvent(this, "setDownsampleType", type.name());
			ServiceHolder.getMacroService().publish(evt);
		}
	}

	private DownsampleMode getDownsampleTypeDiamond() {
		switch(getDownsampleType()) {
		case MEAN:
			return DownsampleMode.MEAN;
		case MAXIMUM:
			return DownsampleMode.MAXIMUM;
		case MINIMUM:
			return DownsampleMode.MINIMUM;
		case POINT:
			return DownsampleMode.POINT;
		}
		return DownsampleMode.MEAN;
	}

	@Override
	public void rehistogram() {
		if (imageServiceBean==null) return;
		imageServiceBean.setMax(null);
		imageServiceBean.setMin(null);
		createScaledImage(ImageScaleType.REHISTOGRAM, null);
		// Max and min changed in all likely-hood
		fireMaxDataListeners();
		fireMinDataListeners();
		repaint();
	}
	
	public void remask() {
		if (imageServiceBean==null) return;
		
		createScaledImage(ImageScaleType.FORCE_REIMAGE, null);

		// Max and min changed in all likely-hood
		fireMaskListeners();
		repaint();
	}

	
	@Override
	public List<IDataset> getAxes() {
		return (List<IDataset>) axes;
	}

	/**
	 * return the HistoType being used
	 * @return
	 */
	@Override
	public HistoType getHistoType() {
		if (imageServiceBean==null) return null;
		return imageServiceBean.getHistogramType();
	}
	
	/**
	 * Sets the histo type.
	 */
	@Override
	public boolean setHistoType(HistoType type) {
		
		if (imageServiceBean==null) return false;
		HistoType orig = imageServiceBean.getHistogramType();
		imageServiceBean.setHistogramType(type);
		getPreferenceStore().setValue(BasePlottingConstants.HISTO_PREF, type.getLabel());
//		boolean histoOk = createScaledImage(ImageScaleType.REHISTOGRAM, null);
		updateImageDirty(ImageScaleType.REHISTOGRAM);
		repaint();
		
		
		if (type!=orig && ServiceHolder.getMacroService()!=null) {
			TraceMacroEvent evt = new TraceMacroEvent(this, "setHistoType", type.name());
			ServiceHolder.getMacroService().publish(evt);
		}

		return true;
	}

	@Override
	public ITrace getTrace() {
		return this;
	}

	@Override
	public void setTrace(ITrace trace) {
		// Does nothing, you cannot change the trace, this is the trace.
	}
	
	public void setImageUpdateActive(boolean active) {
		this.imageCreationAllowed = active;
		if (active) {
			createScaledImage(ImageScaleType.FORCE_REIMAGE, null);
			repaint();
		}
		firePaletteDataListeners(getPaletteData());
	}

	@Override
	public HistogramBound getMinCut() {
		return imageServiceBean.getMinimumCutBound();
	}

	@Override
	public void setMinCut(HistogramBound bound) {
		
		storeBound(bound, BasePlottingConstants.MIN_CUT);
		if (imageServiceBean==null) return;
		HistogramBound orig = imageServiceBean.getMinimumCutBound();
		imageServiceBean.setMinimumCutBound(bound);
		fireMinCutListeners();
		
		if (!bound.equals(orig) && ServiceHolder.getMacroService()!=null) {
			
			MacroEventObject evt = new MacroEventObject(this);
			evt.setPythonCommand("bound = dnp.plot.createHistogramBound("+bound.getStringBound()+", "+bound.getColor()[0]+", "+bound.getColor()[1]+", "+bound.getColor()[2]+")");
			evt.append(TraceMacroEvent.getTraceCommand(this));
			evt.append(TraceMacroEvent.getVarName(this)+".setMinCut(bound)\n");
			ServiceHolder.getMacroService().publish(evt);
		}
	}

	private void storeBound(HistogramBound bound, String prop) {
		if (bound!=null) {
			getPreferenceStore().setValue(prop, bound.toString());
		} else {
			getPreferenceStore().setValue(prop, "");
		}
	}

	@Override
	public HistogramBound getMaxCut() {
		return imageServiceBean.getMaximumCutBound();
	}

	@Override
	public void setMaxCut(HistogramBound bound) {
		
		storeBound(bound, BasePlottingConstants.MAX_CUT);
		if (imageServiceBean==null) return;
		HistogramBound orig = imageServiceBean.getMaximumCutBound();
		imageServiceBean.setMaximumCutBound(bound);
		fireMaxCutListeners();
		
		if (!bound.equals(orig) && ServiceHolder.getMacroService()!=null) {
			
			MacroEventObject evt = new MacroEventObject(this);
			evt.setPythonCommand("bound = dnp.plot.createHistogramBound("+bound.getStringBound()+", "+bound.getColor()[0]+", "+bound.getColor()[1]+", "+bound.getColor()[2]+")");
			evt.append(TraceMacroEvent.getTraceCommand(this));
			evt.append(TraceMacroEvent.getVarName(this)+".setMaxCut(bound)\n");
			ServiceHolder.getMacroService().publish(evt);
		}

	}

	@Override
	public HistogramBound getNanBound() {
		return imageServiceBean.getNanBound();
	}

	@Override
	public void setNanBound(HistogramBound bound) {
		storeBound(bound, BasePlottingConstants.NAN_CUT);
		if (imageServiceBean==null) return;
		imageServiceBean.setNanBound(bound);
		fireNanBoundsListeners();
	}
	
    private Dataset fullMask;
	/**
	 * The masking dataset of there is one, normally null.
	 * @return
	 */
	public Dataset getMask() {
		return fullMask;
	}
	
	/**
	 * 
	 * @param bd
	 */
	public void setMask(IDataset mask) {
				
		if (mask!=null && image!=null && !image.isCompatibleWith(mask)) {
			
			BooleanDataset maskDataset = new BooleanDataset(image.getShape());
			maskDataset.setName("mask");
			maskDataset.fill(true);

			final int yMin = Math.min(maskDataset.getShape()[0], mask.getShape()[0]);
			final int xMin = Math.min(maskDataset.getShape()[1], mask.getShape()[1]);
			for (int y = 0; y<yMin; ++y) {
				for (int x = 0; x<xMin; ++x) {
			        try {
			        	// We only add the falses 
			        	if (!mask.getBoolean(y, x)) {
			        		maskDataset.set(Boolean.FALSE, y, x);
			        	}
			        } catch (Throwable ignored) {
			        	continue;
			        }
				}
			}

			mask = maskDataset;
		}
		if (maskMap!=null) maskMap.clear();
		fullMask = (Dataset)mask;
		remask();
	}

	private boolean userTrace = true;
	@Override
	public boolean isUserTrace() {
		return userTrace;
	}

	@Override
	public void setUserTrace(boolean isUserTrace) {
		this.userTrace = isUserTrace;
	}

	public boolean isMaximumZoom() {
		return isMaximumZoom;
	}
	
	private Object userObject;

	public Object getUserObject() {
		return userObject;
	}

	public void setUserObject(Object userObject) {
		this.userObject = userObject;
	}
	
	/**
	 * If the axis data set has been set, this method will return 
	 * a selection region in the coordinates of the axes labels rather
	 * than the indices.
	 * 
	 * Ellipse and Sector rois are not currently supported.
	 * 
	 * @return ROI in label coordinates. This roi is not that useful after it
	 *         is created. The data processing needs rois with indices.
	 */
	@Override
	public IROI getRegionInAxisCoordinates(final IROI roi) throws Exception {
		
		if (!TraceUtils.isCustomAxes(this)) return roi;
		
		final IDataset xl = axes.get(0); // May be null
		final IDataset yl = axes.get(1); // May be null
		
		if (roi instanceof LinearROI) {
			double[] sp = ((LinearROI)roi).getPoint();
			double[] ep = ((LinearROI)roi).getEndPoint();
			TraceUtils.transform(xl,0,sp,ep);
			TraceUtils.transform(yl,1,sp,ep);
			return new LinearROI(sp, ep);
			
		} else if (roi instanceof IPolylineROI) {
			IPolylineROI proi = (IPolylineROI)roi;
			final PolylineROI ret = (proi instanceof PolygonalROI) ? new PolygonalROI() : new PolylineROI();
			for (IROI pointROI : proi) {
				double[] dp = pointROI.getPointRef();
				TraceUtils.transform(xl,0,dp);
				TraceUtils.transform(yl,1,dp);
				ret.insertPoint(dp);
			}
			
		} else if (roi instanceof PointROI) {
			double[] dp = roi.getPointRef();
			TraceUtils.transform(xl,0,dp);
			TraceUtils.transform(yl,1,dp);
			return new PointROI(dp);
			
		} else if (roi instanceof RectangularROI) {
			RectangularROI rroi = (RectangularROI)roi;
			double[] sp=roi.getPoint();
			double[] ep=rroi.getEndPoint();
			TraceUtils.transform(xl,0,sp,ep);
			TraceUtils.transform(yl,1,sp,ep);
				
			return new RectangularROI(sp[0], sp[1], ep[0]-sp[0], sp[1]-ep[1], rroi.getAngle());
						
		} else {
			throw new Exception("Unsupported roi "+roi.getClass());
		}

		return roi;
	}
	
	@Override
	public double[] getPointInAxisCoordinates(final double[] point) throws Exception {
		if (axes == null || axes.size() == 0 || image == null)
			return point;

		final double[] ret = point.clone();
		final int[] shape = image.getShapeRef();
		
		final Dataset xl = (Dataset)axes.get(0); // May be null
		if (TraceUtils.isAxisCustom(xl, shape[1])) {
			TraceUtils.transform(xl, 0, ret);
		}

		if (axes.size() < 2)
			return ret;

		final Dataset yl = (Dataset)axes.get(1); // May be null
		if (TraceUtils.isAxisCustom(yl, shape[0])) {
			TraceUtils.transform(yl, 1, ret);
		}
        return ret;
	}

	@Override
	public double[] getPointInImageCoordinates(final double[] axisLocation) throws Exception {
		if (axes == null || axes.size() == 0 || image == null)
			return axisLocation;

		final double[] ret = axisLocation.clone();
		final int[] shape = image.getShapeRef();

		final Dataset xl = (Dataset) axes.get(0); // May be null
		if (TraceUtils.isAxisCustom(xl, shape[1])) {
			double x = axisLocation[0];
			ret[0] = Double.isNaN(x) ? Double.NaN : DatasetUtils.crossings(xl, x).get(0);
		}

		if (axes.size() < 2)
			return ret;

		final Dataset yl = (Dataset) axes.get(1); // May be null
		if (TraceUtils.isAxisCustom(yl, shape[0])) {
			double y = axisLocation[1];
			ret[1] = Double.isNaN(y) ? Double.NaN : DatasetUtils.crossings(yl, y).get(0);
		}

		return ret;
	}

	public IPlottingSystem<?> getPlottingSystem() {
		return plottingSystem;
	}

	public void setPlottingSystem(IPlottingSystem<?> plottingSystem) {
		this.plottingSystem = plottingSystem;
	}
	
	@Override
	public boolean isActive() {
		return getParent()!=null;
	}

	@Override
	public List<String> getAxesNames() {
        return Arrays.asList(xAxis.getTitle(), yAxis.getTitle());
	}

	@Override
	public boolean is3DTrace() {
		return false;
	}

	@Override
	public int getRank() {
		return 2;
	}

	public String getDataName() {
		return dataName;
	}

	public void setDataName(String dataName) {
		this.dataName = dataName;
	}

	public int getAlpha() {
		return alpha;
	}

	public void setAlpha(int alpha) {
		this.alpha = alpha;
		rehistogram();
	}
	
	@Override
	public boolean hasTrueAxes(){
		return globalRange != null;
	}
	
}
