package io.silvicky.item_br.worldgen;

import static java.lang.Math.sqrt;

public record Point2d(double x, double z)
{
    public Point2d(Point2 p)
    {
        this(p.x, p.z);
    }

    public Point2d add(Point2d point)
    {
        return new Point2d(this.x + point.x, this.z + point.z);
    }

    public Point2d scale(double scale)
    {
        return new Point2d(x * scale, z * scale);
    }

    public double len2()
    {
        return x * x + z * z;
    }

    public Point2d normalize()
    {
        return new Point2d(x / len(), z / len());
    }

    public double dot(Point2d point)
    {
        return x * point.x + z * point.z;
    }

    public double cross(Point2d point)
    {
        return z * point.x - x * point.z;
    }

    public double sin(Point2d point)
    {
        return cross(point) / len() / point.len();
    }

    public double cos(Point2d point)
    {
        return dot(point) / len() / point.len();
    }

    public double tan(Point2d point)
    {
        return sin(point) / cos(point);
    }

    public double len()
    {
        return sqrt(len2());
    }

    public Point2d scaleTo(double target)
    {
        return scale(target / len());
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == null || getClass() != o.getClass()) return false;
        Point2d point2 = (Point2d) o;
        return x == point2.x && z == point2.z;
    }

}
