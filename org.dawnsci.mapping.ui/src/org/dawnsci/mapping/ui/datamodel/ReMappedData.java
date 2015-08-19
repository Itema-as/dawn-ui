package org.dawnsci.mapping.ui.datamodel;

import java.util.List;

import org.dawnsci.mapping.ui.MappingUtils;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetFactory;
import org.eclipse.dawnsci.analysis.dataset.impl.Maths;
import org.eclipse.dawnsci.analysis.dataset.impl.Stats;
import org.eclipse.dawnsci.analysis.dataset.metadata.AxesMetadataImpl;

import uk.ac.diamond.scisoft.analysis.diffraction.powder.PixelIntegration;
import uk.ac.diamond.scisoft.analysis.diffraction.powder.XYImagePixelCache;

public class ReMappedData extends MappedData {

	private IDataset reMapped;
	private IDataset lookup;
	
	public ReMappedData(String name, IDataset map, MappedDataBlock parent) {
		super(name, map, parent);
		
	}
	
	public IDataset getMap(){
		
		updateRemappedData(null);
		
		return reMapped;
	}
	
	private void updateRemappedData(int[] shape) {
		
		IDataset[] axes = MappingUtils.getAxesForDimension(map, 0);
		IDataset y = axes[0];
		IDataset x = axes[1];
		
		double yMax = y.max().doubleValue();
		double yMin = y.min().doubleValue();
		
		double xMax = x.max().doubleValue();
		double xMin = x.min().doubleValue();
		
		if (shape == null) {
			double yStepMed = (double)Stats.median(Maths.abs(Maths.derivative(DatasetFactory.createRange(y.getSize(),Dataset.INT32),(Dataset)y,1)));
			double xStepMed = (double)Stats.median(Maths.abs(Maths.derivative(DatasetFactory.createRange(x.getSize(),Dataset.INT32),(Dataset)x,1)));
			
			int nBinsY = (int)(((yMax-yMin)/yStepMed)*0.5);
			int nBinsX = (int)(((xMax-xMin)/xStepMed)*0.5);
			
			shape = new int[]{nBinsX, nBinsY};
		}
		
		
		XYImagePixelCache cache = new XYImagePixelCache((Dataset)x,(Dataset)y,new double[]{xMin,xMax},new double[]{yMin,yMax},shape[0],shape[1]);
		
		List<Dataset> data = PixelIntegration.integrate(map, null, cache);
		
		AxesMetadataImpl axm = new AxesMetadataImpl(2);
		axm.addAxis(0, data.get(2));
		axm.addAxis(1, data.get(0));
		reMapped = data.get(1);
		reMapped.addMetadata(axm);
		lookup = data.get(3);
		
	}
	
	
private int[] getIndices(double x, double y) {
		
		IDataset[] ax = MappingUtils.getAxesFromMetadata(reMapped);
		
		IDataset xx = ax[0];
		IDataset yy = ax[1];
		
		int xi = Maths.abs(Maths.subtract(xx, x)).argMin();
		int yi = Maths.abs(Maths.subtract(yy, y)).argMin();
		
		return new int[]{xi,yi};
	}
	
	@Override
	public IDataset getSpectrum(double x, double y) {
		int[] indices = getIndices(x, y);
		int index = lookup.getInt(indices);
		return parent.getSpectrum(index);
	}

}
