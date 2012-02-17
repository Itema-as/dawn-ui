package org.dawb.workbench.ui.editors.plotting.swtxy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.csstudio.swt.xygraph.figures.Axis;
import org.csstudio.swt.xygraph.figures.PlotArea;
import org.dawb.common.ui.plot.region.IRegion.RegionType;
import org.dawb.common.ui.plot.region.IRegionListener;
import org.dawb.common.ui.plot.region.RegionEvent;
import org.eclipse.draw2d.Graphics;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.swt.graphics.Image;

public class RegionArea extends PlotArea {

	protected ISelectionProvider selectionProvider;
	
	public RegionArea(XYRegionGraph xyGraph) {
		super(xyGraph);
	}
		
	final private List<Region> regionList = new ArrayList<Region>();	

	public void addRegion(final Region region){
		regionList.add(region);
		region.setXyGraph(xyGraph);
		region.createContents(this);
		region.setSelectionProvider(selectionProvider);
		fireRegionAdded(new RegionEvent(region));
		revalidate();
	}


	public boolean removeRegion(final Region region){
	    final boolean result = regionList.remove(region);
		if (result){
			region.remove();
			fireRegionRemoved(new RegionEvent(region));
			revalidate();
		}
		return result;
	}
	
	private Collection<IRegionListener> regionListeners;
	

	public Region createRegion(String name, Axis x, Axis y, RegionType regionType) {

		Region region = null;
		if (regionType==RegionType.LINE) {

			region = new LineSelection(name, x, y);

		} else if (regionType==RegionType.BOX) {

			region = new BoxSelection(name, x, y);

		} else {
			throw new NullPointerException("Cannot deal with "+regionType+" regions yet - sorry!");
		}	
		
		fireRegionCreated(new RegionEvent(region));
        return region;
	}
	
	protected void fireRegionCreated(RegionEvent evt) {
		if (regionListeners==null) return;
		for (IRegionListener l : regionListeners) l.regionCreated(evt);
	}
	

	protected void fireRegionAdded(RegionEvent evt) {
		if (regionListeners==null) return;
		for (IRegionListener l : regionListeners) l.regionAdded(evt);
	}
	
	protected void fireRegionRemoved(RegionEvent evt) {
		if (regionListeners==null) return;
		for (IRegionListener l : regionListeners) l.regionRemoved(evt);
	}

	/**
	 * 
	 * @param l
	 */
	public boolean addRegionListener(final IRegionListener l) {
		if (regionListeners == null) regionListeners = new HashSet<IRegionListener>(7);
		return regionListeners.add(l);
	}
	
	/**
	 * 
	 * @param l
	 */
	public boolean removeRegionListener(final IRegionListener l) {
		if (regionListeners == null) return true;
		return regionListeners.remove(l);
	}
	
	public List<Region> getRegionList() {
		return regionList;
	}
	
	private Image rawImage;
	
	@Override
	protected void paintClientArea(final Graphics graphics) {
	
// TODO
//		if (rawImage==null) {
//			rawImage = new Image(Display.getCurrent(), "C:/tmp/ESRF_Pilatus_Data.png");
//		}
//		
//		final Rectangle bounds = getBounds();
//		final Image scaled = new Image(Display.getCurrent(),
//				rawImage.getImageData().scaledTo(bounds.width,bounds.height));
//		graphics.drawImage(scaled, new Point(0,0));
//
		super.paintClientArea(graphics);

	}


	public List<String> getRegionNames() {
		if (regionList==null|| regionList.isEmpty()) return null;
		final List<String> names = new ArrayList<String>(regionList.size());
		for (Region region : regionList) names.add(region.getName());
		return names;
	}


	public void setSelectionProvider(ISelectionProvider provider) {
		this.selectionProvider = provider;
	}
}
