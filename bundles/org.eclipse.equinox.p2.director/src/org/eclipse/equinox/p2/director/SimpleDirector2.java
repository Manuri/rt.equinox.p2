/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.equinox.p2.director;

import java.net.URL;
import java.util.*;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.p2.director.*;
import org.eclipse.equinox.internal.p2.rollback.FormerState;
import org.eclipse.equinox.p2.core.eventbus.ProvisioningEventBus;
import org.eclipse.equinox.p2.core.helpers.ServiceHelper;
import org.eclipse.equinox.p2.core.location.AgentLocation;
import org.eclipse.equinox.p2.core.repository.IRepositoryInfo;
import org.eclipse.equinox.p2.core.repository.IWritableRepositoryInfo;
import org.eclipse.equinox.p2.engine.Operand;
import org.eclipse.equinox.p2.engine.Profile;
import org.eclipse.equinox.p2.metadata.*;
import org.eclipse.equinox.p2.metadata.repository.*;
import org.eclipse.equinox.p2.resolution.ResolutionHelper;
import org.eclipse.osgi.service.resolver.VersionRange;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.Version;

public class SimpleDirector2 implements IDirector2 {
	static final int ExpandWork = 10;
	static final int OperationWork = 100;

	public static void tagAsImplementation(IWritableMetadataRepository repository) {
		if (repository != null && repository.getProperties().getProperty(IRepositoryInfo.IMPLEMENTATION_ONLY_KEY) == null) {
			IWritableRepositoryInfo writableInfo = (IWritableRepositoryInfo) repository.getAdapter(IWritableRepositoryInfo.class);
			if (writableInfo != null)
				writableInfo.getModifiableProperties().setProperty(IRepositoryInfo.IMPLEMENTATION_ONLY_KEY, Boolean.valueOf(true).toString());
		}
	}

	public SimpleDirector2() {
		URL rollbackLocation = null;
		AgentLocation agentLocation = (AgentLocation) ServiceHelper.getService(DirectorActivator.context, AgentLocation.class.getName());
		rollbackLocation = agentLocation.getTouchpointDataArea("director");
		ProvisioningEventBus eventBus = (ProvisioningEventBus) ServiceHelper.getService(DirectorActivator.context, ProvisioningEventBus.class.getName());
		IMetadataRepositoryManager manager = (IMetadataRepositoryManager) ServiceHelper.getService(DirectorActivator.context, IMetadataRepositoryManager.class.getName());
		IWritableMetadataRepository rollbackRepo = (IWritableMetadataRepository) manager.loadRepository(rollbackLocation, null);
		if (rollbackRepo == null)
			rollbackRepo = (IWritableMetadataRepository) manager.createRepository(rollbackLocation, "Agent rollback repo", "org.eclipse.equinox.p2.metadata.repository.simpleRepository"); //$NON-NLS-1$//$NON-NLS-2$
		if (rollbackRepo == null)
			throw new IllegalStateException("Unable to open or create Agent's rollback repository");
		tagAsImplementation(rollbackRepo);
		new FormerState(eventBus, rollbackRepo);
	}

	//TODO This is really gross!!!!! We need to make things uniform
	private IInstallableUnit[] toArray(Iterator it) {
		ArrayList result = new ArrayList();
		while (it.hasNext()) {
			result.add(it.next());
		}
		return (IInstallableUnit[]) result.toArray(new IInstallableUnit[result.size()]);
	}

	protected IInstallableUnit[] createEntryPointHelper(String entryPointName, IInstallableUnit[] content) {
		if (entryPointName == null)
			return content;
		return new IInstallableUnit[] {createEntryPoint(entryPointName, content)};
	}

	protected InstallableUnit createEntryPoint(String entryPointName, IInstallableUnit[] content) {
		InstallableUnit result = new InstallableUnit();
		result.setId("entry point " + entryPointId(content)); //$NON-NLS-1$
		result.setVersion(new Version(0, 0, 0, Long.toString(System.currentTimeMillis())));
		result.setRequiredCapabilities(IUTransformationHelper.toRequirements(content, false));
		result.setProperty(IInstallableUnitConstants.ENTRYPOINT_IU_KEY, Boolean.TRUE.toString());
		result.setProperty(IInstallableUnitConstants.NAME, entryPointName);
		return result;
	}

