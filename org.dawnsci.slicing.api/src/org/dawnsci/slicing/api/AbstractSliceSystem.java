package org.dawnsci.slicing.api;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.api.PlotType;
import org.dawnsci.slicing.api.system.AxisChoiceEvent;
import org.dawnsci.slicing.api.system.AxisChoiceListener;
import org.dawnsci.slicing.api.system.DimensionalEvent;
import org.dawnsci.slicing.api.system.DimensionalListener;
import org.dawnsci.slicing.api.system.DimsDataList;
import org.dawnsci.slicing.api.system.ISliceGallery;
import org.dawnsci.slicing.api.system.ISliceSystem;
import org.dawnsci.slicing.api.tool.ISlicingTool;
import org.dawnsci.slicing.api.util.SliceUtils;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.io.SliceObject;

/**
 * Do not expose this class to copying. Instead use ISliceSystem
 * @author fcp94556
 * @internal
 */
public abstract class AbstractSliceSystem implements ISliceSystem {

	protected static final Logger logger = LoggerFactory.getLogger(AbstractSliceSystem.class);

	protected DimsDataList    dimsDataList;
	protected IPlottingSystem plottingSystem;
	protected String          sliceReceiverId;
	private List<IAction>     customActions;
	protected SliceObject     sliceObject;
	
	protected Enum        sliceType=PlotType.IMAGE;
	
	@Override
	public void setPlottingSystem(IPlottingSystem system) {
		this.plottingSystem = system;
	}

	@Override
	public IPlottingSystem getPlottingSystem() {
		return plottingSystem;
	}
	public SliceObject getCurrentSlice() {
		return sliceObject;
	}

	@Override
	public void setDimsDataList(DimsDataList sliceSetup) {
		this.dimsDataList = sliceSetup;
	}

	@Override
	public DimsDataList getDimsDataList() {
		return dimsDataList;
	}
	
	/**
	 * May be implemented to save the current slice set up.
	 */
	protected abstract void saveSliceSettings();
	
	private ISlicingTool activeTool;

	/**
	 * Creates the slice tools by reading extension points
	 * for the slice tools.
	 * 
	 * @return
	 */
	protected IToolBarManager createSliceTools() {
				
		final ToolBarManager man = new ToolBarManager(SWT.FLAT|SWT.RIGHT);
		man.add(new Separator("sliceTools"));
		
		final IConfigurationElement[] eles = Platform.getExtensionRegistry().getConfigurationElementsFor("org.dawnsci.slicing.api.slicingTool");

  		plotTypeActions= new HashMap<Enum, IAction>();

		for (IConfigurationElement e : eles) {
			
			final ISlicingTool slicingTool = createSliceTool(e);
			
			final String requireSep = e.getAttribute("separator");
			if ("true".equals(requireSep)) man.add(new Separator());
			
			IAction action = slicingTool.createAction();
			if (action==null) action = createSliceToolAction(e, slicingTool);
			man.add(action);
			plotTypeActions.put(slicingTool.getSliceType(), action);

		}
								
		return man;
	}
	
	private IAction createSliceToolAction(IConfigurationElement e, final ISlicingTool slicingTool) {
		
		String toolTip = e.getAttribute("tooltip");
		if (toolTip==null) toolTip = slicingTool.getToolId();

		final Action action = new Action(toolTip, IAction.AS_CHECK_BOX) {
        	public void run() {
        		militarize(slicingTool);
        	}
        };
        
    	final String   icon  = e.getAttribute("icon");
    	if (icon!=null) {
	    	final String   id    = e.getContributor().getName();
	    	final Bundle   bundle= Platform.getBundle(id);
	    	final URL      entry = bundle.getEntry(icon);
	    	final ImageDescriptor des = ImageDescriptor.createFromURL(entry);
	    	action.setImageDescriptor(des);
    	}

		action.setId(slicingTool.getToolId());	
	    return action;	
	}
	
	/**
	 * Demilitarizes the current tool (if different) and miliarizes this tool.
	 * 
	 * @param tool
	 */
	@Override
	public void militarize(ISlicingTool slicingTool) {
		saveSliceSettings();
		if (activeTool!=null && slicingTool!=activeTool) {
			activeTool.demilitarize();
		}
		slicingTool.militarize();
		activeTool = slicingTool;
		
		// check the correct actions
		for (Enum key : plotTypeActions.keySet()) {
			final IAction action = plotTypeActions.get(key);
			action.setChecked(key==sliceType);
		}
	}
	
	/**
	 * 
	 * @return null if ok, error message if errors.
	 */
	protected String checkErrors() {
		
		boolean isX = false;
		for (int i = 0; i < dimsDataList.size(); i++) {
			if (dimsDataList.getDimsData(i).getPlotAxis()==0) isX = true;
		}
		boolean isY = false;
		for (int i = 0; i < dimsDataList.size(); i++) {
			if (dimsDataList.getDimsData(i).getPlotAxis()==1) isY = true;
		}
		boolean isZ = false;
		for (int i = 0; i < dimsDataList.size(); i++) {
			if (dimsDataList.getDimsData(i).getPlotAxis()==2) isZ = true;
		}

		String errorMessage = "";
		boolean ok = false;
		
		Enum sliceType = getSliceType();
		try {
			final Method dimCountMethod = sliceType.getClass().getMethod("getDimensions");
			final int dimCount = (Integer)dimCountMethod.invoke(sliceType);

			if (dimCount==1) {
				ok = isX;
				errorMessage = "Please set an X axis.";
			} else if (dimCount==2){
				ok = isX&&isY;
				errorMessage = "Please set an X and Y axis or switch to 'Slice as line plot'.";
			} else if (dimCount==3){
				ok = isX&&isY&&isZ;
				errorMessage = "Please set an X, Y and Z axis or switch to 'Slice as image plot'.";
			}
			
		} catch (Throwable ne) {
			logger.error("Cannot find the getDimensions method in "+sliceType.getClass());
			ok = false;
			errorMessage="Invalid slice type: "+sliceType;
		}
		
		return ok ? null : errorMessage;
	}


