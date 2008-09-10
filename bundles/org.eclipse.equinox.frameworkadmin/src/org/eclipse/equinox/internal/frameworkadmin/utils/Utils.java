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
package org.eclipse.equinox.internal.frameworkadmin.utils;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.internal.provisional.frameworkadmin.BundleInfo;
import org.eclipse.osgi.service.pluginconversion.PluginConversionException;
import org.eclipse.osgi.service.pluginconversion.PluginConverter;
import org.osgi.framework.Constants;

public class Utils {
	private static final String FEATURE_MANIFEST = "feature.xml"; //$NON-NLS-1$
	private static final String FILE_PROTOCOL = "file:"; //$NON-NLS-1$
	private static final String FRAGMENT_MANIFEST = "fragment.xml"; //$NON-NLS-1$
	private static final String PATH_SEP = "/"; //$NON-NLS-1$
	private static final String PLUGIN_MANIFEST = "plugin.xml"; //$NON-NLS-1$

	/**
	 * Overwrite all properties of from to the properties of to. Return the result of to.
	 * 
	 * @param to Properties whose keys and values of other Properties will be appended to.
	 * @param from Properties whose keys and values will be set to the other properties.
	 * @return Properties as a result of this method. 
	 */
	public static Properties appendProperties(Properties to, Properties from) {
		if (from != null) {
			if (to == null)
				to = new Properties();
			//			printoutProperties(System.out, "to", to);
			//			printoutProperties(System.out, "from", from);

			for (Enumeration enumeration = from.keys(); enumeration.hasMoreElements();) {
				String key = (String) enumeration.nextElement();
				to.setProperty(key, from.getProperty(key));
			}
		}
		//		printoutProperties(System.out, "to", to);
		return to;
	}

	//Return a dictionary representing a manifest. The data may result from plugin.xml conversion  
	private static Dictionary basicLoadManifest(File bundleLocation) {
		InputStream manifestStream = null;
		ZipFile jarFile = null;
		try {
			try {
				String fileExtention = bundleLocation.getName();
				fileExtention = fileExtention.substring(fileExtention.lastIndexOf('.') + 1);
				// Handle a JAR'd bundle
				if ("jar".equalsIgnoreCase(fileExtention) && bundleLocation.isFile()) { //$NON-NLS-1$
					jarFile = new ZipFile(bundleLocation, ZipFile.OPEN_READ);
					ZipEntry manifestEntry = jarFile.getEntry(JarFile.MANIFEST_NAME);
					if (manifestEntry != null) {
						manifestStream = jarFile.getInputStream(manifestEntry);
					}
				} else {
					// we have a directory-based bundle
					File bundleManifestFile = new File(bundleLocation, JarFile.MANIFEST_NAME);
					if (bundleManifestFile.exists())
						manifestStream = new BufferedInputStream(new FileInputStream(new File(bundleLocation, JarFile.MANIFEST_NAME)));
				}
			} catch (IOException e) {
				//ignore
			}
			// we were unable to get an OSGi manifest file so try and convert an old-style manifest
			if (manifestStream == null)
				return convertPluginManifest(bundleLocation, true);

			// It is not a manifest, but a plugin or a fragment
			try {
				Manifest m = new Manifest(manifestStream);
				Dictionary manifest = manifestToProperties(m.getMainAttributes());
				// add this check to handle the case were we read a non-OSGi manifest
				if (manifest.get(Constants.BUNDLE_SYMBOLICNAME) == null)
					return convertPluginManifest(bundleLocation, true);
				return manifest;
			} catch (IOException ioe) {
				return null;
			}
		} finally {
			try {
				if (manifestStream != null)
					manifestStream.close();
			} catch (IOException e1) {
				//Ignore
			}
			try {
				if (jarFile != null)
					jarFile.close();
			} catch (IOException e2) {
				//Ignore
			}
		}
	}

	public static void checkAbsoluteDir(File file, String dirName) throws IllegalArgumentException {
		if (file == null)
			throw new IllegalArgumentException(dirName + " is null");
		if (!file.isAbsolute())
			throw new IllegalArgumentException(dirName + " is not absolute path. file=" + file.getAbsolutePath());
		if (!file.isDirectory())
			throw new IllegalArgumentException(dirName + " is not directory. file=" + file.getAbsolutePath());
	}

