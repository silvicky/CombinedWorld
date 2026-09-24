package io.silvicky.item_br.worldgen;

import com.mojang.datafixers.util.Pair;

import java.util.*;
import java.util.function.BiConsumer;

import static java.lang.Math.*;

public class Graphic
{
    public static void fill(int x, List<Integer> zs, BiConsumer<Integer, Integer> consumer)
    {
        if(zs.isEmpty()) return;
        int minZ= zs.getFirst();
        int maxZ= zs.getFirst();
        for(int i=1;i<zs.size();i++)
        {
            minZ = min(minZ, zs.get(i));
            maxZ = max(maxZ, zs.get(i));
        }
        for(int z=minZ; z<=maxZ; z++)consumer.accept(x, z);
    }

    public static <T extends AbstractSegment<T>> void drawSideRect(T segment, RoadPattern pattern)
    {
        //TODO use a timestamp in the future to avoid this
        //todo still mismatch, also in lines, the problem seems to be that, some ending points are not accessible at all, and all should be rewritten
        Map<Point2, BiConsumer<Integer, Integer>> edges=new HashMap<>();
        Map<Point2, BiConsumer<Integer, Integer>> rects=new HashMap<>();
        for(Pair<Double, BiConsumer<Integer, Integer>> i: pattern.features())
        {
            segment.move(i.getFirst()).draw((x, z)-> edges.put(new Point2(x,z), i.getSecond()));
        }
        for(Pair<Pair<Double,Double>, BiConsumer<Integer, Integer>> i: pattern.rectFeatures())
        {
            segment.drawRectOf(i.getFirst().getFirst(),
                    i.getFirst().getSecond(),
                    (x,z)->{
                        if(!edges.containsKey(new Point2(x,z)))rects.put(new Point2(x,z),i.getSecond());
                    });
        }
        segment.drawRectOf(pattern.min(),
                pattern.max(),
                (x,z)-> {
                    if(!edges.containsKey(new Point2(x,z))&&!rects.containsKey(new Point2(x,z)))pattern.road().accept(x, z);
                });
        for(Pair<Pair<Double,Double>, BiConsumer<Integer, Integer>> i: pattern.rectFeatures())
        {
            segment.drawRectOf(i.getFirst().getFirst(),
                    i.getFirst().getSecond(),
                    (x,z)->{
                        if(!edges.containsKey(new Point2(x,z)))i.getSecond().accept(x, z);
                    });
        }
        for(Pair<Double, BiConsumer<Integer, Integer>> i: pattern.features())
        {
            segment.move(i.getFirst()).draw(i.getSecond());
        }
    }

    public static List<Double> solveQuadratic(double a, double b, double c)
    {
        List<Double> ret=new ArrayList<>();
        if(abs(a)<1e-6)
        {
            ret.add(-c/b);
            return ret;
        }
        double det=b*b-4*a*c;
        if(det<0)return ret;
        if(det==0)
        {
            ret.add(-b/(2*a));
            return ret;
        }
        ret.add((-b-sqrt(det))/(2*a));
        ret.add((-b+sqrt(det))/(2*a));
        return ret;
    }

    /**
     * @return two centers
     */
    public static Point2d[] connect(Point2 p0, Point2 d0, Point2 p1, Point2 d1)
    {
        Point2 d0v=d0.turnLeft();
        if(d0v.dot(p1.sub(p0))<0)d0v=d0v.scale(-1);
        Point2 d1v=d1.turnLeft();
        if(d1v.dot(p0.sub(p1))<0)d1v=d1v.scale(-1);
        if(d0v.dot(d1v)>0)
        {
            throw new RuntimeException("A straight line might be better here...");
        }
        double l0=d0v.len();
        double l1=d1v.len();
        //c0{p0.x + r * d0v.x / l0, ...}
        //c1-c0{(d1v.x/l1-d0v.x/l0)*r+(p1.x-p0.x), ...}
        double kcx=d1v.x/l1-d0v.x/l0;
        double bcx=p1.x-p0.x;
        double kcz=d1v.z/l1-d0v.z/l0;
        double bcz=p1.z-p0.z;
        //len2(c1-c0)=4r^2
        //that is, (kcx*r+bcx)^2+...=4r^2, or (kcx*kcx+kcz*kcz-4)r^2+(2*kcx*bcx+2*kcz*bcz)r+(bcx*bcx+bcz*bcz)=0
        List<Double> rs=solveQuadratic(kcx*kcx+kcz*kcz-4,2*kcx*bcx+2*kcz*bcz,bcx*bcx+bcz*bcz);
        if(rs.isEmpty())
        {
            throw new RuntimeException("No solution found!");
        }
        double r;
        if(rs.size()==1)r=rs.getFirst();
        else
        {
            double r0 =rs.getFirst();
            double r1 =rs.getLast();
            if(r0 > r1)
            {
                double t= r0;
                r0 = r1;
                r1 =t;
            }
            if(r0 <=0)r= r1;
            else if(r1 >=1e6)r= r0;
            else r= r1;
        }
        if(r<=0||r>=1e6||d0v.scaleTo(r).len2()==0||d1v.scaleTo(r).len2()==0)
        {
            throw new RuntimeException("Invalid solution!");
        }
        return new Point2d[]{
                new Point2d(p0).add(new Point2d(d0v).scaleTo(r)),
                new Point2d(p1).add(new Point2d(d1v).scaleTo(r))};
    }