	private  Map<Enum, IAction> plotTypeActions;
	protected IAction getActionByPlotType(Object plotType) {
		if (plotTypeActions==null) return null;
		return plotTypeActions.get(plotType);
	}


	
	/**
	 * 
	 * @param e
	 * @return
	 */
	private ISlicingTool createSliceTool(IConfigurationElement e) {
    	
		ISlicingTool tool = null;
    	try {
    		tool  = (ISlicingTool)e.createExecutableExtension("class");
    	} catch (Throwable ne) {
    		logger.error("Cannot create tool page "+e.getAttribute("class"), ne);
    		return null;
    	}
    	tool.setToolId(e.getAttribute("id"));	       	
    	tool.setSlicingSystem(this);
    	
    	// TODO Provide the tool with a reference to the part with the
    	// slice will end up being showed in?
    	
    	return tool;
	}


	@Override
	public void dispose() {
		if (dimensionalListeners!=null) dimensionalListeners.clear();
		dimensionalListeners = null;
	}

	@Override
	public void setSliceGalleryId(String id) {
		this.sliceReceiverId = id;
	}
	
	protected void openGallery() {
		
		if (sliceReceiverId==null) return;
		SliceObject cs;
		try {
			final SliceObject current = getCurrentSlice();
			final int[] dataShape     = getData().getLazySet().getShape();
			cs = SliceUtils.createSliceObject(dimsDataList, dataShape, current);
		} catch (Exception e1) {
			logger.error("Cannot create a slice!");
			return;
		}
		
		IViewPart view;
		try {
			view = getActivePage().showView(sliceReceiverId);
		} catch (PartInitException e) {
			logger.error("Cannot find view "+sliceReceiverId);
			return;
		}
		if (view instanceof ISliceGallery) {
			((ISliceGallery)view).updateSlice(getData().getLazySet(), cs);
		}
		
	}
	private static IWorkbenchPage getActivePage() {
		final IWorkbench bench = PlatformUI.getWorkbench();
		if (bench == null)
			return null;
		final IWorkbenchWindow window = bench.getActiveWorkbenchWindow();
		if (window == null)
			return null;
		return window.getActivePage();
	}

	protected boolean rangesAllowed = false;
	public void setRangesAllowed(boolean isVis) {
		rangesAllowed = isVis;
	}
	public boolean isRangesAllowed() {
		return rangesAllowed;
	}

	public void addCustomAction(IAction customAction) {
		if (customActions == null)customActions = new ArrayList<IAction>();
		customActions.add(customAction);
	}
	
	protected void createCustomActions(IContributionManager man) {
		if (customActions!=null) {
			man.add(new Separator("group5"));
			for (IAction action : customActions) man.add(action);
		}
	}


	private Collection<DimensionalListener> dimensionalListeners;
	@Override
	public void addDimensionalListener(DimensionalListener l) {
		if (dimensionalListeners==null) dimensionalListeners= new HashSet<DimensionalListener>(7);
		dimensionalListeners.add(l);
	}
	
	@Override
	public void removeDimensionalListener(DimensionalListener l) {
		if (dimensionalListeners==null) return;
		dimensionalListeners.remove(l);
	}
	
	protected void fireDimensionalListeners() {
		if (dimensionalListeners==null) return;
		final DimensionalEvent evt = new DimensionalEvent(this, dimsDataList);
		for (DimensionalListener l : dimensionalListeners) {
			l.dimensionsChanged(evt);
		}
	}
	
	private Collection<AxisChoiceListener> axisChoiceListeners;
	@Override
	public void addAxisChoiceListener(AxisChoiceListener l) {
		if (axisChoiceListeners==null) axisChoiceListeners= new HashSet<AxisChoiceListener>(7);
		axisChoiceListeners.add(l);
	}
	
	@Override
	public void removeAxisChoiceListener(AxisChoiceListener l) {
		if (axisChoiceListeners==null) return;
		axisChoiceListeners.remove(l);
	}
	
	protected void fireAxisChoiceListeners(AxisChoiceEvent evt) {
		if (axisChoiceListeners==null) return;
		for (AxisChoiceListener l : axisChoiceListeners) {
			l.axisChoicePerformed(evt);
		}
	}


	@Override
	public Enum getSliceType() {
		return sliceType;
	}

	@Override
	public void setSliceType(Enum plotType) {
		this.sliceType = plotType;
		setSliceTypeInfo(null, null);
	}
	
	/**
	 * Does nothing by default.
	 */
	@Override
	public void setSliceTypeInfo(String label, ImageDescriptor icon) {
		
	}
	
	/**
	 * 
	 * @return true if the current slice type is a 3D one.
	 */
	public boolean is3D() {
		return sliceType instanceof PlotType && ((PlotType)sliceType).is3D();
	}

	public ISlicingTool getActiveTool() {
		return activeTool;
	}

	/**
	 * Call this method if overriding.
	 */
	@Override
	public void setVisible(final boolean vis) {
		if (activeTool!=null) {
			try {
				if (vis) {
					activeTool.militarize();
				} else {
					activeTool.demilitarize();
				}
			} catch (Throwable ne) {
				logger.error("Cannot change militarized state of slice tool! "+activeTool.getToolId());
			}
		}
	}
}