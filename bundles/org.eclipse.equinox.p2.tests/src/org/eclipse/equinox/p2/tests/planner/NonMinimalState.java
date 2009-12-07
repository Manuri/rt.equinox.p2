/*******************************************************************************
 *  Copyright (c) 2005, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *      IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.tests.planner;

import java.io.File;
import java.net.URI;
import java.util.*;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.p2.engine.SimpleProfileRegistry;
import org.eclipse.equinox.internal.provisional.p2.director.IPlanner;
import org.eclipse.equinox.internal.provisional.p2.director.ProfileChangeRequest;
import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnitFragment;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.Collector;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.InstallableUnitQuery;
import org.eclipse.equinox.p2.engine.*;
import org.eclipse.equinox.p2.engine.query.IUProfilePropertyQuery;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;

public class NonMinimalState extends AbstractProvisioningTest {
	IProfile profile = null;
	IMetadataRepository repo = null;
	private String searchedId;
	private Set visited = new HashSet();

	protected void setUp() throws Exception {
		super.setUp();
		File reporegistry1 = getTestData("Non Minimal state", "testData/nonMinimalState/");
		File tempFolder = getTempFolder();
		copy("0.2", reporegistry1, tempFolder);
		SimpleProfileRegistry registry = new SimpleProfileRegistry(tempFolder, null, false);
		profile = registry.getProfile("NonMinimalState");
		getMetadataRepositoryManager().addRepository(getTestData("nonMinimalState-galileoM7", "testData/galileoM7/").toURI());
		assertNotNull(profile);
	}

	public void testValidateProfileWithRepository() {
		IPlanner planner = createPlanner();
		ProfileChangeRequest request = new ProfileChangeRequest(profile);
		IProvisioningPlan plan = planner.getProvisioningPlan(request, null, new NullProgressMonitor());
		assertOK("Plan OK", plan.getStatus());
		assertEquals(0, plan.getAdditions().query(new InstallableUnitQuery("org.eclipse.tptp.platform.agentcontroller"), new Collector(), null).size());
		why("slf4j.api");
		why("slf4j.jcl");
		why("org.eclipse.tptp.platform.iac.administrator");
		why("org.eclipse.tptp.platform.agentcontroller");
	}

	public void testValidateProfileWithoutRepo() {
		IPlanner planner = createPlanner();
		ProfileChangeRequest request = new ProfileChangeRequest(profile);
		ProvisioningContext ctx = new ProvisioningContext(new URI[0]);
		IProvisioningPlan plan = planner.getProvisioningPlan(request, ctx, new NullProgressMonitor());
		assertOK("Plan OK", plan.getStatus());
		assertEquals(0, plan.getAdditions().query(new InstallableUnitQuery("org.eclipse.tptp.platform.agentcontroller"), new Collector(), null).size());
	}

	private void why(String id) {
		System.out.println("=-=-=" + id + "=-=-=");
		visited = new HashSet();
		Collector roots = profile.query(new IUProfilePropertyQuery("org.eclipse.equinox.p2.type.root", "true"), new Collector(), null);
		searchedId = id;
		for (Iterator iterator = roots.iterator(); iterator.hasNext();) {
			IInstallableUnit type = (IInstallableUnit) iterator.next();
			if (type instanceof IInstallableUnitFragment) {
				visited.add(type);
				continue;
			}
			if (processIU(type))
				return;
		}
	}

	public boolean processIU(IInstallableUnit iu) {
		if (iu.getId().equals("toolingorg.eclipse.equinox.launcher") || iu.getId().equals("tooling.osgi.bundle.default") || iu.getId().startsWith("tooling")) {
			//		if (iu instanceof IInstallableUnitFragment) {
			visited.add(iu);
			return false;
		}
		IRequirement[] caps = iu.getRequiredCapabilities();
		for (int i = 0; i < caps.length; i++) {
			boolean result = expandRequirement(iu, caps[i]);
			if (result) {
				System.out.println(iu + " because " + caps[i].toString());
				return true;
			}
		}
		return false;
	}

	private boolean expandRequirement(IInstallableUnit iu, IRequirement req) {
		Collector matches = profile.query(req.getMatches(), new Collector(), null);
		for (Iterator iterator = matches.iterator(); iterator.hasNext();) {
			IInstallableUnit match = (IInstallableUnit) iterator.next();
			if (match.getId().equals(searchedId))
				return true;
			if (!visited.contains(match)) {
				visited.add(match);
				if (processIU(match))
					return true;
			}
		}
		return false;
	}
}
