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
	 * If less bytes are currently allocated, forces allocation of new chunks
	 */
	final static int FORCED_ALLOCATION_THRESHOLD = Allocator.MEMORY_SIZE / 10;
	/**
	 * Maximum number of bytes to allocate
	 */
	final static int ALLOCATE_UP_TO = Allocator.MEMORY_SIZE /5;
	
	/**
	 * @param args the command line arguments
	 * @throws java.lang.Exception
	 */
	public static void main(String[] args) throws Exception
	{
		SimulationState.setAutoVerify(true);
		
		SimulationState state = 
				new SimulationState(
						new Allocator[]{
								new FirstFit(FirstFit.Flavor.IncreasingSize), 
								new FirstFit(FirstFit.Flavor.DecreasingSize), 
								new FirstFit(FirstFit.Flavor.NextFit), 
								new FirstFit(FirstFit.Flavor.NextFit, new FirstFit.PreciseX15()), 
								new FirstFit(FirstFit.Flavor.NextFit, new FirstFit.PreciseX1()), 
								new FirstFit(FirstFit.Flavor.NextFit, new FirstFit.MultiplesOf(16)), 
								new Buddy(),
								//new StackAllocator(), 
								//new NullAllocator(),
						}
				);
		Random random = new Random();
		
		try
		{
			final int numRuns = 1000;
			for (int j = 0; j < numRuns; j++)
			{
				try
				{
					for (int i = 0; i < 10000; i++)
					{
						int allocated = state.getCurrentlyAllocatedBytes();
						if (allocated < FORCED_ALLOCATION_THRESHOLD || (random.nextBoolean() && allocated < ALLOCATE_UP_TO))
							state.allocate(random.nextInt(256) * random.nextInt(256));
						if (allocated >= ALLOCATE_UP_TO || (random.nextBoolean() && allocated > FORCED_ALLOCATION_THRESHOLD) )
							state.freeRandom(random);
					}
				}
				catch (Exception ex)
				{
					System.out.println(ex);
				}
				state.endRun();
				if ( (j % (numRuns / 20)) == 0)
					System.out.println(Math.round((double)j / numRuns*100)+"%");
	//			System.out.flush();
			}
		}
		catch (Exception ex)
		{
			System.out.println(ex);
		}
		System.out.println(state);
	}
	
	
}
