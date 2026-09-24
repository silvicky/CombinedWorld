package io.silvicky.item_br.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static io.silvicky.item_br.worldgen.Graphic.fill;
import static java.lang.Math.*;

/*
 * This line is x*cos(a)+z*sin(a)=b
 * The distance is x*sin(a)-z*cos(a)
 */
public record Line(double a, Point2 start, Point2 end, double b, double dStart, double dEnd) implements AbstractSegment<Line>
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

    @Override
    public Line move(double offset) {
        return new Line(a,b+offset,dStart,dEnd);
    }

    @Override
    public void draw(BiConsumer<Integer, Integer> consumer) {
        double dx = sin(a);
        double dz = -cos(a);

        Point2 mx=new Point2(dx>=0?1:-1,0);
        Point2 mz=new Point2(0,dz>=0?1:-1);

        Point2 advance, shift;

        if(abs(dx)>=abs(dz))
        {
            advance=mx;
            shift=mz;
        }
        else
        {
            advance=mz;
            shift=mx;
        }

        Point2 realStart;
        double realEnd;

        if(dStart<dEnd)
        {
            realStart=start;
            realEnd=end.x*sin(a)-end.z*cos(a);
        }
        else {
            realStart=end;
            realEnd=start.x*sin(a)-start.z*cos(a);
        }

        int x=realStart.x;
        int z=realStart.z;

        while (true) {
            consumer.accept(x, z);
            double d=getProgress(new Point2d(x,z));
            if(d>=realEnd)break;
            x+=advance.x;
            z+=advance.z;
            double err=getDistance(new Point2d(x,z));
            int x1=x+shift.x;
            int z1=z+shift.z;
            double err1=getDistance(new Point2d(x1,z1));
            if(err1<err)
            {
                x=x1;
                z=z1;
            }
        }
    }

    private static void drawRect(Line l0, Line l1, BiConsumer<Integer, Integer> consumer)
    {
        Map<Integer, List<Integer>> points=new HashMap<>();
        BiConsumer<Integer,Integer> consumerBorder = (x,z)->points.computeIfAbsent(x,_->new ArrayList<>()).add(z);
        l0.draw(consumerBorder);
        l1.draw(consumerBorder);
        new Line(l0.a()-PI/2,l0.dStart(),-l0.b(),-l1.b()).draw(consumerBorder);
        new Line(l0.a()-PI/2,l0.dEnd(),-l0.b(),-l1.b()).draw(consumerBorder);
        for(Map.Entry<Integer, List<Integer>> i:points.entrySet())
        {
            fill(i.getKey(), i.getValue(), consumer);
        }
    }

    @Override
    public void drawRectOf(double min, double max, BiConsumer<Integer, Integer> consumer) {
        drawRect(move(min),move(max),consumer);
    }
}
