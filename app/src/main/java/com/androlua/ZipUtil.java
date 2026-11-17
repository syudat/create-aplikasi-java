package com.androlua;
import java.io.*;

public class ZipUtil {
	public static boolean zip(String sourceFilePath, String zipFilePath) {
		return IoUtil.zip(sourceFilePath, zipFilePath);
	}

	public static boolean unzip(String zipPath, String destPath) {
		try {
			IoUtil.unZip(zipPath, destPath);
			return true;
		}
		catch (IOException e) {
			return false;
		}
	}
}
