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
    int randomChunkRange=7;
    int linearChunkRange=100;
    int xXor=1;
    int zXor=1;
    /**
     * How long a chunk the player has just interacted with keeps its client position, in milliseconds.
     *
     * <p>This has to cover the full round trip, not just the local work. A click travels client to server,
     * the server applies it, and the result travels back before the client can see the outcome. On a LAN
     * that is a couple of milliseconds and any small value works. On a weak connection it can be seconds,
     * and a short window lets the page be re-rolled while the outcome is still in flight - at which point
     * the server tells the client about a different block than the one they hit, and the block they broke
     * comes back.
     *
     * <p>So this is deliberately generous. Over-holding only means a page the player recently touched
     * keeps its position a little longer than it otherwise would, which costs a little of the mechanic.
     * Under-holding produces the block-reverting glitch, which is much worse.
     */
    long remapPinMillis=10_000L;
}
public class JSONConfig {
    public static boolean useStorage=true;
    public static boolean playerVisibilityRandomize=true;
    public static long playerVisibilityRange=2;
    public static int randomChunkRange=7;
    public static int linearChunkRange=100;
    public static int xXor=1;
    public static int zXor=1;
    /** @see Cfg#remapPinMillis */
    public static long remapPinMillis=10_000L;
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
                randomChunkRange=cfg.randomChunkRange;
                linearChunkRange=Math.max(0,cfg.linearChunkRange);
                xXor=cfg.xXor;
                zXor=cfg.zXor;
                //A zero or negative window would disable the hold entirely and bring the block-reverting
                //glitch straight back, so floor it rather than trusting the file.
                remapPinMillis=Math.max(1_000L,cfg.remapPinMillis);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
