package bigwig.io;

import java.io.*;
import java.util.zip.*;

public class OutputStreamDeflater extends OutputStream {
	
	private Deflater deflater;
	private OutputStream inner;
	
	private byte[] buffer;
	private int bufferLength;
	
	private byte[] deflated;
	
	public OutputStreamDeflater(OutputStream inner) { 
		this.inner = inner;
		deflater = new Deflater();
		
		buffer = new byte[1024];
		bufferLength = 0;
		
		deflated = new byte[1024];
	}

	public void write(int b) throws IOException {
		buffer[bufferLength++] = (byte)b;
		if(bufferLength >= buffer.length) { 
			flush();
		}		
	}
	
	public void flush() throws IOException { 
		deflater.setInput(buffer, 0, bufferLength);
		deflater.finish();
		
		while(!deflater.finished()) { 
			int count = deflater.deflate(deflated);
			
			if(count > 0) {
				inner.write(deflated, 0, count);
			} else { 
				throw new IllegalStateException();
			}
		}
		bufferLength = 0;
		
		inner.flush();
	}
	
	public void close() throws IOException { 
		flush();
		inner.close();
	}
}
