/*******************************************************************************
 * Copyright (c) 2008, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.tests.ui.query;

import java.io.File;
import java.net.URI;
import java.util.Collection;
import org.eclipse.core.tests.harness.CancelingProgressMonitor;
import org.eclipse.equinox.internal.p2.ui.DefaultQueryProvider;
import org.eclipse.equinox.internal.p2.ui.model.AvailableIUElement;
import org.eclipse.equinox.internal.provisional.p2.core.Version;
import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.IUPropertyQuery;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.InstallableUnitQuery;
import org.eclipse.equinox.internal.provisional.p2.metadata.repository.IMetadataRepositoryManager;
import org.eclipse.equinox.internal.provisional.p2.query.Collector;
import org.eclipse.equinox.internal.provisional.p2.repository.IRepository;
import org.eclipse.equinox.internal.provisional.p2.ui.*;
import org.eclipse.equinox.internal.provisional.p2.ui.model.MetadataRepositories;
import org.eclipse.equinox.internal.provisional.p2.ui.policy.IUViewQueryContext;
import org.eclipse.equinox.internal.provisional.p2.ui.policy.Policy;
import org.eclipse.equinox.p2.tests.TestData;

/**
 * Tests for {@link QueryableMetadataRepositoryManager}.
 */
public class QueryableMetadataRepositoryManagerTest extends AbstractQueryTest {
	/**
	 * Tests querying against a non-existent repository
	 */
	public void testBrokenRepository() {
		URI brokenRepo;
		try {
			brokenRepo = TestData.getFile("metadataRepo", "bad").toURI();
		} catch (Exception e) {
			fail("0.99", e);
			return;
		}
		IMetadataRepositoryManager metadataRepositoryManager = getMetadataRepositoryManager();
		metadataRepositoryManager.addRepository(brokenRepo);
		QueryableMetadataRepositoryManager manager = getQueryableManager();
		assertTrue("1.0", !manager.areRepositoriesLoaded());

		manager.loadAll(getMonitor());

		//false because the broken repository is not loaded
		assertTrue("1.1", !manager.areRepositoriesLoaded());
	}

	/**
	 * Tests canceling a load
	 */
	public void testCancelLoad() {
		URI location;
		try {
			location = TestData.getFile("metadataRepo", "good").toURI();
		} catch (Exception e) {
			fail("0.99", e);
			return;
		}
		IMetadataRepositoryManager metadataRepositoryManager = getMetadataRepositoryManager();
		metadataRepositoryManager.addRepository(location);
		QueryableMetadataRepositoryManager manager = getQueryableManager();
		assertTrue("1.0", !manager.areRepositoriesLoaded());

		manager.loadAll(new CancelingProgressMonitor());

		//should not be loaded due to cancelation
		assertTrue("1.1", !manager.areRepositoriesLoaded());
	}

	public void testCancelQuery() {
		URI existing, nonExisting, broken;
		try {
			existing = TestData.getFile("metadataRepo", "good").toURI();
			nonExisting = new File("does/not/exist/testNotFoundRepository").toURI();
			broken = TestData.getFile("metadataRepo", "bad").toURI();
		} catch (Exception e) {
			fail("0.99", e);
			return;
		}
		IMetadataRepositoryManager metadataRepositoryManager = getMetadataRepositoryManager();
		metadataRepositoryManager.addRepository(existing);
		metadataRepositoryManager.addRepository(nonExisting);
		metadataRepositoryManager.addRepository(broken);
		QueryableMetadataRepositoryManager manager = getQueryableManager();

		Collector result = manager.query(new InstallableUnitQuery("test.bundle", new Version(1, 0, 0)), new Collector(), new CancelingProgressMonitor());
		assertEquals("1.0", 0, result.size());

		//null query collects repository URLs
		result = manager.query(null, new Collector(), new CancelingProgressMonitor());
		assertEquals("2.0", 0, result.size());
	}

	public void testExistingRepository() {
		URI location;
		try {
			location = TestData.getFile("metadataRepo", "good").toURI();
		} catch (Exception e) {
			fail("0.99", e);
			return;
		}
		IMetadataRepositoryManager metadataRepositoryManager = getMetadataRepositoryManager();
		metadataRepositoryManager.addRepository(location);
		QueryableMetadataRepositoryManager manager = getQueryableManager();
		assertTrue("1.0", !manager.areRepositoriesLoaded());

		manager.loadAll(getMonitor());

		//we can never be sure that repositories are loaded because the repository manager cache can be flushed at any time
		//		assertTrue("1.1", manager.areRepositoriesLoaded());
	}

	/**
	 * Tests querying against a non-existent repository
	 */
	public void testNotFoundRepository() {
		URI existing, nonExisting;
		try {
			existing = TestData.getFile("metadataRepo", "good").toURI();
			nonExisting = new File("does/not/exist/testNotFoundRepository").toURI();
		} catch (Exception e) {
			fail("0.99", e);
			return;
		}
		IMetadataRepositoryManager metadataRepositoryManager = getMetadataRepositoryManager();
		metadataRepositoryManager.addRepository(existing);
		metadataRepositoryManager.addRepository(nonExisting);
		QueryableMetadataRepositoryManager manager = getQueryableManager();
		// not loaded yet
		assertFalse("1.0", manager.areRepositoriesLoaded());

		manager.loadAll(getMonitor());

		// the repositories have been loaded.  Because the non-existent 
		// repository has been noticed and recorded as missing, it
		// does not count "not loaded."
		assertTrue("1.1", manager.areRepositoriesLoaded());
	}

