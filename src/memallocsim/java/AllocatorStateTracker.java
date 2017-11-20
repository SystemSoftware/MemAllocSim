/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package memallocsim.java;

import java.util.ArrayList;

/**
 * State recorded for an individual allocator
 * @author IronFox
 */
public class AllocatorStateTracker
{
	/**
	 * Bytes to calculate external fragmentation for
	 */
	public final static int EXTERNAL_FRAGMENTATION_THRESHOLD = 65536;
	
	
	private Allocator allocator;
	private boolean faulted = false;
	private String faultedMessage;
	private MetricSet thisRun = new MetricSet();
	private final MetricSet allTime = new MetricSet();
	private final Metric faultedAtByteCount = new Metric(false),
						faultedAtAllocation = new Metric(false);
	private int currentlyAllocatedBytes = 0;

	private final ArrayList<Allocator.MemoryChunk>	allocatedList = new ArrayList<>();

	private final Allocator.StepCounter counter = new Allocator.StepCounter();

	public AllocatorStateTracker(Allocator alloc)
	{
		allocator = alloc;	
	}

	public void verifyIntegrity()
	{
		for (int i = 0; i+1 < allocatedList.size(); i++)
			for (int j = i+1; j < allocatedList.size(); j++)
			{
				Allocator.MemoryChunk a = allocatedList.get(i),
								b = allocatedList.get(j);
				if (a.byteOffset < b.getEnd() && b.byteOffset < a.getEnd() )
					throw new IllegalStateException("Chunks overlap: "+a+", "+b);
			}
	}

	private void updateFragmentation() throws Exception
	{
		thisRun.internalFragmentation.include((double)allocator.getInternalFragmentationBytes() / allocator.getOccupiedMemoryBytes());
		thisRun.externalFragmentation.include((double)allocator.getExternalFragmentationBytes(EXTERNAL_FRAGMENTATION_THRESHOLD) / getTheoreticalFreeBytes());
	}
	private void fault(String msg)
	{
		faultedAtByteCount.include(currentlyAllocatedBytes);
		faultedAtAllocation.include(allocatedList.size());
		faulted = true;
		faultedMessage = msg;
	}

	public boolean hasFaulted()
	{
		return faulted;
	}

	/**
	 * Allocates a new chunk using the local allocator.
	 * Once the allocator has faulted, the method stops doing anything.
	 * A fault is caused by the allocator throwing an exception, or
	 * returning null
	 * @param numBytes Size of the requested chunk in bytes
	 * @throws Exception Exceptions may be thrown in case internal
	 * integrity is violated.
	 */
	public void allocate(int numBytes) throws Exception
	{
		if (faulted)
			return;
		counter.reset();
		Allocator.MemoryChunk rs;
		try
		{
			rs = allocator.allocate(numBytes,counter);
			if (rs == null)
				throw new Exception(allocator+ ".allocate() returned null");
		}
		catch (Exception ex)
		{
			fault(ex.getMessage());
			return;
		}
		try
		{
			rs.assertValidity();
		}
		catch (IllegalStateException ex)
		{
			throw new IllegalStateException(allocator+" has returned an invalid chunk",ex);
		}
		currentlyAllocatedBytes += rs.byteSize;
		thisRun.allocationCost.include(counter.getSteps());
		allocatedList.add(rs);
		updateFragmentation();
	}

	/**
	 * Retrieves a cost metric of all allocation operations
	 * @return Cost metric
	 */
	public Metric getAllocationCost()
	{
		return allTime.allocationCost;
	}
	/**
	 * Retrieves a cost metric of all free operations
	 * @return Cost metric
	 */
	public Metric getFreeCost()
	{
		return allTime.freeCost;
	}

	/**
	 * Retrieves a cost metric of all allocation and free operations
	 * @return Cost metric
	 */
	public Metric getTotalCost()
	{
		return Metric.combine(allTime.allocationCost,allTime.freeCost);
	}

