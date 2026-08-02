package io.silvicky.item_br.worldgen;

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
}
