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

### Player management

```/listdimensionplayers```

```/listworldplayers```

```/listgroupplayers```

List players in specific...

### World management

```/importworld``` Import a full vanilla world or its overworld.

```/deleteworld``` Delete a world.

```/createworld``` Create a world from given world generation settings, which would offer more freedom as colliding namespace is allowed here.

### World settings (for backrooms or something, inspired by someone?)

Coming soon...

```/silence``` Set types of sound that player cannot hear sound in a dimension.

```/visibility``` Set types of entities that player can see in a dimension. For other players, more complex options are offered, for example, you may make players "visibility" change after each death, and only players with same visibility can see each other.

### Other

```/packme``` Pack everything on you into a chest with NBT, maybe also your ender chest items.
