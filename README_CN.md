# CombinedWorld

[English](README.md)

在不同世界之间切换，就像在不同的服务器之间一样。（代理服在面板服上的平替？）

下载见 https://modrinth.com/mod/combinedworld

## 基本概念

切换世界时物品栏等个人数据会被保存，位置也会被记录。

整个服务器的维度被这样划分：

命名空间 - 内部共享存档数据

世界 - mod的基本单位，一个维度或一组包含主世界、下界和末地的维度，mod会记住你上次的维度和位置。

维度 - 游戏里的一个基本世界

带有原版维度那样后缀的会被视作三个一组的原版世界，否则会被视作独立维度。

配置文件：/config/ItemStorage.json 可以选择关闭物品栏存储

存档文件：<save>/data/silvicky/item.dat 大部分都在这

## 命令

不带参数输入命令可以查看具体参数

### Basic warp

```/warp``` 把自己传送到一个世界

```/warptp``` 同上，但可指定被传送的玩家和目标位置

可以用在命令方块里，现在物品栏切换已经注入到游戏底层，所以在指定位置时用原版命令一样效果

```/banwarp``` 设定进入世界需要的权限等级

```/evacuate``` 从一个世界撤离所有玩家到另一个世界，不在线的玩家也可以

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
