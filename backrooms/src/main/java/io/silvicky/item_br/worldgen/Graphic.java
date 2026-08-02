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
        //TODO draw side rect
    }
}
