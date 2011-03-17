/*
 * Copyright (c) 2009, Ken Gilmer
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without modification, 
 * are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list 
 * of conditions and the following disclaimer.
 * 
 * Redistributions in binary form must reproduce the above copyright notice, 
 * this list of conditions and the following disclaimer in the documentation 
 * and/or other materials provided with the distribution.
 * 
 * Neither the name Ken Gilmer nor the names of other contributors may be used 
 * to endorse or promote products derived from this software without specific 
 * prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE 
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED 
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR 
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.steb.preferences;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

/**
 * A field editor for viewing and selecting editors which should be ignored when being loaded by steb.
 * This is useful as some editors cannot edit files without extra information not provided in the file.
 * 
 * @author kgilmer
 *
 */
class EditorOverrideFieldEditor extends FieldEditor {

	private static final int TABLE_HEIGHT_HINT = 200;
	private CheckboxTableViewer tv;
	private Label title;
	private int elementCount;

	public EditorOverrideFieldEditor(String name, String labelText, Composite parent) {
		init(name, labelText);
		createControl(parent);
	}

	@Override
	protected void adjustForNumColumns(int numColumns) {
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = numColumns;
		title.setLayoutData(gd);

		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = numColumns;
		gd.heightHint = TABLE_HEIGHT_HINT;
		tv.getTable().setLayoutData(gd);

	}

	@Override
	protected void doFillIntoGrid(Composite parent, int numColumns) {
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = numColumns;
		gd.heightHint = TABLE_HEIGHT_HINT;

		title = new Label(parent, SWT.None);
		title.setText(this.getLabelText());
		title.setLayoutData(gd);
		tv = CheckboxTableViewer.newCheckList(parent, SWT.BORDER);
		tv.getTable().setLayoutData(gd);

		tv.setContentProvider(new ConfigurationElementContentProvider());
		tv.setLabelProvider(new ConfigurationElementTableLabelProvider());
		Object[] o = Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.ui.editors");
		elementCount = o.length;
		tv.setInput(o);
	}

	@Override
	protected void doLoad() {
		String s = getPreferenceStore().getString(getPreferenceName());

		if (s.length() > 0) {
			List override = Arrays.asList(s.split(","));

			for (int i = 0; i < elementCount; ++i) {
				IConfigurationElement ce = (IConfigurationElement) tv.getElementAt(i);

				if (override.contains(ce.getAttribute("id"))) {
					tv.setChecked(ce, true);
				}
			}
		}
	}

	@Override
	protected void doLoadDefault() {

	}

	@Override
	protected void doStore() {
		getPreferenceStore().setValue(getPreferenceName(), getCheckedAsDelimitedString(tv.getCheckedElements()));
	}

	/**
	 * Takes in an array of IConfigurationElements and returns a string of the "id" attributes, comma-delimited.
	 * @param checkedElements
	 * @return
	 */
	private String getCheckedAsDelimitedString(Object[] checkedElements) {
		StringBuffer sb = new StringBuffer();
		boolean hasCheckedElement = false;

		for (int i = 0; i < checkedElements.length; ++i) {
			sb.append(((IConfigurationElement) checkedElements[i]).getAttribute("id"));
			sb.append(",");
			hasCheckedElement = true;
		}

		if (hasCheckedElement) {
			return sb.substring(0, sb.length() - 1);
		}

		return "";
	}

	@Override
	public int getNumberOfControls() {
		return 2;
	}

}