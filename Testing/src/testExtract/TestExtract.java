package testExtract;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

public class TestExtract {
	public static void main(String[] args) throws URISyntaxException, IOException, InterruptedException {
		String[] fs = { "file1.txt", "file2.txt", "file3.txt", "file4.txt" };
		long start = System.currentTimeMillis();
		Thread[] ts = new Thread[4];

		for (int i = 0; i < fs.length; i++) {
			ts[i] = new Thread(new Runnable() {
				String string;

				@Override
				public void run() {
					try {
						extractFile(string);
					} catch (URISyntaxException | IOException e) {
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
		for(Thread t : ts){
			t.join();
		}
		System.out.println("Time taken: " + (System.currentTimeMillis() - start));
	}

	private static void extractFile(String fileName) throws URISyntaxException, IOException {
		InputStream is = TestExtract.class.getResourceAsStream("/lib/" + fileName);
		File f = new File(
				new File(getOwnPath())
						.getParentFile().getAbsolutePath() + "/" + fileName);
		System.out.println("Extracting " + fileName + " into " + f.getAbsolutePath());
		FileOutputStream of = new FileOutputStream(f);
		while (is.available() > 0) {
			of.write(is.read());
		}
		of.close();
		is.close();
	}

	private static String getOwnPath() throws URISyntaxException {
		return TestExtract.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
	}
}
