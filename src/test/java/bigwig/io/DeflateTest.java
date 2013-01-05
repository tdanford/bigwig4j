package bigwig.io;

import java.util.*;
import java.io.*;
import java.nio.charset.Charset;

import static org.testng.Assert.*;
import org.testng.annotations.*;

public class DeflateTest {
	
	private String value = null;
	
	@BeforeClass
	public void loadString() throws IOException { 
		InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("data/test-compress-1.txt");
		StringBuilder sb = new StringBuilder();
		Reader r = new InputStreamReader(is, "UTF-8");
		char[] buffer = new char[255];
		int read = -1;
		while((read = r.read(buffer)) != -1) { 
			for(int i = 0; i < read; i++) { 
				sb.append(buffer[i]);
			}
		}
		value = sb.toString();
		r.close();
	}
	
	public static void printBytes(byte[] bs, int len) { 
		for(int i = 0; i < len; i++) { 
			System.out.print(Byte.toString(bs[i]));
		}
		System.out.println();
	}

	@Test(groups={ "functional" })
	public void testDeflate() throws IOException { 
		System.out.println(String.format("Original: %d bytes", value.getBytes(Charset.defaultCharset()).length));
		
		ByteArrayOutputStream outs = new ByteArrayOutputStream();
		OutputStreamDeflater def = new OutputStreamDeflater(outs);
		PrintWriter ps = new PrintWriter(new OutputStreamWriter(def));
		ps.println(value);
		ps.flush();
		
		byte[] bytes = outs.toByteArray();
		System.out.println(String.format("Compressed: %d bytes", bytes.length));
		
		ByteArrayInputStream ins  =new ByteArrayInputStream(bytes);
		InputStreamInflater inf = new InputStreamInflater(ins);
		BufferedReader br = new BufferedReader(new InputStreamReader(inf));
		
		try {
			String circle = br.readLine();
			//System.out.println(String.format("\"%s\" -> \n\"%s\"", value, circle));
			
		} catch (IOException e) {
			e.printStackTrace(System.err);
		}
	}
}
