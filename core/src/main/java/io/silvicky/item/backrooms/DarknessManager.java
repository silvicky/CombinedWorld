package io.silvicky.item.backrooms;

import io.silvicky.item.StateSaver;
import net.minecraft.server.level.ServerLevel;

public class DarknessManager
{
    private static int getDarkness(ServerLevel level)
    {
        return StateSaver.getServerState(level.getServer()).darkness.getOrDefault(level.dimension.identifier(),0);
    }
    public static boolean isRenderModified(ServerLevel level)
    {
        return (getDarkness(level)&(1<<8))!=0;
    }
    public static boolean isCalculationModified(ServerLevel level)
    {
        return (getDarkness(level)&(1<<9))!=0;
    }
    public static int getRender(ServerLevel level)
    {
        return getDarkness(level)&((1<<4)-1);
    }
    public static int getCalculation(ServerLevel level)
    {
        return (getDarkness(level)>>4)&((1<<4)-1);
    }
}
