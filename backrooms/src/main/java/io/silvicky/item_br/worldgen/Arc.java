package io.silvicky.item_br.worldgen;

import static java.lang.Math.*;

public record Arc(Point2d center, Point2 start, Point2 end, double r, double aStart, double aEnd) implements AbstractSegment<Arc>
{
    public Arc(Point2d center, Point2 start, Point2 end, double r)
    {
        double aStart=new Point2d(start).sub(center).atan2();
        double aEnd=new Point2d(end).sub(center).atan2();
        if(aEnd<aStart)aEnd+=2*PI;
        this(center,start,end,r,aStart,aEnd);
    }

    public Arc(Point2d center, double r, double aStart, double aEnd)
    {
        if(aEnd<aStart)aEnd+=2*PI;
        Point2 start=new Point2(center.add(new Point2d(r*cos(aStart),r*sin(aStart))));
        Point2 end=new Point2(center.add(new Point2d(r*cos(aEnd),r*sin(aEnd))));
        this(center,start,end,r,aStart,aEnd);
    }

    @Override
    public double getOffset(Point2d point2d)
    {
        return point2d.sub(center).len();
    }

    @Override
    public double getProgress(Point2d point2d)
    {
        return point2d.sub(center).atan2();
    }

    @Override
    public double getDistance(Point2d point2d)
    {
        double p=getProgress(point2d);
        if(p<aStart)p+=2*PI;
        if(p>aEnd)
        {
            //TODO use accurate
            return min(new Point2d(start).sub(point2d).len(),
                    new Point2d(end).sub(point2d).len());
        }
        return abs(getOffset(point2d)-r);
    }

    @Override
    public double length() {
        return r*(aEnd-aStart);
    }

    @Override
    public double getRelativeProgress(Point2d point2d)
    {
        //TODO better rounding
        double p=getProgress(point2d);
        if(p<aStart-0.1)p+=2*PI;
        return r*(p-aStart);
    }

    @Override
    public Arc move(double offset) {
        return new Arc(center,r+offset,aStart,aEnd);
    }
}