	private String entryPointId(IInstallableUnit[] ius) {
		StringBuffer result = new StringBuffer();
		for (int i = 0; i < ius.length; i++) {
			result.append(ius[i].getId());
			if (i < ius.length - 1)
				result.append(", "); //$NON-NLS-1$
		}
		return result.toString();
	}

	public DirectorResult install(IInstallableUnit[] installRoots, Profile profile, String entryPointName, IProgressMonitor monitor) {
		SubMonitor sub = SubMonitor.convert(monitor, ExpandWork + OperationWork);
		sub.setTaskName(Messages.Director_Task_Resolving_Dependencies);
		try {
			MultiStatus result = new MultiStatus(DirectorActivator.PI_DIRECTOR, 1, Messages.Director_Install_Problems, null);
			// Get the list of ius installed in the profile we are installing into
			IInstallableUnit[] alreadyInstalled = toArray(profile.getInstallableUnits());
			//Compute the complete closure of things to install to successfully install the installRoots.
			NewDependencyExpander expander = new NewDependencyExpander(createEntryPointHelper(entryPointName, installRoots), alreadyInstalled, gatherAvailableInstallableUnits(installRoots), profile, true);
			//			NewDependencyExpander expander = new NewDependencyExpander(installRoots, alreadyInstalled, gatherAvailableInstallableUnits(), profile, true);
			IStatus expanderResult = expander.expand(sub.newChild(ExpandWork));
			if (!expanderResult.isOK()) {
				result.merge(expanderResult);
				return new DirectorResult(result);
			}

			ResolutionHelper oldStateHelper = new ResolutionHelper(profile.getSelectionContext(), null);
			Collection oldState = oldStateHelper.attachCUs(Arrays.asList(alreadyInstalled));
			List oldStateOrder = oldStateHelper.getSorted();

			ResolutionHelper newStateHelper = new ResolutionHelper(profile.getSelectionContext(), expander.getRecommendations());
			Collection newState = newStateHelper.attachCUs(expander.getAllInstallableUnits());
			List newStateOrder = newStateHelper.getSorted();

			//TODO Here we need to sort the operations to ensure that the dependents will be treated first (see ensureDependencyOrder)
			sub.setTaskName(NLS.bind(Messages.Director_Task_Installing, entryPointName, profile.getValue(Profile.PROP_INSTALL_FOLDER)));
			return new DirectorResult(Status.OK_STATUS, generateOperations(oldState, newState, oldStateOrder, newStateOrder));
		} finally {
			sub.done();
		}
	}

	private Operand[] generateOperations(Collection fromState, Collection toState, List fromStateOrder, List newStateOrder) {
		return sortOperations(new OperationGenerator().generateOperation(fromState, toState), newStateOrder, fromStateOrder);
	}

	private Operand[] sortOperations(Operand[] toSort, List installOrder, List uninstallOrder) {
		List updateOp = new ArrayList();
		for (int i = 0; i < toSort.length; i++) {
			Operand op = toSort[i];
			if (op.first() == null && op.second() != null) {
				installOrder.set(installOrder.indexOf(op.second()), op);
				continue;
			}
			if (op.first() != null && op.second() == null) {
				uninstallOrder.set(uninstallOrder.indexOf(op.first()), op);
				continue;
			}
			if (op.first() != null && op.second() != null) {
				updateOp.add(op);
				continue;
			}
		}
		int i = 0;
		for (Iterator iterator = installOrder.iterator(); iterator.hasNext();) {
			Object elt = iterator.next();
			if (elt instanceof Operand) {
				toSort[i++] = (Operand) elt;
			}
		}
		for (Iterator iterator = uninstallOrder.iterator(); iterator.hasNext();) {
			Object elt = iterator.next();
			if (elt instanceof Operand) {
				toSort[i++] = (Operand) elt;
			}
		}
		for (Iterator iterator = updateOp.iterator(); iterator.hasNext();) {
			Object elt = iterator.next();
			if (elt instanceof Operand) {
				toSort[i++] = (Operand) elt;
			}
		}
		return toSort;
	}

