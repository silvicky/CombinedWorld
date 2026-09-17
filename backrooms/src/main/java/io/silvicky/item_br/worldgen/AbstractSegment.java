package io.silvicky.item_br.worldgen;

public interface AbstractSegment {
    double getOffset(Point2d point2d);

    double getProgress(Point2d point2d);

    double getDistance(Point2d point2d);

    double length();

    double getRelativeProgress(Point2d point2d);
}
