/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package memallocsim.java;

import java.util.ArrayList;
import java.util.Random;

/**
 *
 * @author IronFox
 */
public class MemAllocSimJava
{
	/**
	 * Bytes to calculate external fragmentation for
	 */
	final static int EXTERNAL_FRAGMENTATION_THRESHOLD = 65536;
	
	/**
	 * Allocation test state.
	 * Keeps track of several Allocator instances simultaneously.
	 */
	static final class TestState
	{
		/**
		 * Statistical metric container.
		 * Records minimum, maximum, mean, and deviation
		 */
		public static class Metric
		{
			private int count=0;
			private double sum=0, sqrSum = 0, max=-Double.MAX_VALUE, min = Double.MAX_VALUE;
			
			/**
			 * Includes a new sample in the local metric
			 * @param v Value to include
			 */
			public void include(double v)
			{
				count++;
				sum += v;
				sqrSum += v*v;
				max = Math.max(max,v);
				min = Math.min(min, v);
			}
			
			/**
			 * Calculates the mean value of the local metric.
			 * Returns 0 if no samples were recorded
			 * @return Mean value
			 */
			public double getMean()
			{
				return count > 0 ? sum / count : 0.0;
			}
			
			/**
			 * Calculates the standard deviation of the local metric.
			 * Returns 0 if no samples were recorded
			 * @return Standard deviation
			 */
			public double getDeviation()
			{
				if (count == 0)
					return 0;
				double mean = getMean();
				double sqrMean = sqrSum/(double)count;
				return Math.sqrt(sqrMean - mean * mean);
			}
			
			public double getMax()
			{
				return max;
			}
			public double getMin()
			{
				return min;
			}
			
			private static String rounded(double v)
			{
				double r = Math.round(v * 100) / 100.0;
				if (r == (int)r)
					return Integer.toString((int)r);
				return Double.toString( r );
			}
			private static String roundedPercentage(double v)
			{
				return rounded(v * 100.0)+"%";
				//return Math.round(v * 1000) / 10.0;
			}
			
			public String percentageToString()
			{
				if (count == 0)
					return "not recorded";
				StringBuilder rs = new StringBuilder();
				if (min != 0)
					rs.append("min ").append(roundedPercentage(min)).append(" <= ");
				rs.append("avg ").append(roundedPercentage(getMean()));
				if (max != 0)
					rs.append(" <= max ").append(roundedPercentage(max));
				rs.append(", calculated from ").append(count).append(" sample(s)");
				return rs.toString();
			}
			
			@Override
			public String toString()
			{
				if (count == 0)
					return "not recorded";
				StringBuilder rs = new StringBuilder();
				if (min != 0)
					rs.append("min ").append(rounded(min)).append(" <= ");
				rs.append("avg ").append(rounded(getMean()));
				if (max != 0)
					rs.append(" <= max ").append(rounded(max));
				rs.append(", calculated from ").append(count).append(" sample(s)");
				return rs.toString();
			}
		};
		
		/**
		 * State recorded for an individual allocator
		 */
		public class PerAllocator
		{
			public final Allocator allocator;
			private final Metric 
					allocationCost = new Metric(),
					freeCost = new Metric(), 
					totalCost = new Metric(),
					internalFragmentation = new Metric(),
					externalFragmentation = new Metric();
			private int faultedAtByteCount = -1,
					  faultedAtAllocation = -1;
			private String faultedMessage;

			private final ArrayList<Allocator.MemoryChunk>	allocatedList = new ArrayList<>();

			private final Allocator.StepCounter counter = new Allocator.StepCounter();
			
			private PerAllocator(Allocator alloc)
			{
				allocator = alloc;	
			}
			
			private void verifyIntegrity()
			{
				for (Allocator.MemoryChunk a:allocatedList)
					for (Allocator.MemoryChunk b:allocatedList)
						if ( a != b && a.byteOffset < b.getEnd() && b.byteOffset < a.getEnd() )
							throw new IllegalStateException("Chunks overlap: "+a+", "+b);
			}
			
			private void updateFragmentation() throws Exception
			{
				int occupied = allocator.getOccupiedMemoryBytes();
				if (occupied < currentlyAllocatedBytes)
					throw new Exception("Validation failed: Should have allocated at least "+currentlyAllocatedBytes+" byte(s), but allocator reports only "+occupied);
				internalFragmentation.include((double)allocator.getInternalFragmentationBytes() / allocator.getOccupiedMemoryBytes());
				externalFragmentation.include((double)allocator.getExternalFragmentationBytes(EXTERNAL_FRAGMENTATION_THRESHOLD) / getTheoreticalFreeBytes());
			}
			
