package io.silvicky.item_br.worldgen;

import static java.lang.Math.PI;

public record Arc(Point2d center, Point2 start, Point2 end, double r, double aStart, double aEnd)
{
    public Arc(Point2d center, Point2 start, Point2 end, double r)
    {
        double aStart=new Point2d(start).sub(center).atan2();
        double aEnd=new Point2d(end).sub(center).atan2();
        if(aEnd<aStart)aEnd+=2*PI;
        this(center,start,end,r,aStart,aEnd);
    }
}
