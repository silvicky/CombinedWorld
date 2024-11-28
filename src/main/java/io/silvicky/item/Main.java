package io.silvicky.item;

import net.minecraft.server.world.ServerWorld;
import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.StringTag;
import net.querz.nbt.tag.Tag;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Objects;

import static io.silvicky.item.InventoryManager.*;

public class Main {
    public static String getDimensionId(String id)
    {
        if(id.endsWith(NETHER))id=id.substring(0,id.length()-10)+OVERWORLD;
        if(id.endsWith(END))id=id.substring(0,id.length()-7)+OVERWORLD;
        return id;
    }
    public static void main(String[] args) throws IOException {
        BufferedReader reader=new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Welcome to CombinedWorld maintenance console!");
        System.out.println("Give me a ItemStorage.dat path for later use:");
        String mainFile;
        while(true)
        {
            mainFile=reader.readLine();
            if(!new File(mainFile).exists())
            {
                System.out.println("uhh...retry(no quote, just a bare line)");
            }
            else break;
        }
        while(true)
        {
            NamedTag mainDat= NBTUtil.read(mainFile);
            CompoundTag mainTag= (CompoundTag) mainDat.getTag();
            ListTag<CompoundTag> pos= (ListTag<CompoundTag>) mainTag.getCompoundTag("data").getListTag("pos");
            ListTag<CompoundTag> saved= (ListTag<CompoundTag>) mainTag.getCompoundTag("data").getListTag("saved");
            System.out.println("Choose operation you need:");
            System.out.println("1.Rename group");
            System.out.println("2.Turn non-vanilla player data into mod stored data(to prevent collision)");
            System.out.println("9.Quit");
            int i=Integer.parseInt(reader.readLine());
            if(i==1)
            {
                System.out.println("Input old group name:");
                String old=reader.readLine();
                System.out.println("Input new group name:");
                String neu=reader.readLine();
                for(CompoundTag ctg1:pos)
                {
                    if(ctg1.getStringTag("dimension").getValue().startsWith(old))
                    {
                        String oldVal=ctg1.getStringTag("dimension").getValue();
                        ctg1.getStringTag("dimension").setValue(neu+oldVal.replaceFirst(old,""));
                    }
                    if(ctg1.getStringTag("rdim").getValue().startsWith(old))
                    {
                        String oldVal=ctg1.getStringTag("rdim").getValue();
                        ctg1.getStringTag("rdim").setValue(neu+oldVal.replaceFirst(old,""));
                    }
                }
                for(CompoundTag ctg1:saved)
                {
                    if(ctg1.getStringTag("dimension").getValue().equals(old))
                    {
                        ctg1.getStringTag("dimension").setValue(neu);
                    }
                }
                /*System.out.println("Now, give me a path to level.dat:");
                String ld;
                while(true)
                {
                    ld=reader.readLine();
                    if(!new File(ld).exists())
                    {
                        System.out.println("uhh...retry(no quote, just a bare line)");
                    }
                    else break;
                }
                NamedTag ldTag=NBTUtil.read(ld);
                CompoundTag ctg0=((CompoundTag) ldTag.getTag())
                        .getCompoundTag("Data")
                                .getCompoundTag("WorldGenSettings")
                                        .getCompoundTag("dimensions");
                for(Map.Entry<String, Tag<?>> en:ctg0)
                {
                    if(!en.getKey().startsWith(old))continue;
                    Tag<?> ctg1=en.getValue();
                    ctg0.put(neu+en.getKey().replace(old,""),ctg1);
                    ctg0.remove(en.getKey());
                }*/
                System.out.println("Now, give me a path to playerdata folder:");
                String pd;
                while(true)
                {
                    pd=reader.readLine();
                    if(!(new File(pd).exists()&&new File(pd).isDirectory()))
                    {
                        System.out.println("uhh...retry(no quote, just a bare line)");
                    }
                    else break;
                }
                File pdFile=new File(pd);
                for(File file: Objects.requireNonNull(pdFile.listFiles()))
                {
                    if(!file.getPath().endsWith("dat"))continue;
                    NamedTag tg=NBTUtil.read(file);
                    CompoundTag ctg1= (CompoundTag) tg.getTag();
                    if(ctg1.getStringTag("Dimension").getValue().startsWith(old))
                    {
                        String oldVal=ctg1.getStringTag("Dimension").getValue();
                        ctg1.getStringTag("Dimension").setValue(neu+oldVal.replaceFirst(old,""));
                    }
                    if(ctg1.getStringTag("SpawnDimension")!=null&&ctg1.getStringTag("SpawnDimension").getValue().startsWith(old))
                    {
                        String oldVal=ctg1.getStringTag("SpawnDimension").getValue();
                        ctg1.getStringTag("SpawnDimension").setValue(neu+oldVal.replaceFirst(old,""));
                    }
                    NBTUtil.write(tg,file);
                }
                NBTUtil.write(mainDat,mainFile);
                //NBTUtil.write(ldTag,ld);
                System.out.println("Done. You need to move world folder and level.dat manually.");
            }
            else if(i==2)
            {
                System.out.println("Now, give me a path to playerdata folder:");
                String pd;
                while(true)
                {
                    pd=reader.readLine();
                    if(!(new File(pd).exists()&&new File(pd).isDirectory()))
                    {
                        System.out.println("uhh...retry(no quote, just a bare line)");
                    }
                    else break;
                }
                File pdFile=new File(pd);
                for(File file: Objects.requireNonNull(pdFile.listFiles()))
                {
                    if(!file.getPath().endsWith("dat"))continue;
                    NamedTag tg=NBTUtil.read(file);
                    CompoundTag tgc= (CompoundTag) tg.getTag();
                    if(tgc.getStringTag("Dimension").getValue().startsWith("minecraft:"))continue;
                    CompoundTag posDat=new CompoundTag();
                    CompoundTag savedDat=new CompoundTag();
                    CompoundTag posDat1=new CompoundTag();
                    posDat1.put("x",tgc.getListTag("Pos").asDoubleTagList().get(0));
                    posDat1.put("y",tgc.getListTag("Pos").asDoubleTagList().get(1));
                    posDat1.put("z",tgc.getListTag("Pos").asDoubleTagList().get(2));
                    posDat.put("pos",posDat1);
                    posDat.put("rdim",tgc.getStringTag("Dimension"));
                    posDat.put("player",new StringTag(file.getName().replaceFirst(".dat","")));
                    posDat.put("dimension",new StringTag(getDimensionId( tgc.getStringTag("Dimension").getValue())));
                    pos.add(posDat);
                    savedDat.put("inventory",tgc.getListTag("Inventory"));
                    savedDat.put("ender",tgc.getListTag("EnderItems"));
                    String did=tgc.getStringTag("Dimension").getValue();
                    savedDat.put("dimension",new StringTag(did.substring(0,did.indexOf(':'))));;
                    savedDat.put("player",new StringTag(file.getName().replaceFirst(".dat","")));
                    savedDat.put("air",tgc.getShortTag("Air"));
                    savedDat.put("gamemode",tgc.getIntTag("playerGameType"));
                    saved.add(savedDat);
                }
                NBTUtil.write(mainDat,mainFile);
            }
            else if(i==9)
            {
                break;
            }
        }
    }
}
