package testExtract;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TestExtract {
	public static void main(String[] args) throws URISyntaxException, IOException, InterruptedException {
		String[] fs = { "file1.txt", "file2.txt", "file3.txt", "file4.txt" };

		try {
			long start = System.currentTimeMillis();
			extractFiles(fs);
			System.out.println("Time taken 1: " + (System.currentTimeMillis() - start));
			start = System.currentTimeMillis();
			extractFiles2(fs);
			System.out.println("Time taken 2: " + (System.currentTimeMillis() - start));
		} catch (URISyntaxException | IOException e) {
			System.out.println("denna var visst shit");
		}


	}

	private static void extractFiles(String[] fs) throws IOException, InterruptedException {
		Thread[] ts = new Thread[4];
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
						while (is.available() > 0) {
							of.write(is.read());
						}
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
	

	private static void extractFiles2(String[] fileNames) throws URISyntaxException, IOException {
		File loc = new File(TestExtract.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
		ZipInputStream zin = new ZipInputStream(new FileInputStream(loc));
		ArrayList<String> names = new ArrayList<String>();
		for(String s : fileNames){
			names.add("lib/" + s);
		}
		
		ZipEntry ze = null;
		
		while((ze = zin.getNextEntry()) != null){
			if(names.contains(ze.getName())){
				File f = new File(toPath() + "/" + ze.getName());
				System.out.println("Extracting " + ze.getName() + " into " + f.getAbsolutePath());
				FileOutputStream of = new FileOutputStream(f);
				while(zin.available() > 0){
					of.write(zin.read());
				}
			}
		}
		
	}

	private static String getOwnPath() throws URISyntaxException {
		return TestExtract.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
	}

	private static String toPath() {
		try{
			return new File(getOwnPath()).getParentFile().getAbsolutePath();
			
		} catch (URISyntaxException e){
			return "jävla piss skit";
		}
	}
}
