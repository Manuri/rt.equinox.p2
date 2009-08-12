/*******************************************************************************
 *  Copyright (c) 2008, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.touchpoint.eclipse.actions;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.internal.p2.touchpoint.eclipse.EclipseTouchpoint;
import org.eclipse.equinox.internal.p2.touchpoint.eclipse.Util;
import org.eclipse.equinox.internal.provisional.p2.engine.IProfile;
import org.eclipse.equinox.internal.provisional.p2.engine.ProvisioningAction;
import org.eclipse.equinox.internal.provisional.p2.metadata.IArtifactKey;
import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.InstallableUnitQuery;
import org.eclipse.equinox.internal.provisional.p2.query.Collector;

/**
 * This action collects the set of bundle files on which the signature trust check
 * should be performed. The actual trust checking is done by the CheckTrust phase.
 */
public class CheckTrustAction extends ProvisioningAction {

	public static final String ID = "checkTrust"; //$NON-NLS-1$

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.provisional.p2.engine.ProvisioningAction#execute(java.util.Map)
	 */
	public IStatus execute(Map parameters) {
		IInstallableUnit iu = (IInstallableUnit) parameters.get(EclipseTouchpoint.PARM_IU);
		if (iu == null)
			return null;
		IProfile profile = (IProfile) parameters.get(ActionConstants.PARM_PROFILE);
		//if the IU is already in the profile there is nothing to do
		if (!profile.available(new InstallableUnitQuery(iu.getId(), iu.getVersion()), new Collector(), null).isEmpty())
			return null;
		Collection bundleFiles = (Collection) parameters.get(ActionConstants.PARM_ARTIFACT_FILES);
		IArtifactKey[] artifacts = iu.getArtifacts();
		if (artifacts == null)
			return null;
		for (int i = 0; i < artifacts.length; i++) {
			File bundleFile = Util.getArtifactFile(artifacts[i], profile);
			if (!bundleFiles.contains(bundleFile))
				bundleFiles.add(bundleFile);
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.internal.provisional.p2.engine.ProvisioningAction#undo(java.util.Map)
	 */
	public IStatus undo(Map parameters) {
		return Status.OK_STATUS;
	}
}