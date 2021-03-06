package org.dawnsci.mapping.ui.dialog;

import java.util.ArrayList;
import java.util.List;

import org.dawnsci.mapping.ui.Activator;
import org.dawnsci.mapping.ui.MappingUtils;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlotType;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageGridDialog {

	private static Logger logger = LoggerFactory.getLogger(ImageGridDialog.class);

	private List<IDataset> data;
	private List<IPlottingSystem<Composite>> systems = new ArrayList<IPlottingSystem<Composite>>();
	private Image image;
	private Shell shell;

	public ImageGridDialog(List<IDataset> data) throws Exception {
		shell = new Shell(Display.getDefault());
		shell.setText("Comparison Viewer");
		shell.setImage(image = Activator.getImageDescriptor("icons/images-stack.png").createImage());
		shell.addListener(SWT.Close, new Listener() {
			public void handleEvent(Event event) {
				ImageGridDialog.this.close();
			}
		});

		if (data == null || data.isEmpty())
			throw new Exception("No data is available to visualize in the Comparison Image Viewer dialog.");
		this.data = data;
		try {
			for (int i = 0; i < data.size(); i++) {
				systems.add(PlottingFactory.createPlottingSystem(Composite.class));
			}
		} catch (Exception e) {
			String error = "Error creating Image Grid plotting systems:" + e.getMessage();
			logger.error("Error creating Image Grid plotting systems:", e);
			throw new Exception(error);
		}
	}

	/**
	 * Create the content of the Shell dialog
	 * 
	 * @return
	 */
	public Control createContents() {
		Display display = Display.getDefault();
		Color white = new Color(display, 255, 255, 255);
		// Shell setting
		shell.setLayout(new GridLayout());
		shell.setSize(800, 600);
		Monitor primary = display.getPrimaryMonitor();
		Rectangle bounds = primary.getBounds();
		Rectangle rect = shell.getBounds();
		int x = bounds.x + (bounds.width - rect.width) / 2;
		int y = bounds.y + (bounds.height - rect.height) / 2;
		shell.setLocation(x, y);
		
		Composite container = new Composite(shell, SWT.NONE);
		container.setLayout(new GridLayout(1, false));
		container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		container.setBackground(white);

		Composite plotsComp = new Composite(container, SWT.NONE);
		plotsComp.setLayout(new GridLayout(3, false));
		plotsComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		plotsComp.setBackground(white);
		try {
			int i = 0;
			for (IPlottingSystem<Composite> system : systems) {
				system.createPlotPart(plotsComp, "Plot " + i, null, PlotType.IMAGE, null);
				system.getPlotComposite().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
//				system.updatePlot2D(data.get(i), null, null);
				MappingUtils.plotDataWithMetadata(data.get(i), system, null);
				i++;
			}
		} catch (Exception e) {
			logger.error("Error plotting data:", e);
			e.printStackTrace();
		}
		Button closeButton = new Button(container, SWT.NONE);
		closeButton.setText("Close");
		closeButton.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		closeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent evt) {
				ImageGridDialog.this.close();
			}
		});
		return container;
	}

	public void close() {
		image.dispose();
		if (shell != null)
			shell.dispose();
	}

	/**
	 *open the shell dialog
	 */
	public void open() {
		shell.open();
		while (!shell.isDisposed()) {
			if (!shell.getDisplay().readAndDispatch())
				shell.getDisplay().sleep();
		}
	}
}
