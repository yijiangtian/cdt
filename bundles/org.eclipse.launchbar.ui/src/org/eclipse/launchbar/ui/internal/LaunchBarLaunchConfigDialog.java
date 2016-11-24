package org.eclipse.launchbar.ui.internal;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchMode;
import org.eclipse.debug.internal.ui.launchConfigurations.LaunchConfigurationPresentationManager;
import org.eclipse.debug.ui.ILaunchConfigurationTab;
import org.eclipse.debug.ui.ILaunchConfigurationTabGroup;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.operation.ModalContext;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.wizard.ProgressMonitorPart;
import org.eclipse.launchbar.core.ILaunchDescriptor;
import org.eclipse.launchbar.core.target.ILaunchTarget;
import org.eclipse.launchbar.ui.ILaunchBarLaunchConfigDialog;
import org.eclipse.launchbar.ui.ILaunchBarUIManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

public class LaunchBarLaunchConfigDialog extends TitleAreaDialog implements ILaunchBarLaunchConfigDialog {

	private final ILaunchConfigurationWorkingCopy workingCopy;
	private final ILaunchDescriptor descriptor;
	private final ILaunchMode mode;
	private final ILaunchTarget target;

	private ILaunchConfigurationTabGroup group;
	private CTabFolder tabFolder;
	private CTabItem lastSelection;
	private ProgressMonitorPart pmPart;
	private boolean initing;

	public LaunchBarLaunchConfigDialog(Shell shell, ILaunchConfigurationWorkingCopy workingCopy,
			ILaunchDescriptor descriptor, ILaunchMode mode, ILaunchTarget target) {
		super(shell);
		this.workingCopy = workingCopy;
		this.descriptor = descriptor;
		this.mode = mode;
		this.target = target;
		setShellStyle(getShellStyle() | SWT.RESIZE);
	}

	@Override
	protected int getDialogBoundsStrategy() {
		// Don't persist the size since it'll be different for every config
		return DIALOG_PERSISTLOCATION;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		initing = true;

		// create the top level composite for the dialog area
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.verticalSpacing = 0;
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		composite.setFont(parent.getFont());

		getShell().setText(Messages.LaunchBarLaunchConfigDialog_EditConfiguration);
		boolean supportsTargets = true;
		try {
			supportsTargets = descriptor.getType().supportsTargets();
		} catch (CoreException e) {
			Activator.log(e);
		}

		try {
			ILaunchBarUIManager uiManager = Activator.getService(ILaunchBarUIManager.class);
			ILabelProvider labelProvider = uiManager.getLabelProvider(descriptor);
			String descName = labelProvider != null ? labelProvider.getText(descriptor) : descriptor.getName();
			if (supportsTargets) {
				setTitle(String.format(Messages.LaunchBarLaunchConfigDialog_Edit2, descName, mode.getLabel(), target.getId()));
			} else {
				setTitle(String.format(Messages.LaunchBarLaunchConfigDialog_Edit1, descName, mode.getLabel()));
			}
		} catch (CoreException e) {
			Activator.log(e);
		}

		setMessage(Messages.LaunchBarLaunchConfigDialog_SetParameters);

		tabFolder = new CTabFolder(composite, SWT.NONE);
		tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tabFolder.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				CTabItem selItem = tabFolder.getSelection();
				if (selItem != null) {
					selItem.getControl().setFocus();
				}
			}
		});
		tabFolder.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ILaunchConfigurationTab oldTab = (ILaunchConfigurationTab) lastSelection.getData();
				oldTab.deactivated(workingCopy);

				CTabItem selItem = tabFolder.getSelection();
				ILaunchConfigurationTab newTab = (ILaunchConfigurationTab) selItem.getData();
				newTab.activated(workingCopy);

				selItem.getControl().setFocus();
			}
		});

		try {
			group = LaunchConfigurationPresentationManager.getDefault().getTabGroup(workingCopy, mode.getIdentifier());
			group.createTabs(this, mode.getIdentifier());

			for (ILaunchConfigurationTab configTab : group.getTabs()) {
				configTab.setLaunchConfigurationDialog(this);

				CTabItem tabItem = new CTabItem(tabFolder, SWT.NONE);
				tabItem.setData(configTab);
				tabItem.setText(configTab.getName());

				Composite tabComp = new Composite(tabFolder, SWT.NONE);
				tabComp.setLayout(new GridLayout());
				tabItem.setControl(tabComp);

				configTab.createControl(tabComp);
				Control configControl = configTab.getControl();
				configControl.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

				if (lastSelection == null) {
					// Assuming the first one ends up selected
					lastSelection = tabItem;
				}
			}

			group.initializeFrom(workingCopy);
		} catch (CoreException e) {
			Activator.log(e.getStatus());
		}

		pmPart = new ProgressMonitorPart(composite, new GridLayout(), true);
		pmPart.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		pmPart.setVisible(false);

		initing = false;
		return composite;
	}

	@Override
	protected void okPressed() {
		group.performApply(workingCopy);
		super.okPressed();
	}

	@Override
	public void run(boolean fork, boolean cancelable, IRunnableWithProgress runnable)
			throws InvocationTargetException, InterruptedException {
		Control lastControl = getShell().getDisplay().getFocusControl();
		if (lastControl != null && lastControl.getShell() != getShell()) {
			lastControl = null;
		}
		getButton(IDialogConstants.OK_ID).setEnabled(false);
		getButton(IDialogConstants.CANCEL_ID).setEnabled(false);
		pmPart.attachToCancelComponent(null);

		try {
			ModalContext.run(runnable, fork, pmPart, getShell().getDisplay());
		} finally {
			pmPart.removeFromCancelComponent(null);
			getButton(IDialogConstants.OK_ID).setEnabled(true);
			getButton(IDialogConstants.CANCEL_ID).setEnabled(true);
			if (lastControl != null) {
				lastControl.setFocus();
			}
			updateButtons();
		}
	}

	@Override
	public void updateButtons() {
		// Lots of tabs want to be applied when this is called
		if (!initing) {
			ILaunchConfigurationTab[] tabs = getTabs();
			if (tabFolder != null && tabs != null) {
				int pageIndex = tabFolder.getSelectionIndex();
				if (pageIndex >= 0) {
					tabs[pageIndex].performApply(workingCopy);
				}
			}
		}
	}

	@Override
	public void updateMessage() {
		// TODO Auto-generated method stub

	}

	@Override
	public void setName(String name) {
		// Names aren't setable from this dialog
	}

	@Override
	public String generateName(String name) {
		// Names aren't setable from this dialog
		return null;
	}

	@Override
	public ILaunchConfigurationTab[] getTabs() {
		return group.getTabs();
	}

	@Override
	public ILaunchConfigurationTab getActiveTab() {
		CTabItem selItem = tabFolder.getSelection();
		if (selItem != null) {
			return (ILaunchConfigurationTab) selItem.getData();
		} else {
			return null;
		}
	}

	@Override
	public String getMode() {
		return mode.getIdentifier();
	}

	@Override
	public ILaunchTarget getLaunchTarget() {
		return target;
	}

	@Override
	public void setActiveTab(ILaunchConfigurationTab tab) {
		for (CTabItem item : tabFolder.getItems()) {
			if (tab.equals(item.getData())) {
				tabFolder.setSelection(item);
				return;
			}
		}
	}

	@Override
	public void setActiveTab(int index) {
		tabFolder.setSelection(index);
	}

}