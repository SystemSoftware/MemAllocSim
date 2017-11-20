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
public class StackAllocator implements Allocator
{
	@Override
	public String toString()
	{
		return "StackAllocator";
	}
	
	private int offset = 0, allocated = 0;
	
	

	@Override
	public MemoryChunk allocate(int numBytes, StepCounter counter)
	{
		if (numBytes <= 0)
			return null;
		if (offset + numBytes > MEMORY_SIZE)
			return null;
		MemoryChunk rs = new MemoryChunk(offset, numBytes);
		offset += numBytes;
		allocated += numBytes;
		counter.inc();
		
		return rs;
	}

	@Override
	public void free(MemoryChunk chunk, StepCounter counter)
	{
		if (chunk == null)
			return;
		allocated -= chunk.byteSize;
		counter.inc();
	}

	@Override
	public int getInternalFragmentationBytes()
	{
		return 0;
	}

	@Override
	public int getExternalFragmentationBytes(int allocRequestBytes)
	{
		int rs = offset - allocated;	//will not be allocated again
		
		if (allocRequestBytes > (MEMORY_SIZE - offset))
			rs += MEMORY_SIZE-offset;
		return rs;
	}

	@Override
	public int getOccupiedMemoryBytes()
	{
		return allocated;
	}


	@Override
	public Allocator createNew()
	{
		return new StackAllocator();
	}

}
