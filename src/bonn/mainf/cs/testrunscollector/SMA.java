package bonn.mainf.cs.testrunscollector;

import java.math.*;
import java.util.ArrayList;
import java.util.LinkedList;

public class SMA
{
    public ArrayList<Double> values = new ArrayList<Double>();
    
    public ArrayList<Double> convertedValues = new ArrayList<Double>();

    public int length;

    public double sum = 0;

    public double average = 0;
    
    /**
     * 
     * @param length the maximum length
     */
    public SMA(int length)
    {
        if (length <= 0)
        {
            throw new IllegalArgumentException("length must be greater than zero");
        }
        this.length = length;
    }

    public double currentAverage()
    {
        return average;
    }

    /**
     * Compute the moving average.
     * Synchronised so that no changes in the underlying data is made during calculation.
     * @param value The value
     * @return The average
     */
//    public synchronized double compute(double value)
//    {
//        if (values.size() == length && length > 0)
//        {
//            sum -= ((Double) values.getFirst()).doubleValue();
//            values.removeFirst();
//        }
//        sum += value;
//        values.addLast(new Double(value));
//        average = sum / values.size();
//        return average;
//    }
    
    public ArrayList<Double> computeValue(){
    	
    	for ( int i = 0; i < 60; i++)
    	{
    		for( int j = i ; j <(length+i); j++ ){
    			sum += values.get(j);
    		}
    		average = sum/length;
        	convertedValues.add(average);
    	}
		return convertedValues;
    }
    
    
}
