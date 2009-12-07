/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.ui;

import org.eclipse.equinox.p2.engine.InstallableUnitOperand;
import org.eclipse.equinox.p2.engine.Operand;

import org.eclipse.equinox.p2.metadata.IInstallableUnit;

import java.util.*;
import java.util.List;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.internal.p2.metadata.License;
import org.eclipse.equinox.internal.p2.ui.ProvUIActivator;
import org.eclipse.equinox.internal.p2.ui.ProvUIMessages;
import org.eclipse.equinox.internal.p2.ui.dialogs.ILayoutConstants;
import org.eclipse.equinox.internal.p2.ui.viewers.IUColumnConfig;
import org.eclipse.equinox.internal.provisional.p2.metadata.ILicense;
import org.eclipse.equinox.p2.common.LicenseManager;
import org.eclipse.equinox.p2.common.TranslationSupport;
import org.eclipse.equinox.p2.engine.IProvisioningPlan;
import org.eclipse.equinox.p2.operations.ProfileChangeOperation;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

/**
 * AcceptLicensesWizardPage shows a list of the IU's that have
 * licenses that have not been approved by the user, and allows the
 * user to approve them.
 * 
 * @since 2.0
 * @noextend This class is not intended to be subclassed by clients.
 */
public class AcceptLicensesWizardPage extends WizardPage {
	private static final String DIALOG_SETTINGS_SECTION = "LicensessPage"; //$NON-NLS-1$
	private static final String LIST_WEIGHT = "ListSashWeight"; //$NON-NLS-1$
	private static final String LICENSE_WEIGHT = "LicenseSashWeight"; //$NON-NLS-1$
	private static final String NAME_COLUMN_WIDTH = "NameColumnWidth"; //$NON-NLS-1$
	private static final String VERSION_COLUMN_WIDTH = "VersionColumnWidth"; //$NON-NLS-1$

	class IUWithLicenseParent {
		IInstallableUnit iu;
		ILicense license;

		IUWithLicenseParent(ILicense license, IInstallableUnit iu) {
			this.license = license;
			this.iu = iu;
		}
	}

	class LicenseContentProvider implements ITreeContentProvider {
		public Object[] getChildren(Object parentElement) {
			if (!(parentElement instanceof ILicense))
				return new Object[0];

			if (licensesToIUs.containsKey(parentElement)) {
				List iusWithLicense = (List) licensesToIUs.get(parentElement);
				IInstallableUnit[] ius = (IInstallableUnit[]) iusWithLicense.toArray(new IInstallableUnit[iusWithLicense.size()]);
				IUWithLicenseParent[] children = new IUWithLicenseParent[ius.length];
				for (int i = 0; i < ius.length; i++) {
					children[i] = new IUWithLicenseParent((ILicense) parentElement, ius[i]);
				}
				return children;
			}
			return null;
		}

		public Object getParent(Object element) {
			if (element instanceof IUWithLicenseParent) {
				return ((IUWithLicenseParent) element).license;
			}
			return null;
		}

		public boolean hasChildren(Object element) {
			return licensesToIUs.containsKey(element);
		}

		public Object[] getElements(Object inputElement) {
			return licensesToIUs.keySet().toArray();
		}

