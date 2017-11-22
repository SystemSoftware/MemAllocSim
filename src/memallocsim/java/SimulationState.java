/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package memallocsim.java;

import java.util.Random;

/**
 * Allocation test state.
 * Keeps track of several Allocator instances simultaneously.
 * @author IronFox
 */
public final class SimulationState
{
	/**
	 * Exception 
	 * @author IronFox
	 */
	public class AllAllocatorsHaveFaultedException extends Exception
	{
		public AllAllocatorsHaveFaultedException()
		{
			super("All allocators have faulted");
		}
	}
	
	
	private final AllocatorStateTracker[] allocators;


	private int currentlyAllocatedBytes = 0, mostBytesAllocated = 0,
				mostAllocatedChunks = 0, numAllocated = 0;

	private final Metric bytesPerAllocation = new Metric(false);


	/**
	 * Retrieves a byte metric of all allocation sizes
	 * @return Byte metric
	 */
	public Metric getBytesPerAllocation()
	{
		return bytesPerAllocation;
	}


	private static boolean autoVerify = false;

	public static boolean doesAutoVerify()
	{
		return autoVerify;
	}

	/**
	 * Updates automatic allocator verification.
	 * If set, verification checks are executed after each operation to check
	 * for bad memory chunks.
	 * @param autoVerify New value for allocator auto verification
	 */
	public static void setAutoVerify(boolean doAutoVerify)
	{
		autoVerify = doAutoVerify;
	}


	public int getCurrentlyAllocatedBytes()
	{
		return currentlyAllocatedBytes;
	}

	public int getMostSimultaneouslyAllocatedChunks()
	{
		return mostAllocatedChunks;
	}




	public int getMostBytesSimultaneouslyAllocated()
	{
		return mostBytesAllocated;
	}



	public SimulationState(Allocator[] s)
	{
		allocators = new AllocatorStateTracker[s.length];
		for (int i = 0; i < s.length; i++)
			allocators[i] = new AllocatorStateTracker(s[i]);

		if (autoVerify)
			verifyIntegrity();
	}

	public boolean allFaulted()
	{
		for (AllocatorStateTracker alloc : allocators)
			if (!alloc.hasFaulted())
				return false;
		return true;

	}

	/**
	 * Executes an allocation instruction on all local allocators
	 * @param numBytes Bytes to allocate
	 * @throws Exception 
	 */
	public void allocate(int numBytes) throws Exception
	{
		if (allFaulted())
			throw new AllAllocatorsHaveFaultedException();
		if (numBytes <= 0)
			return;
		bytesPerAllocation.include(numBytes);

		for (AllocatorStateTracker alloc : allocators)
			alloc.allocate(numBytes, numAllocated+1);
		numAllocated++;
		currentlyAllocatedBytes += numBytes;
		mostAllocatedChunks = Math.max(mostAllocatedChunks,numAllocated);
		mostBytesAllocated = Math.max(mostBytesAllocated, currentlyAllocatedBytes);
		if (autoVerify)
			verifyIntegrity();
	}

	/**
	 * Frees a random chunk from all local allocators.
	 * The same chunk is removed from all allocates, to keep them in sync
	 * @param rng Random source to use for the index
	 * @throws Exception 
	 */
	public void freeRandom(Random rng) throws Exception
	{
		if (numAllocated == 0)
			return;
		int at = rng.nextInt(numAllocated);
		free(at);
	}

	/**
	 * Frees a specified chunk from all local allocators
	 * @param index Index of the chunk to remove
	 * @throws Exception 
	 */
	public boolean free(int index) throws Exception
	{
		int size = 0;
		for (AllocatorStateTracker alloc : allocators)
		{
			int s = alloc.free(index, numAllocated);
			if (s != 0)
			{
				size = s;
			}
		}
		if (size == 0)
			return false;
		currentlyAllocatedBytes -= size;
		numAllocated--;
		if (autoVerify)
			verifyIntegrity();
		return true;
	}

	/**
	 * Frees all currently allocated chunks
	 * @throws Exception 
	 */
	public void endRun() throws Exception
	{
		currentlyAllocatedBytes = 0;
		numAllocated = 0;
		for (AllocatorStateTracker alloc : allocators)
			alloc.endRun();
	}


	/**
	 * Thoroughly checks if the current state is valid.
	 * May be expensive
	 */
	public void verifyIntegrity()
	{
		if (currentlyAllocatedBytes < 0)
			throw new IllegalStateException("Internal error: Memory released to negative total: "+currentlyAllocatedBytes);

		if (numAllocated == 0 && currentlyAllocatedBytes != 0)
			throw new IllegalStateException("Internal error: All chunks released, but total is not 0: "+currentlyAllocatedBytes);

		if (currentlyAllocatedBytes > Allocator.MEMORY_SIZE)
			throw new IllegalStateException("Total allocated memory exceeds allocatable memory space");

		for (AllocatorStateTracker alloc : allocators)
			if (!alloc.hasFaulted())
				alloc.verifyIntegrity(numAllocated);
	}

	@Override
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		builder.append("Test of {");
		for (AllocatorStateTracker alloc : allocators)
			builder.append(" ").append(alloc.getAllocatorName());
		builder.append("}:\n");
		builder.append("  bytes per allocation: ").append(getBytesPerAllocation()).append("\n");
		builder.append("  most chunks/bytes simultaneously allocated: ").append(getMostSimultaneouslyAllocatedChunks()).append("/")
				.append(getMostBytesSimultaneouslyAllocated()).append("\n");

		for (AllocatorStateTracker alloc : allocators)
			alloc.appendTo(builder);

		return builder.toString();
	}
}

