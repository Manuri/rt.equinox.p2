/*******************************************************************************
 *  Copyright (c) 2007, 2010 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.repository.metadata;

import java.util.Collection;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.repository.IRepository;
import org.eclipse.equinox.p2.repository.IRepositoryReference;
import org.eclipse.equinox.p2.repository.metadata.spi.AbstractMetadataRepository;

/**
 * A metadata repository stores information about a set of installable units
 * <p>
 * This interface is not intended to be implemented by clients.  Metadata repository
 * implementations must subclass {@link AbstractMetadataRepository} rather than 
 * implementing this interface directly.
 * </p>
 * @noimplement This interface is not intended to be implemented by clients.
 * @since 2.0
 */
public interface IMetadataRepository extends IRepository<IInstallableUnit> {

	/** 
	 * Add the given installable units to this repository.
	 * 
	 * @param installableUnits the installable units to add
	 */
	public void addInstallableUnits(Collection<IInstallableUnit> installableUnits);

	/**
	 * <p>Adds references to another repository to this repository. When a repository
	 * is loaded by {@link IMetadataRepositoryManager}, its references
	 * are automatically added to the repository manager's set of known repositories.</p>
	 * <p>Note that this method does not add the <b>contents</b> of the given
	 * repositories to this repository, but merely adds the location of other
	 * repositories to the metadata of this repository.</p>
	 * 
	 * @param references The references to add
	 */
	void addReferences(Collection<? extends IRepositoryReference> references);

	/**
	 * Returns the repositories that this repository is referencing.
	 * @return An immutable collection of repository references, possibly empty but never <code>null</code>.
	 */
	Collection<IRepositoryReference> getReferences();

	/**
	 * Removes all installable units in the given collection from this repository.
	 * 
	 * @param installableUnits the installable units to remove
	 * @return <code>true</code> if any units were actually removed, and
	 * <code>false</code> otherwise
	 */
	public boolean removeInstallableUnits(Collection<IInstallableUnit> installableUnits);

	/**
	 * Remove all installable units from this repository.  
	 */
	public void removeAll();

}
