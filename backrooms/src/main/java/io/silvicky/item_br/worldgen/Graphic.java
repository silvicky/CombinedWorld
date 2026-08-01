package io.silvicky.item_br.worldgen;

import java.util.function.BiConsumer;

import static java.lang.Math.abs;

public class Graphic
{
    public static void drawLine(int x0, int y0, int x1, int y1, BiConsumer<Integer, Integer> consumer)
    {
        int dx = abs(x1 - x0);
        int dy = abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            consumer.accept(x0, y0);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }
}
