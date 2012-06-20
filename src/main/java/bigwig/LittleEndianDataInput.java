package bigwig;

import java.io.DataInput;
import java.io.IOException;

public class LittleEndianDataInput implements DataInput {
	
	private DataInput inner;
	
	public LittleEndianDataInput(DataInput in) { 
		inner = in;
	}

	public boolean readBoolean() throws IOException {
		return inner.readBoolean();
	}

	public byte readByte() throws IOException {
		return inner.readByte();
	}

	public char readChar() throws IOException {
		return Character.reverseBytes(inner.readChar());
	}

	public double readDouble() throws IOException {
		return Double.longBitsToDouble(readLong());
	}

	public float readFloat() throws IOException {
		return Float.intBitsToFloat(readInt());
	}

	public void readFully(byte[] ba) throws IOException {
		inner.readFully(ba);
	}

	public void readFully(byte[] arg0, int arg1, int arg2) throws IOException {
		inner.readFully(arg0, arg1, arg2);
	}

	public int readInt() throws IOException {
		return Integer.reverseBytes(inner.readInt());
	}

	public String readLine() throws IOException {
		return inner.readLine();
	}

	public long readLong() throws IOException {
		return Long.reverseBytes(inner.readLong());
	}

	public short readShort() throws IOException {
		return Short.reverseBytes(inner.readShort());
	}

	public String readUTF() throws IOException {
		return inner.readUTF();
	}

	public int readUnsignedByte() throws IOException {
		return Integer.reverseBytes(inner.readUnsignedByte());
	}

	public int readUnsignedShort() throws IOException {
		short innerShort = (short)inner.readUnsignedShort();
		return (int)Short.reverseBytes(innerShort);
	}

	public int skipBytes(int arg0) throws IOException {
		return inner.skipBytes(arg0);
	}

}
