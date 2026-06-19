package com.mikarific.eidtracker.client.mixins;

import com.mikarific.eidtracker.client.EIDTrackerClient;
import com.mikarific.eidtracker.client.EntityIDSource;
import com.mikarific.eidtracker.client.carpet.CarpetHandler;
import com.mikarific.eidtracker.client.commands.EIDCommand;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleLogin", at = @At("RETURN"))
    private void handleLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        EIDTrackerClient.entityIDSource = EntityIDSource.GUESS;
        EIDTrackerClient.setCurrentEntityID(packet.playerId());
        CarpetHandler.entityIdLoggerExists = false;
        EIDCommand.singleplayerEntityIdFix = false;
    }

    @Inject(method = "handleAddEntity", at = @At("RETURN"))
    private void handleAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        if (EIDTrackerClient.entityIDSource == EntityIDSource.GUESS || EIDTrackerClient.entityIDSource == EntityIDSource.CARPETTIS) {
            int newCurrentEntityID = packet.getId();
            if (EIDTrackerClient.currentEntityID == null || newCurrentEntityID == EIDTrackerClient.currentEntityID) return;
            boolean flippedNegative = newCurrentEntityID < 0 && EIDTrackerClient.currentEntityID >= 0;
            if (newCurrentEntityID - EIDTrackerClient.currentEntityID > 0 || flippedNegative) EIDTrackerClient.setCurrentEntityID(newCurrentEntityID);
        }
    }

    @Inject(method = "handleCommandSuggestions", at = @At("RETURN"))
    private void handleCommandSuggestions(ClientboundCommandSuggestionsPacket packet, CallbackInfo ci) {
        if (packet.id() != Integer.MIN_VALUE) return;
        if (packet.suggestions().stream().noneMatch(suggestion -> suggestion.text().equals("entityIdCounter"))) return;
        CarpetHandler.entityIdLoggerExists = true;
    }

    @Inject(method = "handleTabListCustomisation", at = @At("RETURN"))
    private void handleTabListCustomisation(ClientboundTabListPacket packet, CallbackInfo ci) {
        if (EIDTrackerClient.entityIDSource == EntityIDSource.EIDTRACKER) return;
        Component footer = packet.footer();
        boolean foundEntityIdLogger = false;
        if (CarpetHandler.entityIdLoggerExists && !packet.footer().getString().isEmpty()) {
            String text = ChatFormatting.stripFormatting(footer.getString());
            String[] lines = text.split("\n");
            for (String line : lines) {
                Matcher matcher = Pattern.compile("EID (?<eid>-?[0-9]+) (?<percent>[0-9]+[.,][0-9]{2})%").matcher(line);
                if (matcher.matches()) {
                    foundEntityIdLogger = true;
                    if (EIDTrackerClient.entityIDSource != EntityIDSource.CARPETTIS) {
                        EIDTrackerClient.entityIDSource = EntityIDSource.CARPETTIS;
                    }
                    try { EIDTrackerClient.setCurrentEntityID(Integer.parseInt(matcher.group("eid"))); } catch (NumberFormatException ignore) {}
                }
            }
        }
        if (!foundEntityIdLogger && EIDTrackerClient.entityIDSource == EntityIDSource.CARPETTIS) {
            EIDTrackerClient.entityIDSource = EntityIDSource.GUESS;
        }
    }
}
