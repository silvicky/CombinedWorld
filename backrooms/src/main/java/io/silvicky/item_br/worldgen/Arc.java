package io.silvicky.item_br.worldgen;

import static java.lang.Math.*;

public record Arc(Point2d center, Point2 start, Point2 end, double r, double aStart, double aEnd)
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
}
