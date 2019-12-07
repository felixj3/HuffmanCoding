import java.util.PriorityQueue;
import java.util.ArrayList;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = readForCounts(in); // reads frequencies
		HuffNode root = makeTreeFromCounts(counts); // makes huff tree based on freq as weight
		String[] codings = makeCodingsFromTree(root); // creates the codings from huff tree
		out.writeBits(BITS_PER_INT, HUFF_TREE); // magic number
		writeHeader(root, out); // put encoding of huff tree in compressed file
		in.reset(); // first used to read freq, now to encode message
		writeCompressedBits(codings, in, out); // converts original message into same content
		// but each character represented by a different value now
		out.close();
	}

	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out)
	{
		// when we normally read text it's in 8 bits
		// for the tree it's 9 so that PSEUDO_EOF can be included
		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			String code = codings[val]; // val is the bit value converted to integer of a
			// letter in the original file. Codings lets us know its new shortened value
			out.writeBits(code.length(), Integer.parseInt(code, 2));
			// since code is in base 2 but string, we know code.length is the same as parseInt
			// radix 2
		}
		String c = codings[PSEUDO_EOF];
		out.writeBits(c.length(), Integer.parseInt(c, 2));
	}

	private void writeHeader(HuffNode root, BitOutputStream out)
	{
		// writes the code for the tree at the start of the compressed file
		if(root != null)
		{
			if(root.myLeft == null && root.myRight == null)
			{
				out.writeBits(1, 1);
				out.writeBits(BITS_PER_WORD + 1, root.myValue);
			}
			else
			{
				out.writeBits(1, 0);
				writeHeader(root.myLeft, out);
				writeHeader(root.myRight, out);
			}
		}
	}

	private String[] makeCodingsFromTree(HuffNode root)
	{
		String[] codings = new String[ALPH_SIZE + 1];
		codingsHelper(root, codings, "");
		return codings;
	}

	private void codingsHelper(HuffNode root, String[] paths, String path)
	{
		if(root != null)
		{
			if(root.myLeft == null && root.myRight == null)
			{
				paths[root.myValue] = path;
			}
			else
			{
				codingsHelper(root.myLeft, paths, path + "0");
				codingsHelper(root.myRight, paths, path + "1");
			}
		}
	}

	private HuffNode makeTreeFromCounts(int[] counts)
	{
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for(int i = 0; i < counts.length; i++)
		{
			if(counts[i] != 0)
			{
				pq.add(new HuffNode(i, counts[i]));
			}
		}
		while(pq.size() > 1)
		{
			// take two trees out and put one back in
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			pq.add(new HuffNode(-1, left.myWeight + right.myWeight, left, right));
		}
		return pq.remove();
	}

	private int[] readForCounts(BitInputStream in)
	{
		int[] out = new int[ALPH_SIZE + 1]; // ALPH_SIZE is 1 with 8 zeros, representing 256
		// characters are from 0 - 255 index and the 256 index is for PSEUDO_EOF
		out[PSEUDO_EOF] = 1;
		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out[val] = out[val] + 1;
		}
		return out;
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		int magic = in.readBits(BITS_PER_INT);
		if (magic != HUFF_TREE) {
			throw new HuffException("invalid magic number "+magic);
		}
		HuffNode root = readTree(in);
		HuffNode current = root;
		//out.writeBits(BITS_PER_INT,magic);
		while (true){
			int val = in.readBits(1);
			if (val == -1)
			{
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else
			{
				if(val == 0)
				{
					current = current.myLeft;
				}
				else
				{
					current = current.myRight;
				}
				if(current.myLeft == null && current.myRight == null)
				{
					if(current.myValue == PSEUDO_EOF)
					{
						break;
					}
					else
					{
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
		out.close();
	}

	private HuffNode readTree(BitInputStream in)
	{
		int bit = in.readBits(1);
		if(bit == -1)
		{
			throw new HuffException("Invalid InputStream");
		}
		if(bit == 0)
		{
			HuffNode left = readTree(in);
			HuffNode right = readTree(in);
			return new HuffNode(0,0,left, right);
		}
		else
		{
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value, 0, null, null);
		}
	}
}