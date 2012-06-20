package bigwig.io;

import java.util.*;
import java.io.*;
import java.nio.charset.Charset;


public class DeflateTest {
	
	public static void main(String[] args) throws IOException { 
		test("FLOO BAR BLAHA #$@#$^^#$%FLOO BAR BLAHA #$@#$^^#$%FLOO BAR BLAHA #$@#$^^#$%FLOO BAR BLAHA #$@#$^^#$%");
	}
	
	public static void printBytes(byte[] bs, int len) { 
		for(int i = 0; i < len; i++) { 
			System.out.print(Byte.toString(bs[i]));
		}
		System.out.println();
	}

	public static void test(String value) throws IOException { 
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
			System.out.println(String.format("\"%s\" -> \n\"%s\"", value, circle));
			
		} catch (IOException e) {
			e.printStackTrace(System.err);
		}
	}
}
