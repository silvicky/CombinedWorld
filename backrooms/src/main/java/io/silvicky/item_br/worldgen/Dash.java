package io.silvicky.item_br.worldgen;

import java.util.ArrayDeque;

public class Dash
{
    private final AbstractSegment<?> segment;

    private final ArrayDeque<Double> nodes = new ArrayDeque<>();

    private boolean status;

    private static final int defaultDashLength=6;

    private static final int defaultDashSpacing=9;

    public Dash(AbstractSegment<?> segment, int dashLength, int dashSpacing, boolean initialStatus)
    {
        this.segment = segment;
        this.status=initialStatus;
        int dashCount=(int) segment.length()/(dashLength+dashSpacing);
        if(dashCount<=0)return;
        double dashRealLength= segment.length()/dashCount;
        if(dashRealLength<=dashSpacing)return;
        double cur;
        if(initialStatus) {
            cur = (double) dashLength / 2;
            for(int i=0;i<dashCount;i++)
            {
                nodes.add(cur);
                cur+=dashRealLength-dashLength;
                nodes.add(cur);
                cur+=dashLength;
            }
        }
        else {
            cur = (dashRealLength - dashLength) /2;
            for(int i=0;i<dashCount;i++)
            {
                nodes.add(cur);
                cur+=dashLength;
                nodes.add(cur);
                cur+=dashRealLength-dashLength;
            }
        }
    }

    public Dash(AbstractSegment<?> segment)
    {
        this(segment,defaultDashLength,defaultDashSpacing,true);
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
