import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.Object;
import java.net.URL;

import javax.imageio.ImageWriteParam;
import javax.imageio.spi.ServiceRegistry.Filter;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.util.PDFImageWriter;
import org.apache.pdfbox.util.PDFTextStripper;


import javafx.scene.image.WritableImage;


public class PDFfunc{
		
	public static String pdf2Image (PDDocument pdf, String fileName){
		try{
			
			PDFImageWriter ImWriter = new PDFImageWriter();
			
			if (ImWriter.writeImage(pdf,"png","" , 1, 1, fileName)) 
				return "ToImage " + fileName + ".pdf " + fileName + "1.png"; //added 1 to the file name because it converted the first page
			else
				return "ToImage: " + fileName + ".pdf" + "error occured during creation of the image";
				
		}
		catch (IOException e){
			return "ToImage: " + fileName + ".pdf" + "I/O exception";
		}
	}
	
	public static String pdf2Text (PDDocument pdf, String fileName){
		
		try {
			
			PDFTextStripper textStrip = new PDFTextStripper();
			textStrip.setEndPage(1);  //only convert the text from the first page
			
		    String text =  textStrip.getText(pdf);
		    
		    // write the content to text file
		    PrintWriter pw = new PrintWriter(new FileWriter(fileName + ".txt")); 
		    
			pw.write(text); //write the text to the output file
		 
			pw.close();	
			return "ToText " + fileName + ".pdf " + fileName + ".txt";
 
		    
		} catch (IOException e) {
		    e.printStackTrace();
			return "ToText: " + fileName + ".pdf" + "I/O exception";
		}
	}
	
	public String pdf2HTML (PDDocument pdf, String fileName){
		return "";
	}
	
	public static void main(String[] args) throws IOException  {
		//PDDocument pdf = PDDocument.load(new File("C:\\Users\\shirciv\\Desktop\\aws\\git\\AWS1\\src\\main\\FIRST.pdf"));
		
		PDDocument pdf = PDDocument.load(new URL("http://www.thebagelemporium.com/images/BE-Holiday-Menu.pdf"));	
		System.out.println(pdf2Image(pdf,"FIRST"));
		
	}
}