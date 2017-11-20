/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package memallocsim.java;

/**
 *
 * @author IronFox
 */
public class NullAllocator implements Allocator
{

	@Override
	public MemoryChunk allocate(int numBytes, StepCounter counter)
	{
		return null;
	}

	@Override
	public void free(MemoryChunk addr, StepCounter counter)
	{}

	@Override
	public int getInternalFragmentationBytes()
	{
		return 0;
	}

	@Override
	public int getExternalFragmentationBytes(int allocRequestBytes)
	{
		return 0;
	}

	@Override
	public String toString()
	{
		return "Null";
	}

	@Override
	public int getOccupiedMemoryBytes()
	{
		return 0;
	}

	@Override
	public Allocator createNew()
	{
		return new NullAllocator();
	}
}
