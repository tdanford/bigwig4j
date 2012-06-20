package bigwig.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class InputStreamInflater extends InputStream {
	
	private InputStream inner;
	private Inflater inflater;
	
	private byte[] buffer;
	private int bufferStart, bufferEnd;
	
	private byte[] input;
	
	public InputStreamInflater(InputStream is) { 
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
				throw new IOException(e);
			}
		}
		
		if(bufferStart >= bufferEnd) { 
			return -1;
		}
		
		return (int)(buffer[bufferStart++]);
	}
	
	private void refillBuffer() throws IOException, DataFormatException {
		bufferEnd = inflater.inflate(buffer, 0, buffer.length);
		bufferStart = 0;
		
		if(bufferEnd == 0) { 
			if(inflater.needsInput()) { 
				int inputLength = inner.read(input, 0, input.length);
				
				if(inputLength == -1) {
					throw new IOException("unexpected input end.");
				} else { 
					inflater.setInput(input, 0, inputLength);
					refillBuffer();
				}
				
			} else if(inflater.needsDictionary()) {  
				throw new IOException("needs dictionary");
			}
		}
	}
}
