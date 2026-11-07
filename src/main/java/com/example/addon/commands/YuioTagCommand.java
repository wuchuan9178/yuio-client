package com.example.addon.commands;

import com.example.addon.utils.YuioTag;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

public class YuioTagCommand extends Command {
    public YuioTagCommand() {
        super("yuio", "Toggle the yuio chat tag suffix.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("tag")
            .then(literal("on").executes(context -> apply(true)))
            .then(literal("off").executes(context -> apply(false)))
            .executes(context -> apply(!YuioTag.isSuffixEnabled())));
    }

    private int apply(boolean value) {
        YuioTag.setSuffixEnabled(value);
        info("yuio tag suffix %s.", value ? "on" : "off");
        return SINGLE_SUCCESS;
    }
}
