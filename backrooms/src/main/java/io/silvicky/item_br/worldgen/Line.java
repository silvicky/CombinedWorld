package io.silvicky.item_br.worldgen;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

/*
 * This line is x*cos(a)+z*sin(a)=b
 * The distance is x*sin(a)-z*cos(a)
 */
public record Line(double a, Point2 start, Point2 end, double b, double dStart, double dEnd)
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
}
