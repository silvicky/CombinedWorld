# CombinedWorld

[中文](README_CN.md)

Switch between dimensions as if they are different servers.

For download see https://modrinth.com/mod/combinedworld

## Basics

Your inventory and most of the properties would be saved and loaded when you switch dimensions.

Here, we categorize dimensions as such:

Group - all dimensions in the same namespace, you can travel without changing inventory.

World - dimensions that are overworld, nether or the end of the same prefix, they are considered as the same dimension by the mod, but the mod can remember the last dimension you were in when you leave this world.

Dimension - elementary dimension as the game define.

If a dimension is named as xxx:yyy_overworld or xxx:yyy_the_nether, you can lit a nether portal there and go to corresponding dimension.

If a dimension is named as xxx:yyy_the_end, it would be used as the end of xxx:yyy_overworld and xxx:yyy_the_nether and an end portal would send you back to xxx:yyy_overworld. Make sure all 3 dimensions are present.

Config file: /config/ItemStorage.json and you can close the item storage function.

Save file: <save>/data/silvicky/item.dat which contains everything you are interested in.

## Commands

Parameters can be shown by executing without argument.

### Basic warp

```/warp``` Send yourself to a world.

```/warptp``` Same as above, but you may specify player and position.

Useful in command blocks.

As inventory switching is now integrated into game, this is largely the same as vanilla approach.

```/banwarp``` Set minimal privilege level for warping into a world.

```/evacuate``` Evacuate all players in a world into another one, even offline ones!

TODO

/listdimensionplayers

/listworldplayers

/listgroupplayers

/importworld

/packme

/deleteworld

/createworld

/silence

/visibility

To customize seed of a specific dimension, simply add a Long field to data/seed/ in it, set the key to the identifier of the dimension, and value to be the seed. If seed is not set, seed of the whole server would be used.

To customize default gamemode of a specific group(namespace), simply add an Integer field to data/gamemode/ in it, set the key to the namespace of the group, and value to be the numeric ID of the gamemode(0=survival, 1=creative, 2=adventure, 3=spectator). If gamemode is not set, gamemode of the whole server would be used.
