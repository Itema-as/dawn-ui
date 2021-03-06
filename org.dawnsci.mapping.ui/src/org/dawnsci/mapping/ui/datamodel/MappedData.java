package org.dawnsci.mapping.ui.datamodel;

import org.dawnsci.mapping.ui.MappingUtils;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Maths;

public class MappedData implements MapObject{

	private String name;
	private String path;
	protected IDataset map;
	protected MappedDataBlock oParent;
	protected MappedDataBlock parent;
	private int transparency = -1;
	private double[] range;
	
	public MappedData(String name, IDataset map, MappedDataBlock parent, String path) {
		this.name = name;
		this.map = map;
		this.path = path;
		this.oParent = this.parent = parent;
		range = calculateRange(map);
	}
	
	protected double[] calculateRange(IDataset map){

		range = MappingUtils.getGlobalRange(map);
		
		return range;
	}
	
	public IDataset getMap(){
		return map;
	}
	
	private int[] getIndices(double x, double y) {
		
		IDataset[] ax = MappingUtils.getAxesFromMetadata(map);
		
		IDataset xx = ax[1];
		IDataset yy = ax[0];
		
		double xMin = xx.min().doubleValue();
		double xMax = xx.max().doubleValue();
		
		double yMin = yy.min().doubleValue();
		double yMax = yy.max().doubleValue();
		
		double xd = ((xMax-xMin)/xx.getSize())/2;
		double yd = ((yMax-yMin)/yy.getSize())/2;
		
		if (x > xMax+xd || x < xMin-xd || y > yMax+yd || y < yMin-yd) return null;
		
		int xi = Maths.abs(Maths.subtract(xx, x)).argMin();
		int yi = Maths.abs(Maths.subtract(yy, y)).argMin();
		
		return new int[]{xi,yi};
	}
	
	public ILazyDataset getSpectrum(double x, double y) {
		int[] indices = getIndices(x, y);
		if (indices == null) return null;
		return parent.getSpectrum(indices[0], indices[1]);
	}
	
	public MappedData makeNewMapWithParent(String name, IDataset ds) {
		return new MappedData(name, ds, parent, path);
	}
	
	@Override
	public String toString() {
		return name;
	}

	@Override
	public boolean hasChildren() {
		return false;
	}

	@Override
	public Object[] getChildren() {
		return null;
	}

	public int getTransparency() {
		return transparency;
	}

	public void setTransparency(int transparency) {
		this.transparency = transparency;
	}

	public MappedDataBlock getParent() {
		return parent;
	}

	public void setParent(MappedDataBlock parent) {
		this.parent = parent;
	}

	public void resetParent() {
		parent = oParent;
	}

	@Override
	public double[] getRange() {
		return range.clone();
	}
	
	public String getLongName() {
		return path + " : " + name;
	}
	
}