			private void fault(String msg)
			{
				faultedAtByteCount = currentlyAllocatedBytes;
				faultedAtAllocation = allocatedList.size();
				faultedMessage = msg;
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
			private void allocate(int numBytes) throws Exception
			{
				if (faultedAtByteCount != -1)
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
					fault(ex.toString());
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
				allocationCost.include(counter.getSteps());
				totalCost.include(counter.getSteps());
				allocatedList.add(rs);
				updateFragmentation();
			}
			
			/**
			 * Retrieves a cost metric of all allocation operations
			 * @return Cost metric
			 */
			public Metric getAllocationCost()
			{
				return allocationCost;
			}
			/**
			 * Retrieves a cost metric of all free operations
			 * @return Cost metric
			 */
			public Metric getFreeCost()
			{
				return freeCost;
			}

			/**
			 * Retrieves a cost metric of all allocation and free operations
			 * @return Cost metric
			 */
			public Metric getTotalCost()
			{
				return totalCost;
			}

			/**
			 * Retrieves a percentage metric of recorded internal fragmentation
			 * @return Percentage metric
			 */
			public Metric getInternalFragmentation()
			{
				return internalFragmentation;
			}
			
			/**
			 * Retrieves a percentage metric of recorded external fragmentation
			 * @return Percentage metric
			 */
			public Metric getExternalFragmentation()
			{
				return externalFragmentation;
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
			private int free(int chunkIndex) throws Exception
			{
				if (faultedAtByteCount != -1)
					return 0;
				Allocator.MemoryChunk chunk = allocatedList.remove(chunkIndex);
				counter.reset();
				try
				{
					allocator.free(chunk,counter);
				}
				catch (Exception ex)
				{
					fault(ex.toString());
					return 0;
				}
				freeCost.include(counter.getSteps());
				totalCost.include(counter.getSteps());
//				updateFragmentation();
				return chunk.byteSize;
			}

			/**
			 * Appends the local state to the specified builder for console output
			 * @param builder 
			 */
			private void appendTo(StringBuilder builder)
			{
				builder.append("  ").append(allocator).append("\n");
				if (faultedAtAllocation != -1)
					builder.append("   FAULTED ('"+faultedMessage+"') at byte/chunk: ").append(faultedAtByteCount).append("/").append(faultedAtAllocation).append("\n");
				
				{
					builder
					.append("    allocation cost: ").append(getAllocationCost()).append("\n")
					.append("    free cost: ").append(getFreeCost()).append("\n")
					.append("    relative internal fragmentation: ").append(getInternalFragmentation().percentageToString()).append("\n")
					.append("    relative external fragmentation (at "+EXTERNAL_FRAGMENTATION_THRESHOLD+" bytes): ").append(getExternalFragmentation().percentageToString()).append("\n");
				}

			}
		};
		
		private final PerAllocator[] allocators;
		
		
		private int currentlyAllocatedBytes = 0, mostBytesAllocated = 0,
					mostAllocatedChunks = 0, numAllocated = 0;
		
		private final Metric bytesPerAllocation = new Metric();


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
		public static void setAutoVerify(boolean autoVerify)
		{
			TestState.autoVerify = autoVerify;
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
		
		
		
		public TestState(Allocator[] s)
		{
			allocators = new PerAllocator[s.length];
			for (int i = 0; i < s.length; i++)
				allocators[i] = new PerAllocator(s[i]);
			
			if (autoVerify)
				verifyIntegrity();
		}
		
		/**
		 * Executes an allocation instruction on all local allocators
		 * @param numBytes Bytes to allocate
		 * @throws Exception 
		 */
		public void allocate(int numBytes) throws Exception
		{
			if (numBytes <= 0)
				return;
			bytesPerAllocation.include(numBytes);
			
			numAllocated++;
			currentlyAllocatedBytes += numBytes;
			mostAllocatedChunks = Math.max(mostAllocatedChunks,numAllocated);
			mostBytesAllocated = Math.max(mostBytesAllocated, currentlyAllocatedBytes);
			for (PerAllocator alloc : allocators)
				alloc.allocate(numBytes);
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
			for (PerAllocator alloc : allocators)
			{
				int s = alloc.free(index);
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
		public void freeAll() throws Exception
		{
			while (numAllocated > 0 && free(numAllocated-1));
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
			
			for (PerAllocator alloc : allocators)
				alloc.verifyIntegrity();
		}
		
		@Override
		public String toString()
		{
			StringBuilder builder = new StringBuilder();
			builder.append("Test of {");
			for (PerAllocator alloc : allocators)
				builder.append(" ").append(alloc.allocator);
			builder.append("}:\n");
			builder.append("  bytes per allocation: ").append(getBytesPerAllocation()).append("\n");
			builder.append("  most chunks/bytes simultaneously allocated: ").append(getMostSimultaneouslyAllocatedChunks()).append("/")
					.append(getMostBytesSimultaneouslyAllocated()).append("\n");
			
			for (PerAllocator alloc : allocators)
				alloc.appendTo(builder);
			
			return builder.toString();
		}
	}
	
	/**
	 * If less bytes are currently allocated, forces allocation of new chunks
	 */
	final static int FORCED_ALLOCATION_THRESHOLD = Allocator.MEMORY_SIZE / 10;
	/**
	 * Maximum number of bytes to allocate
	 */
	final static int ALLOCATE_UP_TO = Allocator.MEMORY_SIZE /5;
	
	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) throws Exception
	{
		TestState state = new TestState(new Allocator[]{
								//new FirstFit(FirstFit.Flavor.IncreasingSize), 
								//new FirstFit(FirstFit.Flavor.DecreasingSize), 
								//new FirstFit(FirstFit.Flavor.NextFit), 
								//new Buddy(),
								new StackAllocator(), 
								new NullAllocator(),
		});
		TestState.setAutoVerify(true);
		Random random = new Random();
		
		for (int i = 0; i < 10000; i++)
		{
			int allocated = state.getCurrentlyAllocatedBytes();
			if (allocated < FORCED_ALLOCATION_THRESHOLD || (random.nextBoolean() && allocated < ALLOCATE_UP_TO))
				state.allocate(random.nextInt(256) * random.nextInt(256));
			if (allocated >= ALLOCATE_UP_TO || (random.nextBoolean() && allocated > FORCED_ALLOCATION_THRESHOLD) )
				state.freeRandom(random);
		}
		state.freeAll();
		System.out.println(state);
	}
	
}
