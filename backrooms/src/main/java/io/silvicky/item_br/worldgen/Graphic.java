package io.silvicky.item_br.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        drawRect(p0,p1,p10,p11,consumer);
        drawLine(p10,p11,consumerEdge);
        drawLine(p0,p1,consumerEdge);
    }

    public static void drawArc(Point2 p0, Point2 p1, Point2 center, BiConsumer<Integer, Integer> consumer)
    {
        int r=center.sub(p1).len2();
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
            int err=abs(new Point2(x,z).sub(center).len2()-r);
            int x1=x+shift.x;
            int z1=z+shift.z;
            int err1=abs(new Point2(x1,z1).sub(center).len2()-r);
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
        drawRing(p0,p1,p10,p11,center,consumer);
        drawArc(p10,p11,center,consumerEdge);
        drawArc(p0,p1,center,consumerEdge);
    }

    public static List<Double> solveQuadratic(double a, double b, double c)
    {
        List<Double> ret=new ArrayList<>();
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
     * two centers
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
            //TODO ???
        }
        return new Point2[]{p0.add(d0v.scaleTo(r)),p1.add(d1v.scaleTo(r))};
    }
}
