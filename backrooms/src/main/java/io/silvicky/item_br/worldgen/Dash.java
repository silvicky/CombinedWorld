package io.silvicky.item_br.worldgen;

import java.util.ArrayDeque;
import java.util.Queue;

public class Dash
{
    private final AbstractSegment<?> segment;

    private final Queue<Double> nodes = new ArrayDeque<>();

    private boolean status=true;

    private static final int defaultDashLength=6;

    private static final int defaultDashSpacing=9;

    public Dash(AbstractSegment<?> segment, int dashLength, int dashSpacing)
    {
        this.segment = segment;
        int dashCount=(int) segment.length()/(dashLength+dashSpacing);
        if(dashCount<=0)return;
        double dashRealLength= segment.length()/dashCount;
        if(dashRealLength<=dashSpacing)return;
        double cur= (double) dashLength /2;//TODO
        for(int i=0;i<dashCount;i++)
        {
            nodes.add(cur);
            cur+=dashRealLength-dashLength;
            nodes.add(cur);
            cur+=dashLength;
        }
    }

    public Dash(AbstractSegment<?> segment)
    {
        this(segment,defaultDashLength,defaultDashSpacing);
    }

    public boolean get(Point2d point2d) {
        double cur= segment.getRelativeProgress(point2d);
        if(nodes.isEmpty())return status;
        double nextNode=nodes.peek();
        if(cur>=nextNode)
        {
            status=!status;
            nodes.remove();
        }
        return status;
    }
}