	public void testQuery() {
		URI existing, nonExisting, broken;
		try {
			existing = TestData.getFile("metadataRepo", "good").toURI();
			nonExisting = new File("does/not/exist/testNotFoundRepository").toURI();
			broken = TestData.getFile("metadataRepo", "bad").toURI();
		} catch (Exception e) {
			fail("0.99", e);
			return;
		}
		IMetadataRepositoryManager metadataRepositoryManager = getMetadataRepositoryManager();
		metadataRepositoryManager.addRepository(existing);
		metadataRepositoryManager.addRepository(nonExisting);
		metadataRepositoryManager.addRepository(broken);
		QueryableMetadataRepositoryManager manager = getQueryableManager();

		Collector result = manager.query(new InstallableUnitQuery("test.bundle", new Version(1, 0, 0)), new Collector(), getMonitor());
		assertEquals("1.0", 1, result.size());
		IInstallableUnit iu = (IInstallableUnit) result.iterator().next();
		assertEquals("1.1", "test.bundle", iu.getId());

		//RepoLocationQuery collects repository URLs
		result = manager.query(new RepositoryLocationQuery(), new Collector(), getMonitor());
		assertEquals("2.0", 3, result.size());
		Collection resultCollection = result.toCollection();
		assertTrue("2.1", resultCollection.contains(existing));
		assertTrue("2.1", resultCollection.contains(nonExisting));
		assertTrue("2.1", resultCollection.contains(broken));

		// null IUPropertyQuery collects all IUs
		result = manager.query(new InstallableUnitQuery(null), new Collector(), getMonitor());
		int iuCount = result.size();
		result = manager.query(new IUPropertyQuery(null, null), new Collector(), getMonitor());
		assertEquals("2.2", iuCount, result.size());
	}

	public void testNonLatestInMultipleRepositories() {
		URI multipleVersion1, multipleVersion2;
		try {
			multipleVersion1 = TestData.getFile("metadataRepo", "multipleversions1").toURI();
			multipleVersion2 = TestData.getFile("metadataRepo", "multipleversions2").toURI();
		} catch (Exception e) {
			fail("0.99", e);
			return;
		}
		IMetadataRepositoryManager metadataRepositoryManager = getMetadataRepositoryManager();
		metadataRepositoryManager.addRepository(multipleVersion1);
		metadataRepositoryManager.addRepository(multipleVersion2);
		QueryableMetadataRepositoryManager manager = getQueryableManager();

		IUViewQueryContext context = new IUViewQueryContext(IUViewQueryContext.AVAILABLE_VIEW_FLAT);
		context.setShowLatestVersionsOnly(false);

		MetadataRepositories rootElement = new MetadataRepositories(context, Policy.getDefault(), manager);
		DefaultQueryProvider queryProvider = new DefaultQueryProvider(Policy.getDefault());
		ElementQueryDescriptor queryDescriptor = queryProvider.getQueryDescriptor(rootElement);
		Collection collection = queryDescriptor.performQuery(null);
		assertEquals("1.0", 5, collection.size());
	}

	public void testLatestInMultipleRepositories() {
		URI multipleVersion1, multipleVersion2;
		try {
			multipleVersion1 = TestData.getFile("metadataRepo", "multipleversions1").toURI();
			multipleVersion2 = TestData.getFile("metadataRepo", "multipleversions2").toURI();
		} catch (Exception e) {
			fail("0.99", e);
			return;
		}
		IMetadataRepositoryManager metadataRepositoryManager = getMetadataRepositoryManager();
		metadataRepositoryManager.addRepository(multipleVersion1);
		metadataRepositoryManager.addRepository(multipleVersion2);
		QueryableMetadataRepositoryManager manager = getQueryableManager();

		IUViewQueryContext context = new IUViewQueryContext(IUViewQueryContext.AVAILABLE_VIEW_FLAT);
		context.setShowLatestVersionsOnly(true);

		MetadataRepositories rootElement = new MetadataRepositories(context, Policy.getDefault(), manager);
		manager.setQueryContext(context);
		DefaultQueryProvider queryProvider = new DefaultQueryProvider(Policy.getDefault());
		ElementQueryDescriptor queryDescriptor = queryProvider.getQueryDescriptor(rootElement);
		Collection collection = queryDescriptor.performQuery(null);
		assertEquals("1.0", 1, collection.size());
		AvailableIUElement next = (AvailableIUElement) collection.iterator().next();
		assertEquals("1.1", new Version(3, 0, 0), next.getIU().getVersion());
	}

	/**
	 * Tests that the repository nickname is set on load.  See bug 274334 for details.
	 */
	public void testNicknameOnLoad() {
		URI location;
		try {
			location = TestData.getFile("metadataRepo", "good").toURI();
		} catch (Exception e) {
			fail("0.99", e);
			return;
		}
		IMetadataRepositoryManager metadataRepositoryManager = getMetadataRepositoryManager();
		metadataRepositoryManager.removeRepository(location);
		metadataRepositoryManager.addRepository(location);
		QueryableMetadataRepositoryManager manager = getQueryableManager();
		manager.loadAll(getMonitor());
		assertEquals("1.0", "Good Test Repository", metadataRepositoryManager.getRepositoryProperty(location, IRepository.PROP_NICKNAME));

	}

	private QueryableMetadataRepositoryManager getQueryableManager() {
		return new QueryableMetadataRepositoryManager(Policy.getDefault().getQueryContext(), false);
	}
}