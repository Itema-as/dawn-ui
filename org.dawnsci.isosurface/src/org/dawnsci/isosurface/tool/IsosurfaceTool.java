/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.dawnsci.isosurface.tool;

import org.dawnsci.isosurface.Activator;
import org.dawnsci.isosurface.alg.MarchingCubesModel;
import org.dawnsci.isosurface.alg.Surface;
import org.dawnsci.isosurface.isogui.IsoBean;
import org.dawnsci.isosurface.isogui.IsoComposite;
import org.dawnsci.isosurface.isogui.IsoHandler;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.processing.IOperation;
import org.eclipse.dawnsci.analysis.api.processing.IOperationService;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.plotting.api.PlotType;
import org.eclipse.dawnsci.plotting.api.histogram.IImageService;
import org.eclipse.dawnsci.plotting.api.histogram.ImageServiceBean;
import org.eclipse.dawnsci.plotting.api.histogram.ImageServiceBean.HistoType;
import org.eclipse.dawnsci.slicing.api.system.AxisChoiceEvent;
import org.eclipse.dawnsci.slicing.api.system.AxisChoiceListener;
import org.eclipse.dawnsci.slicing.api.system.AxisType;
import org.eclipse.dawnsci.slicing.api.system.DimensionalEvent;
import org.eclipse.dawnsci.slicing.api.system.DimensionalListener;
import org.eclipse.dawnsci.slicing.api.system.DimsDataList;
import org.eclipse.dawnsci.slicing.api.system.SliceSource;
import org.eclipse.dawnsci.slicing.api.tool.AbstractSlicingTool;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author nnb55016 Class for visualising isosurfaces in DAWN
 */
public class IsosurfaceTool extends AbstractSlicingTool
{
	
	private static final Logger logger = LoggerFactory
			.getLogger(IsosurfaceTool.class);
	
	// Listeners
	private DimensionalListener dimensionalListener;
	private AxisChoiceListener axisChoiceListener;
	
	// UI Stuff
	private IsoComposite isoComp;
	private IsoBean isoBean;
	
	public IsosurfaceTool()
	{
		this.dimensionalListener = new DimensionalListener() // !! what are these fore
		{
			@Override
			public void dimensionsChanged(DimensionalEvent evt)
			{
				update();
			}
		};
		
		this.axisChoiceListener = new AxisChoiceListener()
		{
			@Override
			public void axisChoicePerformed(AxisChoiceEvent evt)
			{
				update();
			}
		};
	}
	
	@Override
	public void dispose()
	{
		super.dispose();
	}
	
	/**
	 * Create controls for the surface in the user interface
	 */
	public void createToolComponent(Composite parent)
	{
		
		ScrolledComposite sc = new ScrolledComposite(parent, SWT.H_SCROLL | SWT.V_SCROLL);
		sc.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		this.isoComp = new IsoComposite(
						sc, 
						SWT.FILL);
		isoComp.setSize(isoComp.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		
		sc.setContent(isoComp);
		
		isoComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		setControlsVisible(false);
		
		isoBean = new IsoBean();
		
	}
	
	/**
	 * Method that shows the display of the isosurface while the corresponding
	 * button is selected
	 */
	@Override
	public void militarize(boolean newData)
	{
		
		boolean alreadyIso = getSlicingSystem().getSliceType() == getSliceType();
		if (!newData && alreadyIso)
			return;
		
		getSlicingSystem().setSliceType(getSliceType());
		
		
		final DimsDataList dimsDataList = getSlicingSystem().getDimsDataList();
		if (dimsDataList != null)
			dimsDataList.setThreeAxesOnly(AxisType.X, AxisType.Y, AxisType.Z);
		
		getSlicingSystem().update(false);
		getSlicingSystem().addDimensionalListener(dimensionalListener);
		getSlicingSystem().addAxisChoiceListener(axisChoiceListener);
		
		update();
		
	}
	
	private void setControlsVisible(boolean vis)
	{
		isoComp.setVisible(vis);
	}
	
	/**
	 * Called to update when lazy data changed.
	 */
	private void update()
	{
		//get the data from the slicing system
		final SliceSource data = getSlicingSystem().getData();
		
		// declare the data as a lazydata set (i.e. slices)
		ILazyDataset dataSlices = data.getLazySet().getSliceView(getSlices());
		
		dataSlices = dataSlices.squeezeEnds();
		// slice.setName("Sliced " + data.getLazySet().getName());
		
		// check if the dataslice is compatible
		if (dataSlices.getRank() != 3)
			throw new RuntimeException("Invalid slice for isosurface tool!");
		final ILazyDataset finalDataslices = dataSlices;
		
		// estimate the min/max values for the isosurface
		// the estimation is currently quite inaccurate
		double[] minMax = {Integer.MAX_VALUE, Integer.MIN_VALUE};
		minMax = IsoSurfaceUtil.estimateMinMaxIsoValueFromDataSet(finalDataslices);
		
		// roughly calculate the default cube size
		int[] defaultCubeSize= new int[] {
				(int) Math.max(1, Math.ceil(finalDataslices.getShape()[2]/20.0)),   
				(int) Math.max(1, Math.ceil(finalDataslices.getShape()[1]/20.0)), 
				(int) Math.max(1, Math.ceil(finalDataslices.getShape()[0]/20.0))};
		
		// set the min and max isovalues - set the default cube size for new sufaces
		isoComp.setMinMaxIsoValueAndCubeSize(minMax, defaultCubeSize);
		
		// create the isoController
		try 
		{
			
			final IOperationService service = (IOperationService) Activator
					.getService(IOperationService.class);
			
			IOperation<MarchingCubesModel, Surface> generator = (IOperation<MarchingCubesModel, Surface>) service
					.create("org.dawnsci.isosurface.marchingCubes");
			
			IsoHandler isoController = new IsoHandler(
					isoComp, 
					isoBean, 
					new IsosurfaceJob(
							"isoSurfaceJob" , 
							getSlicingSystem().getPlottingSystem(), 
							finalDataslices, 
							generator));
		}
		catch (Exception e)
		{
			System.out.println("IsoController not initilised");
			e.printStackTrace();
		}
		
		setControlsVisible(true);
	}
	
	/**
	 * Does nothing unless overridden.
	 */
	@Override
	public void demilitarize()
	{
		if (dimensionalListener != null)
		{
			getSlicingSystem().removeDimensionalListener(dimensionalListener);
		}
		if (axisChoiceListener != null)
		{
			getSlicingSystem().removeAxisChoiceListener(axisChoiceListener);
		}
		
		setControlsVisible(false);
		
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	public Enum getSliceType()
	{
		return PlotType.ISOSURFACE;
	}
	
	@Override
	public boolean isSliceRequired()
	{
		return false;
	}
	
	@Override
	public boolean isAdvancedSupported()
	{
		return false;
	}
	
}
