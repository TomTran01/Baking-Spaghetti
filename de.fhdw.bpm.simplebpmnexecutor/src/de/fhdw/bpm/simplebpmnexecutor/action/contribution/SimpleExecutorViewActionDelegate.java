package de.fhdw.bpm.simplebpmnexecutor.action.contribution;

import java.lang.ProcessBuilder.Redirect.Type;
import java.nio.file.DirectoryStream.Filter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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

		final Bpmn2ModelerDocumentRootImpl bpmn2ModelerDocumentRootImpl = (Bpmn2ModelerDocumentRootImpl) bpmnModelResource
				.getContents().get(0);

		final FeatureMap featureMap = bpmn2ModelerDocumentRootImpl.getMixed();

		org.eclipse.bpmn2.Process process = null;

		for (FeatureMap.Entry entry : featureMap) {
			if (entry.getValue() instanceof Definitions) {
				List<RootElement> rootElements = ((Definitions) entry.getValue()).getRootElements();
				for (RootElement rootElement : rootElements) {
					if (rootElement instanceof Process) {
						process = (Process) rootElement;
					}
				}
			}
		}

		// Implementation
		runProcess(process);

		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (process == null) {
			MessageDialog.openError(window.getShell(), "Simple BPMN Executor", "Found no process model.");
			return;
		}

		MessageDialog.openInformation(window.getShell(), "Simple BPMN Executor",
				"Found a process model: " + process.getName());

	}
	
	

	// Runs Process from start to end.
	private void runProcess(Process process) {
		System.out.println("-----------------------BEGIN--------------------------");
		List<SequenceFlow> tokens = new ArrayList<>();
		List<FlowNode> active = new ArrayList<>();
		FlowNode current = null;
		List<StartEvent> startevents = collectStartEvents(process);
		
		for (StartEvent start : startevents) {
			active.add(start);
			
			do {
				
				current = active.get(0);
				
				List<SequenceFlow> incomings = current.getIncoming();
				List<SequenceFlow> outgoings = current.getOutgoing();
				// Current aus der Aktiv-Liste entfernen
				active.remove(current);
				
				if(current instanceof Activity) {
					
					tokens.removeAll(incomings);
					addTokensToAllOutgoingFlows(tokens, current);
					addNextActives(active, current);
					printCurrentNode(current);
					
					
				} else if (current instanceof ParallelGateway) {
					
					if(tokens.containsAll(incomings)) {
						tokens.removeAll(incomings);
						addTokensToAllOutgoingFlows(tokens, current);
						addNextActives(active, current);
						printCurrentNode(current);
					}
					
				} else if (current instanceof ExclusiveGateway ) {
						
					List<SequenceFlow> tokenFlows = incomings.stream().filter(flow -> tokens.contains(flow)).collect(Collectors.toList());
					
					for (SequenceFlow flow : tokenFlows) {
						tokens.remove(flow);
						Integer rand = new Random().nextInt(outgoings.size());
						tokens.add(outgoings.get(rand));
						active.add(outgoings.get(rand).getTargetRef());
						printCurrentNode(current);	
					}
					
					
				} else if (current instanceof EndEvent) {
					tokens.removeAll(incomings);
					printCurrentNode(current);
					
				} else if (current instanceof StartEvent) {
					// Alle nachfolgenden Flows mit Token versehen
					addTokensToAllOutgoingFlows(tokens, current);
					// Alle nachfolgenden FlowNodes als Aktiv setzen
					addNextActives(active, current);
					// Ausgabe
					printCurrentNode(current);
				} else if ( current instanceof IntermediateThrowEvent) {
					
						tokens.removeAll(incomings);
						addTokensToAllOutgoingFlows(tokens, current);
						addNextActives(active, current);
						printCurrentNode(current);
						
				}
				
			} while (!(current instanceof EndEvent));
			
			System.out.println("------------------------END---------------------------------");
			
		}
		
		
		

	}


	
	
	
	/***
	 *  Fügt alle current nachfolgenden FlowNodes der aktiven Liste hinzu.
	 * @param active
	 * @param current
	 */
	private void addNextActives(List<FlowNode> active, FlowNode current) {
		current.getOutgoing().stream().forEach( flow -> active.add(flow.getTargetRef()));
	}


	/***
	 *  Fügt alle von current ausgehenden SequenceFlows der Token-Liste hinzu.
	 * @param tokens
	 * @param current
	 */
	private void addTokensToAllOutgoingFlows(List<SequenceFlow> tokens, FlowNode current) {
		tokens.addAll(current.getOutgoing().stream().collect(Collectors.toList()));
	}
	
	/***
	 *  Gibt current auf der Konsole aus.
	 * @param current
	 */
	private void printCurrentNode(FlowNode current) {
		System.out.println("["+ " ID: "+ current.getId() + " " + current.getName() + "]" + " wurde ausgeführt.");
	}

	

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
	 * 
	 * @param process Process, which Events shall get collected.
	 * @return List of Events (List<Event>).
	 */
	@SuppressWarnings("unchecked")
	private List<Event> collectEvents(Process process) {
		return (List<Event>) (Object) process.getFlowElements().stream().filter(e -> e instanceof Event)
				.collect(Collectors.toList());
	}
	
	/**
	 * Collects all StartEvents from process.
	 * 
	 * @param process Process, which Events shall get collected.
	 * @return List of StartEvents (List<StartEvent>).
	 */
	@SuppressWarnings("unchecked")
	private List<StartEvent> collectStartEvents(Process process) {
		return (List<StartEvent>) (Object) process.getFlowElements().stream().filter(e -> e instanceof StartEvent)
				.collect(Collectors.toList());
	}

}
