package org.dawnsci.plotting.system.preference;

import org.dawnsci.plotting.api.tool.IToolPage.ToolPageRole;

/**
 * Groups of actions available in the UI. Might promote to API plugin
 * so that anyone can configure tools by group.
 * 
 * @author fcp94556
 *
 */
public enum ToolbarConfigurationConstants {
	
	CONFIG    ("org.dawnsci.plotting.system.preference.config",           "Configuration"),
	ANNOTATION("org.dawnsci.plotting.system.preference.annotation",       "Annotation"),
	TOOL1D    (ToolPageRole.ROLE_1D.getId(),                              "XY Tools"),
	TOOL2D    (ToolPageRole.ROLE_2D.getId(),                              "Image Tools"),
	TOOL3D    (ToolPageRole.ROLE_3D.getId(),                              "Surface Tools"),
	REGION    ("org.dawnsci.plotting.system.preference.region",           "Regions"),
	ASPECT    ("org.dawnsci.plotting.system.preference.aspectRatio",      "Aspect Ratio"),
	ZOOM      ("org.dawnsci.plotting.system.preference.zoom",             "Zoom"),
	UNDO      ("org.dawnsci.plotting.system.preference.undo",             "Undo/Redo"),
	EXPORT    ("org.dawnsci.plotting.system.preference.export",           "Export"),
	HISTO     ("org.dawnsci.plotting.system.preference.histo",            "Histogram"),
	XYPLOT    ("org.dawnsci.plotting.system.preference.xyPlot",           "XY Plot"),
	FULLSCREEN("org.dawnsci.plotting.system.preference.fullScreen",       "Full screen");
	
	private String id;
	private String label;
	
	ToolbarConfigurationConstants(String id, String label) {
		this.id    = id;
		this.label = label;
	}

	public String getId() {
		return id;
	}

	public String getLabel() {
		return label;
	}
}
