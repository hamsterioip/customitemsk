package com.example.mixin;

import com.example.CustomItemsK;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public class CraftBroadcastMixin {

    private static final Identifier TEMPEST_REAVER_ID =
            Identifier.fromNamespaceAndPath(CustomItemsK.MOD_ID, "tempest_reaver");

    @Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private void onCraft(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        var loc = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (loc == null || !loc.getNamespace().equals(CustomItemsK.MOD_ID)) return;

        String playerName = player.getName().getString();
        String itemName   = stack.getHoverName().getString();

        boolean isTempestReaver = TEMPEST_REAVER_ID.equals(loc);

        Component message;
        if (isTempestReaver) {
            message = Component.literal(
                    "§4§l☠ THE TEMPEST REAVER HAS BEEN FORGED ☠\n" +
                    "§c§l» §e" + playerName + " §c§lhas unleashed a weapon of pure destruction upon this world.\n" +
                    "§4§l§oFear what is to come... ☠"
            );
        } else {
            message = Component.literal(
                    "§6✦ §e" + playerName + " §7has crafted §b" + itemName + "§7!"
            );
        }

        serverLevel.getServer().getPlayerList().broadcastSystemMessage(message, false);

        for (ServerPlayer sp : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (isTempestReaver) {
                // Ender dragon death + wither spawn for maximum terror
                ((ServerLevel) sp.level()).playSound(null,
                        sp.getX(), sp.getY(), sp.getZ(),
                        SoundEvents.ENDER_DRAGON_DEATH, SoundSource.MASTER, 10000.0f, 1.0f);
                ((ServerLevel) sp.level()).playSound(null,
                        sp.getX(), sp.getY(), sp.getZ(),
                        SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 10000.0f, 0.8f);
            } else {
                ((ServerLevel) sp.level()).playSound(null,
                        sp.getX(), sp.getY(), sp.getZ(),
                        SoundEvents.WITHER_DEATH, SoundSource.MASTER, 10000.0f, 1.0f);
            }
        }
    }
}
