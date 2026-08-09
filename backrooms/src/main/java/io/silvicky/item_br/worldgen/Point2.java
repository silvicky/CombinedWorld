package io.silvicky.item_br.worldgen;

import java.util.Objects;

import static java.lang.Math.round;
import static java.lang.Math.sqrt;

public class Point2
{
    public final int x;
    public final int z;

    public Point2(int x, int z)
    {
        this.x = x;
        this.z = z;
    }

    public Point2(Point2d point)
    {
        this.x=(int)round(point.x());
        this.z=(int)round(point.z());
    }

    public Point2 add(int x, int z)
    {
        return new Point2(this.x + x, this.z + z);
    }

    public Point2 add(Point2 point)
    {
        return new Point2(this.x + point.x, this.z + point.z);
    }

    public Point2 sub(Point2 point)
    {
        return new Point2(this.x - point.x, this.z - point.z);
    }

    public Point2 scale(double scale)
    {
        return new Point2((int)round(x*scale),(int)round(z*scale));
    }

    public long len2()
    {
        return (long) x *x + (long) z *z;
    }

    public long dot(Point2 point)
    {
        return (long) x *point.x + (long) z *point.z;
    }

    public long cross(Point2 point)
    {
        return (long) z *point.x - (long) x *point.z;
    }

    public double len()
    {
        return sqrt(len2());
    }

    public Point2 scaleTo(double target)
    {
        return scale(target/len());
    }

    public Point2 turnLeft()
    {
        return new Point2(z, -x);
    }

    public double atan2()
    {
        return Math.atan2(z,x);
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == null || getClass() != o.getClass()) return false;
        Point2 point2 = (Point2) o;
        return x == point2.x && z == point2.z;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(x, z);
    }
}
