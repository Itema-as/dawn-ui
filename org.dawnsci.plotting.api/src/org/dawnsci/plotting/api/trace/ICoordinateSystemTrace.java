package org.dawnsci.plotting.api.trace;

import java.util.List;

import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.roi.IROI;

public interface ICoordinateSystemTrace extends ITrace {

	
	/**
	 * If the axis data set has been set, this method will return 
	 * a selection region in the coordinates of the axes labels rather
	 * than the indices. If no axes are set, then the original roi is
	 * returned.
	 * 
	 * @return ROI in label coordinates. This roi is not that useful after it
	 *         is created. The data processing needs rois with indices.
	 *         
	 * @throws Exception if the roi could not be transformed or the roi type
	 *         is unknown.
	 */
	public IROI getRegionInAxisCoordinates(final IROI roi) throws Exception;
	
	
	/**
	 * If the axis data set has been set, this method will return 
	 * a point in the coordinates of the axes labels rather
	 * than the indices. If no axes are set, then the original point is
	 * returned.
	 * 
	 * NOTE the double[] passed in is not the pixel coordinates point from
	 * events like a mouse click (int[]). It is the data point (indices of
	 * real data the case of the image). The return value is
	 * the data point looked up in the image custom axes. If no custom axes
	 * are set (via the setAxes(..) method) then you will simply get the 
	 * same double passed back.
	 * 
	 * @see ICoordinateSystem
	 * @param  point in index of dataset coordinates (same as ROIs on images use). 
	 * @return point in label coordinates. 
	 * 
	 * @throws Exception if the point could not be transformed or the point type
	 *         is unknown.
	 */
	public double[] getPointInAxisCoordinates(final double[] point) throws Exception;

	/**
	 * For regions over images: if the axis data set has been set, this method will return 
	 * a point in the coordinates of the image indices rather
	 * than the axes. If no axes are set, then the original point is
	 * returned. If the plot is 1D then the original values are returned.
	 * 
	 * @see ICoordinateSystem
	 * @param  point in label coordinates
	 * @return point in index of dataset coordinates (same as ROIs on images use). 
	 * 
	 * @throws Exception if the point could not be transformed or the point type
	 *         is unknown.
	 */
	public double[] getPointInImageCoordinates(final double[] axisLocation) throws Exception;


	/**
	 * The axes used for the trace.
	 * @return
	 */
	public List<IDataset> getAxes();

}