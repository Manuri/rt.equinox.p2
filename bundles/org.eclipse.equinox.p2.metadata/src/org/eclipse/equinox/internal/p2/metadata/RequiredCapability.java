/*******************************************************************************
 *  Copyright (c) 2007, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *     EclipseSource - ongoing development
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.metadata;

import org.eclipse.equinox.p2.metadata.VersionRange;

import org.eclipse.core.runtime.Assert;
import org.eclipse.equinox.p2.metadata.*;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.MatchQuery;

/**
 * A required capability represents some external constraint on an {@link IInstallableUnit}.
 * Each capability represents something an {@link IInstallableUnit} needs that
 * it expects to be provided by another {@link IInstallableUnit}. Capabilities are
 * entirely generic, and are intended to be capable of representing anything that
 * an {@link IInstallableUnit} may need either at install time, or at runtime.
 * <p>
 * Capabilities are segmented into namespaces.  Anyone can introduce new 
 * capability namespaces. Some well-known namespaces are introduced directly
 * by the provisioning framework.
 * 
 * @see IInstallableUnit#NAMESPACE_IU_ID
 */
public class RequiredCapability extends MatchQuery<IInstallableUnit> implements IRequiredCapability, IRequirement {
	private LDAPQuery filter;
	private final String name;//never null
	private final String namespace;//never null
	private boolean greedy = true;
	private final VersionRange range;//never null
	private int min;
	private int max;

	/**
	 * TODO replace booleans with int options flag.
	 */
	public RequiredCapability(String namespace, String name, VersionRange range, String filter, boolean optional, boolean multiple) {
		Assert.isNotNull(namespace);
		Assert.isNotNull(name);
		this.namespace = namespace;
		this.name = name;
		this.range = range == null ? VersionRange.emptyRange : range;
		min = optional ? 0 : 1;
		max = 1;
		setFilter(filter);
	}

	public RequiredCapability(String namespace, String name, VersionRange range, IQuery<Boolean> filter, int min, int max, boolean greedy) {
		Assert.isNotNull(namespace);
		Assert.isNotNull(name);
		this.namespace = namespace;
		this.name = name;
		this.range = range == null ? VersionRange.emptyRange : range;
		this.min = min;
		this.max = max;
		this.greedy = greedy;
		if (filter != null) {
			if (filter instanceof LDAPQuery) {
				this.filter = (LDAPQuery) filter;
			} else {
				throw new IllegalArgumentException();
			}
		}
	}

	public RequiredCapability(String namespace, String name, VersionRange range, String filter, boolean optional, boolean multiple, boolean greedy) {
		this(namespace, name, range, filter, optional, multiple);
		this.greedy = greedy;
	}

	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof IRequiredCapability))
			return false;
		final IRequiredCapability other = (IRequiredCapability) obj;
		if (filter == null) {
			if (other.getFilter() != null)
				return false;
		} else if (!filter.equals(other.getFilter()))
			return false;
		if (!name.equals(other.getName()))
			return false;
		if (!namespace.equals(other.getNamespace()))
			return false;
		if (!range.equals(other.getRange()))
			return false;
		return true;
	}

	public String getName() {
		return name;
	}

	public String getNamespace() {
		return namespace;
	}

	/**
	 * Returns the range of versions that satisfy this required capability. Returns
	 * an empty version range ({@link VersionRange#emptyRange} if any version
	 * will satisfy the capability.
	 * @return the range of versions that satisfy this required capability.
	 */
	public VersionRange getRange() {
		return range;
	}

	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((filter == null) ? 0 : filter.hashCode());
		result = prime * result + name.hashCode();
		result = prime * result + namespace.hashCode();
		result = prime * result + range.hashCode();
		return result;
	}

	/**
	 * TODO This object shouldn't be mutable since it makes equality unstable, and
	 * introduces lifecycle issues (how are the changes persisted, etc)
	 */
	public void setFilter(String filter) {
		if (filter != null)
			this.filter = new LDAPQuery(filter);
	}

	public boolean isGreedy() {
		return greedy;
	}

	public String toString() {
		StringBuffer result = new StringBuffer();

		if (IInstallableUnit.NAMESPACE_IU_ID.equals(getNamespace())) {
			//print nothing for an IU id dependency because this is the default (most common) case
			result.append(""); //$NON-NLS-1$
		} else if ("osgi.bundle".equals(getNamespace())) { //$NON-NLS-1$
			result.append("bundle"); //$NON-NLS-1$
		} else if ("java.package".equals(getNamespace())) { //$NON-NLS-1$
			result.append("package"); //$NON-NLS-1$
		} else {
			result.append(getNamespace());
		}
		if (result.length() > 0)
			result.append(' ');
		result.append(getName());
		result.append(' ');
		//for an exact version match, print a simpler expression
		if (range.getMinimum().equals(range.getMaximum()))
			result.append('[').append(range.getMinimum()).append(']');
		else
			result.append(range);
		return result.toString();
	}

	public boolean isNegation() {
		return false;
	}

	public boolean satisfiedBy(IProvidedCapability cap) {
		if (getName() == null || !getName().equals(cap.getName()))
			return false;
		if (getNamespace() == null || !getNamespace().equals(cap.getNamespace()))
			return false;
		return getRange().isIncluded(cap.getVersion());
	}

	public int getMin() {
		return min;
	}

	public int getMax() {
		return max;
	}

	public IQuery<IInstallableUnit> getMatches() {
		return this;
	}

	public IQuery<Boolean> getFilter() {
		return filter;
	}

	public boolean isMatch(IInstallableUnit candidate) {
		if (!candidate.satisfies(this))
			return false;
		return true;
	}
}
