package io.silvicky.item_br.worldgen;

import java.util.function.BiConsumer;

public interface AbstractSegment<T> {
    double getOffset(Point2d point2d);

    double getProgress(Point2d point2d);

    double getDistance(Point2d point2d);

    double length();

    double getRelativeProgress(Point2d point2d);

    T move(double offset);

    void draw(BiConsumer<Integer,Integer> consumer);
}
