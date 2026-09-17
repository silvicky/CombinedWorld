package io.silvicky.item_br.worldgen;

import static java.lang.Math.*;

/*
 * This line is x*cos(a)+z*sin(a)=b
 * The distance is x*sin(a)-z*cos(a)
 */
public record Line(double a, Point2 start, Point2 end, double b, double dStart, double dEnd) implements AbstractSegment
{
    public Line(Point2 start, Point2 end)
    {
        double a=new Point2d(end.sub(start).turnLeft()).atan2();
        double b=start.x*cos(a)+start.z*sin(a);
        this(a, start, end, b);
    }

    public Line(double a, Point2 start, Point2 end, double b)
    {
        double dStart=start.x*sin(a)-start.z*cos(a);
        double dEnd=end.x*sin(a)-end.z*cos(a);
        this(a,start,end,b,dStart,dEnd);
    }

    public Line(double a, double b, double dStart, double dEnd)
    {
        Point2 start=new Point2(new Point2d(b*cos(a)+dStart*sin(a),b*sin(a)-dStart*cos(a)));
        Point2 end=new Point2(new Point2d(b*cos(a)+dEnd*sin(a),b*sin(a)-dEnd*cos(a)));
        this(a,start,end,b,dStart,dEnd);
    }

    @Override
    public double getOffset(Point2d point2d)
    {
        return point2d.x*cos(a)+point2d.z*sin(a);
    }

    @Override
    public double getProgress(Point2d point2d)
    {
        return point2d.x*sin(a)-point2d.z*cos(a);
    }

    @Override
    public double getDistance(Point2d point2d)
    {
        double p=getProgress(point2d);
        double d=getOffset(point2d)-b;
        double dx;
        double ts=min(dStart,dEnd);
        double te=max(dStart,dEnd);
        if(p<ts)dx=ts-p;
        else if(p>te)dx=p-te;
        else dx=0;
        return sqrt(d*d+dx*dx);
    }

    @Override
    public double length() {
        return abs(dEnd-dStart);
    }

    @Override
    public double getRelativeProgress(Point2d point2d)
    {
        double d=getProgress(point2d);
        if(dStart<dEnd)return d-dStart;
        return d-dEnd;
    }
}