	/**
	 * Retrieves a percentage metric of recorded internal fragmentation
	 * @return Percentage metric
	 */
	public Metric getInternalFragmentation()
	{
		return allTime.internalFragmentation;
	}

	/**
	 * Retrieves a percentage metric of recorded external fragmentation
	 * @return Percentage metric
	 */
	public Metric getExternalFragmentation()
	{
		return allTime.externalFragmentation;
	}

	/**
	 * Calculates how much memory should theoretically remain for
	 * allocation
	 * @return Byte count
	 */
	public int getTheoreticalFreeBytes()
	{
		return Allocator.MEMORY_SIZE - currentlyAllocatedBytes;
	}

	/**
	 * Calculates how much memory can actually be allocated,
	 * when taking internal fragmentation into account
	 * @param allocationBytes Bytes to check allocation availability for
	 * @return Byte count
	 */
	public int getRemainingFreeBytes(int allocationBytes)
	{
		return getTheoreticalFreeBytes() - allocator.getInternalFragmentationBytes() - allocator.getExternalFragmentationBytes(allocationBytes);
	}

	/**
	 * Frees a specific chunk from the local allocator.
	 * If the local allocator has previously faulted, then nothing is
	 * done.
	 * @param chunkIndex Chunk to free
	 * @return Number of bytes that were freed, or 0 if a fault occurred
	 * @throws Exception 
	 */
	public int free(int chunkIndex) throws Exception
	{
		if (faulted)
			return 0;
		Allocator.MemoryChunk chunk = allocatedList.remove(chunkIndex);
		counter.reset();
		currentlyAllocatedBytes -= chunk.byteSize;
		try
		{
			allocator.free(chunk,counter);
		}
		catch (Exception ex)
		{
			fault(ex.toString());
			return 0;
		}
		thisRun.freeCost.include(counter.getSteps());
	//				updateFragmentation();
		return chunk.byteSize;
	}

	/**
	 * Appends the local state to the specified builder for console output
	 * @param builder 
	 */
	public void appendTo(StringBuilder builder)
	{
		builder.append("  ").append(allocator).append("\n");
		if (faultedAtByteCount.isSet())
			builder.append("   faulted ")
					.append(faultedAtByteCount.getInclusionCount())
					.append("/").append(numRuns)
					.append(" time(s) at avg byte/chunk: ")
					.append((double)Math.round(faultedAtByteCount.getMean()*10)/10)
					.append("/")
					.append((double)Math.round(faultedAtAllocation.getMean()*10)/10)
					.append("\n");

		{
			builder
					.append("    allocation cost: ").append(getAllocationCost()).append("\n")
					.append("    free cost: ").append(getFreeCost()).append("\n")
					.append("    relative internal fragmentation: ")
						.append(getInternalFragmentation())
						.append("\n")
					.append("    relative external fragmentation (at ")
						.append(EXTERNAL_FRAGMENTATION_THRESHOLD)
						.append(" bytes): ")
						.append(getExternalFragmentation())
						.append("\n");
		}
	}
	private int numRuns = 0;

	public void endRun() throws Exception
	{
		numRuns ++;
		if (!faulted)
			allTime.include(thisRun);
		thisRun = new MetricSet();

		currentlyAllocatedBytes = 0;
		faulted = false;
		allocatedList.clear();
		Class old = allocator.getClass();
		String oldName = allocator.toString();
		allocator = allocator.createNew();
		if (!old.equals(allocator.getClass()))
			throw new Exception("Clone is not equal to original: "+old+" != "+allocator.getClass());
		if (!oldName.equals(allocator.toString()))
			throw new Exception("Clone is not equal to original: "+oldName+" != "+allocator.toString());
		if (allocator.getOccupiedMemoryBytes() != 0)
			throw new Exception("Clone has occupied space: "+allocator+": "+allocator.getOccupiedMemoryBytes()); 
	}

	public String getAllocatorName()
	{
		return allocator.toString();
	}

}
