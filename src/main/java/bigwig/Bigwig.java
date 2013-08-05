package bigwig;

import java.io.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.*;

/*
 * Structure: 
    bbiHeader   64  
        Contains high-level information about file and offsets to various parts of file.  See Supp. Table 5.
    zoomHeaders N*24    
        One for each level of zoom built into file.  See Supp. Table 6. 
    autoSql Varies      
        Zero-terminated string in autoSql format describing formats.  Optional, not used in BigWig.  See Supp. Table 2.  
    totalSummary    40  
        Statistical summary of entire file.  See Supp. Table 7. Only in files of version 2 and later.
    chromosomeTree  Varies      
        B+ tree-formatted index of chromosomes, their sizes, and a unique ID for each. See Supp. Tables 8-11.
    dataCount   4   
        Number of records in data. For BigWig this corresponds to the number of sections, not the number of floating point values.
    data    Varies  
        Possibly compressed data in format specific for file type. See Supp. Tables 12 and 13.
    index   Varies  
        R tree index of data.  See Supp. Tables 14 and 15.
    zoomInfo    Varies  
        One for each zoom level.
 */

/*
 * bbiHeader: 
    magic   4   uint    
        0x888FFC26 for BigWig, 0x8789F2EB for BigBed.  If byte-swapped, all numbers in file are byte-swapped.
    version 2   uint    
        Currently 3. 
    zoomLevels  2   uint    
        Number of different zoom summary resolutions.
    chromosomeTreeOffset    8   uint    
        Offset in file to chromosome B+ tree index.
    fullDataOffset  8   uint    
        Offset to main (unzoomed) data.  Points specifically to the dataCount.
    fullIndexOffset 8   uint    
        Offset to R tree index of items.
    fieldCount  2   uint    
        Number of fields in BED file.  (0 for BigWig)
    definedFieldCount   2   uint    
        Number of fields that are predefined BED fields.
    autoSqlOffset   8   uint    
        Offset to zero-terminated string with .as spec.
    totalSummaryOffset  8   uint    
        Offset to overall file summary data block.
    uncompressBufSize   4   uint    
        Maximum size of decompression buffer needed (nonzero on compressed files).  Used only on files of version 3 and later.
    reserved    8   uint    
        Reserved for future expansion. Currently 0.
*/

/*
 * ZoomHeader: 
reductionLevel	4	uint	Number of bases summarized in each reduction level.
reserved	4	uint	Reserved for future expansion.  Currently 0. 
dataOffset	8	uint	Position of zoomed data in file.
indexOffset	8	uint	Position of zoomed data index in file.
 */

/*
 * Total Summary Block: 
basesCovered	8	uint	Number of bases for which there is data.
minVal	8	float	Minimum value in file. 
maxVal	8	float	Maximum value in file.
sumData	8	float	Sum of all values in file.
sumSquares	8	float	Sum of all squares of values in file.
 */

/*
 * Section Header: 
chromId	4	uint	Numerical ID for chromosome/contig.
chromStart	4	uint	Start position (starting with 0).
chromEnd	4	uint	End of item. Same as chromStart + itemSize in bases. 
itemStep	4	uint	Spaces between start of adjacent items in fixedStep sections.
itemSpan	4	uint	Number of bases in item in fixedStep and varStep sections.
type	1	uint	Section type. 1 for bedGraph, 2 for varStep, 3 for fixedStep.
reserved	1	uint	Currently 0.
itemCount	2	uint	Number of items in section.
 */

/*
 * R-Tree Index header
magic	4	uint	0x2468ACE0.  If byte-swapped all numbers in index are byte-swapped.
blockSize	4	uint	Number of children per block (not byte size of block). 
itemCount	8	uint	The number of chromosomes/contigs.
startChromIx	4	uint	ID of first chromosome in index.
startBase	4	uint	Position of first base in index.
endChromIx	4	uint	ID of last chromosome in index.
endBase	4	uint	Position of last base in index.
endFileOffset	8	uint	Position in file where data being indexed ends.
itemsPerSlot	4	uint	Number of items pointed to by leaves of index.
Reserved	4	uint	Reserved for future expansion. Currently 0.

 */

public class Bigwig { 
	
    public static String testFilename = "wgEncodeCaltechRnaSeqH1hescR2x75Th1014Il200SigRep1.bigwig";