	public static void checkAbsoluteFile(File file, String dirName) {//throws ManipulatorException {
		if (file == null)
			throw new IllegalArgumentException(dirName + " is null");
		if (!file.isAbsolute())
			throw new IllegalArgumentException(dirName + " is not absolute path. file=" + file.getAbsolutePath());
		if (file.isDirectory())
			throw new IllegalArgumentException(dirName + " is not file but directory");
	}

	public static URL checkFullUrl(URL url, String urlName) throws IllegalArgumentException {//throws ManipulatorException {
		if (url == null)
			throw new IllegalArgumentException(urlName + " is null");
		if (!url.getProtocol().endsWith("file"))
			return url;
		File file = new File(url.getFile());
		if (!file.isAbsolute())
			throw new IllegalArgumentException(urlName + "(" + url + ") does not have absolute path");
		if (file.getAbsolutePath().startsWith(PATH_SEP))
			return url;
		try {
			return getUrl("file", null, PATH_SEP + file.getAbsolutePath());
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(urlName + "(" + "file:" + PATH_SEP + file.getAbsolutePath() + ") is not fully quallified");
		}
	}

	/*
	 * Copied from BundleDescriptionFactory in the metadata generator.
	 */
	private static Dictionary convertPluginManifest(File bundleLocation, boolean logConversionException) {
		PluginConverter converter;
		try {
			converter = org.eclipse.equinox.internal.frameworkadmin.utils.Activator.acquirePluginConverter();
			if (converter == null) {
				new RuntimeException("Unable to aquire PluginConverter service during generation for: " + bundleLocation).printStackTrace(); //$NON-NLS-1$
				return null;
			}
			return converter.convertManifest(bundleLocation, false, null, true, null);
		} catch (PluginConversionException convertException) {
			// only log the exception if we had a plugin.xml or fragment.xml and we failed conversion
			if (bundleLocation.getName().equals(FEATURE_MANIFEST))
				return null;
			if (!new File(bundleLocation, PLUGIN_MANIFEST).exists() && !new File(bundleLocation, FRAGMENT_MANIFEST).exists())
				return null;
			if (logConversionException) {
				IStatus status = new Status(IStatus.WARNING, "org.eclipse.equinox.frameworkadmin", 0, "Error converting bundle manifest.", convertException);
				System.out.println(status);
				//TODO Need to find a way to get a logging service to log
			}
			return null;
		}
	}

	public static void createParentDir(File file) throws IOException {
		File parent = file.getParentFile();
		if (parent == null)
			return;
		parent.mkdirs();
	}

	public static BundleInfo[] getBundleInfosFromList(List list) {
		if (list == null)
			return new BundleInfo[0];
		BundleInfo[] ret = new BundleInfo[list.size()];
		list.toArray(ret);
		return ret;
	}

	public static String[] getClauses(String header) {
		StringTokenizer token = new StringTokenizer(header, ",");
		List list = new LinkedList();
		while (token.hasMoreTokens()) {
			list.add(token.nextToken());
		}
		String[] ret = new String[list.size()];
		list.toArray(ret);
		return ret;
	}

	public static String[] getClausesManifestMainAttributes(String location, String name) {
		return getClauses(getManifestMainAttributes(location, name));
	}

	public static String getManifestMainAttributes(String location, String name) {
		Dictionary manifest = Utils.getOSGiManifest(location);
		if (manifest == null)
			throw new RuntimeException("Unable to locate bundle manifest: " + location);
		return (String) manifest.get(name);
	}

