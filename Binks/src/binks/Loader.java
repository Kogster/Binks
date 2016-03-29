package binks;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jdt.internal.jarinjarloader.RsrcURLStreamHandlerFactory;

/**
 * This class will try to load all libraries in the lib/ folder in it's jar, all
 * libraries in the folder corresponding to the current platform under
 * platformlib/<osname-os.arch>/ and launch a main class from a jar in lib with
 * the main class name specified in META-INF/MANIFEST.MF. The name is added in
 * the ant build file
 */

public class Loader {
	private static ArrayList<String> jarContents;
	private static File folder = null;

	static {
		jarContents = new ArrayList<>();
	}

	public static void main(String[] args) throws Throwable {
		LoadingScreen load = new LoadingScreen();
		long time = System.currentTimeMillis();
		System.out.println("Launching for " + plattformString());
		createTempDir();
		ClassLoader cl = loadClasses();
		addExtractedLibsToLibPath();
		Thread.currentThread().setContextClassLoader(cl);
		System.out.println("Launching application after " + (System.currentTimeMillis() - time) + " ms");
		load.disposeIt();
		launchSubJar(args, cl);
	}

	private static void createTempDir() {
		try {
			folder = Files.createTempDirectory("Binks").toFile();
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					// Delete temp folder and it's contents before shut down.
					for (File f : folder.listFiles()) {
						f.delete();
					}
					folder.delete();
				}
			});
		} catch (IOException e) {
			System.err.println("Unable to create temporary files for platform libraries");
			e.printStackTrace();
		}
	}

	private static ClassLoader loadClasses() {
		System.out.println("ClassLoading");
		ClassLoader parent = Loader.class.getClassLoader();
		URL.setURLStreamHandlerFactory(new RsrcURLStreamHandlerFactory(parent));
		try {
			// Jar libraries to load
			ArrayList<String> librariesToLoad = new ArrayList<>();
			// libraries the sub jar main is in here
			librariesToLoad.addAll(jarFileNames("lib/"));
			// platform specific libraries
			librariesToLoad.addAll(jarFileNames("platformlib/" + plattformString()));
			URL[] jars = new URL[librariesToLoad.size()];
			for (int i = 0; i < librariesToLoad.size(); i++) {
				jars[i] = new URL("rsrc:" + librariesToLoad.get(i));
				// System.out.println(jars[i]);
			}

			ClassLoader cl = new URLClassLoader(jars, parent);

			System.out.println("ClassLoading - done");
			return cl;
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	private static void addExtractedLibsToLibPath() {
		// Adds folder with libraries to library paths
		try {
			Field usrPathsField;
			usrPathsField = ClassLoader.class.getDeclaredField("usr_paths");
			usrPathsField.setAccessible(true);
			// get array of paths
			final String[] paths = (String[]) usrPathsField.get(null);
			String pathToAdd = folder.getAbsolutePath();
			// check if the path to add is already present
			for (String path : paths) {
				if (path.equals(pathToAdd)) {
					return;
				}
			}
			// add the new path
			final String[] newPaths = Arrays.copyOf(paths, paths.length + 1);
			newPaths[newPaths.length - 1] = pathToAdd;
			usrPathsField.set(null, newPaths);
		} catch (NoSuchFieldException | SecurityException | IllegalAccessException e) {
			e.printStackTrace();
		}
		// This doesn't accomplish anything beyond consistency between
		// java.library.path system property and were libraries may be loaded
		// from.
		String prop = System.getProperty("java.library.path");
		prop = prop + System.getProperty("path.separator") + folder.getAbsolutePath();
		System.setProperty("java.library.path", prop);
	}

	private static void launchSubJar(String[] args, ClassLoader cl) throws Throwable {
		try {
			Class<?> c = Class.forName(getMainClassName(), true, cl);
			Method main = c.getMethod("main", new Class[] { args.getClass() });
			main.invoke((Object) null, new Object[] { args });
		} catch (ClassNotFoundException e) {
			System.err.println("Launch failed: Failed to find main class - " + getMainClassName()
					+ " :(\n are you sure you " + "have written the correct class name in the build.xml file?");
		} catch (NoSuchMethodException e) {
			System.err.println("The class specified does not contain a main method. Are you sure you have written "
					+ "the correct main-class name in the build.xxml file");
		} catch (InvocationTargetException e) {
			System.err.println("The sub jar launched threw a " + e.getTargetException().getMessage());
		}
	}

	private static String getMainClassName() {
		URL tUrl = Loader.class.getClassLoader().getResource("META-INF/MANIFEST.MF");
		String s = "";
		if (tUrl != null) {
			try (Scanner scan = new Scanner(tUrl.openStream())) {
				while (scan.hasNextLine()) {
					s = scan.nextLine();
					if (s.startsWith("Launch-main: ")) {
						s = s.substring("Launch-main: ".length());
						System.out.println("Launching ingress point: " + s);
						return s;
					}
				}
			} catch (IOException e) {
				// Crash program instead with the throw.
			}
		}
		throw new RuntimeException("Could not find the name for the sub jar to load in MANIFEST file");
	}

	/*
	 * Returns a string list of filenames for jars in the containing jar
	 */
	private static List<String> jarFileNames(String filter) {
		if (jarContents.size() == 0) {
			probeJar();
		}
		List<String> list = new ArrayList<>();

		for (String s : jarContents) {
			if (s.startsWith(filter) && s.substring(s.length() - 4, s.length()).equals(".jar")) {
				list.add(s);
			}
		}
		return list;
	}

	/*
	 * This method finds all the filenames in the jar and adds them to the
	 * static list jarContents.
	 */
	private static void probeJar() {
		long time = System.currentTimeMillis();
		try {
			System.out.println("Probing jar");
			CodeSource src = Loader.class.getProtectionDomain().getCodeSource();

			if (src != null) {
				URL thisJar = src.getLocation();

				ZipInputStream zin = new ZipInputStream(thisJar.openStream());
				ZipEntry ze = null;

				while ((ze = zin.getNextEntry()) != null) {
					String eN = ze.getName();
					if (eN.charAt(eN.length() - 1) != '/') {
						jarContents.add(eN);

						if (eN.startsWith("platformlib/" + plattformString(), 0)
								&& !eN.substring(eN.length() - 4, eN.length()).equals(".jar")) {
							extractLib(zin, ze, eN);
						}
					}
				}
				zin.close();
			}

		} catch (FileNotFoundException e) {
			System.err.println("Unable find jar file");
		} catch (IOException e) {
			System.err.println("Unable to list files present in jar.");
		}
		System.out.println("probing took: " + (System.currentTimeMillis() - time) + " ms");

	}

	private static void extractLib(ZipInputStream zin, ZipEntry ze, String eN)
			throws FileNotFoundException, IOException {
		// Perhaps Thread to extract faster.
		String n = ze.getName();
		OutputStream fout = new BufferedOutputStream(
				new FileOutputStream(folder.getAbsoluteFile() + n.substring(n.lastIndexOf('/'), n.length())));
		for (int c = zin.read(); c != -1; c = zin.read()) {
			fout.write(c);
		}
		zin.closeEntry();
		fout.close();
	}

	private static String plattformString() {
		return System.getProperty("os.name") + "-" + System.getProperty("os.arch");
	}
}