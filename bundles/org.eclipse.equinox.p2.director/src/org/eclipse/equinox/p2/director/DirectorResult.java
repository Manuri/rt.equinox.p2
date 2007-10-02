/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.equinox.p2.director;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.p2.engine.Operand;

public class DirectorResult {
	private IStatus status;
	private Operand[] operands;

	public DirectorResult(IStatus status) {
		this(status, null);
	}

	public DirectorResult(IStatus status, Operand[] operands) {
		this.status = status;
		this.operands = operands;
	}

	public IStatus getStatus() {
		return status;
	}

	/** 
	 * The operands to pass to the engine.
	 * @return the operands or <code>null</code> if the the operation has not been successfull. 
	 */
	public Operand[] getOperands() {
		return operands;
	}
}