	public DirectorResult become(IInstallableUnit target, Profile profile, IProgressMonitor monitor) {
		SubMonitor sub = SubMonitor.convert(monitor, OperationWork + ExpandWork);
		sub.setTaskName(Messages.Director_Task_Resolving_Dependencies);
		try {
			MultiStatus result = new MultiStatus(DirectorActivator.PI_DIRECTOR, 1, Messages.Director_Become_Problems, null);

			if (!Boolean.valueOf(target.getProperty(IInstallableUnitConstants.PROFILE_IU_KEY)).booleanValue()) {
				result.add(new Status(IStatus.ERROR, DirectorActivator.PI_DIRECTOR, NLS.bind(Messages.Director_Unexpected_IU, target.getId())));
				return new DirectorResult(result);
			}

			//TODO Here we need to deal with the change of properties between the two profiles
			//Also if the profile changes (locations are being modified, etc), should not we do a full uninstall then an install?
			//Maybe it depends on the kind of changes in a profile
			//We need to get all the ius that were part of the profile and give that to be what to become
			NewDependencyExpander toExpander = new NewDependencyExpander(new IInstallableUnit[] {target}, null, gatherAvailableInstallableUnits(new IInstallableUnit[] {target}), profile, true);
			toExpander.expand(sub.newChild(ExpandWork));
			ResolutionHelper newStateHelper = new ResolutionHelper(profile.getSelectionContext(), toExpander.getRecommendations());
			Collection newState = newStateHelper.attachCUs(toExpander.getAllInstallableUnits());
			newState.remove(target);

			Iterator it = profile.getInstallableUnits();
			Collection oldIUs = new HashSet();
			for (; it.hasNext();) {
				oldIUs.add(it.next());
			}

			ResolutionHelper oldStateHelper = new ResolutionHelper(profile.getSelectionContext(), null);
			Collection oldState = oldStateHelper.attachCUs(oldIUs);
			sub.setTaskName(Messages.Director_Task_Updating);
			return new DirectorResult(Status.OK_STATUS, generateOperations(oldState, newState, oldStateHelper.getSorted(), newStateHelper.getSorted()));
		} finally {
			sub.done();
		}
	}

	private IInstallableUnit[] inProfile(IInstallableUnit[] toFind, Profile profile, boolean found, IProgressMonitor monitor) {
		ArrayList result = new ArrayList(toFind.length);
		for (int i = 0; i < toFind.length; i++) {
			if (profile.query(toFind[i].getId(), new VersionRange(toFind[i].getVersion(), true, toFind[i].getVersion(), true), null, false, monitor).length > 0) {
				if (found)
					result.add(toFind[i]);
			} else {
				if (!found)
					result.add(toFind[i]);
			}
		}
		return (IInstallableUnit[]) result.toArray(new IInstallableUnit[result.size()]);
	}

	public DirectorResult uninstall(IInstallableUnit[] uninstallRoots, Profile profile, IProgressMonitor monitor) {
		SubMonitor sub = SubMonitor.convert(monitor, OperationWork + ExpandWork);
		sub.setTaskName(Messages.Director_Task_Resolving_Dependencies);
		try {
			IInstallableUnit[] toReallyUninstall = inProfile(uninstallRoots, profile, true, sub.newChild(0));
			if (toReallyUninstall.length == 0) {
				return new DirectorResult(new Status(IStatus.OK, DirectorActivator.PI_DIRECTOR, Messages.Director_Nothing_To_Uninstall));
			} else if (toReallyUninstall.length != uninstallRoots.length) {
				uninstallRoots = toReallyUninstall;
			}

			MultiStatus result = new MultiStatus(DirectorActivator.PI_DIRECTOR, 1, Messages.Director_Uninstall_Problems, null);

			IInstallableUnit[] alreadyInstalled = toArray(profile.getInstallableUnits());
			ResolutionHelper oldStateHelper = new ResolutionHelper(profile.getSelectionContext(), null);
			Collection oldState = oldStateHelper.attachCUs(Arrays.asList(alreadyInstalled));

			NewDependencyExpander expander = new NewDependencyExpander(uninstallRoots, new IInstallableUnit[0], alreadyInstalled, profile, true);
			expander.expand(sub.newChild(ExpandWork / 2));
			Collection toUninstallClosure = new ResolutionHelper(profile.getSelectionContext(), null).attachCUs(expander.getAllInstallableUnits());

			Collection remainingIUs = new HashSet(oldState);
			remainingIUs.removeAll(toUninstallClosure);
			NewDependencyExpander finalExpander = new NewDependencyExpander(null, (IInstallableUnit[]) remainingIUs.toArray(new IInstallableUnit[remainingIUs.size()]), gatherAvailableInstallableUnits(uninstallRoots), profile, true);
			finalExpander.expand(sub.newChild(ExpandWork / 2));
			ResolutionHelper newStateHelper = new ResolutionHelper(profile.getSelectionContext(), null);
			Collection newState = newStateHelper.attachCUs(finalExpander.getAllInstallableUnits());

			for (int i = 0; i < uninstallRoots.length; i++) {
				if (newState.contains(uninstallRoots[i]))
					result.add(new Status(IStatus.ERROR, DirectorActivator.PI_DIRECTOR, NLS.bind(Messages.Director_Cannot_Uninstall, uninstallRoots[i])));
			}
			if (!result.isOK())
				return new DirectorResult(result);

			sub.setTaskName(Messages.Director_Task_Uninstalling);
			return new DirectorResult(Status.OK_STATUS, generateOperations(oldState, newState, oldStateHelper.getSorted(), newStateHelper.getSorted()));
		} finally {
			sub.done();
		}
	}

