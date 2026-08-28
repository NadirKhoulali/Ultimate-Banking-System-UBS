package net.austizz.ultimatebankingsystem.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.migration.numismatics.NumismaticsMigrationService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.file.Path;
import java.util.UUID;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class NumismaticsMigrationCommands {
    private NumismaticsMigrationCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ubs")
                .then(Commands.literal("admin").requires(source -> source.hasPermission(3))
                        .then(Commands.literal("migrate")
                                .then(buildNumismaticsLiteral()))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildNumismaticsLiteral() {
        return Commands.literal("numismatics")
                .executes(context -> {
                    NumismaticsMigrationService.open(context.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("open").executes(context -> {
                    NumismaticsMigrationService.open(context.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("status").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(
                            NumismaticsMigrationService.status(context.getSource().getServer())), false);
                    return 1;
                }))
                .then(Commands.literal("world").executes(context -> {
                    UUID owner = context.getSource().getEntity() == null
                            ? null : context.getSource().getEntity().getUUID();
                    try {
                        String result = NumismaticsMigrationService.selectWorldSource(
                                context.getSource().getServer(), owner);
                        context.getSource().sendSuccess(() -> Component.literal(result)
                                .withStyle(ChatFormatting.GREEN), true);
                        return 1;
                    } catch (Exception exception) {
                        context.getSource().sendFailure(Component.literal(exception.getMessage()));
                        return 0;
                    }
                }))
                .then(Commands.literal("file")
                        .then(Commands.argument("server_path", StringArgumentType.greedyString())
                                .executes(context -> {
                                    UUID owner = context.getSource().getEntity() == null
                                            ? null : context.getSource().getEntity().getUUID();
                                    try {
                                        String result = NumismaticsMigrationService.selectServerSource(
                                                context.getSource().getServer(), owner,
                                                Path.of(StringArgumentType.getString(context, "server_path")));
                                        context.getSource().sendSuccess(() -> Component.literal(result)
                                                .withStyle(ChatFormatting.GREEN), true);
                                        return 1;
                                    } catch (Exception exception) {
                                        context.getSource().sendFailure(Component.literal(exception.getMessage()));
                                        return 0;
                                    }
                                })))
                .then(Commands.literal("report").executes(context -> {
                    Path report = NumismaticsMigrationService.reportPath(context.getSource().getServer());
                    if (report == null) {
                        context.getSource().sendFailure(Component.literal("No migration report is available."));
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.literal(report.toString()), false);
                    return 1;
                }));
    }
}
