/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
/**
 * 
 */
package org.eclipse.equinox.internal.p2.ui.sdk.scheduler;

import org.eclipse.equinox.internal.p2.engine.PhaseSet;

import org.eclipse.equinox.internal.p2.engine.Phase;

import org.eclipse.equinox.internal.p2.engine.phases.Collect;


public class DownloadPhaseSet extends PhaseSet {
	public DownloadPhaseSet() {
		super(new Phase[] {new Collect(10)});
	}
}