    public static int getSlopeLine(Point2 cur, Line line, double base, double height, double bufferInsideLine)
    {
        double ratio=(line.getProgress(new Point2d(cur))-line.dStart())/(line.dEnd()-line.dStart());
        ratio=(ratio-bufferInsideLine)/(1-2*bufferInsideLine);
        ratio=clamp(ratio,0,1);
        return (int)round(base+height*ratio);
    }

    public static double getSlopeArcD(Point2 cur, Arc arc, double base, double height, double bufferStart, double bufferEnd)
    {
        Point2d center=arc.center();
        Point2 p0=arc.start();
        Point2 p1=arc.end();
        final double bufferOutsideArc =0.1;
        double t0=new Point2d(p0).sub(center).atan2();
        double t1=new Point2d(p1).sub(center).atan2();
        if(t1<t0- bufferOutsideArc)t1+=2*PI;
        double tc=new Point2d(cur).sub(center).atan2();
        if(tc<t0- bufferOutsideArc)tc+=2*PI;
        if(tc>t1+ bufferOutsideArc)tc-=2*PI;
        double ratio=(tc-t0-bufferStart)/(t1-t0-bufferStart-bufferEnd);
        ratio=clamp(ratio,0,1);
        return base+ratio*height;
    }

    public static int getSlopeArc(Point2 cur, Arc arc, double base, double height, double bufferInsideArc)
    {
        return (int)round(getSlopeArcD(cur, arc, base, height, bufferInsideArc, bufferInsideArc));
    }

    public static int getSlopeArc(Point2 cur, Arc arc, double base, double height, double bufferStart, double bufferEnd)
    {
        return (int)round(getSlopeArcD(cur, arc, base, height, bufferStart, bufferEnd));
    }

    public static Arc getInscribedCircle(Point2d p, Point2 d0, Point2 d1, double r)
    {
        Point2d d0d=new Point2d(d0);
        Point2d d1d=new Point2d(d1);
        Point2d avg=d0d.normalize().add(d1d.normalize());
        double sin=abs(avg.sin(d0d));
        double tan=abs(avg.tan(d1d));
        double dis=r/sin;
        double dis2=r/tan;
        Point2d c=p.add(avg.scaleTo(dis));
        Point2d p0=p.add(d0d.scaleTo(dis2));
        Point2d p1=p.add(d1d.scaleTo(dis2));
        return new Arc(
                c,
                r,
                p0.sub(c).atan2(),
                p1.sub(c).atan2());
    }

    /**
     * note the names!
     */
    public static Point2d getIntersection(Point2d p0, Point2 d0v, Point2d p1, Point2 d1v)
    {
        //d.x * x + d.z * z = ...
        double a0=d0v.x;
        double a1=d1v.x;
        double b0=d0v.z;
        double b1=d1v.z;
        double c0=-new Point2d(d0v).dot(p0);
        double c1=-new Point2d(d1v).dot(p1);
        double det=a0*b1-a1*b0;
        double x=(b0*c1-b1*c0)/det;
        double z=(a1*c0-a0*c1)/det;
        //(b0c1-b1c0)dx/det+(a1c0-a0c1)dz/det
        //=(a0b0c1-a0b1c0+a1b0c0-a0b0c1)/det
        //=(a1b0-a0b1)c0/det=-c0
        return new Point2d(x,z);
    }

    /**
     * @return points on d0 and d1
     */
    public static Point2[] getLineOutsideInscribedCircle(Point2d p, Point2 d0, Point2 d1, Point2d center, double r, double bufferOutsideArc)
    {
        Point2[] ret=new Point2[2];
        double d=center.sub(p).len()+r+bufferOutsideArc;
        Point2d rot=center.sub(p);
        Point2d i=rot.scaleTo(d).add(p);
        Point2 rotI=new Point2(rot);
        ret[0]=new Point2(getIntersection(p,d0.turnLeft(), i,rotI));
        ret[1]=new Point2(getIntersection(p,d1.turnLeft(), i,rotI));
        return ret;
    }

    /**
     * If direction=true, arc is drawn on the "right" if the line is below the circle.
     * @return center, points on the line and circle
     */
    public static Arc getInscribedCircleOfCircleAndLine(Point2d center, Point2 intersection, double r, boolean direction)
    {
        Point2d d=center.sub(new Point2d(intersection));
        Point2d dv=d.turnLeft();
        Point2d intersectionD=new Point2d(intersection);
        if(direction)dv=dv.scale(-1);
        double r0=d.len();
        //sqrt (r+r0)^2-(r-r0)^2
        //=... (r^2+2rr0+r0^2)-(r^2-2rr0+r0^2)
        //=sqrt(4rr0)
        double dis=sqrt(4*r*r0);
        Point2d pLine=intersectionD.add(dv.scaleTo(dis));
        Point2d pCenter=pLine.add(d.scaleTo(r));
        Point2d pJoint=center.add(pCenter.sub(center).scaleTo(r0));
        Point2 pLineI=new Point2(pLine);
        Point2 pJointI=new Point2(pJoint);
        if(direction)return new Arc(pCenter,pLineI,pJointI,r);
        else return new Arc(pCenter,pJointI,pLineI,r);
    }
}
