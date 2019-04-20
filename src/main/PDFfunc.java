import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.Object;
import java.net.MalformedURLException;
import java.net.URL;

import javax.imageio.ImageWriteParam;
import javax.imageio.spi.ServiceRegistry.Filter;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.util.PDFImageWriter;
import org.apache.pdfbox.util.PDFText2HTML;
import org.apache.pdfbox.util.PDFTextStripper;


import javafx.scene.image.WritableImage;


public class PDFfunc{
		
	public static File pdf2Image (PDDocument pdf, String fileName) throws IOException{
			PDFImageWriter ImWriter = new PDFImageWriter();
			
			if (ImWriter.writeImage(pdf,"png","" , 1, 1, fileName)) 
				return new File(fileName+"1.png");
			else
				throw new IOException(); //TODO:change this to fitting exception
	}
	
	public static File pdf2Text (PDDocument pdf, String fileName) throws IOException{
					
			PDFTextStripper textStrip = new PDFTextStripper();
			textStrip.setEndPage(1);  //only convert the text from the first page
			
		    String text =  textStrip.getText(pdf);
		    
		    // write the content to text file
		    PrintWriter pw = new PrintWriter(new FileWriter(fileName + ".txt")); 

			pw.write(text); //write the text to the output file
		 
			pw.close();	
			return new File(fileName+".txt");

	}
	
	public File pdf2HTML (PDDocument pdf, String fileName) throws IOException{
		PDFText2HTML pdfTextStripper = new PDFText2HTML("UTF-8");
		pdfTextStripper.setEndPage(1);
		String text = pdfTextStripper.getText(pdf);
		
		PrintWriter pw = new PrintWriter(new FileWriter(fileName + ".html")); 
		pw.write(text);		 
		pw.close();	
		return new File(fileName+".html");
	}
	
	public File handle (String operation, String url) throws MalformedURLException, IOException{
		
		PDDocument curr_pdf = null;
			curr_pdf = PDDocument.load(new URL(url)); //extract the pdf file from given URL
			String file_name = getPDFname(url);	
			System.out.println("passed url");
				switch(operation){
					case "ToImage":
						return pdf2Image(curr_pdf,file_name);
					case "ToText":
						return pdf2Text(curr_pdf,file_name);
					case "ToHTML":
						return pdf2HTML(curr_pdf,file_name);
					default:
						System.out.println("incorrect operation"); //not supposed to happen- assumption valid operation input
						return null;
					}

		}
	
	private static String getPDFname(String url){
		String [] splitted = url.split("/");
		return splitted[splitted.length-1].split("\\.")[0]; //split the name from .pdf
		
	}

	public static void main(String[] args) throws IOException  {
		//PDDocument pdf = PDDocument.load(new File("C:\\Users\\shirciv\\Desktop\\aws\\git\\AWS1\\src\\main\\FIRST.pdf"));
		
		//PDDocument pdf = PDDocument.load(new URL("http://www.thebagelemporium.com/images/BE-Holiday-Menu.pdf"));	
		//System.out.println(pdf2Image(pdf,"FIRST"));
		
		//System.out.println(getPDFname("http://www.thebagelemporium.com/images/BE-Holiday-Menu.pdf"));
		
	}
}