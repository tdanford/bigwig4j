package bigwig.io;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public class RandomInputStream extends InputStream {
	
	private RandomAccessFile file;

	protected RandomInputStream(RandomAccessFile ins) {
		file = ins;
	}
	
	public boolean markSupported() { return false; }
	
	public long skip(long n) throws IOException { 
		long t = file.getFilePointer();
		long l = file.length();
		long skippable = Math.min(n, l-t);
		file.seek(t + skippable);
		return skippable;
	}
	
	public int available() throws IOException { 
		return (int)(file.length() - file.getFilePointer());
	}

	public int read() throws IOException {
		try { 
			return (int)file.readByte();
		} catch(EOFException e) { 
			return -1;
		}
	}
	
	public int read(byte[] buffer) throws IOException { 
		return read(buffer, 0, buffer.length);
	}
	
	public void close() throws IOException { 
		System.err.println("Closing RandomInputStream...");
		file.close();
	}
	
	public int read(byte[] buffer, int offset, int length) throws IOException { 
		int c = 0;
		try { 
			for(int i = 0; i < length; i++) {
				buffer[offset+i] = file.readByte();

			}
		} catch(EOFException e) { 
		}
		return c;
	}
}
