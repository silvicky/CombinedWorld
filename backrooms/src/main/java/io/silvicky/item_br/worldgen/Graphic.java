package io.silvicky.item_br.worldgen;

import java.util.ArrayList;
import java.util.List;
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
        for (int i=0;i<1000;i++) {
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
}
