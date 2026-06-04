package io.silvicky.item.common;

import java.util.*;

public class WeightedSelector<T>
{
    private final Map<T,Integer> weights=new HashMap<>();
    private final Random random=new Random();
    private final TreeMap<Integer,T> borders=new TreeMap<>();
    private void update()
    {
        borders.clear();
        int cur=0;
        for(Map.Entry<T,Integer> entry: weights.entrySet())
        {
            cur+=entry.getValue();
            borders.put(cur, entry.getKey());
        }
    }
    public WeightedSelector(){}
    public WeightedSelector(Map<T,Integer> weights)
    {
        this.weights.putAll(weights);
        update();
    }
    public void put(T key, int value)
    {
        if(value<=0)
        {
            weights.remove(key);
        }
        else
        {
            weights.put(key,value);
        }
        update();
    }
    public T select()
    {
        int key= random.nextInt(65536);
        Map.Entry<Integer,T> entry=borders.higherEntry(key);
        return entry==null?null:entry.getValue();
    }
    public Map<T, Integer> asMap(){return weights;}
}
