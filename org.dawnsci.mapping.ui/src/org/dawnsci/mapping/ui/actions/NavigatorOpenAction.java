package org.dawnsci.mapping.ui.actions;

import java.nio.file.Files;
import java.nio.file.Path;

import org.dawnsci.mapping.ui.datamodel.MappedFileManager;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.sda.navigator.views.IOpenFileAction;

public class NavigatorOpenAction implements IOpenFileAction {
	
	
	private static final Logger logger = LoggerFactory.getLogger(NavigatorOpenAction.class);
	
	@Override
	public void openFile(Path file) {

		if (file==null) return;
		
		if (!Files.isDirectory(file)) {
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			IViewPart view = page.findView("org.dawnsci.mapping.ui.mappeddataview");
			if (view==null) return;
			
			final MappedFileManager manager = (MappedFileManager)view.getAdapter(MappedFileManager.class);
			if (manager != null) {
				manager.importFile(file.toAbsolutePath().toString());
			} else {
				logger.error("Could not get file manager");
			}
		}
	}
}
