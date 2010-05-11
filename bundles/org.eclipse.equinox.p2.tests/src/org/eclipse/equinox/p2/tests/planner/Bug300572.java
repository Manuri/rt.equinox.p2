package org.eclipse.equinox.p2.tests.planner;

import java.util.Iterator;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.p2.core.*;
import org.eclipse.equinox.p2.engine.*;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.planner.*;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;

public class Bug300572 extends AbstractProvisioningTest {
	public void testInstallBabel() throws ProvisionException {
		IProvisioningAgentProvider provider = getAgentProvider();
		IProvisioningAgent agent = provider.createAgent(getTestData("Bug300572 data", "testData/bug300572/p2").toURI());
		IMetadataRepositoryManager repoMgr = (IMetadataRepositoryManager) agent.getService(IMetadataRepositoryManager.SERVICE_NAME);
		IMetadataRepository repo = repoMgr.loadRepository(getTestData("bug300572 data", "testData/bug300572/repo/").toURI(), new NullProgressMonitor());

		IPlanner planner = (IPlanner) agent.getService(IPlanner.SERVICE_NAME);
		IProfile sdkProfile = ((IProfileRegistry) agent.getService(IProfileRegistry.SERVICE_NAME)).getProfile("SDKProfile");
		assertFalse(sdkProfile.query(QueryUtil.createIUQuery("hellopatch.feature.group"), null).isEmpty());
		assertFalse(sdkProfile.query(QueryUtil.createIUQuery("hellofeature.feature.group"), null).isEmpty());

		IProfileChangeRequest request = planner.createChangeRequest(sdkProfile);
		IQueryResult<IInstallableUnit> allIUs = repo.query(QueryUtil.ALL_UNITS, null);
		request.addAll(allIUs.toUnmodifiableSet());
		for (Iterator<IInstallableUnit> allIUsIterator = allIUs.iterator(); allIUsIterator.hasNext();) {
			IInstallableUnit iu = allIUsIterator.next();
			request.setInstallableUnitInclusionRules(iu, ProfileInclusionRules.createOptionalInclusionRule(iu));
		}

		ProvisioningContext pc = new ProvisioningContext(agent);

		IProvisioningPlan plan = planner.getProvisioningPlan(request, pc, new NullProgressMonitor());
		assertOK("plan is not ok", plan.getStatus());
		assertFalse(plan.getAdditions().query(QueryUtil.createIUQuery("hellopatch.feature.group", Version.create("1.0.2.201001211536")), null).isEmpty());
	}
}