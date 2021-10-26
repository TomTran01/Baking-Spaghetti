package de.fhdw.bpm.simplebpmnexecutor.action.contribution;

import java.lang.ProcessBuilder.Redirect.Type;
import java.nio.file.DirectoryStream.Filter;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.bpmn2.*;
import org.eclipse.bpmn2.Process;
import org.eclipse.bpmn2.modeler.core.model.Bpmn2ModelerFactory.Bpmn2ModelerDocumentRootImpl;
import org.eclipse.core.resources.IFile;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.plugin.EcorePlugin;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

public class SimpleExecutorViewActionDelegate implements IObjectActionDelegate {

	@Override
	public void run(IAction action) {
		IStructuredSelection structuredSelection = (IStructuredSelection) PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow().getSelectionService().getSelection();
		final IFile file = (IFile) structuredSelection.getFirstElement();

		final ResourceSet resourceSet = new ResourceSetImpl();

		resourceSet.getURIConverter().getURIMap().putAll(EcorePlugin.computePlatformURIMap(true));

		final Resource bpmnModelResource = resourceSet
				.getResource(URI.createPlatformResourceURI(file.getFullPath().toString(), true), true);

		final Bpmn2ModelerDocumentRootImpl bpmn2ModelerDocumentRootImpl = 
				(Bpmn2ModelerDocumentRootImpl) bpmnModelResource.getContents().get(0);

		final FeatureMap featureMap = bpmn2ModelerDocumentRootImpl.getMixed();

		org.eclipse.bpmn2.Process process = null;

		for (FeatureMap.Entry entry : featureMap) {
			if (entry.getValue() instanceof Definitions) {
				List<RootElement> rootElements = ((Definitions) entry.getValue()).getRootElements();
				for (RootElement rootElement : rootElements) {
					System.out.println("###### LET'S GO! ######");
					
					if (rootElement instanceof Process) {
						process = (Process) rootElement;	
						
						List<Event> events = this.collectEvents(process);
						
						System.out.println(events.size());
					}					
				}
			}
		}

		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (process == null) {
			MessageDialog.openError(window.getShell(), "Simple BPMN Executor", "Found no process model.");
			return;
		}

		MessageDialog.openInformation(window.getShell(), "Simple BPMN Executor",
				"Found a process model: " + process.getName());

	}

	//HILFE
	
	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		// TODO Auto-generated method stub

	}
	
	/**
	 * Collects all Events from process.
	 * @param process Process, which Events shall get collected.
	 * @return List of Events (List<Event>).
	 */
	@SuppressWarnings("unchecked")
	private List<Event> collectEvents(Process process) {
		return (List<Event>)(Object)process
				.getFlowElements()
				.stream()
				.filter(
						e -> e instanceof Event
				)
				.collect(Collectors.toList());
	}
	
	

}
