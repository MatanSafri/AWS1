package myInterfaces;

import java.io.InputStream;

public interface IfileSystem {
	InputStream getFile(String fileKey);
	void saveFile(String path);
}
