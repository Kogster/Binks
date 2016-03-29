package testExtract;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TestExtract {
	public static void main(String[] args) throws URISyntaxException, IOException, InterruptedException {
		String[] fs = { "file1.txt", "file2.txt", "file3.txt", "file4.txt" };

		try {
			long start = System.currentTimeMillis();
			extractFilesThreadedGetResource(fs);
			System.out.println("Threaded getResource: " + (System.currentTimeMillis() - start));
			verify(fs);
			start = System.currentTimeMillis();
			extractFilesZipStream(fs);
			System.out.println("Unthreaded zipstream: " + (System.currentTimeMillis() - start));
			verify(fs);
			start = System.currentTimeMillis();
			extractFilesZipStreamThreaded(fs);
			System.out.println("Attempt at threaded zipstream: " + (System.currentTimeMillis() - start));
			verify(fs);
		} catch (/* URISyntaxException | */ IOException e) {
			e.printStackTrace();
			System.out.println("denna var visst shit");
		}

	}

	private static void extractFilesThreadedGetResource(String[] fs) throws IOException, InterruptedException {
		Thread[] ts = new Thread[fs.length];
		for (int i = 0; i < fs.length; i++) {
			ts[i] = new Thread(new Runnable() {
				String string;

				@Override
				public void run() {
					try {
						InputStream is = TestExtract.class.getResourceAsStream("/lib/" + string);
						File f = new File(toPath() + "/" + string);
						System.out.println("Extracting " + string + " into " + f.getAbsolutePath());
						FileOutputStream of = new FileOutputStream(f);
						BufferedOutputStream bos = new BufferedOutputStream(of);
						int read;
						while ((read = is.read()) != -1) {
							bos.write(read);
						}
						bos.flush();
						bos.close();
						of.close();
						of.close();
						is.close();
					} catch (IOException e) {
						System.out.println("shit");
					}
				}

				public Runnable addString(String str) {
					string = str;
					return this;
				}
			}.addString(fs[i]));
			ts[i].start();
		}
		for (Thread t : ts) {
			t.join();
		}
	}

	private static void extractFilesZipStream(String[] fileNames) throws URISyntaxException, IOException {
		File loc = new File(TestExtract.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
		ZipInputStream zin = new ZipInputStream(new FileInputStream(loc));
		ArrayList<String> names = new ArrayList<String>();
		for (String s : fileNames) {
			names.add("lib/" + s);
		}

		ZipEntry ze = null;

		while ((ze = zin.getNextEntry()) != null) {
			if (names.contains(ze.getName())) {
				File f = new File(toPath() + "/" + ze.getName().substring("lib/".length()));
				System.out.println("Extracting " + ze.getName() + " into " + f.getAbsolutePath());
				FileOutputStream of = new FileOutputStream(f);
				BufferedOutputStream bos = new BufferedOutputStream(of);
				int read;
				while ((read = zin.read()) != -1) {
					bos.write(read);
				}
				bos.flush();
				bos.close();
				of.close();
			}
		}
		zin.close();

	}

	private static void extractFilesZipStreamThreaded(String[] fileNames)
			throws URISyntaxException, IOException, InterruptedException {
		File loc = new File(TestExtract.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
		ArrayList<String> names = new ArrayList<String>();
		for (String s : fileNames) {
			names.add("lib/" + s);
		}
		Thread[] ts = new Thread[fileNames.length];
		for (int i = 0; i < fileNames.length; i++) {
			ts[i] = new Thread(new Runnable() {
				private String file;

				public Runnable addFile(String s) {
					file = s;
					return this;
				}

				@Override
				public void run() {
					ZipInputStream zin;
					try {
						zin = new ZipInputStream(new FileInputStream(loc));
						ZipEntry ze = null;
						while ((ze = zin.getNextEntry()) != null) {
							if (file.equals(ze.getName())) {
								File f = new File(toPath() + "/" + ze.getName().substring("lib/".length()));
								System.out.println("Extracting " + ze.getName() + " into " + f.getAbsolutePath());
								FileOutputStream of = new FileOutputStream(f);
								BufferedOutputStream bos = new BufferedOutputStream(of);
								int read;
								while ((read = zin.read()) != -1) {
									bos.write(read);
								}
								bos.flush();
								bos.close();
								of.close();
							} else {
								// System.out.println(file + "-------" +
								// ze.getName());
							}
						}
						zin.close();
					} catch (IOException e) {
						System.err.println("trouble in threaded zip-extractor");
					}
				}
			}.addFile(names.get(i)));
			ts[i].start();
		}
		for (Thread t : ts) {
			t.join();
		}
	}

	private static String getOwnPath() throws URISyntaxException {
		return TestExtract.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
	}

	private static String toPath() {
		try {
			return new File(getOwnPath()).getParentFile().getAbsolutePath();

		} catch (URISyntaxException e) {
			return "jävla piss skit"; // This will crash the calling function
		}
	}

	private static void verify(String[] fs) throws FileNotFoundException {
		for (String s : fs) {
			File testee = new File(toPath() + "/" + s);
			File correct = new File(toPath());
			correct = correct.getParentFile();
			correct = new File(correct + "/output/" + s);
			Scanner tscan = new Scanner(testee);
			Scanner cscan = new Scanner(correct);
			while(tscan.hasNextLine()){
				if(!cscan.nextLine().equals(tscan.nextLine())){
					cscan.close();
					tscan.close();
					throw new RuntimeException("FILES DID NOT MATCH!");
				}
			}
			cscan.close();
			tscan.close();
		}
	}
}
