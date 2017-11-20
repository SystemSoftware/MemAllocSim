/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package memallocsim.java;

/**
 * Set of metrics used during statistical evaluation
 * @author IronFox
 */
public class MetricSet
{
	/**
	 * Execution steps spent on memory allocation
	 */
	public final Metric allocationCost = new Metric(false);
	/**
	 * Execution steps spent on memory freeing
	 */
	public final Metric freeCost = new Metric(false);
	
	/**
	 * Relative amount of memory lost due to internal fragmentation.
	 * Effectively, Allocator.getInternalFragmentationBytes()/Allocator.getOccupiedMemoryBytes()
	 */
	public final Metric internalFragmentation = new Metric(true);
	
	/**
	 * Relative amount of memory unavailable due to external fragmentation
	 */
	public final Metric	externalFragmentation = new Metric(true);

	public void include(MetricSet other)
	{
		allocationCost.include(other.allocationCost);	
		freeCost.include(other.freeCost);
		internalFragmentation.include(other.internalFragmentation);
		externalFragmentation.include(other.externalFragmentation);
	}
}