		public void dispose() {
			// Nothing to do
		}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			// Nothing to do
		}
	}

	class LicenseLabelProvider extends LabelProvider {
		public Image getImage(Object element) {
			return null;
		}

		public String getText(Object element) {
			if (element instanceof License) {
				return getFirstLine(((License) element).getBody());
			} else if (element instanceof IUWithLicenseParent) {
				return getIUName(((IUWithLicenseParent) element).iu);
			} else if (element instanceof IInstallableUnit) {
				return getIUName((IInstallableUnit) element);
			}
			return ""; //$NON-NLS-1$
		}

		private String getFirstLine(String body) {
			int i = body.indexOf('\n');
			int j = body.indexOf('\r');
			if (i > 0) {
				if (j > 0)
					return body.substring(0, i < j ? i : j);
				return body.substring(0, i);
			} else if (j > 0) {
				return body.substring(0, j);
			}
			return body;
		}
	}

	TreeViewer iuViewer;
	Text licenseTextBox;
	Button acceptButton;
	Button declineButton;
	SashForm sashForm;
	private IInstallableUnit[] originalIUs;
	HashMap licensesToIUs; // License -> IU Name
	private LicenseManager manager;
	private TranslationSupport translations;
	IUColumnConfig nameColumn;
	IUColumnConfig versionColumn;

	static String getIUName(IInstallableUnit iu) {
		StringBuffer buf = new StringBuffer();
		String name = ProvisioningUI.getDefaultUI().getTranslationSupport().getIUProperty(iu, IInstallableUnit.PROP_NAME);
		if (name != null)
			buf.append(name);
		else
			buf.append(iu.getId());
		buf.append(" "); //$NON-NLS-1$
		buf.append(iu.getVersion().toString());
		return buf.toString();
	}

	/**
	 * Create a license acceptance page for showing licenses to the user.
	 * 
	 * @param manager the license manager that should be used to check for already accepted licenses.  May be <code>null</code>.
	 * @param translationSupport the translation support to use for retrieving localized licenses and other properties
	 * @param ius the IInstallableUnits for which licenses should be checked
	 * @param operation the provisioning operation describing what changes are to take place on the profile
	 */
	public AcceptLicensesWizardPage(LicenseManager manager, TranslationSupport translationSupport, IInstallableUnit[] ius, ProfileChangeOperation operation) {
		super("AcceptLicenses"); //$NON-NLS-1$
		setTitle(ProvUIMessages.AcceptLicensesWizardPage_Title);
		this.manager = manager;
		this.translations = translationSupport;
		update(ius, operation);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		initializeDialogUnits(parent);
		ArrayList ius;
		if (licensesToIUs == null || licensesToIUs.size() == 0) {
			Label label = new Label(parent, SWT.NONE);
			setControl(label);
		} else if (licensesToIUs.size() == 1 && (ius = (ArrayList) (licensesToIUs.values().iterator().next())).size() == 1) {
			createLicenseContentSection(parent, (IInstallableUnit) ius.get(0));
		} else {
			sashForm = new SashForm(parent, SWT.HORIZONTAL);
			sashForm.setLayout(new GridLayout());
			GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
			sashForm.setLayoutData(gd);

			createLicenseListSection(sashForm);
			createLicenseContentSection(sashForm, null);
			sashForm.setWeights(getSashWeights());
			setControl(sashForm);
		}
		Dialog.applyDialogFont(getControl());
	}

	private void createLicenseListSection(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		composite.setLayout(layout);
		GridData gd = new GridData(GridData.FILL_BOTH);
		composite.setLayoutData(gd);

		Label label = new Label(composite, SWT.NONE);
		label.setText(ProvUIMessages.AcceptLicensesWizardPage_ItemsLabel);
		iuViewer = new TreeViewer(composite, SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		iuViewer.setContentProvider(new LicenseContentProvider());
		iuViewer.setLabelProvider(new LicenseLabelProvider());
		iuViewer.setComparator(new ViewerComparator());
		iuViewer.setInput(licensesToIUs);

		iuViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				handleSelectionChanged((IStructuredSelection) event.getSelection());
			}

		});
		gd = new GridData(GridData.FILL_BOTH);
		gd.widthHint = convertWidthInCharsToPixels(ILayoutConstants.DEFAULT_PRIMARY_COLUMN_WIDTH);
		gd.heightHint = convertHeightInCharsToPixels(ILayoutConstants.DEFAULT_TABLE_HEIGHT);
		iuViewer.getControl().setLayoutData(gd);
	}

	private void createLicenseAcceptSection(Composite parent, boolean multiple) {
		// Buttons for accepting licenses
		Composite buttonContainer = new Composite(parent, SWT.NULL);
		GridData gd = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		buttonContainer.setLayout(new GridLayout());
		buttonContainer.setLayoutData(gd);

		acceptButton = new Button(buttonContainer, SWT.RADIO);
		if (multiple)
			acceptButton.setText(ProvUIMessages.AcceptLicensesWizardPage_AcceptMultiple);
		else
			acceptButton.setText(ProvUIMessages.AcceptLicensesWizardPage_AcceptSingle);

		acceptButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				setPageComplete(acceptButton.getSelection());
			}
		});
		declineButton = new Button(buttonContainer, SWT.RADIO);
		if (multiple)
			declineButton.setText(ProvUIMessages.AcceptLicensesWizardPage_RejectMultiple);
		else
			declineButton.setText(ProvUIMessages.AcceptLicensesWizardPage_RejectSingle);
		declineButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				setPageComplete(!declineButton.getSelection());
			}
		});

		acceptButton.setSelection(false);
		declineButton.setSelection(true);
	}

	private void createLicenseContentSection(Composite parent, IInstallableUnit singleIU) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		composite.setLayout(layout);
		GridData gd = new GridData(GridData.FILL_BOTH);
		composite.setLayoutData(gd);

		Label label = new Label(composite, SWT.NONE);
		if (singleIU == null)
			label.setText(ProvUIMessages.AcceptLicensesWizardPage_LicenseTextLabel);
		else
			label.setText(NLS.bind(ProvUIMessages.AcceptLicensesWizardPage_SingleLicenseTextLabel, getIUName(singleIU)));
		licenseTextBox = new Text(composite, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.WRAP | SWT.READ_ONLY);
		licenseTextBox.setBackground(licenseTextBox.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
		initializeDialogUnits(licenseTextBox);
		gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd.heightHint = convertHeightInCharsToPixels(ILayoutConstants.DEFAULT_TABLE_HEIGHT);
		gd.widthHint = convertWidthInCharsToPixels(ILayoutConstants.DEFAULT_COLUMN_WIDTH);
		licenseTextBox.setLayoutData(gd);

		createLicenseAcceptSection(composite, licensesToIUs.size() > 1);

		if (singleIU != null) {
			String licenseBody = ""; //$NON-NLS-1$
			// We've already established before calling this method that it's a single IU with a single license
			ILicense[] licenses = translations.getLicenses(singleIU);
			if (licenses.length > 0 && licenses[0].getBody() != null) {
				licenseBody = licenses[0].getBody();
			}
			licenseTextBox.setText(licenseBody);
		}
		setControl(composite);
	}

	void handleSelectionChanged(IStructuredSelection selection) {
		if (!selection.isEmpty()) {
			Object selected = selection.getFirstElement();
			if (selected instanceof License)
				licenseTextBox.setText(((License) selected).getBody());
			else if (selected instanceof IUWithLicenseParent)
				licenseTextBox.setText(((IUWithLicenseParent) selected).license.getBody());
		}
	}

	/**
	 * The wizard is finishing.  Perform any necessary processing.
	 * 
	 * @return <code>true</code> if the finish can proceed, 
	 * <code>false</code> if it should not.
	 */
	public boolean performFinish() {
		rememberAcceptedLicenses();
		return true;
	}

	/**
	 * Return a boolean indicating whether there are licenses that must be accepted
	 * by the user.
	 * 
	 * @return <code>true</code> if there are licenses that must be accepted, and
	 * <code>false</code> if there are no licenses that must be accepted.
	 */
	public boolean hasLicensesToAccept() {
		return licensesToIUs != null && licensesToIUs.size() > 0;
	}

	/**
	 * Update the current page to show the licenses that must be approved for the
	 * selected IUs and the provisioning plan
	 * 
	 * @param theIUs the installable units to be installed for which licenses must be checked
	 * @param plan the provisioning plan for the install operation
	 * @deprecated use {@link #update(IInstallableUnit[], ProfileChangeOperation)}
	 */

	public void updateForPlan(IInstallableUnit[] theIUs, IProvisioningPlan plan) {
		updateLicenses(theIUs, plan);
	}

	private void updateLicenses(IInstallableUnit[] theIUs, IProvisioningPlan plan) {
		this.originalIUs = theIUs;
		if (theIUs == null)
			licensesToIUs = new HashMap();
		else
			findUnacceptedLicenses(theIUs, plan);
		setDescription();
		setPageComplete(licensesToIUs.size() == 0);
		if (getControl() != null) {
			Composite parent = getControl().getParent();
			getControl().dispose();
			createControl(parent);
			parent.layout(true);
		}
	}

	/**
	 * Update the page for the specified IInstallableUnits and operation.
	 * 
	 * @param theIUs the IInstallableUnits for which licenses should be checked
	 * @param operation the operation describing the pending profile change
	 */
	public void update(IInstallableUnit[] theIUs, ProfileChangeOperation operation) {
		if (operation != null && operation.hasResolved()) {
			int sev = operation.getResolutionResult().getSeverity();
			if (sev != IStatus.ERROR && sev != IStatus.CANCEL) {
				updateLicenses(theIUs, operation.getProvisioningPlan());
			} else {
				updateLicenses(new IInstallableUnit[0], null);
			}
		}
	}

	private void findUnacceptedLicenses(IInstallableUnit[] selectedIUs, IProvisioningPlan plan) {
		IInstallableUnit[] iusToCheck = selectedIUs;
		if (plan != null) {
			List allIUs = new ArrayList();
			Operand[] operands = plan.getOperands();
			for (int i = 0; i < operands.length; i++)
				if (operands[i] instanceof InstallableUnitOperand) {
					IInstallableUnit addedIU = ((InstallableUnitOperand) operands[i]).second();
					if (addedIU != null)
						allIUs.add(addedIU);
				}
			iusToCheck = (IInstallableUnit[]) allIUs.toArray(new IInstallableUnit[allIUs.size()]);
		}

		// See https://bugs.eclipse.org/bugs/show_bug.cgi?id=218532
		// Current metadata generation can result with a feature group IU and the feature jar IU
		// having the same name and license.  We will weed out duplicates if the license and name are both
		// the same.  
		licensesToIUs = new HashMap();//map of License->ArrayList of IUs with that license
		HashMap namesSeen = new HashMap(); // map of License->HashSet of names with that license
		for (int i = 0; i < iusToCheck.length; i++) {
			IInstallableUnit iu = iusToCheck[i];
			ILicense[] licenses = translations.getLicenses(iu);
			for (int k = 0; k < licenses.length; k++) {
				ILicense license = licenses[k];
				if (manager != null && !manager.isAccepted(license)) {
					String name = translations.getIUProperty(iu, IInstallableUnit.PROP_NAME);
					if (name == null)
						name = iu.getId();
					// Have we already found this license?  
					if (licensesToIUs.containsKey(license)) {
						HashSet names = (HashSet) namesSeen.get(license);
						if (!names.contains(name)) {
							names.add(name);
							((ArrayList) licensesToIUs.get(license)).add(iu);
						}
					} else {
						ArrayList list = new ArrayList(1);
						list.add(iu);
						licensesToIUs.put(license, list);
						HashSet names = new HashSet(1);
						names.add(name);
						namesSeen.put(license, names);
					}
				}
			}
		}
	}

	private void rememberAcceptedLicenses() {
		if (licensesToIUs == null || manager == null)
			return;
		Iterator iter = licensesToIUs.keySet().iterator();
		while (iter.hasNext()) {
			License license = (License) iter.next();
			manager.accept(license);
		}
	}

	private void setDescription() {
		// No licenses but the page is open.  Shouldn't happen, but just in case...
		if (licensesToIUs == null || licensesToIUs.size() == 0)
			setDescription(ProvUIMessages.AcceptLicensesWizardPage_NoLicensesDescription);
		// We have licenses.  Use a generic message if we think we aren't showing extra
		// licenses from required IU's.  This check is not entirely accurate, for example
		// one root IU could have no license and the next one has two different
		// IU's with different licenses.  But this cheaply catches the common cases.
		else if (licensesToIUs.size() <= originalIUs.length)
			setDescription(ProvUIMessages.AcceptLicensesWizardPage_ReviewLicensesDescription);
		else {
			// Without a doubt we know we are showing extra licenses.
			setDescription(ProvUIMessages.AcceptLicensesWizardPage_ReviewExtraLicensesDescription);
		}
	}

	private String getDialogSettingsName() {
		return getWizard().getClass().getName() + "." + DIALOG_SETTINGS_SECTION; //$NON-NLS-1$
	}

	/**
	 * Save any settings related to the current size and location of the wizard page.
	 */
	public void saveBoundsRelatedSettings() {
		if (iuViewer == null || iuViewer.getTree().isDisposed())
			return;
		IDialogSettings settings = ProvUIActivator.getDefault().getDialogSettings();
		IDialogSettings section = settings.getSection(getDialogSettingsName());
		if (section == null) {
			section = settings.addNewSection(getDialogSettingsName());
		}
		section.put(NAME_COLUMN_WIDTH, iuViewer.getTree().getColumn(0).getWidth());
		section.put(VERSION_COLUMN_WIDTH, iuViewer.getTree().getColumn(1).getWidth());

		if (sashForm == null || sashForm.isDisposed())
			return;
		int[] weights = sashForm.getWeights();
		section.put(LIST_WEIGHT, weights[0]);
		section.put(LICENSE_WEIGHT, weights[1]);
	}

	private int[] getSashWeights() {
		IDialogSettings settings = ProvUIActivator.getDefault().getDialogSettings();
		IDialogSettings section = settings.getSection(getDialogSettingsName());
		if (section != null) {
			try {
				int[] weights = new int[2];
				if (section.get(LIST_WEIGHT) != null) {
					weights[0] = section.getInt(LIST_WEIGHT);
					if (section.get(LICENSE_WEIGHT) != null) {
						weights[1] = section.getInt(LICENSE_WEIGHT);
						return weights;
					}
				}
			} catch (NumberFormatException e) {
				// Ignore if there actually was a value that didn't parse.  
			}
		}
		return new int[] {55, 45};
	}
}
