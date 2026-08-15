# CombinedWorld

[中文](README_CN.md)

Switch between dimensions as if they are different servers.

For download see https://modrinth.com/mod/combinedworld

## Basics

This mod is now divided into a "core" one and a "backrooms" one, the first one is all you need while the second one is our own implementation of some interfaces, which can be used as references.

Your inventory and most of the properties would be saved and loaded when you switch dimensions.

Here, we categorize dimensions as such:

Group - all dimensions in the same namespace, you can travel without changing inventory.

World - dimensions that are overworld, nether or the end of the same prefix, they are considered as the same dimension by the mod, but the mod can remember the last dimension you were in when you leave this world.

Dimension - elementary dimension as the game define.

If a dimension is named as xxx:yyy_overworld or xxx:yyy_the_nether, you can lit a nether portal there and go to corresponding dimension.

If a dimension is named as xxx:yyy_the_end, it would be used as the end of xxx:yyy_overworld and xxx:yyy_the_nether and an end portal would send you back to xxx:yyy_overworld. Make sure all 3 dimensions are present.

Config file: /config/ItemStorage.json and you can close the item storage function, or modify some numbers.

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

```/defaultmode``` Get or set default game mode of group.

### Player management

```/listdimensionplayers```

```/listworldplayers```

```/listgroupplayers```

List players in specific...

### World management

```/importworld``` Import a full vanilla world or its overworld.

```/deleteworld``` Delete a world.

```/createworld``` Create a world from given world generation settings, which would offer more freedom as colliding namespace is allowed here.

### World settings

For Backrooms or something in our server, inspired by [Elihuso](https://github.com/LS-KR), who introduced it to me during a meeting on 2023.12.24, and helped me quite much since then. IDK if our implementations of non-linear space and noclipping are satisfactory to her...

For many commands, viewing source code is a good way to know usage, at least at this point, as many of them use binary coding.

```/silence``` Set types of sound that player cannot hear sound in a dimension.

```/visibility``` Set types of entities that player can see in a dimension. For other players, more complex options are offered, for example, you may make players "visibility" change after each death, and only players with same visibility can see each other.

```/noclip``` Set chance of entering another world when starting suffocation in a dimension.

```/noclipvoid``` Set chance of entering another world when one falls into the void of a dimension. This is calculated every tick, so lower net chance results in longer falling time. If any chance is present, void damage would be canceled. Falling damage would be canceled after entering another world.

```/darkness``` Force rendering(client) and calculation(server) light level to be a specific value.

```/distance``` Set view and simulation distances of a dimension.

```/ctrans``` Set type of chunk transformer of a dimension, that is, the way chunks are accessed by clients. Make sure you have configured the distance above to a small value, for example 4, and restarted server before using some of these, or game would crash. This is injected at protocol level, so server worlds are always ordered and static, while client worlds can be different among players. Many gameplay-related calculations are however modified for smooth playing.

The "random" one would remap currently invisible chunks when player crossed chunk borders.

The "linear" one would give player a random offset, which makes locating hard.

The "xor" one is mainly for debugging.

The "nop" one is vanilla mapping, and should align to vanilla perfectly. If not so, please report to me.

### Other

```/packme``` Pack everything on you into a chest with NBT, maybe also your ender chest items.

```/locateplayer``` Locate a player, very useful for admins.

## World Generation

We offered some interfaces and example implementations for custom world generation, and you may register some yourself.

For parameters, see their codecs.

### Decay

It takes a base generator as argument, but in regions far from center, chunks are randomly deleted.

It also requires a "decay rule" which decides chunks to be deleted or not.

The example decay rule is already enough for general usage, and we are already using it.

### Custom

It takes a biome source, same as vanilla, and a custom rule.

You may manually implement the CustomRule interface to generate chunk as you want.

Such rule is static: DO NOT rely on the instance of rule!

To make world consistent, the method should do the same thing no matter when it is called.
