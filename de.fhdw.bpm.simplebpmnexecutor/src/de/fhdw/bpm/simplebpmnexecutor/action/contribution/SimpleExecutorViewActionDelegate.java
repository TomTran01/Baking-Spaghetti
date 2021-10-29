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

	/**
	 * Runs the BPMN-Process from Start (StartEvent) to End (EndEvent)
	 * @param process The Process, which shall be run.
	 */
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
				active.remove(current);
				trigger(tokens, active, current);
			} while (!(current instanceof EndEvent));

			System.out.println("------------------------END---------------------------------");

		}

	}

	/**
	 * Triggers the current FlowNode if possible.
	 * @param tokens List of all SequenceFlow elements, which currently have a Token.
	 * @param active List of all FlowNodes, which shall get triggered next.
	 * @param current Current FlowNode, which shall get triggered.
	 */
	private void trigger(List<SequenceFlow> tokens, List<FlowNode> active, FlowNode current) {
		List<SequenceFlow> incomings = current.getIncoming();
		
		if (current instanceof Activity || current instanceof StartEvent || 
			current instanceof EndEvent || current instanceof IntermediateThrowEvent ||
			current instanceof ParallelGateway && tokens.containsAll(incomings)) {
			triggerDefault(tokens, active, current);
		} else if (current instanceof ExclusiveGateway) {
			triggerXOR(tokens, active, current);
		}
		
	}

	/**
	 * Triggers the Action of an Exclusive-Gateway.
	 * @param tokens List of all SequenceFlow elements, which currently have a Token.
	 * @param active List of all FlowNodes, which shall get triggered next.
	 * @param current Current Exclusive Gateway, which shall get triggered.
	 */
	private void triggerXOR(List<SequenceFlow> tokens, List<FlowNode> active, FlowNode current) {
		List<SequenceFlow> incomings = current.getIncoming();
		List<SequenceFlow> outgoings = current.getOutgoing();
		List<SequenceFlow> tokenFlows = incomings.stream().filter(flow -> tokens.contains(flow))
				.collect(Collectors.toList());

		for (SequenceFlow flow : tokenFlows) {
			tokens.remove(flow);
			Integer rand = new Random().nextInt(outgoings.size());
			tokens.add(outgoings.get(rand));
			active.add(outgoings.get(rand).getTargetRef());
			printCurrentNode(current);
		}
	}

	/**
	 * The default way of how FlowNodes like Events and Tasks trigger. 
	 * All tokens of the incoming SequenceFlows of the current FlowNode will be consumed and get produced on to all outgoing SequenceFlows.
	 * @param tokens List of all SequenceFlow elements, which currently have a Token.
	 * @param active List of all FlowNodes, which shall get triggered next.
	 * @param current Current FlowNode, which shall get triggered.
	 */
	private void triggerDefault(List<SequenceFlow> tokens, List<FlowNode> active, FlowNode current) {
		List<SequenceFlow> incomings = current.getIncoming();

		tokens.removeAll(incomings);
		tokens.addAll(current.getOutgoing());
		addNextActives(active, current);
		printCurrentNode(current);
	}

	/***
	 * Adds all directly following FlowNodes of the current FlowNode to the Active-List. 
	 * 
	 * @param active List of all FlowNodes, which shall get triggered next
	 * @param current Current FlowNode, which shall get triggered.
	 */
	private void addNextActives(List<FlowNode> active, FlowNode current) {
		current.getOutgoing().stream().forEach(flow -> active.add(flow.getTargetRef()));
	}

	/***
	 * Prints the current FlowNode onto the console.
	 * 
	 * @param current FlowNode, which shall get printed onto the console.
	 */
	private void printCurrentNode(FlowNode current) {
		System.out.println("[" + " ID: " + current.getId() + " " + current.getName() + "]" + " got executed.");
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
