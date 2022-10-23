# ABPPlugin
Spigot Plugin to support Carpet AccurateBlockPlacement Protocol

/abp-enable to enable using it. (per-player)
Automatically enables if the player sends a PluginMessage packet on the 'kjnine:abp' channel.

Supports the block rotation protocol implemented in the Tweakeroo and Litematica mods.

### Important:
This is a very rough implementation of the protocol, due to limitations of spigot api.
It is possible some bugs/mis-rotations could arise even with normal block placement,
so for that reason, this plugin ONLY modifies packets and blocks from players who opt-in with the command or PluginMessage.
