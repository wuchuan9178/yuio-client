package com.example.addon.commands;

import com.example.addon.utils.VClipUtils;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.util.math.Vec3d;

public class TpCommand extends Command {
    public TpCommand() {
        super("tp", "Vclips to the given coordinates.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("x", DoubleArgumentType.doubleArg())
            .then(argument("y", DoubleArgumentType.doubleArg())
                .then(argument("z", DoubleArgumentType.doubleArg())
                    .executes(context -> {
                        double x = DoubleArgumentType.getDouble(context, "x");
                        double y = DoubleArgumentType.getDouble(context, "y");
                        double z = DoubleArgumentType.getDouble(context, "z");

                        VClipUtils.teleport(new Vec3d(x, y, z), 10.0);
                        info("Teleported to %.1f %.1f %.1f.", x, y, z);
                        return SINGLE_SUCCESS;
                    }))));
    }
}