	public static Dictionary getOSGiManifest(String location) {
		if (location == null)
			return null;
		// if we have a file-based URL that doesn't end in ".jar" then...
		if (location.startsWith(FILE_PROTOCOL) && !location.endsWith(".jar"))
			return basicLoadManifest(new File(location.substring(FILE_PROTOCOL.length())));

		try {
			JarFile jar = null;
			File file = null;
			if (location.startsWith(FILE_PROTOCOL)) {
				file = new File(location.substring(FILE_PROTOCOL.length()));
				jar = new JarFile(file);
			} else {
				URL url = new URL("jar:" + location + "!/");
				JarURLConnection jarConnection = (JarURLConnection) url.openConnection();
				jar = jarConnection.getJarFile();
				// todo should set this var if possible
				// file = ;
			}
			try {
				Manifest manifest = jar.getManifest();
				// we might have an old-style plug-in so look for an old plug-in manifest
				if (manifest == null) {
					if (file == null)
						return null;
					// make sure we have something to convert
					JarEntry entry = jar.getJarEntry(PLUGIN_MANIFEST);
					if (entry == null)
						entry = jar.getJarEntry(FRAGMENT_MANIFEST);
					if (entry == null)
						return null;
					return convertPluginManifest(file, true);
				}
				Attributes attributes = manifest.getMainAttributes();
				// if we have a JAR'd bundle that has a non-OSGi manifest file (like
				// the ones produced by Ant, then try and convert the plugin.xml
				if (attributes.getValue(Constants.BUNDLE_SYMBOLICNAME) == null) {
					if (file == null)
						return null;
					return convertPluginManifest(file, true);
				}
				Dictionary result = new Hashtable();
				for (Iterator iter = attributes.keySet().iterator(); iter.hasNext();) {
					String key = iter.next().toString();
					result.put(key, attributes.getValue(key));
				}
				return result;
			} finally {
				jar.close();
			}
		} catch (IOException e) {
			if (System.getProperty("osgi.debug") != null) {
				System.err.println("location=" + location);
				e.printStackTrace();
			}
		}
		return null;
	}

	public static String getPathFromClause(String clause) {
		if (clause == null)
			return null;
		if (clause.indexOf(";") != -1)
			clause = clause.substring(0, clause.indexOf(";"));
		return clause.trim();
	}

	public static String getRelativePath(File target, File from) {

		String targetPath = Utils.replaceAll(target.getAbsolutePath(), File.separator, PATH_SEP);
		String fromPath = Utils.replaceAll(from.getAbsolutePath(), File.separator, PATH_SEP);

		String[] targetTokens = Utils.getTokens(targetPath, PATH_SEP);
		String[] fromTokens = Utils.getTokens(fromPath, PATH_SEP);
		int index = -1;
		for (int i = 0; i < fromTokens.length; i++)
			if (fromTokens[i].equals(targetTokens[i]))
				index = i;
			else
				break;

		StringBuffer sb = new StringBuffer();
		for (int i = index + 1; i < fromTokens.length; i++)
			sb.append(".." + PATH_SEP);

		for (int i = index + 1; i < targetTokens.length; i++)
			if (i != targetTokens.length - 1)
				sb.append(targetTokens[i] + PATH_SEP);
			else
				sb.append(targetTokens[i]);
		return sb.toString();
	}

	/**
	 * This method will be called for create a backup file.
	 * 
	 * @param file target file
	 * @return File backup file whose filename consists of "hogehoge.yyyyMMddHHmmss.ext" or 
	 * 	"hogehoge.yyyyMMddHHmmss".
	 */
	public static File getSimpleDataFormattedFile(File file) {
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
		String date = df.format(new Date());
		String filename = file.getName();
		int index = filename.lastIndexOf(".");
		if (index != -1)
			filename = filename.substring(0, index) + "." + date + "." + filename.substring(index + 1);
		else
			filename = filename + "." + date;
		File dest = new File(file.getParentFile(), filename);
		return dest;
	}

	public static String[] getTokens(String msg, String delim) {
		return getTokens(msg, delim, false);
	}

	public static String[] getTokens(String msg, String delim, boolean returnDelims) {
		StringTokenizer targetST = new StringTokenizer(msg, delim, returnDelims);
		String[] tokens = new String[targetST.countTokens()];
		ArrayList list = new ArrayList(targetST.countTokens());
		while (targetST.hasMoreTokens()) {
			list.add(targetST.nextToken());
		}
		list.toArray(tokens);
		return tokens;
	}

	public static URL getUrl(String protocol, String host, String file) throws MalformedURLException {// throws ManipulatorException {
		file = Utils.replaceAll(file, File.separator, "/");
		return new URL(protocol, host, file);
	}

