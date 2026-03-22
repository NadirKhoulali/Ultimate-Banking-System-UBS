package net.austizz.ultimatebankingsystem.command;



import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;


@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public class UBSCommands {

    private static final Component helpMessage = Component.literal("§6§lUltimate Banking System §7- §eAccount Commands\n" +
            "§8/§faccount §7help §8- §7Show this help\n" +
            "§8/§faccount §7create §8- §7Create a new account\n" +
            "§8/§faccount §7delete §8- §7Delete your account\n" +
            "§8/§faccount §7info §8- §7View your account info\n" +
            "§8/§faccount §7deposit §8<§famount§8> §8- §7Deposit money\n" +
            "§8/§faccount §7withdraw §8<§famount§8> §8- §7Withdraw money\n" +
            "§8/§faccount §7balance §8- §7Show your balance\n" +
            "§8/§faccount §7transfer §8<§fplayer§8> <§famount§8> §8- §7Transfer money");

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {

        event.getDispatcher().register(
                Commands.literal("account").executes(
                        context -> {
                            context.getSource().sendSystemMessage(helpMessage);
                            return 1;})
                        .then(Commands.literal("help").executes(context -> {
                            context.getSource().sendSystemMessage(helpMessage);
                            return 1;}))
                        .then(Commands.literal("create")
                                .then(Commands.argument("Account Type", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (AccountTypes account : AccountTypes.values()) {
                                                builder.suggest(account.name());
                                            }
                                            return builder.buildFuture();
                                        }).then(Commands.argument("Bank", StringArgumentType.word())) // Need to implement: Some way to write Bank name and get the right Bank to fetch their ID. Thinking of making Bank Name Unique identifier so it can be looked up with for loop (name == arg)

        )));
    }
}
