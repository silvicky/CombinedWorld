package io.silvicky.item_br.worldgen;

import java.util.*;
import java.util.function.BiConsumer;

import static java.lang.Math.*;

public class Graphic
{
    public static void drawLine(Point2 p0, Point2 p1, BiConsumer<Integer, Integer> consumer)
    {
        int dx = abs(p1.x - p0.x);
        int dz = abs(p1.z - p0.z);
        int sx = p0.x < p1.x ? 1 : -1;
        int sz = p0.z < p1.z ? 1 : -1;
        int err = dx - dz;

        int x=p0.x;
        int z=p0.z;

        while (true) {
            consumer.accept(x, z);
            if (x == p1.x && z == p1.z) break;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; x += sx; }
            if (e2 < dx) { err += dx; z += sz; }
        }
    }

    private static void fill(int x, List<Integer> zs, BiConsumer<Integer, Integer> consumer)
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

    public static void drawRect(Point2 p00, Point2 p01, Point2 p10, Point2 p11, BiConsumer<Integer, Integer> consumer)
    {
        int minX=p00.x;
        int maxX=p00.x;
        for(Point2 p:new Point2[]{p01,p10,p11})
        {
            minX=min(minX,p.x);
            maxX=max(maxX,p.x);
        }
        List<List<Integer>> points=new ArrayList<>();
        for(int x=minX;x<=maxX;x++)points.add(new ArrayList<>());
        int baseX = minX;
        BiConsumer<Integer,Integer> consumerBorder = (x,z)->points.get(x- baseX).add(z);
        drawLine(p00,p01,consumerBorder);
        drawLine(p10,p11,consumerBorder);
        drawLine(p00,p10,consumerBorder);
        drawLine(p01,p11,consumerBorder);
        for(int i=0;i<points.size();i++)
        {
            fill(i+baseX, points.get(i), consumer);
        }
    }

    public static void drawSideRect(Point2 p0, Point2 p1, double width, BiConsumer<Integer, Integer> consumer, BiConsumer<Integer, Integer> consumerEdge)
    {
        Point2 vecLine=p1.sub(p0);
        Point2 vecTrans = vecLine.turnLeft().scaleTo(width);
        Point2 p10=p0.add(vecTrans);
        Point2 p11=p1.add(vecTrans);
        Set<Point2> edges=new HashSet<>();
        drawLine(p10,p11,(x,z)->edges.add(new Point2(x,z)));
        drawLine(p0,p1,(x,z)->edges.add(new Point2(x,z)));
        drawRect(p0,p1,p10,p11,(x,z)->
        {
            if(edges.contains(new Point2(x,z)))consumerEdge.accept(x,z);
            else consumer.accept(x,z);
        });
    }

    public static void drawArc(Point2 p0, Point2 p1, Point2 center, BiConsumer<Integer, Integer> consumer)
    {
        long r=center.sub(p1).len2();
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
            if (x == p1.x && z == p1.z) break;
            Point2 dir=new Point2(x,z).sub(center);
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
            long err=abs(new Point2(x,z).sub(center).len2()-r);
            int x1=x+shift.x;
            int z1=z+shift.z;
            long err1=abs(new Point2(x1,z1).sub(center).len2()-r);
            if(err1<err)
            {
                x=x1;
                z=z1;
            }
        }
    }

    private static List<List<Point2>> fragment(List<Point2> line, int cz)
    {
        List<List<Point2>> ret=new ArrayList<>();
        List<Point2> cur=new ArrayList<>();
        ret.add(cur);
        for(Point2 p:line)
        {
            cur.add(p);
            if(p.z==cz)
            {
                cur=new ArrayList<>();
                ret.add(cur);
                cur.add(p);
            }
        }
        if(ret.getLast().size()==1)ret.removeLast();
        return ret;
    }

    public static void drawRing(Point2 p00, Point2 p01, Point2 p10, Point2 p11, Point2 center, BiConsumer<Integer, Integer> consumer)
    {
        List<Point2> arc0=new ArrayList<>();
        List<Point2> arc1=new ArrayList<>();
        drawArc(p00,p01,center,(x,z)->arc0.add(new Point2(x,z)));
        drawArc(p10,p11,center,(x,z)->arc1.add(new Point2(x,z)));
        List<List<Point2>> arc0f=fragment(arc0,center.z);
        List<List<Point2>> arc1f=fragment(arc1,center.z);
        if(arc0f.size()!=arc1f.size())
        {
            throw new RuntimeException("Arc sizes mismatch!");
        }
        for(int i=0;i<arc0f.size();i++)
        {
            Map<Integer,List<Integer>> points=new HashMap<>();
            for(Point2 p:arc0f.get(i))
            {
                points.computeIfAbsent(p.x,_->new ArrayList<>()).add(p.z);
            }
            for(Point2 p:arc1f.get(i))
            {
                points.computeIfAbsent(p.x,_->new ArrayList<>()).add(p.z);
            }
            drawLine(arc0f.get(i).getFirst(),arc1f.get(i).getFirst(),
                    (x,z)->points.computeIfAbsent(x,_->new ArrayList<>()).add(z));
            drawLine(arc0f.get(i).getLast(),arc1f.get(i).getLast(),
                    (x,z)->points.computeIfAbsent(x,_->new ArrayList<>()).add(z));
            for(Map.Entry<Integer,List<Integer>> e:points.entrySet())
            {
                fill(e.getKey(),e.getValue(),consumer);
            }
        }
    }

    public static void drawSideRing(Point2 p0, Point2 p1, Point2 center, double width, BiConsumer<Integer, Integer> consumer, BiConsumer<Integer, Integer> consumerEdge)
    {
        Point2 vecTrans0 = p0.sub(center).scaleTo(width);
        Point2 vecTrans1 = p1.sub(center).scaleTo(width);
        Point2 p10=p0.add(vecTrans0);
        Point2 p11=p1.add(vecTrans1);
        Set<Point2> edges=new HashSet<>();
        drawArc(p10,p11,center,(x,z)->edges.add(new Point2(x,z)));
        drawArc(p0,p1,center,(x,z)->edges.add(new Point2(x,z)));
        drawRing(p0,p1,p10,p11,center,(x,z)->
        {
            if(edges.contains(new Point2(x,z))) consumerEdge.accept(x,z);
            else consumer.accept(x,z);
        });
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
    public static Point2[] connect(Point2 p0, Point2 d0, Point2 p1, Point2 d1)
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
        return new Point2[]{p0.add(d0v.scaleTo(r)),p1.add(d1v.scaleTo(r))};
    }

    public static int getSlopeLine(Point2 cur, Point2 p0, Point2 p1, int base, double height, double bufferInsideLine)
    {
        Point2 d=p1.sub(p0);
        Point2 dc=cur.sub(p0);
        double ratio=(double)d.dot(dc)/d.len2();
        ratio=(ratio-bufferInsideLine)/(1-2*bufferInsideLine);
        ratio=clamp(ratio,0,1);
        return base+(int)round(height*ratio);
    }

    public static int getSlopeArc(Point2 cur, Point2 p0, Point2 p1, Point2 center, int base, double height, double bufferInsideArc)
    {
        final double bufferOutsideArc =0.1;
        double t0=p0.sub(center).atan2();
        double t1=p1.sub(center).atan2();
        if(t1<t0- bufferOutsideArc)t1+=2*PI;
        double tc=cur.sub(center).atan2();
        if(tc<t0- bufferOutsideArc)tc+=2*PI;
        if(tc>t1+ bufferOutsideArc)tc-=2*PI;
        double ratio=(tc-t0-bufferInsideArc)/(t1-t0-2*bufferInsideArc);
        ratio=clamp(ratio,0,1);
        return base+(int)round(ratio*height);
    }

    /**
     * @return center, points on d0 and d1
     */
    public static Point2[] getInscribedCircle(Point2 p, Point2 d0, Point2 d1, double r)
    {
        Point2[] ret=new Point2[3];
        Point2d d0d=new Point2d(d0);
        Point2d d1d=new Point2d(d1);
        Point2d avg=d0d.normalize().add(d1d.normalize());
        double sin=abs(avg.sin(d0d));
        double tan=abs(avg.tan(d1d));
        double dis=r/sin;
        double dis2=r/tan;
        ret[0]=p.add(new Point2(avg.scaleTo(dis)));
        ret[1]=p.add(new Point2(d0d.scaleTo(dis2)));
        ret[2]=p.add(new Point2(d1d.scaleTo(dis2)));
        return ret;
    }

    /**
     * note the names!
     */
    public static Point2d getIntersection(Point2 p0, Point2 d0v, Point2 p1, Point2 d1v)
    {
        //d.x * x + d.z * z = ...
        double a0=d0v.x;
        double a1=d1v.x;
        double b0=d0v.z;
        double b1=d1v.z;
        double c0=-d0v.dot(p0);
        double c1=-d1v.dot(p1);
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
    public static Point2[] getLineOutsideInscribedCircle(Point2 p, Point2 d0, Point2 d1, Point2 center, double r)
    {
        final double bufferOutsideArc =3;
        Point2[] ret=new Point2[2];
        double d=center.sub(p).len()+r+bufferOutsideArc;
        Point2 rot=center.sub(p);
        Point2 i=rot.scaleTo(d).add(p);
        ret[0]=new Point2(getIntersection(p,d0.turnLeft(),i,rot));
        ret[1]=new Point2(getIntersection(p,d1.turnLeft(),i,rot));
        return ret;
    }

    /**
     * By default (direction=false), arc is drawn on the "left" if the line is below the circle.
     * @return center, points on the line and circle
     */
    public static Point2[] getInscribedCircleOfCircleAndLine(Point2 center, Point2 intersection, double r, boolean direction)
    {
        Point2 d=center.sub(intersection);
        Point2 dv=d.turnLeft();
        if(direction)dv=dv.scale(-1);
        double r0=d.len();
        //sqrt (r+r0)^2-(r-r0)^2
        //=... (r^2+2rr0+r0^2)-(r^2-2rr0+r0^2)
        //=sqrt(4rr0)
        double dis=sqrt(4*r*r0);
        Point2[] ret=new Point2[3];
        ret[1]=intersection.add(dv.scaleTo(dis));
        ret[0]=ret[1].add(d.scaleTo(r));
        ret[2]=center.add(ret[0].sub(center).scaleTo(r0));
        return ret;
    }
}
