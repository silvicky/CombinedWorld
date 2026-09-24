package io.silvicky.item_br.worldgen;

import java.util.List;
import java.util.function.BiConsumer;

import com.mojang.datafixers.util.Pair;

public record RoadPattern(double min, double max, BiConsumer<Integer, Integer> road, List<Pair<Double, BiConsumer<Integer, Integer>>> features, List<Pair<Pair<Double,Double>,BiConsumer<Integer,Integer>>> rectFeatures)
{
    public double width()
    {
        return max - min;
    }

    public RoadPattern flip()
    {
        return new RoadPattern(
                -max,
                -min,
                road,
                features.stream().map(i->new Pair<>(-i.getFirst(),i.getSecond())).toList().reversed(),
                rectFeatures.stream().map(i->new Pair<>(new Pair<>(-i.getFirst().getSecond(),-i.getFirst().getFirst()),i.getSecond())).toList().reversed());
    }
}
