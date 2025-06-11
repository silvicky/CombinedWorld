package io.silvicky.item.cfg;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;


import java.io.*;
import java.nio.file.Paths;

import static io.silvicky.item.common.Util.*;
class Cfg
{
    public boolean useStorage=true;
}
public class JSONConfig {
    public static Boolean useStorage=true;
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
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