	protected IInstallableUnit[] gatherAvailableInstallableUnits(IInstallableUnit[] additionalSource) {
		IMetadataRepositoryManager repoMgr = (IMetadataRepositoryManager) ServiceHelper.getService(DirectorActivator.context, IMetadataRepositoryManager.class.getName());
		IMetadataRepository[] repos = repoMgr.getKnownRepositories();
		List results = new ArrayList();
		if (additionalSource != null) {
			for (int i = 0; i < additionalSource.length; i++) {
				results.add(additionalSource[i]);
			}
		}

		for (int i = 0; i < repos.length; i++) {
			results.addAll(Arrays.asList(repos[i].getInstallableUnits(null)));
		}
		return (IInstallableUnit[]) results.toArray(new IInstallableUnit[results.size()]);
	}

	public DirectorResult replace(IInstallableUnit[] toUninstall, IInstallableUnit[] toInstall, Profile profile, IProgressMonitor monitor) {
		SubMonitor sub = SubMonitor.convert(monitor, OperationWork + ExpandWork);
		sub.setTaskName(Messages.Director_Task_Resolving_Dependencies);
		try {
			MultiStatus result = new MultiStatus(DirectorActivator.PI_DIRECTOR, 1, Messages.Director_Replace_Problems, null);

			//TODO Need to worry about the entry points

			//find the things being updated in the profile
			IInstallableUnit[] alreadyInstalled = toArray(profile.getInstallableUnits());
			IInstallableUnit[] uninstallRoots = toUninstall;

			//compute the transitive closure and remove them.
			ResolutionHelper oldStateHelper = new ResolutionHelper(profile.getSelectionContext(), null);
			Collection oldState = oldStateHelper.attachCUs(Arrays.asList(alreadyInstalled));

			NewDependencyExpander expander = new NewDependencyExpander(uninstallRoots, new IInstallableUnit[0], alreadyInstalled, profile, true);
			expander.expand(sub.newChild(ExpandWork / 2));
			Collection toUninstallClosure = new ResolutionHelper(profile.getSelectionContext(), null).attachCUs(expander.getAllInstallableUnits());

			//add the new set.
			Collection remainingIUs = new HashSet(oldState);
			remainingIUs.removeAll(toUninstallClosure);
			//		for (int i = 0; i < updateRoots.length; i++) {
			//			remainingIUs.add(updateRoots[i]);
			//		}
			NewDependencyExpander finalExpander = new NewDependencyExpander(toInstall, (IInstallableUnit[]) remainingIUs.toArray(new IInstallableUnit[remainingIUs.size()]), gatherAvailableInstallableUnits(null), profile, true);
			finalExpander.expand(sub.newChild(ExpandWork / 2));
			ResolutionHelper newStateHelper = new ResolutionHelper(profile.getSelectionContext(), null);
			Collection newState = newStateHelper.attachCUs(finalExpander.getAllInstallableUnits());

			sub.setTaskName(Messages.Director_Task_Updating);
			return new DirectorResult(Status.OK_STATUS, generateOperations(oldState, newState, oldStateHelper.getSorted(), newStateHelper.getSorted()));
		} finally {
			sub.done();
		}
	}
}
