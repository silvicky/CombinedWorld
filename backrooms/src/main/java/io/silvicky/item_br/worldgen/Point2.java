package io.silvicky.item_br.worldgen;

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
        return new Point2((int)(x*scale),(int)(z*scale));
    }

    public Point2 scale(int scale)
    {
        return new Point2(x*scale,z*scale);
    }

    public double len()
    {
        return sqrt(x*x + z*z);
    }

    public Point2 scaleTo(double target)
    {
        return scale(target/len());
    }

    public Point2 turnLeft()
    {
        return new Point2(-z, x);
    }
}
