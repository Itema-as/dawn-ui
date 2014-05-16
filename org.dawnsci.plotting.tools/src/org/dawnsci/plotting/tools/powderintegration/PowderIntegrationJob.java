package org.dawnsci.plotting.tools.powderintegration;

import java.util.Arrays;
import java.util.List;

import org.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Maths;
import uk.ac.diamond.scisoft.analysis.diffraction.QSpace;
import uk.ac.diamond.scisoft.analysis.diffraction.powder.AbstractPixelIntegration;
import uk.ac.diamond.scisoft.analysis.diffraction.powder.AbstractPixelIntegration1D;
import uk.ac.diamond.scisoft.analysis.diffraction.powder.AbstractPixelIntegration2D;
import uk.ac.diamond.scisoft.analysis.diffraction.powder.NonPixelSplittingIntegration;
import uk.ac.diamond.scisoft.analysis.diffraction.powder.NonPixelSplittingIntegration2D;
import uk.ac.diamond.scisoft.analysis.diffraction.powder.PixelIntegrationUtils;
import uk.ac.diamond.scisoft.analysis.diffraction.powder.PixelSplittingIntegration;
import uk.ac.diamond.scisoft.analysis.diffraction.powder.PixelSplittingIntegration2D;
import uk.ac.diamond.scisoft.analysis.io.IDiffractionMetadata;
import uk.ac.diamond.scisoft.analysis.roi.IROI;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile.XAxis;

public class PowderIntegrationJob extends Job {

	private final static Logger logger = LoggerFactory.getLogger(PowderIntegrationJob.class);
	
	IPlottingSystem system;
	IDiffractionMetadata md;
	QSpace qSpace;
	AbstractPixelIntegration integrator;
	XAxis xAxis = XAxis.Q;
	IntegrationMode mode = IntegrationMode.NONSPLITTING;
	AbstractDataset data;
	AbstractDataset mask;
	AbstractDataset correction;
	PowderIntegrationModel model;
	PowderCorrectionModel corModel;
	IROI roi;
	int nBins;

	public enum IntegrationMode{NONSPLITTING,SPLITTING,SPLITTING2D,NONSPLITTING2D}
	
	public PowderIntegrationJob(IDiffractionMetadata md, IPlottingSystem system) {
		super("Integration");
		this.md = md;
		this.system = system;
		this.qSpace = new QSpace(md.getDetector2DProperties(), md.getDiffractionCrystalEnvironment());
	}
	
	public void setData(AbstractDataset data, AbstractDataset mask, IROI roi) {
		this.data = data;
		this.mask = mask;
		this.roi = roi;
		this.nBins = AbstractPixelIntegration.calculateNumberOfBins(md.getDetector2DProperties().getBeamCentreCoords(),
				data.getShape());
	}
	
	public void setAxisType(XAxis xAxis) {
		if (this.xAxis != xAxis) {
			this.xAxis = xAxis;
			integrator = null;
		}
	}
	
	public XAxis getAxisType() {
		return xAxis;
	}
	
	public void setIntegrationMode(IntegrationMode mode) {
		if (this.mode != mode) {
			this.mode = mode;
			integrator = null;
		}
	}
	
	public IntegrationMode getIntegrationMode() {
		return mode;
	}
	
	public void clearCorrectionArray() {
		correction = null;
	}

	
	public void setModels(PowderIntegrationModel model, PowderCorrectionModel corModel) {
		this.model = model;
		this.corModel = corModel;
		updateIntegratorFromModel();
	}
	