	public static URL getUrlInFull(String path, URL from) throws MalformedURLException {//throws ManipulatorException {
		Utils.checkFullUrl(from, "from");
		path = Utils.replaceAll(path, File.separator, "/");
		//System.out.println("from.toExternalForm()=" + from.toExternalForm());
		String fromSt = Utils.removeLastCh(from.toExternalForm(), '/');
		//System.out.println("fromSt=" + fromSt);
		if (path.startsWith("/")) {
			String fileSt = from.getFile();
			return new URL(fromSt.substring(0, fromSt.lastIndexOf(fileSt) - 1) + path);
		}
		return new URL(fromSt + "/" + path);
	}

	private static Properties manifestToProperties(Attributes d) {
		Iterator iter = d.keySet().iterator();
		Properties result = new Properties();
		while (iter.hasNext()) {
			Attributes.Name key = (Attributes.Name) iter.next();
			result.put(key.toString(), d.get(key));
		}
		return result;
	}

	/**
	 * Just used for debug.
	 * 
	 * @param ps printstream
	 * @param name name of properties 
	 * @param props properties whose keys and values will be printed out.
	 */
	public static void printoutProperties(PrintStream ps, String name, Properties props) {
		if (props == null || props.size() == 0) {
			ps.println("Props(" + name + ") is empty");
			return;
		}
		ps.println("Props(" + name + ")=");
		for (Enumeration enumeration = props.keys(); enumeration.hasMoreElements();) {
			String key = (String) enumeration.nextElement();
			ps.print("\tkey=" + key);
			ps.println("\tvalue=" + props.getProperty(key));
		}
	}

	public static String removeLastCh(String target, char ch) {
		while (target.charAt(target.length() - 1) == ch) {
			target = target.substring(0, target.length() - 1);
		}
		return target;
	}

	public static String replaceAll(String st, String oldSt, String newSt) {
		if (oldSt.equals(newSt))
			return st;
		int index = -1;
		while ((index = st.indexOf(oldSt)) != -1) {
			st = st.substring(0, index) + newSt + st.substring(index + oldSt.length());
		}
		return st;
	}

	/**
	 * Sort by increasing order of startlevels.
	 * 
	 * @param bInfos array of BundleInfos to be sorted.
	 * @param initialBSL initial bundle start level to be used.
	 * @return sorted array of BundleInfos
	 */
	public static BundleInfo[] sortBundleInfos(BundleInfo[] bInfos, int initialBSL) {
		SortedMap bslToList = new TreeMap();
		for (int i = 0; i < bInfos.length; i++) {
			Integer sL = new Integer(bInfos[i].getStartLevel());
			if (sL.intValue() == BundleInfo.NO_LEVEL)
				sL = new Integer(initialBSL);
			List list = (List) bslToList.get(sL);
			if (list == null) {
				list = new LinkedList();
				bslToList.put(sL, list);
			}
			list.add(bInfos[i]);
		}

		// bslToList is sorted by the key (StartLevel).
		List bundleInfoList = new LinkedList();
		for (Iterator ite = bslToList.keySet().iterator(); ite.hasNext();) {
			Integer sL = (Integer) ite.next();
			List list = (List) bslToList.get(sL);
			for (Iterator ite2 = list.iterator(); ite2.hasNext();) {
				BundleInfo bInfo = (BundleInfo) ite2.next();
				bundleInfoList.add(bInfo);
			}
		}
		return getBundleInfosFromList(bundleInfoList);
	}

	/**
	 * get String representing the given properties.
	 * 
	 * @param name name of properties 
	 * @param props properties whose keys and values will be printed out.
	 */
	public static String toStringProperties(String name, Properties props) {
		if (props == null || props.size() == 0) {
			return "Props(" + name + ") is empty\n";
		}
		StringBuffer sb = new StringBuffer();
		sb.append("Props(" + name + ") is \n");
		for (Enumeration enumeration = props.keys(); enumeration.hasMoreElements();) {
			String key = (String) enumeration.nextElement();
			sb.append("\tkey=" + key + "\tvalue=" + props.getProperty(key) + "\n");
		}
		return sb.toString();
	}

}
