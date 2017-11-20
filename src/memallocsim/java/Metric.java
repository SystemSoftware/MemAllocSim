/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package memallocsim.java;

/**
 *
 * @author IronFox
* Statistical metric container.
* Records minimum, maximum, mean, and deviation
*/
public class Metric
{

	private int count=0;
	private double sum=0, sqrSum = 0, max=-Double.MAX_VALUE, min = Double.MAX_VALUE;
	private final boolean percentage;
	
	
	public Metric(boolean isPercentage)
	{
		this.percentage = isPercentage;
	}
	
	public boolean isPercentage()
	{
		return percentage;	
	}
	
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
	 * Includes another metric into the local metric
	 * @param other Metric to include
	 */
	public void include(Metric other)
	{
		count += other.count;
		sum += other.sum;
		sqrSum += other.sqrSum;
		max = Math.max(max, other.max);
		min = Math.min(min, other.min);
	}

	static Metric combine(Metric a, Metric b)
	{
		if (a.percentage != b.percentage)
			throw new IllegalArgumentException("Metric.combine() requires equal percentage states");
		Metric rs = new Metric(a.percentage);
		rs.include(a);
		rs.include(b);
		return rs;
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

	/**
	 * Retrieves the maximum included value. 
	 * @return Maximum value. -Double.MAX_VALUE if empty
	 */
	public double getMax()
	{
		return max;
	}
	/**
	 * Retrieves the minimum included value.
	 * 
	 * @return Minimum value. Double.MAX_VALUE if empty
	 */
	public double getMin()
	{
		return min;
	}
	
	/**
	 * Checks if any values were included
	 * @return true if no values were included, false otherwise
	 */
	public boolean isEmpty()
	{
		return count == 0;
	}
	
	/**
	 * Checks if any values were included
	 * @return true if any values were included, false otherwise
	 */
	public boolean isSet()
	{
		return count > 0;
	}

	private static String roundedRaw(double v)
	{
		double r = (double)Math.round(v * 100) / 100.0;
		if (r == (int)r)
			return Integer.toString((int)r);
		return Double.toString( r );
	}
	private static String roundedPercentage(double v)
	{
		return roundedRaw(v * 100.0)+"%";
		//return Math.round(v * 1000) / 10.0;
	}
	
	private String rounded(double v)
	{
		return percentage ? roundedPercentage(v) : roundedRaw(v);
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

	/**
	 * Retrieves the number of values that have been included in the local metric
	 * @return Number of included values. 0 if empty
	 */
	int getInclusionCount()
	{
		return count;
	}

};
