package bigwig.io;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class InputStreamDataInflater extends InputStream {
	
	private DataInput inner;
	private Inflater inflater;
	
	private byte[] buffer;
	private int bufferStart, bufferEnd;
	
	private byte[] input;
	
	public InputStreamDataInflater(DataInput is) { 
		inner = is;
		inflater = new Inflater();
		
		buffer = new byte[1024];
		bufferStart = bufferEnd = 0;
		
		input = new byte[1024];
	}

	public int read() throws IOException {
		if(bufferStart >= bufferEnd) { 
			try {
				refillBuffer();
			} catch (DataFormatException e) {
				e.printStackTrace(System.err);
				throw new IOException(e);
			}
		}
		
		if(bufferStart >= bufferEnd) { 
			return -1;
		}
		
		return (int)(buffer[bufferStart++]);
	}
	
	private int readBuffer() throws IOException { 
		int read = 0;
		try { 
			for(int i = 0; i < input.length; i++) { 
				input[i] = inner.readByte();
				read += 1;
			}
		} catch(EOFException e) { 
			return -1;
		}
		return read;
	}
	
	private void refillBuffer() throws IOException, DataFormatException {
		bufferEnd = inflater.inflate(buffer, 0, buffer.length);
		bufferStart = 0;
		
		if(bufferEnd == 0) { 
			if(inflater.needsInput()) {
				int inputLength = readBuffer();
				
				if(inputLength == -1) {
					throw new IOException("unexpected input end.");
				} else if(inflater.needsInput()) { 
					inflater.setInput(input, 0, inputLength);
					refillBuffer();
				} else { 
					throw new IllegalStateException();
				}
				
			} else if(inflater.needsDictionary()) {  
				throw new IOException("needs dictionary");
			}
		}
	}
}
