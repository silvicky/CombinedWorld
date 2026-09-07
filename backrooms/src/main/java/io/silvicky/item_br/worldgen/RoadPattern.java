package io.silvicky.item_br.worldgen;

import java.util.List;
import java.util.function.BiConsumer;
import com.mojang.datafixers.util.Pair;

public record RoadPattern(double min, double max, BiConsumer<Integer, Integer> road, List<Pair<Double, BiConsumer<Integer, Integer>>> features) {
}