	public void updateIntegratorFromModel() {
		if (integrator == null) {
			updateIntegrator();
		}
		if (model != null) {
			//clone incase they get nulled
			
			if (model.getRadialRange() == null) integrator.setRadialRange(null);
			else integrator.setRadialRange(model.getRadialRange().clone());
			
			if (model.getAzimuthalRange() == null) integrator.setAzimuthalRange(null);
			else integrator.setAzimuthalRange(model.getAzimuthalRange().clone());
			
			integrator.setNumberOfBins(model.getNumberOfPrimaryBins());
			
			if (integrator instanceof AbstractPixelIntegration2D) {
				((AbstractPixelIntegration2D)integrator).setNumberOfAzimuthalBins(model.getNumberOfSecondaryBins());
			}
			
			if (integrator instanceof AbstractPixelIntegration1D) {
				((AbstractPixelIntegration1D)integrator).setAzimuthalIntegration(model.isAzimuthal());
			}
		}
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		if (integrator == null) {
			updateIntegratorFromModel();
		}
		
		if (monitor.isCanceled()) return Status.CANCEL_STATUS;
		
		integrator.setMask(mask);
//		integrator.setROI(roi);
		integrator.setAxisType(xAxis);
		
		if (monitor.isCanceled()) return Status.CANCEL_STATUS;
		
		//all accept 2d no splitting should be fast
		if (mode == IntegrationMode.SPLITTING2D) system.setEnabled(false);

		AbstractDataset processed = applyCorrections(data);
		
		if (monitor.isCanceled()) return Status.CANCEL_STATUS;
		
		double maxd = data.max().doubleValue();
		double max = processed.max().doubleValue();
		max = max + 0;
		maxd = maxd + 0;
		
		final List<AbstractDataset> out;
		
		try {
			out = integrator.integrate(processed);
		} catch (Exception e) {
			logger.error("Someones probably just toggling buttons to quickly, but if something looks wrong check here!");
			return Status.CANCEL_STATUS;
		}
		
		system.setEnabled(true);
		
		if (mode == IntegrationMode.NONSPLITTING || mode ==IntegrationMode.SPLITTING) {
			if (system.is2D()) system.reset();
			out.get(1).setName("Intensity");

			system.updatePlot1D(out.get(0), Arrays.asList(new IDataset[]{out.get(1)}), null);
			Display.getDefault().syncExec(new Runnable() {

				@Override
				public void run() {
					system.getSelectedXAxis().setTitle(out.get(0).getName());

				}
			});
		} else {
			out.get(1).setName("Intensity");
			system.createPlot2D(out.remove(1), out, null);
		}
		
		system.repaint();
		
		
		return Status.OK_STATUS;
	}
	
	public List<AbstractDataset> process(AbstractDataset data) {
		
		if (integrator == null) {
			updateIntegratorFromModel();
			integrator.setMask(mask);
//			integrator.setROI(roi);
			integrator.setAxisType(xAxis);
		}
		
		AbstractDataset processed = applyCorrections(data);
		
		List<AbstractDataset> out = integrator.integrate(processed);
		
		return out;
		
	}
	
	private AbstractDataset applyCorrections(AbstractDataset data) {
		
		if (corModel == null) return data;
		
		if (!corModel.isApplyPolarisationCorrection() &&
				!corModel.isApplySolidAngleCorrection() &&
				!corModel.isAppyDetectorTransmissionCorrection() &&
				!corModel.isApplyLorentzCorrection()) return data;
		
		AbstractDataset localRef;
		
		if (correction == null) {
			correction = AbstractDataset.ones(data, AbstractDataset.FLOAT32);
			
			//incase correction gets nulled while job is running
			localRef = correction;
			AbstractDataset tth = PixelIntegrationUtils.generate2ThetaArrayRadians(data.getShape(), md);
			
			if (corModel.isApplySolidAngleCorrection()) {
				PixelIntegrationUtils.solidAngleCorrection(localRef,tth);
			}
			
			if (corModel.isApplyPolarisationCorrection()) {
				AbstractDataset az = PixelIntegrationUtils.generateAzimuthalArrayRadians(data.getShape(), md);
				az.iadd(Math.toRadians(corModel.getPolarisationAngularOffset()));
				PixelIntegrationUtils.polarisationCorrection(localRef, tth, az, corModel.getPolarisationFactor());
			}
			
			if (corModel.isAppyDetectorTransmissionCorrection()) {
				PixelIntegrationUtils.detectorTranmissionCorrection(localRef, tth, corModel.getTransmittedFraction());
			}
			
			if (corModel.isApplyLorentzCorrection()) {
				PixelIntegrationUtils.lorentzCorrection(localRef, tth);
			}
			
		} else {
			localRef = correction;
		}
		
		return Maths.multiply(data,localRef);
	}
	
	private void updateIntegrator() {
		switch (mode) {
		case NONSPLITTING:
			integrator = new NonPixelSplittingIntegration(md, nBins);
			break;
		case SPLITTING:
			integrator = new PixelSplittingIntegration(md, nBins);
			break;
		case SPLITTING2D:
			integrator = new PixelSplittingIntegration2D(md, nBins,nBins);
			break;
		case NONSPLITTING2D:
			integrator = new NonPixelSplittingIntegration2D(md, nBins,nBins);
			break;
		}
	}

}
