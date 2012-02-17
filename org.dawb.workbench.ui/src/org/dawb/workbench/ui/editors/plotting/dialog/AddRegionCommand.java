package org.dawb.workbench.ui.editors.plotting.dialog;

import org.csstudio.swt.xygraph.undo.IUndoableCommand;
import org.dawb.workbench.ui.editors.plotting.swtxy.Region;
import org.dawb.workbench.ui.editors.plotting.swtxy.XYRegionGraph;

/**The undoable command to add an annotation.
 * @author Xihui Chen
 *
 */
public class AddRegionCommand implements IUndoableCommand {
	
	private XYRegionGraph xyGraph;
	private Region region;
	
	public AddRegionCommand(XYRegionGraph xyGraph, Region region) {
		this.xyGraph = xyGraph;
		this.region = region;
	}

	public void redo() {
		xyGraph.addRegion(region);
	}

	public void undo() {
		xyGraph.removeRegion(region);
	}
	
	@Override
	public String toString() {
		return "Add Region";
	}

}
