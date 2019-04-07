package myInterfaces;

import java.io.InputStream;

public interface IfileSystem {
	InputStream getFile(String fileKey);
	String saveFile(String path);
	void deleteFile(String fileKey);
}
