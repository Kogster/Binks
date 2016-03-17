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
 * platformlib/<osname-bitness>/ and launch a main class from a jar in it's root
 * with the main class name specified in META-INF/subJarMainName.prop.
 * META-INF/subJarMainName.prop is created in this projects ant-build file.
 * 
 * This class does not use the logger because it is run before the logger is
 * loaded to the JVM.
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
		System.out.println("Launching");
		createTempDir();
		ClassLoader cl = loadClasses();
		addExtractedLibsToLibPath();
		Thread.currentThread().setContextClassLoader(cl);
		System.out.println("Launching application after "
				+ (System.currentTimeMillis() - time) + " ms");
		load.disposeIt();
		launchSubJar(args, cl);
	}

	private static void createTempDir() {
		try {
			folder = Files.createTempDirectory("Scancoin.Configuratior.")
					.toFile();
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
			System.err
					.println("Unable to create temporary files for platform libraries");
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
			librariesToLoad.add(mainJarName()); // main jar
			librariesToLoad.addAll(jarFileNames("lib/")); // libraries
			librariesToLoad.addAll(jarFileNames("platformlib/"
					+ plattformString())); // platform specific libraries

			URL[] jars = new URL[librariesToLoad.size()];
			for (int i = 0; i < librariesToLoad.size(); i++) {
				jars[i] = new URL("rsrc:" + librariesToLoad.get(i));
				// System.out.println(jars[i]);
			}

			ClassLoader cl = new URLClassLoader(jars, parent);

			try {
				Class.forName("org.eclipse.swt.widgets.Display", true, cl);
				// probeExistence(cl); //can be used for debugging classloading
			} catch (ClassNotFoundException e) {
				System.err
						.println("Launch failed: Failed to load class from jar\n"
								+ e.getMessage());
				throw new RuntimeException(e);
			}
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
		} catch (NoSuchFieldException | SecurityException
				| IllegalAccessException e1) {
			e1.printStackTrace();
		}
		// This doesn't accomplish anything beyond consistency between
		// java.library.path system property and were libraries may be loaded
		// from.
		String prop = System.getProperty("java.library.path");
		prop = prop + System.getProperty("path.separator")
				+ folder.getAbsolutePath();
		System.setProperty("java.library.path", prop);
	}

	private static void launchSubJar(String[] args, ClassLoader cl)
			throws Throwable {
		try {
			try {
				Class<?> c = Class.forName(getMainClassName(), true, cl);
				Method main = c.getMethod("main",
						new Class[] { args.getClass() });
				main.invoke((Object) null, new Object[] { args });
			} catch (InvocationTargetException ex) {
				if (ex.getCause() instanceof UnsatisfiedLinkError) {
					System.err.println("Launch failed: (UnsatisfiedLinkError)");
					String arch = System.getProperty("sun.arch.data.model");
					if ("32".equals(arch)) {
						System.err
								.println("Try adding '-d64' to your command line arguments");
					} else if ("64".equals(arch)) {
						((UnsatisfiedLinkError) ex.getCause())
								.printStackTrace();
						System.err
								.println("Try adding '-d32' to your command line arguments");
					}
				} else {
					throw ex;
				}
			}
		} catch (ClassNotFoundException ex) {
			System.err.println("Launch failed: Failed to find main class - "
					+ getMainClassName() + " :(");
		} catch (NoSuchMethodException ex) {
			System.err.println("Launch failed: Failed to find main method");
		} catch (InvocationTargetException ex) {
			Throwable th = ex.getCause();
			if ((th.getMessage() != null)
					&& th.getMessage().toLowerCase()
							.contains("invalid thread access")) {
				System.err
						.println("Launch failed: (SWTException: Invalid thread access)");
				System.err
						.println("Try adding '-XstartOnFirstThread' to your command line arguments");
			} else {
				throw th;
			}
		}
	}

	private static String getMainClassName() {
		URL tUrl = Loader.class.getClassLoader().getResource(
				"META-INF/subJarMainName.prop");
		String s = "";
		boolean loadedMainName = false;
		if (tUrl != null) {
			try (Scanner scan = new Scanner(tUrl.openStream())) {
				s = scan.nextLine();
				System.out.println("Loaded the name: " + s);
				loadedMainName = true;
			} catch (IOException e) {
				// loadedMainName remains false
			}
		}
		if (!loadedMainName) {
			System.err
					.println("failed to load resource META-INF/subJarMainName.prop and obtain subjar main class name");
		}
		return s;
	}

	private static String mainJarName() {
		List<String> fileNames = jarFileNames("");
		String mainJarName = "";
		for (String s : fileNames) {
			if (!s.contains("/") // the same for all platforms in this context.
					&& s.substring(s.length() - 4, s.length()).equals(".jar")) {
				mainJarName = s;
				break;
			}
		}
		return mainJarName;
	}

	private static List<String> jarFileNames(String filter) {
		if (jarContents.size() == 0) {
			probeJar();
		}
		List<String> list = new ArrayList<>();

		for (String s : jarContents) {
			if (s.startsWith(filter)
					&& s.substring(s.length() - 4, s.length()).equals(".jar")) {
				list.add(s);
			}
		}
		return list;
	}

	private static void probeJar() {

		// This part finds all the filenames in the jar.
		long time = System.currentTimeMillis();
		try {
			System.out.println("Probing jar");
			CodeSource src = Loader.class.getProtectionDomain()
					.getCodeSource();

			if (src != null) {
				URL thisJar = src.getLocation();

				ZipInputStream zin = new ZipInputStream(thisJar.openStream());
				ZipEntry ze = null;

				while ((ze = zin.getNextEntry()) != null) {
					String eN = ze.getName();
					if (eN.charAt(eN.length() - 1) != '/') {
						jarContents.add(eN);

						if (eN.startsWith("platformlib/" + plattformString(), 0)
								&& !eN.substring(eN.length() - 4, eN.length())
										.equals(".jar")) {
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
		System.out.println("Probing jar - done");
		System.out.println("probing took: "
				+ (System.currentTimeMillis() - time) + " ms");

	}

	private static void extractLib(ZipInputStream zin, ZipEntry ze, String eN)
			throws FileNotFoundException, IOException {
		// Perhaps Thread to extract faster.
		String n = ze.getName();
		OutputStream fout = new BufferedOutputStream(new FileOutputStream(
				folder.getAbsoluteFile()
						+ n.substring(n.lastIndexOf('/'), n.length())));
		for (int c = zin.read(); c != -1; c = zin.read()) {
			fout.write(c);
		}
		zin.closeEntry();
		fout.close();
	}

	private static String plattformString() {
		String osName = System.getProperty("os.name");
		String fileName = (osName.equalsIgnoreCase("linux") ? "linux" : "")
				+ (osName.toLowerCase().contains("windows") ? "windows" : "")
				// something for arm perhaps.
				+ "-"
				+ (System.getProperty("os.arch").equals("amd64") ? "64" : "")
				+ (System.getProperty("os.arch").equals("x86") ? "32" : "")
		// something for arm perhaps
		;
		return fileName;
	}

	private static void probeLibraryExistence(String className, ClassLoader cl) {
		try {
			Class.forName(className, true, cl);
			System.out.println(className + "\t\t\tis there");
		} catch (ClassNotFoundException e) {
			System.out.println(className + "\t\t\tis gone");
		}

	}

	@SuppressWarnings("unused")
	private static void probeExistence(ClassLoader cl)
			throws ClassNotFoundException {
		String[] libraryNames = { "com.scancoin.configuration.Configurator",
				"org.eclipse.swt.widgets.MessageBox",
				"org.eclipse.swt.widgets.Shell",
				"org.eclipse.swt.layout.RowData",
				"org.eclipse.core.commands.util.Tracing",
				"org.eclipse.core.commands.State",
				"org.eclipse.core.runtime.jobs.ProgressProvider",
				"org.eclipse.core.runtime.jobs.ILock",
				"org.eclipse.core.resources.IStorage",
				"org.eclipse.core.resources.mapping.ModeLStatus",
				"org.eclipse.core.runtime.Status",
				"org.eclipse.core.runtime.Path",
				"org.eclipse.jface.dialogs.MessageDialog",
				"org.eclipse.jface.dialogs.Dialog",
				"org.eclipse.jface.wizard.WizardPage",
				"org.eclipse.ui.texteditor.TaskRuleraction",
				"org.eclipse.ui.views.IViewRegistry",
				"org.eclipse.ui.wizards.IWizardRegistry",
				"com.thoughtworks.xstream.XStreamException", };

		for (String s : libraryNames) {
			probeLibraryExistence(s, cl);
		}
		System.out.println();
	}
}