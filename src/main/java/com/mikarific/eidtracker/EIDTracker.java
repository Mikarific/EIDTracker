package com.mikarific.eidtracker;

import com.mikarific.eidtracker.commands.EIDCommand;
import com.mikarific.eidtracker.networking.NetworkingHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class EIDTracker implements ModInitializer {
    public static final String MOD_ID = "eidtracker";

    @Override
    public void onInitialize() {
        NetworkingHandler.init();

        CommandRegistrationCallback.EVENT.register(EIDCommand::register);
    }
}
