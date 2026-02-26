package io.silvicky.item.cfg;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;


import java.io.*;
import java.nio.file.Paths;

import static io.silvicky.item.common.Util.*;
class Cfg
{
    boolean useStorage=true;
    boolean playerVisibilityRandomize=true;
    long playerVisibilityRange=2;
}
public class JSONConfig {
    public static boolean useStorage=true;
    public static boolean playerVisibilityRandomize=true;
    public static long playerVisibilityRange=2;
    protected static final File cfgFile = Paths.get(FabricLoader.getInstance().getConfigDir().toString(),"ItemStorage.json").toFile();
    protected static final Gson gson=new Gson();

    private static void reCreateCfg()
    {
        try {
            Writer writer=new FileWriter(cfgFile,false);
            gson.toJson(new Cfg(),writer);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void config(){
        if(!cfgFile.exists()) {

            reCreateCfg();
        }
        else
        {
            LOGGER.info("Find ItemStorage config!");
            try {
                Cfg cfg=gson.fromJson(new FileReader(cfgFile),Cfg.class);
                if(cfg==null)
                {
                    reCreateCfg();
                    return;
                }
                useStorage=cfg.useStorage;
                playerVisibilityRandomize=cfg.playerVisibilityRandomize;
                playerVisibilityRange=Math.max(2L,cfg.playerVisibilityRange);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
