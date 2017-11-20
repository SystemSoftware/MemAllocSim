/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package memallocsim.java;

/**
 * Memory allocation strategy.
 * Responsible both for allocating new, as well as freeing previously allocated chunks of memory.
 * @author IronFox
 */
public interface Allocator {

	/**
	 * Total amount of memory (in bytes) that can be allocated.
	 * Allocated memory chunks must be located in [0,MEMORY_SIZE]
	 */
	public final int MEMORY_SIZE = 1<<20;
	
	/**
	 * Allocated memory chunk.
	 * Chunks allocated from the same strategy instance are not allowed
	 * to overlap.
	 */
	public static class MemoryChunk
	{
		/**
		 * Offset of the local chunk in the allowed address range.
		 * Must be located in [0,MEMORY_SIZE)
		 */
		public final int byteOffset;
		/**
		 * Size (in bytes) of the local chunk.
		 * byteOffset + byteSize must be less or equal to MEMORY_SIZE
		 */
		public final int byteSize;
		
		public MemoryChunk(int byteOffset, int byteSize)
		{
			this.byteOffset = byteOffset;
			this.byteSize = byteSize;
		}
		
		/**
		 * Checks the validity of the local range.
		 * Throws exceptions in case the local state is found to be invalid.
		 */
		public void assertValidity() throws IllegalStateException
		{
			if (byteOffset < 0)
				throw new IllegalStateException(this+": byteOffset is negative");
			if (byteOffset >= MEMORY_SIZE)
				throw new IllegalStateException(this+": byteOffset is not less than available memory size ("+MEMORY_SIZE+")");
			if (byteSize < 0)
				throw new IllegalStateException(this+": byteSize is negative");
			if (byteOffset + byteSize > MEMORY_SIZE)
				throw new IllegalStateException(this+": byteOffset+byteSize is greater than available memory size ("+MEMORY_SIZE+")");
		}
		
		@Override
		public String toString()
		{
			return "["+byteOffset+","+(byteOffset+byteSize)+")";
		}
		
		@Override
		public int hashCode()
		{
			return byteOffset* 31 + byteSize;
		}

		@Override
		public boolean equals(Object obj)
		{
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			final MemoryChunk other = (MemoryChunk) obj;
			return (this.byteOffset == other.byteOffset && this.byteSize == other.byteSize);
		}

		public int getEnd()
		{
			return byteOffset + byteSize;
		}
	};
	
	/**
	 * Operation step counter to estimate runtime complexity
	 */
	public final class StepCounter
	{
		private	int total = 0;
		
		public void add(int steps)
		{
			total += steps;
		}
		
		public void inc()
		{
			total++;
		}
		
		public int getSteps()
		{
			return total;
		}
		
		public void reset()
		{
			total = 0;
		}
	};
	
	/**
	 * Allocates a new homogenous chunk of memory from the available pool.
	 * The method may return null or throw exceptions if allocation is currently
	 * not possible, or an invalid number of bytes were requested.
	 * @param numBytes Size of the requested chunk (in bytes)
	 * @param stepCounter Counter object to add any operational steps to.
	 * E.g. how many loop iterations were executed, or tree nodes were
	 * searched.
	 * @return Reference to the new chunk, or null if no such was created.
	 * @throws java.lang.Exception
	 */
	MemoryChunk allocate(int numBytes, StepCounter stepCounter) throws Exception;
	
	/**
	 * Frees the specified memory chunk, allowing future allocation of the
	 * addressed memory.
	 * @param chunk Chunk to free. May be null
	 * @param stepCounter Counter object to add any operational steps to.
	 * E.g. how many loop iterations were executed, or tree nodes were
	 * searched.
	 * @throws java.lang.Exception Exceptions may be thrown if invalid
	 * parameters or internal inconsistencies were detected.
	 */
	void free( MemoryChunk chunk, StepCounter stepCounter) throws Exception;
	/**
	 * Calculates the current internal fragmentation level (in bytes).
	 * Internal fragmentation denotes non-addressable memory within allocated
	 * memory segments.
	 * @return Total internal fragmentation in bytes
	*/
	int getInternalFragmentationBytes();
	/**
	 * Calculates the current external fragmentation level (in bytes) for
	 * a given frame size.
	 * External fragmentation accounts for all allocatable memory of
	 * insufficient size for a given allocation request size.
	 * @param allocRequestBytes Number of bytes to check for
	 * @return Sum of all allocatable memory regions that are smaller than the
	 * given size.
	*/
	int getExternalFragmentationBytes( int allocRequestBytes);

	/**
	 * Calculates the amount of memory allocated in chunks.
	 * This value should be the sum of all currently allocated memory
	 * plus internal fragmentation.
	 * @return Allocated memory + internal fragmentation
	 */
	int getOccupiedMemoryBytes();
	
	
	/**
	 * Creates an exact copy of the local object without any data allocated
	 * @return New instance of the local class (same configuration), no blocks
	 * allocated
	 */
	Allocator createNew();

};