    public static void main(String[] args) throws IOException { 
        Bigwig bw = null;
        try { 
            bw = new Bigwig(testFilename);
        } catch(Exception e) { 
            System.err.println(String.format("\"%s\"", e.getMessage()));
            e.printStackTrace(System.err);
        } finally { 
            if(bw != null) { 
                bw.close();
            }
        }
    }

	public class RTreeIndexHeader {
		
		public boolean _flipped;
		
		public int magic;
		public int blockSize;
		public long itemCount;
		public int startChromIdx;
		public int startBase;
		public int endChromIdx;
		public int endBase;
		public long endFileOffset;
		public int itemsPerSlot;
		public int reserved;
		
		public RTreeIndexHeader() throws IOException { 
			this(file);
		}
		
		public RTreeIndexHeader(DataInput dis) throws IOException { 
			magic = dis.readInt();
			_flipped = (magic == RTREE_MAGIC_FLIPPED);
			
			blockSize = readInt(dis, _flipped);
			itemCount = readLong(dis, _flipped);

			startChromIdx = readInt(dis, _flipped);
			startBase = readInt(dis, _flipped);
			endChromIdx = readInt(dis, _flipped);
			endBase = readInt(dis, _flipped);

			endFileOffset = readLong(dis, _flipped);
			itemsPerSlot = readInt(dis, _flipped);
			reserved = readInt(dis, _flipped);
		}

		public RTreeNode readNode() throws IOException {
			return new RTreeNode(_flipped);
		}		
	}
	
