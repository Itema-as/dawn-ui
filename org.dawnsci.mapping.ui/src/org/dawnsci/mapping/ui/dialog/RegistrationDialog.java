package org.dawnsci.mapping.ui.dialog;

import java.util.List;

import org.dawb.common.ui.widgets.ActionBarWrapper;
import org.dawnsci.mapping.ui.MappingUtils;
import org.eclipse.core.runtime.Assert;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.metadata.AxesMetadata;
import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetFactory;
import org.eclipse.dawnsci.analysis.dataset.impl.LinearAlgebra;
import org.eclipse.dawnsci.analysis.dataset.impl.RGBDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.function.MapToRotatedCartesian;
import org.eclipse.dawnsci.analysis.dataset.metadata.AxesMetadataImpl;
import org.eclipse.dawnsci.analysis.dataset.roi.PointROI;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlotType;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.dawnsci.plotting.api.region.IROIListener;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegion.RegionType;
import org.eclipse.dawnsci.plotting.api.region.ROIEvent;
import org.eclipse.dawnsci.plotting.api.trace.IImageTrace;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegistrationDialog extends Dialog {

	private IDataset image;
	private IDataset map;
	private IDataset registered;
	private IPlottingSystem<Composite> systemImage;
	private IPlottingSystem<Composite> systemMap;
	private IPlottingSystem<Composite> systemComposite;
	private IRegion[] mapPoints = new IRegion[4];
	private IRegion[] imagePoints = new IRegion[4];
	int count = 0;
	
	private final static Logger logger = LoggerFactory.getLogger(RegistrationDialog.class);
	
	public RegistrationDialog(Shell parentShell, IDataset map, IDataset image) {
		super(parentShell);
		setShellStyle(getShellStyle() | SWT.RESIZE);
		this.map = map;
		this.image = image.getSliceView();
		this.image.clearMetadata(AxesMetadata.class);
		
		try {
			systemMap = PlottingFactory.createPlottingSystem();
			systemImage = PlottingFactory.createPlottingSystem();
			systemComposite = PlottingFactory.createPlottingSystem();
		} catch (Exception e) {
			logger.error("Could not create plotting systems!", e);
		}
	}
	
	@Override
	public Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);
		
		container.setLayout(new GridLayout(1, true));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));
		Composite main = new Composite(container, SWT.FILL);
		main.setLayoutData(new GridData(GridData.FILL_BOTH));
		main.setLayout(new GridLayout(3, true));
		ActionBarWrapper actionBarMap = ActionBarWrapper.createActionBars(main, null);
		ActionBarWrapper actionBarImage = ActionBarWrapper.createActionBars(main, null);
		ActionBarWrapper actionBarComposite = ActionBarWrapper.createActionBars(main, null);
		systemMap.createPlotPart(main, "Map Plot", actionBarMap, PlotType.IMAGE, null);
		systemMap.getPlotComposite().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		systemMap.setTitle("Map");
		systemMap.setShowIntensity(false);
		systemMap.getSelectedXAxis().setVisible(false);
		systemMap.getSelectedYAxis().setVisible(false);

		
		systemImage.createPlotPart(main, "Image Plot", actionBarImage, PlotType.IMAGE, null);
		systemImage.getPlotComposite().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		systemImage.setTitle("Image");
		systemImage.setShowIntensity(false);
		systemImage.getSelectedXAxis().setVisible(false);
		systemImage.getSelectedYAxis().setVisible(false);

		
		systemComposite.createPlotPart(main, "Composite Plot", actionBarComposite, PlotType.IMAGE, null);
		systemComposite.getPlotComposite().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		

		MappingUtils.plotDataWithMetadata(map, systemMap, null);
		image.setName("Image");
		MappingUtils.plotDataWithMetadata(image, systemImage, null);
		
		doInitialMapping();
		
		return container;
	}
	
	private void doInitialMapping(){
		
		IDataset[] ax = MappingUtils.getAxesFromMetadata(map);
		
		Assert.isNotNull(ax);
		double mapX = map.getShape()[1];
		double mapY =  map.getShape()[0];
		
		double imX = image.getShape()[1];
		double imY =  image.getShape()[0];
		
		double[] xValsMap = new double[]{mapX/3., mapX/2., mapX-mapX/3,mapX/2};
		double[] yValsMap = new double[]{mapY/3., mapY-mapY/3,mapY/3.,mapY/2};
		double[] xValsImage = new double[]{imX/3., imX/2., imX-imX/3,imX/2};
		double[] yValsImage = new double[]{imY/3., imY-imY/3,imY/3.,imY/2};
		
		try {
			for (int i = 0; i < 4; i++) {

				Color c = Display.getDefault().getSystemColor(SWT.COLOR_RED);
				if (i == 1) c = Display.getDefault().getSystemColor(SWT.COLOR_BLUE);
				if (i == 2) c = Display.getDefault().getSystemColor(SWT.COLOR_GREEN);
				if (i == 3) c = Display.getDefault().getSystemColor(SWT.COLOR_CYAN);

				final IRegion point1 = systemMap.createRegion("Point" +i, RegionType.POINT);
				point1.setRegionColor(c);
				mapPoints[i] = point1;
				point1.setROI(new PointROI(xValsMap[i],yValsMap[i]));
				point1.addROIListener(new IROIListener.Stub() {


					@Override
					public void roiChanged(ROIEvent evt) {
						IROI roi = evt.getROI();
						sanitizeROI(roi, map.getShape());
						systemMap.repaint(false);

						RegistrationDialog.this.update();
					}
				});
				systemMap.addRegion(point1);
			}

			for (int i = 0; i < 4; i++) {
				Color c = Display.getDefault().getSystemColor(SWT.COLOR_RED);
				if (i == 1) c = Display.getDefault().getSystemColor(SWT.COLOR_BLUE);
				if (i == 2) c = Display.getDefault().getSystemColor(SWT.COLOR_GREEN);
				if (i == 3) c = Display.getDefault().getSystemColor(SWT.COLOR_CYAN);

				final IRegion point1 = systemImage.createRegion("Point" +i, RegionType.POINT);
				point1.setRegionColor(c);
				imagePoints[i] = point1;
				point1.setROI(new PointROI(xValsImage[i],yValsImage[i]));
				point1.addROIListener(new IROIListener.Stub() {


					@Override
					public void roiChanged(ROIEvent evt) {
						IROI roi = evt.getROI();
						sanitizeROI(roi, image.getShape());
						systemImage.repaint(false);
						
						RegistrationDialog.this.update();
					}
				});
				systemImage.addRegion(point1);
			}
		} catch (Exception e) {
			logger.error("Could not create Regions",e);
		}



		update();
		
		
	}
	
	public IDataset getRegisteredImage(){
		return registered;
		
	}
	
	private void sanitizeROI(IROI roi, int[] shape) {
		
		double[] point = roi.getPoint();
		
		
		if (point[0] >= shape[1]) point[0] = shape[1]-1;
		if (point[0] < 0) point[0] = 0;
		if (point[1] < 0) point[1] = 0;
		if (point[1] >= shape[0]) point[1] = shape[0]-1;
		
		roi.setPoint(point);
	}
	
	private void update() {
		Dataset v = buildDataset(mapPoints);
		Dataset x = buildDataset(imagePoints);
		if (x == null || v == null) return;
		Dataset trans = LinearAlgebra.solveSVD(x, v);

		double tX = trans.getDouble(2,0);
		double tY = trans.getDouble(2,1);
		
		double sX = Math.hypot(trans.getDouble(0,0), trans.getDouble(0,1));
		double sY = Math.hypot(trans.getDouble(1,0), trans.getDouble(1,1));
		
		double r = Math.toDegrees(Math.atan(trans.getDouble(0,1)/trans.getDouble(1,1)));
		
		int[] shape = image.getShape();
		
		Dataset xR = DatasetFactory.createRange(shape[1], Dataset.FLOAT64);
		Dataset yR = DatasetFactory.createRange(shape[0], Dataset.FLOAT64);
		
		xR.imultiply(sX);
		xR.iadd(tX);
		
		yR.imultiply(sY);
		yR.iadd(tY);
		
		MapToRotatedCartesian mrc = new MapToRotatedCartesian(0, 0, shape[1], shape[0], r*-1);
		
		IDataset im;
		
		if (image instanceof RGBDataset) {
			
			RGBDataset rgb = (RGBDataset)image;
			im = new RGBDataset(mrc.value(rgb.getRedView()).get(0),
								mrc.value(rgb.getGreenView()).get(0),
								mrc.value(rgb.getBlueView()).get(0));
			
			
		} else {
			List<Dataset> value = mrc.value(image);
			im = value.get(0);
		}
		
		logger.debug("XOffset: {}, YOffset: {}, XScale {}, YScale {},",tX,tY,sX,sY);
		
		registered = im;
		AxesMetadataImpl ax = new AxesMetadataImpl(2);
		ax.addAxis(0, yR);
		ax.addAxis(1, xR);
		im.addMetadata(ax);
		registered.addMetadata(ax);
		systemComposite.clear();
		double[] range = MappingUtils.getGlobalRange(im,map);

		IImageTrace image = MappingUtils.buildTrace("image",im, systemComposite);
		image.setGlobalRange(range);
		IImageTrace mapim = MappingUtils.buildTrace("map", map, systemComposite,120);
		mapim.setGlobalRange(range);
		systemComposite.addTrace(image);
		systemComposite.addTrace(mapim);
		
	}
	
	private Dataset buildDataset(IRegion[] regions) {


		try {
			Dataset mat = DatasetFactory.ones(new int[]{4, 3},Dataset.FLOAT64);
			int[] pos = new int[2];

			for (int i = 0; i < 4 ; i++) {
				pos[0] = i;
				pos[1] = 0;
				double[] val;
				val = regions[i].getCoordinateSystem().getValueAxisLocation(regions[i].getROI().getPoint());
				mat.set(val[0], pos);
				pos[1] = 1;
				mat.set(val[1], pos);
			}
			return mat;
		} catch (Exception e) {
			logger.error("Could not get axis location",e);
		}



		return null;

	}
	
	
	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Image Registration");
	}

	@Override
	protected Point getInitialSize() {
		return new Point(800, 600);
	}

}
