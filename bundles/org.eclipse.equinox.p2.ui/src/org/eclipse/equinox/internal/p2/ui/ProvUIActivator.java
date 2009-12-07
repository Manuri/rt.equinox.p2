/*******************************************************************************
 *  Copyright (c) 2007, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.ui;

import org.eclipse.equinox.p2.engine.IProfileRegistry;

import java.net.URL;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.equinox.internal.p2.core.helpers.ServiceHelper;
import org.eclipse.equinox.internal.provisional.p2.core.eventbus.IProvisioningEventBus;
import org.eclipse.equinox.p2.common.TranslationSupport;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.operations.ProvisioningSession;
import org.eclipse.equinox.p2.ui.Policy;
import org.eclipse.equinox.p2.ui.ProvisioningUI;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.statushandlers.StatusManager;
import org.osgi.framework.*;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * Controls the lifecycle of the provisioning UI bundle
 * 
 * @since 3.4
 */
public class ProvUIActivator extends AbstractUIPlugin {
	private static BundleContext context;
	private static PackageAdmin packageAdmin = null;
	private static ServiceReference packageAdminRef = null;
	private static ProvUIActivator plugin;
	public static final String PLUGIN_ID = "org.eclipse.equinox.p2.ui"; //$NON-NLS-1$

	private ProvisioningSession session;
	private ProvisioningUI ui;
	private TranslationSupport translationSupport;

	public static BundleContext getContext() {
		return context;
	}

	/**
	 * Returns the singleton plugin instance
	 * 
	 * @return the instance
	 */
	public static ProvUIActivator getDefault() {
		return plugin;
	}

	public static Bundle getBundle(String symbolicName) {
		if (packageAdmin == null)
			return null;
		Bundle[] bundles = packageAdmin.getBundles(symbolicName, null);
		if (bundles == null)
			return null;
		// Return the first bundle that is not installed or uninstalled
		for (int i = 0; i < bundles.length; i++) {
			if ((bundles[i].getState() & (Bundle.INSTALLED | Bundle.UNINSTALLED)) == 0) {
				return bundles[i];
			}
		}
		return null;
	}

	public ProvUIActivator() {
		// do nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext bundleContext) throws Exception {
		super.start(bundleContext);

		plugin = this;
		ProvUIActivator.context = bundleContext;
		packageAdminRef = bundleContext.getServiceReference(PackageAdmin.class.getName());
		packageAdmin = (PackageAdmin) bundleContext.getService(packageAdminRef);

		initializeProvisioningUI();
	}

	private void initializeProvisioningUI() {
		// TODO for now we need to manually start up the provisioning infrastructure
		// because the Eclipse Application launch config won't let me specify bundles to start.
		try {
			getBundle("org.eclipse.equinox.p2.exemplarysetup").start(Bundle.START_TRANSIENT); //$NON-NLS-1$
		} catch (BundleException e) {
			ProvUI.handleException(e, "Error initializing provisioning UI", StatusManager.LOG); //$NON-NLS-1$
		}
		IProvisioningAgent agent = (IProvisioningAgent) ServiceHelper.getService(getContext(), IProvisioningAgent.class.getName());
		session = new ProvisioningSession(agent);

		Policy policy = (Policy) ServiceHelper.getService(ProvUIActivator.getContext(), Policy.class.getName());
		if (policy == null)
			policy = new Policy();

		ui = new ProvisioningUI(session, IProfileRegistry.SELF, policy);
	}

	public void stop(BundleContext bundleContext) throws Exception {
		try {
			plugin = null;
			ProvUIActivator.context = null;
			ui = null;
		} finally {
			super.stop(bundleContext);
		}
	}

	public void addProvisioningListener(ProvUIProvisioningListener listener) {
		getProvisioningEventBus().addListener(listener);
	}

	public IProvisioningEventBus getProvisioningEventBus() {
		ServiceReference busReference = context.getServiceReference(IProvisioningEventBus.SERVICE_NAME);
		if (busReference == null)
			return null;
		return (IProvisioningEventBus) context.getService(busReference);
	}

	public void removeProvisioningListener(ProvUIProvisioningListener listener) {
		getProvisioningEventBus().removeListener(listener);
	}

	protected void initializeImageRegistry(ImageRegistry reg) {
		createImageDescriptor(ProvUIImages.IMG_METADATA_REPOSITORY, reg);
		createImageDescriptor(ProvUIImages.IMG_ARTIFACT_REPOSITORY, reg);
		createImageDescriptor(ProvUIImages.IMG_IU, reg);
		createImageDescriptor(ProvUIImages.IMG_DISABLED_IU, reg);
		createImageDescriptor(ProvUIImages.IMG_UPDATED_IU, reg);
		createImageDescriptor(ProvUIImages.IMG_CATEGORY, reg);
		createImageDescriptor(ProvUIImages.IMG_PROFILE, reg);
		createImageDescriptor(ProvUIImages.WIZARD_BANNER_INSTALL, reg);
		createImageDescriptor(ProvUIImages.WIZARD_BANNER_REVERT, reg);
		createImageDescriptor(ProvUIImages.WIZARD_BANNER_UNINSTALL, reg);
		createImageDescriptor(ProvUIImages.WIZARD_BANNER_UPDATE, reg);
	}

	/**
	 * Creates the specified image descriptor and registers it
	 */
	private void createImageDescriptor(String id, ImageRegistry reg) {
		URL url = FileLocator.find(getBundle(), new Path(ProvUIImages.ICON_PATH + id), null);
		ImageDescriptor desc = ImageDescriptor.createFromURL(url);
		reg.put(id, desc);
	}

	public ProvisioningUI getProvisioningUI() {
		return ui;
	}

	public ProvisioningSession getSession() {
		return session;
	}

	public TranslationSupport getTranslationSupport() {
		if (translationSupport == null)
			translationSupport = new TranslationSupport();
		return translationSupport;
	}
}
