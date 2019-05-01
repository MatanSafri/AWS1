import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public final class fileProp {

	private File file;
	private FileWriter fw;
	private BufferedWriter bw;
	
	public fileProp(File file,FileWriter fw,BufferedWriter bw){
		this.file = file;
		this.fw = fw;
		this.bw = bw;
	}
	
	public FileWriter getFileWriter(){
		return fw;
	}
	
	public BufferedWriter getBufferedWriter(){
		return bw;
	}
	
	public File getFile(){
		return file;
	}
}
