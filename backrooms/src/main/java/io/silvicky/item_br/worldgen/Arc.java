package io.silvicky.item_br.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static io.silvicky.item_br.worldgen.Graphic.fill;
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

    @Override
    public void draw(BiConsumer<Integer, Integer> consumer) {
        Point2 p0=start;
        double raStart =new Point2d(start).sub(center).atan2();
        double raEnd =new Point2d(end).sub(center).atan2();
        if(raEnd < raStart) raEnd +=2*PI;
        Point2[] move=
                {
                        new Point2(0,1),
                        new Point2(-1,0),
                        new Point2(0,-1),
                        new Point2(1,0),
                };
        int x=p0.x;
        int z=p0.z;
        while (true) {
            consumer.accept(x, z);
            double a=new Point2d(x,z).sub(center).atan2();
            if(a< raStart)a+=2*PI;
            if(a>= raEnd)break;
            Point2d dir=new Point2d(x,z).sub(center);
            int quadrant=0;
            if(dir.z<0)quadrant+=2;
            if(dir.x*dir.z<0||(dir.z==0&&dir.x<0))quadrant+=1;
            Point2 advance;
            Point2 shift;
            if((abs(dir.x)>abs(dir.z))^((quadrant&1)==0))
            {
                advance=move[(quadrant+1)%4];
                shift =move[quadrant];
            }
            else
            {
                advance=move[quadrant];
                shift =move[(quadrant+1)%4];
            }
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

    private static void drawFragmentedRing(Arc arc0, Arc arc1, BiConsumer<Integer, Integer> consumer) {
        Map<Integer, List<Integer>> points = new HashMap<>();
        arc0.draw((x, z) -> points.computeIfAbsent(x, _ -> new ArrayList<>()).add(z));
        arc1.draw((x, z) -> points.computeIfAbsent(x, _ -> new ArrayList<>()).add(z));
        new Line(arc0.aStart() - PI / 2,
                arc0.center().dot(new Point2d(sin(arc0.aStart()), -cos(arc0.aStart()))),
                arc0.center().dot(new Point2d(-cos(arc0.aStart()), -sin(arc0.aStart())))-arc0.r(),
                arc0.center().dot(new Point2d(-cos(arc0.aStart()), -sin(arc0.aStart())))-arc1.r())
                .draw((x, z) -> points.computeIfAbsent(x, _ -> new ArrayList<>()).add(z));
        new Line(arc0.aEnd() - PI / 2,
                arc0.center().dot(new Point2d(sin(arc0.aEnd()), -cos(arc0.aEnd()))),
                arc0.center().dot(new Point2d(-cos(arc0.aEnd()), -sin(arc0.aEnd())))-arc0.r(),
                arc0.center().dot(new Point2d(-cos(arc0.aEnd()), -sin(arc0.aEnd())))-arc1.r())
                .draw((x, z) -> points.computeIfAbsent(x, _ -> new ArrayList<>()).add(z));
        for (Map.Entry<Integer, List<Integer>> e : points.entrySet()) {
            fill(e.getKey(), e.getValue(), consumer);
        }
    }

    private static void drawRing(Arc arc0, Arc arc1, BiConsumer<Integer, Integer> consumer)
    {
        double border=0;
        double lb=arc0.aStart();
        double rb=arc0.aEnd();
        while(border<lb)border+=PI;
        while(border<=rb)
        {
            drawFragmentedRing(new Arc(arc0.center(),arc0.r(),lb,border),
                    new Arc(arc1.center(),arc1.r(),lb,border),
                    consumer);
            lb=border;
            border+=PI;
        }
        drawFragmentedRing(new Arc(arc0.center(),arc0.r(),lb,rb),
                new Arc(arc1.center(),arc1.r(),lb,rb),
                consumer);
    }

    @Override
    public void drawRectOf(double min, double max, BiConsumer<Integer, Integer> consumer) {
        drawRing(move(min),move(max),consumer);
    }
}
