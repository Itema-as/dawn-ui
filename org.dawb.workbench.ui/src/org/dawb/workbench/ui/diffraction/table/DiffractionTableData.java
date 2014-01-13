/*-
 * Copyright 2013 Diamond Light Source Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dawb.workbench.ui.diffraction.table;

import java.util.List;

import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.tools.diffraction.DiffractionImageAugmenter;

import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.diffraction.QSpace;
import uk.ac.diamond.scisoft.analysis.io.IDiffractionMetadata;
import uk.ac.diamond.scisoft.analysis.roi.IROI;

/**
 * Data item used in the table viewer of the Diffraction calibration views
 */
public class DiffractionTableData {
	public IPlottingSystem system;
	public String path;
	public String name;
	public DiffractionImageAugmenter augmenter;
	public IDiffractionMetadata md;
	public IDataset image;
	public List<IROI> rois; // can contain null entries as placeholders
	public QSpace q;
	public double od = Double.NaN;
	public double distance;
	public int nrois = -1; // number of actual ROIs found
	public boolean use = false;
	public double residual;
}