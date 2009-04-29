package org.eclipse.equinox.p2.tests;

import java.io.OutputStream;
import java.net.URI;
import java.util.Map;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.internal.provisional.p2.artifact.repository.*;
import org.eclipse.equinox.internal.provisional.p2.core.ProvisionException;
import org.eclipse.equinox.internal.provisional.p2.metadata.IArtifactKey;

public class AbstractWrappedArtifactRepository implements IArtifactRepository {

	IArtifactRepository delegate;

	public AbstractWrappedArtifactRepository(IArtifactRepository repo) {
		delegate = repo;
	}

	public void addDescriptor(IArtifactDescriptor descriptor) {
		delegate.addDescriptor(descriptor);
	}

	public void addDescriptors(IArtifactDescriptor[] descriptors) {
		delegate.addDescriptors(descriptors);
	}

	public boolean contains(IArtifactDescriptor descriptor) {
		return delegate.contains(descriptor);
	}

	public boolean contains(IArtifactKey key) {
		return delegate.contains(key);
	}

	public IStatus getArtifact(IArtifactDescriptor descriptor, OutputStream destination, IProgressMonitor monitor) {
		return delegate.getArtifact(descriptor, destination, monitor);
	}

	public IArtifactDescriptor[] getArtifactDescriptors(IArtifactKey key) {
		return delegate.getArtifactDescriptors(key);
	}

	public IArtifactKey[] getArtifactKeys() {
		return delegate.getArtifactKeys();
	}

	public IStatus getArtifacts(IArtifactRequest[] requests, IProgressMonitor monitor) {
		return delegate.getArtifacts(requests, monitor);
	}

	public OutputStream getOutputStream(IArtifactDescriptor descriptor) throws ProvisionException {
		return delegate.getOutputStream(descriptor);
	}

	public IStatus getRawArtifact(IArtifactDescriptor descriptor, OutputStream destination, IProgressMonitor monitor) {
		return delegate.getRawArtifact(descriptor, destination, monitor);
	}

	public void removeAll() {
		delegate.removeAll();
	}

	public void removeDescriptor(IArtifactDescriptor descriptor) {
		delegate.removeDescriptor(descriptor);
	}

	public void removeDescriptor(IArtifactKey key) {
		delegate.removeDescriptor(key);
	}

	public String getDescription() {
		return delegate.getDescription();
	}

	public URI getLocation() {
		return delegate.getLocation();
	}

	public String getName() {
		return delegate.getName();
	}

	public Map getProperties() {
		return delegate.getProperties();
	}

	public String getProvider() {
		return delegate.getProvider();
	}

	public String getType() {
		return delegate.getType();
	}

	public String getVersion() {
		return delegate.getVersion();
	}

	public boolean isModifiable() {
		return delegate.isModifiable();
	}

	public void setDescription(String description) {
		delegate.setDescription(description);
	}

	public void setName(String name) {
		delegate.setName(name);
	}

	public String setProperty(String key, String value) {
		return delegate.setProperty(key, value);
	}

	public void setProvider(String provider) {
		delegate.setProvider(provider);
	}

	public Object getAdapter(Class adapter) {
		return delegate.getAdapter(adapter);
	}
}
