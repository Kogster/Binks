package testExtract;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class GenerateFiles {
	public static void main(String[] args) throws IOException{
		FileWriter fw = new FileWriter("/home/dat12aan/workspaces/binks/Testing/output/file4.txt");
		for (int i = 0; i < 100000; i++){
			fw.append(i + "\n");
		}
		fw.close();
		System.out.println("done");
	}
}