	public static String indent(int d) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < d; i++) { sb.append("  "); }
		return sb.toString();
	}
	
	public void printRTree(RTreeNode node, int d) throws IOException { 
		printObject(node, d);
		if(node.isLeaf != 0) { 
			RTreeLeaf[] leaves = node.getChildLeaves();
			for(int i = 0; i < leaves.length; i++) { 
				System.out.println();
				printObject(leaves[i], d+1);
			}
		} else { 
			RTreeNonLeaf[] children = node.getChildNonLeaves();
			for(int i = 0; i < children.length; i++) { 
				System.out.println();
				printObject(children[i], d+1);
				printRTree(children[i].getNode(), d+1);
			}
		}
 	}
	
	public class RTreeNode {
		
		public long _position;
		public boolean _flipped;
		
		public byte isLeaf;
		public byte reserved;
		public short count;
		
		public RTreeNode(boolean flipped) throws IOException {
			_flipped = flipped;
			
			isLeaf = readByte(file, flipped);
			reserved = readByte(file, flipped);
			count = readShort(file, flipped);
			_position = file.getFilePointer();
		}
		
		public RTreeLeaf[] getChildLeaves() throws IOException { 
			file.seek(_position);
			RTreeLeaf[] array = new RTreeLeaf[count];
			for(int i = 0; i < array.length; i++) { 
				array[i] = new RTreeLeaf(_flipped);
			}
			return array;
		}
		
		public RTreeNonLeaf[] getChildNonLeaves() throws IOException { 
			file.seek(_position);
			RTreeNonLeaf[] array = new RTreeNonLeaf[count];
			for(int i = 0; i < array.length; i++) { 
				array[i] = new RTreeNonLeaf(_flipped);
			}
			return array;			
		}
		
		public RTreeLeaf getChildLeaf(int i) throws IOException { 
			file.seek(_position + i * RTreeLeaf.SIZE);
			return new RTreeLeaf(_flipped);
		}
		
		public RTreeNonLeaf getChildNonLeaf(int i) throws IOException { 
			file.seek(_position + i * RTreeNonLeaf.SIZE);
			return new RTreeNonLeaf(_flipped);
		}
		
		public boolean isLeaf() { 
			return isLeaf != 0;
		}
		
		public SortedSet<DataBlock> findOverlappingBlocks(int chromId, int start, int end) throws IOException {
			TreeSet<DataBlock> blocks = new TreeSet<DataBlock>();
			if(isLeaf()) { 
				for(RTreeLeaf leaf : getChildLeaves()) { 
					if(leaf.overlaps(chromId, start, end)) { 
						blocks.add(new DataBlock(leaf));
					}
				}
			} else { 
				for(RTreeNonLeaf nonLeaf : getChildNonLeaves()) { 
					if(nonLeaf.overlaps(chromId, start, end)) { 
						blocks.addAll(nonLeaf.getNode().findOverlappingBlocks(chromId, start, end));
					}
				}
			}
			
			return blocks;
		}

		public SortedSet<RTreeLeaf> findOverlappingLeaves(int chromId, int start, int end) throws IOException {
			TreeSet<RTreeLeaf> blocks = new TreeSet<RTreeLeaf>();
			if(isLeaf()) { 
				for(RTreeLeaf leaf : getChildLeaves()) { 
					if(leaf.overlaps(chromId, start, end)) { 
						blocks.add(leaf);
					}
				}
			} else { 
				for(RTreeNonLeaf nonLeaf : getChildNonLeaves()) { 
					if(nonLeaf.overlaps(chromId, start, end)) { 
						blocks.addAll(nonLeaf.getNode().findOverlappingLeaves(chromId, start, end));
					}
				}
			}
			return blocks;
		}
	}
	
	public class RTreeLeaf implements Comparable<RTreeLeaf> {
		
		public static final int SIZE = 32;
		
		public boolean _flipped;
		
		public int startChromIx;
		public int startBase;
		public int endChromIx;
		public int endBase;
		public long dataOffset;
		public long dataSize;
		
		public RTreeLeaf(boolean flipped) throws IOException {
			_flipped = flipped;
			
			startChromIx = readInt(file, flipped);
			startBase = readInt(file, flipped);
			endChromIx = readInt(file, flipped);
			endBase = readInt(file, flipped);
			dataOffset = readLong(file, flipped);
			dataSize = readLong(file, flipped);
		}
		
		public boolean overlaps(int chromId, int start, int end) { 
			return compareLocs(chromId, end, startChromIx, startBase) < 0 &&
				compareLocs(chromId, start, endChromIx, endBase) > 0;
		}

		public int hashCode() { 
			int code = 17;
			code += startChromIx; code *= 37;
			code += startBase; code *= 37;
			code += endBase; code *= 37;
			return code;
		}
		
		public boolean equals(Object o) { 
			if(!(o instanceof RTreeLeaf)) { return false; }
			RTreeLeaf b = (RTreeLeaf)o;
			if(startChromIx != b.startChromIx || endChromIx != b.endChromIx) { return false; }
			if(startBase != b.startBase || endBase != b.endBase) { return false; }
			return true;
		}
		
		public int compareTo(RTreeLeaf b) { 
			if(startChromIx < b.startChromIx) { return -1; }
			if(startChromIx > b.startChromIx){ return 1; }
			if(endChromIx < b.endChromIx) { return -1; }
			if(endChromIx > b.endChromIx){ return 1; }
			if(startBase < b.startBase){ return -1; }
			if(startBase > b.startBase) { return 1; }
			if(endBase < b.endBase){ return -1; }
			if(endBase > b.endBase) { return 1; }
			return 0;
		}

		public String toString() { 
			return String.format("%d:%d-%d:%d", startChromIx, startBase, endBase, endChromIx);
		}
		
	}
	
	public static int compareLocs(int qChrom, int qPos, int rChrom, int rPos) { 
		if(rChrom > qChrom) { return 1; } 
		if(qChrom > rChrom) { return -1; }
		if(rPos > qPos) { return 1; }
		if(qPos > rPos) { return -1; }
		return 0;
	}
	
	public class DataBlock implements Comparable<DataBlock> { 
		
		public int startChromIx, endChromIx;
		public int startBase, endBase;
		
		public long offset, size;
		
		public DataBlock(RTreeLeaf leaf) { 
			offset = leaf.dataOffset;
			size = leaf.dataSize;
			startChromIx = leaf.startChromIx;
			endChromIx = leaf.endChromIx;
			startBase = leaf.startBase;
			endBase = leaf.endBase;
		}
		
		public byte[] data() throws IOException { 
			byte[] b = new byte[(int)size];
			file.seek(offset);
			file.read(b);
			return b;
		}
		
		public String toString() { 
			return String.format("%d:%d-%d:%d", startChromIx, startBase, endBase, endChromIx);
		}
		
		public byte[] inflate() throws IOException, DataFormatException { 
			byte[] compressed = data();
			byte[] uncompressed = new byte[header.uncompressBufSize];
			Inflater inflater = new Inflater(false);

			inflater.setInput(compressed);
			inflater.inflate(uncompressed);
		
			return uncompressed;
		}
		
		public int hashCode() { 
			int code = 17;
			code += startChromIx; code *= 37;
			code += startBase; code *= 37;
			code += endBase; code *= 37;
			return code;
		}
		
		public boolean equals(Object o) { 
			if(!(o instanceof DataBlock)) { return false; }
			DataBlock b = (DataBlock)o;
			if(startChromIx != b.startChromIx || endChromIx != b.endChromIx) { return false; }
			if(startBase != b.startBase || endBase != b.endBase) { return false; }
			return true;
		}
		
		public int compareTo(DataBlock b) { 
			if(startChromIx < b.startChromIx) { return -1; }
			if(startChromIx > b.startChromIx){ return 1; }
			if(endChromIx < b.endChromIx) { return -1; }
			if(endChromIx > b.endChromIx){ return 1; }
			if(startBase < b.startBase){ return -1; }
			if(startBase > b.startBase) { return 1; }
			if(endBase < b.endBase){ return -1; }
			if(endBase > b.endBase) { return 1; }
			return 0;
		}
		
	}
	
	public class RTreeNonLeaf { 
		
		public static final int SIZE = 24;
		
		public boolean _flipped;
		
		public int startChromIx;
		public int startBase;
		public int endChromIx;
		public int endBase;
		public long dataOffset;
		
		public RTreeNonLeaf(boolean flipped) throws IOException {
			_flipped = flipped;
			startChromIx = readInt(file, flipped);
			startBase = readInt(file, flipped);
			endChromIx = readInt(file, flipped);
			endBase = readInt(file, flipped);
			dataOffset = readLong(file, flipped);
		}
		
		public RTreeNode getNode() throws IOException { 
			file.seek(dataOffset);
			return new RTreeNode(_flipped);
		}

		public boolean overlaps(int chromId, int start, int end) { 
			return compareLocs(chromId, end, startChromIx, startBase) < 0 &&
				compareLocs(chromId, start, endChromIx, endBase) > 0;
		}
	}
	
	public class BigBedSection { 
		
		public int chromId;
		public int chromStart, chromEnd;
		
		public BigBedSection(DataInput inp, boolean flip) throws IOException { 
			chromId = readInt(inp, flip);
			chromStart = readInt(inp, flip);
			chromEnd = readInt(inp, flip);
		}
	}
	
    public class BinaryWIGSectionHeader { 
    	
    	public int chromId;
    	public int chromStart;
    	public int chromEnd;
    	public int itemStep;
    	public int itemSpan;
    	public byte type;
    	public byte reserved;
    	public short itemCount;
    	
    	public ArrayList<Float> values;
    	public ArrayList<Integer> chromStarts, chromEnds;
    	
    	public BinaryWIGSectionHeader() throws IOException { 
    		this(file, true);
    	}
    	
    	public BinaryWIGSectionHeader(byte[] b, boolean flip) throws IOException { 
    		this(new DataInputStream(new ByteArrayInputStream(b)), flip);
    	}
    	
    	public BinaryWIGSectionHeader(DataInput dis, boolean flip) throws IOException { 
    		chromId = readInt(dis, flip);
    		chromStart = readInt(dis, flip);
    		chromEnd = readInt(dis, flip);
    		itemStep = readInt(dis, flip);
    		itemSpan = readInt(dis, flip);
    		type = readByte(dis, flip);
    		reserved = readByte(dis, flip);
    		itemCount = readShort(dis, flip);
    		
    		values = new ArrayList<Float>();
    		if(type < WIGTYPE_FIXEDSTEP) { 
    			chromStarts = new ArrayList<Integer>();
    			
    			if(type < WIGTYPE_VARSTEP) { 
    				chromEnds = new ArrayList<Integer>();
    			}
    		} 
    		
    		for(int i = 0; i < itemCount; i++) { 
        		if(type < WIGTYPE_FIXEDSTEP) { 
        			chromStarts.add(readInt(dis, flip));
        			
        			if(type < WIGTYPE_VARSTEP) { 
        				chromEnds.add(readInt(dis, flip));
        			}
        		} 
        		
        		values.add(readFloat(dis, flip));
    		}
    	}
    }
    
    public class ChromosomeBTreeHeader { 
    	
    	public int magic;
    	public boolean _flipped;
    	
    	public int blockSize;
    	public int keySize;
    	public int valSize;
    	public long itemCount;
    	public long reserved;
    	
    	public ChromosomeBTreeHeader() throws IOException { 
    		magic = readInt(file, false);
    		_flipped = magic == BTREE_MAGIC_FLIPPED;
    		
    		blockSize = readInt(file, _flipped);
    		keySize = readInt(file, _flipped);
    		valSize = readInt(file, _flipped);
    		itemCount = readLong(file, _flipped);
    		reserved = readLong(file, _flipped);
    	}
    }
    
    public class ChromosomeBTreeNode {
    	
    	public int _keySize;
    	public boolean _flipped;
    	
    	public byte isLeaf;
    	public byte reserved;
    	public short count;
    	
    	public long _position;
    	
    	public ChromosomeBTreeNode(int keySize, boolean flipped) throws IOException {
    		_flipped = flipped;
    		_keySize = keySize;
    		
    		isLeaf = readByte(file, _flipped);
    		reserved = readByte(file, _flipped);
    		count = readShort(file, _flipped);
    		
    		_position = file.getFilePointer();
    	}
    	
    	public boolean isLeaf() { 
    		return isLeaf != 0;
    	}
    	
    	public ChromosomeBTreeLeaf[] leaves() throws IOException { 
    		ChromosomeBTreeLeaf[] array = new ChromosomeBTreeLeaf[count];
    		file.seek(_position);
    		for(int i = 0; i < array.length; i++) {
    			array[i] = new ChromosomeBTreeLeaf(_keySize, _flipped);
    		}
    		return array;
    	}
    	
    	public ChromosomeBTreeNonLeaf[] nonLeaves() throws IOException { 
    		ChromosomeBTreeNonLeaf[] array = new ChromosomeBTreeNonLeaf[count];
    		file.seek(_position);
    		for(int i = 0; i < array.length; i++) {
    			array[i] = new ChromosomeBTreeNonLeaf(_keySize, _flipped);
    		}
    		return array;
    	}
    	
    	public ChromosomeBTreeLeaf getLeaf(int i) throws IOException { 
    		long offset = _position + i * (_keySize + 8);
    		file.seek(offset);
    		return new ChromosomeBTreeLeaf(_keySize, _flipped);
    	}

    	public ChromosomeBTreeNonLeaf getNonLeaf(int i) throws IOException { 
    		long offset = _position + i * (_keySize + 8);
    		file.seek(offset);
    		return new ChromosomeBTreeNonLeaf(_keySize, _flipped);
    	}
    }
    
    public class ChromosomeBTreeLeaf {
    	
    	public boolean _flipped;
    	
    	public byte[] key;
    	public int chromId;
    	public int chromSize;
    	
    	public ChromosomeBTreeLeaf(int keySize, boolean flip) throws IOException {
    		_flipped = flip;
    		key = readBytes(keySize, file, _flipped);
    		chromId = readInt(file, _flipped);
    		chromSize = readInt(file, _flipped);
    	}
    }
    
    public static Charset UTF8 = Charset.forName("UTF-8");
    
    public Map<String,Integer> getChromIdMap(ChromosomeBTreeNode top) throws IOException { 
    	Map<String,Integer> map = new TreeMap<String,Integer>();
    	
    	if(top.isLeaf()) { 
    		ChromosomeBTreeLeaf[] leaves = top.leaves();
    		for(ChromosomeBTreeLeaf leaf : leaves) { 
    			ByteBuffer buffer = ByteBuffer.wrap(leaf.key);
    			String strKey = UTF8.decode(buffer).toString();
    			int chromId = leaf.chromId;
    			map.put(strKey, chromId);
    		}
    	} else { 
    		for(ChromosomeBTreeNonLeaf nonleaf : top.nonLeaves()) { 
    			Map<String,Integer> nonleafMap = getChromIdMap(nonleaf.getNode());
    			map.putAll(nonleafMap);
    		}
    	}
    	
    	return map;
    }
    
    public class ChromosomeBTreeNonLeaf { 
    	
    	public boolean _flipped;
    	public int _keySize;

    	public byte[] key;
    	public long childOffset;
    	
    	public ChromosomeBTreeNonLeaf(int keySize, boolean flip) throws IOException {
    		_flipped = flip;
    		_keySize = keySize;
    		
    		key = readBytes(keySize, file, _flipped);
    		childOffset = readLong(_flipped);
    	}
    	
    	public ChromosomeBTreeNode getNode() throws IOException { 
    		file.seek(childOffset);
    		return new ChromosomeBTreeNode(_keySize, _flipped);
    	}
    }

    public class TotalSummaryBlock { 
    	public long basesCovered;
    	public double minVal; 
    	public double maxVal;
    	public double sumData;
    	public double sumSquares;
    	
    	public TotalSummaryBlock() throws IOException { 
    		basesCovered = readLittleLong();
    		minVal = readLittleDouble();
    		maxVal = readLittleDouble();
    		sumData = readLittleDouble();
    		sumSquares = readLittleDouble();
    	}
    }
    
    public class ZoomHeader { 
    	
    	public int reductionLevel;
    	public int reserved;
    	public long dataOffset;
    	public long indexOffset;
    	
    	public ZoomHeader() throws IOException { 
    		this(file, true);
    	}
    	
    	public ZoomHeader(DataInput dis, boolean flip) throws IOException { 
    		reductionLevel = readInt(dis, flip);
    		reserved = readInt(dis, flip);
    		dataOffset = readLong(dis, flip);
    		indexOffset = readLong(dis, flip);
    	}
    }
    
    public class ZoomData { 
    	
    	public int chromId;
    	public int chromStart;
    	public int chromEnd;
    	public int validCount;
    	public float minVal, maxVal;
    	public float sumData, sumSquares;
    	
    	public ZoomData(byte[] bytes, boolean flip) throws IOException { 
    		this(new DataInputStream(new ByteArrayInputStream(bytes)), flip);
    	}
    	
    	public ZoomData(DataInput dis, boolean flip) throws IOException { 
    		chromId = readInt(dis, flip);
    		chromStart = readInt(dis, flip);
    		chromEnd = readInt(dis, flip);
    		validCount = readInt(dis, flip);
    		minVal = readFloat(dis, flip);
    		maxVal = readFloat(dis, flip);
    		sumData = readFloat(dis, flip);
    		sumSquares = readFloat(dis, flip);
    	}
    }

    public class Header { 
    	
        public int magic;
        public short version;
        public short zoomLevels;
        public long chromosomeTreeOffset;
        public long fullDataOffset;
        public long fullIndexOffset;
        public short fieldCount;
        public short definedFieldCount;
        public long autoSqlOffset;
        public long totalSummaryOffset;
        public int uncompressBufSize;
        public long reserved;

        public Header() throws IOException { 
            magic = readLittleInt();
            if(magic != BIGWIG_MAGIC) { throw new IllegalArgumentException(Integer.toHexString(magic)); }
            
    
            version = readLittleShort();
            zoomLevels = readLittleShort();
            chromosomeTreeOffset = readLittleLong();
            fullDataOffset = readLittleLong();
            fullIndexOffset = readLittleLong();
            fieldCount = readLittleShort();
            definedFieldCount = readLittleShort();
            autoSqlOffset = readLittleLong();
            totalSummaryOffset = readLittleLong();
            uncompressBufSize = readLittleInt();
            reserved = readLittleLong();
        }
    }
    
    public static final int BIGWIG_MAGIC = Long.decode("0x888FFC26").intValue();

    public static final int BTREE_MAGIC = Long.decode("0x78CA8C91").intValue();
    public static final int BTREE_MAGIC_FLIPPED = Integer.reverseBytes(BTREE_MAGIC);
    
    public static final int RTREE_MAGIC = Long.decode("0x2468ACE0").intValue();
    public static final int RTREE_MAGIC_FLIPPED = Integer.reverseBytes(RTREE_MAGIC);

    public static final byte WIGTYPE_BEDGRAPH = 1;
    public static final byte WIGTYPE_VARSTEP = 2;
    public static final byte WIGTYPE_FIXEDSTEP = 3;

    private RandomAccessFile file;
    
    public Header header;
    public ZoomHeader[] zoomHeaders;
    public int[] zoomCounts;
    
    public TotalSummaryBlock totalSummary;
    public int dataCount;
    
    public ChromosomeBTreeHeader bTreeHeader;
    public Map<String,Integer> chromIds;
    
    public RTreeIndexHeader indexHeader;

    public Bigwig(String filename) throws IOException {
        this(new File(filename));
    }

    public Bigwig(File f) throws IOException {
        file = new RandomAccessFile(f.getAbsolutePath(), "r");
        header = new Header();
        
        printObject("Header", header);
        
        zoomHeaders = new ZoomHeader[header.zoomLevels];
        zoomCounts = new int[zoomHeaders.length];
        
        for(int i = 0; i < zoomHeaders.length; i++) { 
        	zoomHeaders[i] = new ZoomHeader();
        	long pos = file.getFilePointer();
        	
            printObject("ZoomHeader " + i, zoomHeaders[i]);
            
            file.seek(zoomHeaders[i].dataOffset);
            zoomCounts[i] = readInt(file, true);
            System.out.println(String.format("Zoom Count: %d", zoomCounts[i]));
            
            file.seek(pos);
        }
        
        file.seek(header.totalSummaryOffset);
        totalSummary = new TotalSummaryBlock();
        printObject("Total Summary", totalSummary);
        
        file.seek(header.fullDataOffset);
        dataCount = readLittleInt();
        System.out.println(String.format("dataCount: %d", dataCount));
        System.out.println(String.format("file position: %d / %d", file.getFilePointer(), file.length()));
        
        file.seek(header.fullIndexOffset);
        indexHeader = new RTreeIndexHeader();
        printObject("RTree Index Header", indexHeader);
        
        file.seek(header.chromosomeTreeOffset);
        bTreeHeader = new ChromosomeBTreeHeader();
        ChromosomeBTreeNode topBTreeNode = new ChromosomeBTreeNode(bTreeHeader.keySize, bTreeHeader._flipped);
        
        printObject("BTree Header", bTreeHeader);
        chromIds = getChromIdMap(topBTreeNode);
        System.out.println(chromIds.toString());
        
        //RTreeNode topNode = new RTreeNode(indexHeader._flipped);
        //printObject("Top Node", topNode);
        
        file.seek(zoomHeaders[3].indexOffset);
        RTreeIndexHeader zoomIndexHeader = new RTreeIndexHeader();
        RTreeNode topNode = zoomIndexHeader.readNode();

        int chr1Id = 0;
        int start = 1000000;
        int end = 3000000;
        Set<RTreeLeaf> leaves = topNode.findOverlappingLeaves(chr1Id, start, end);
        
        for(RTreeLeaf leaf : leaves) {
        	System.out.println();
        	System.out.println(leaf.toString());
        	
        	DataBlock block = new DataBlock(leaf);
        	try {
				byte[] uncompressed = block.inflate();
				DataInput input = new DataInputStream(new ByteArrayInputStream(uncompressed));

				for(int i = 0; i < zoomIndexHeader.itemsPerSlot; i++) { 
					ZoomData zoomData = new ZoomData(input, true);
					printObject("zoomData", zoomData);
				}
				
			} catch (DataFormatException e) {
				e.printStackTrace();
			}
        	
        	/*
        	try {
				byte[] bytes = block.inflate();
				BinaryWIGSectionHeader wig = new BinaryWIGSectionHeader(bytes, true);
				printObject("WIG", wig);
				
			} catch (DataFormatException e) {
				e.printStackTrace();
			}
			*/
        }
        
    }
    
    public int convertInt(byte[] bs, int offset) { 
        int v = 0;
        v = bs[offset];
        for(int i = 1; i < 4; i++) { 
        	v = v << 8;
        	v |= bs[offset+i];
        }
        System.out.println(String.format("%d (%d)", v, Integer.reverseBytes(v)));
        return v;    	
    }
    
    public int readIntByBytes(InputStream is) throws IOException { 
        byte[] bs = new byte[4];
        is.read(bs);
        return convertInt(bs, 0);
    }

    public void close() throws IOException { 
        if(file != null) { file.close(); }
    }
    
    public static void printObject(String key, Object obj) { 
    	System.out.println(key + ":");
    	printObject(obj, 1);
    }
   
    public byte[] inflate(long offset, int len) throws DataFormatException, IOException { 
    	file.seek(offset);
    	byte[] input = new byte[len];
    	file.read(input);
    	return inflate(input);
    }
    
    public static byte[] inflate(byte[] input) throws DataFormatException { 
    	Inflater inflater = new Inflater();
    	inflater.setInput(input);
    	ByteArrayOutputStream outs = new ByteArrayOutputStream();
    	byte[] buffer = new byte[Math.max(1024, input.length)];
    	while(!inflater.finished()) { 
    		int inflated = inflater.inflate(buffer);
    		outs.write(buffer, 0, inflated);
    	}
    	return outs.toByteArray();
    }
    
    public static void printObject(Object obj) { 
    	printObject(obj, 0);
    }

    public static void printObject(Object obj, int d) { 
        Class cls = obj.getClass();
        String ind = indent(d);
        System.out.println(String.format("%s* %s", ind, cls.getSimpleName()));
        
        Field[] fs = cls.getFields();
        for(Field f : fs) { 
        	int mod = f.getModifiers();
        	if(Modifier.isPublic(mod) && !Modifier.isStatic(mod)) { 
        		try {
					Object value = f.get(obj);
					if(f.getName().toLowerCase().indexOf("magic") != -1 && int.class.isAssignableFrom(f.getType())) {
						int v = (Integer)value;
						System.out.println(String.format("%s%s=%s", ind, f.getName(), Integer.toHexString(v))); 
								
					} else { 
						System.out.println(String.format("%s%s=%s", ind, f.getName(), String.valueOf(value)));
					}
					
				} catch (IllegalAccessException e) {
					e.printStackTrace(System.err);
				}
        		
        	}
        }
    }

    public static byte[] readBytes(int size, DataInput input, boolean flipped) throws IOException {
    	byte[] array = new byte[size];
    	input.readFully(array);
    	return array;
    }
    
    public static short readShort(DataInput dis, boolean flip) throws IOException { 
    	return flip ? Short.reverseBytes(dis.readShort()) : dis.readShort();
    }

    public static int readInt(DataInput dis, boolean flip) throws IOException { 
    	return flip ? Integer.reverseBytes(dis.readInt()) : dis.readInt();
    }
    
    public static float readFloat(DataInput dis, boolean flip) throws IOException { 
    	return Float.intBitsToFloat(readInt(dis, flip));
    }

    public static byte readByte(DataInput dis, boolean flip) throws IOException { 
    	return dis.readByte();
    }

    public static long readLong(DataInput dis, boolean flip) throws IOException { 
    	return flip ? Long.reverseBytes(dis.readLong()) : dis.readLong();
    }

    public short readLittleShort() throws IOException { return Short.reverseBytes(file.readShort()); }
    public int readLittleInt() throws IOException { return Integer.reverseBytes(file.readInt()); }
    public long readLittleLong() throws IOException { return Long.reverseBytes(file.readLong()); }
    public byte readLittleByte() throws IOException { return file.readByte(); }
    
    public short readShort(boolean flip) throws IOException { 
    	return flip ? Short.reverseBytes(file.readShort()) : file.readShort(); 
    }
    
    public int readInt(boolean flip) throws IOException { 
    	return flip ? Integer.reverseBytes(file.readInt()) : file.readInt(); 
    }
    
    public long readLong(boolean flip) throws IOException { 
    	return flip ? Long.reverseBytes(file.readLong()) : file.readLong(); 
    }
    
    public byte readByte(boolean flip) throws IOException { return file.readByte(); }
    
    public float readLittleFloat() throws IOException { 
    	return Float.intBitsToFloat(readLittleInt());
    }
    
    public double readLittleDouble() throws IOException { 
    	return Double.longBitsToDouble(readLittleLong());
    }
    
    public float readFloat(boolean flip) throws IOException { 
    	return Float.intBitsToFloat(readInt(flip));
    }
    
    public double readDouble(boolean flip) throws IOException { 
    	return Double.longBitsToDouble(readLong(flip));
    }
    
    public byte[] readLittleBytes(int size) throws IOException { 
    	byte[] array = new byte[size];
    	file.read(array);
    	return array;
    }
}
