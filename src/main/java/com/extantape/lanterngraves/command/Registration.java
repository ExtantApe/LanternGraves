package com.extantape.lanterngraves.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Builds Brigadier trees for /grave and /lanterngraves, registered through Paper's lifecycle API.
 * Each subcommand takes up to {@code depth} word arguments, so the client checks the shape and offers
 * suggestions, and the words go to the same handler either way.
 */
public final class Registration {
    public interface Handler {
        boolean handle(CommandSender sender, String label, String[] args);
    }

    private Registration() {}

    public static LiteralCommandNode<CommandSourceStack> tree(String name, String permission, Handler handler,
                                                             BiFunction<CommandSender, String[], List<String>> complete,
                                                             List<String> subs, int depth) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(permission))
                .executes(ctx -> run(ctx, handler, name, List.of()));
        for (String sub : subs) {
            LiteralArgumentBuilder<CommandSourceStack> lit = Commands.literal(sub).executes(ctx -> run(ctx, handler, name, List.of(sub)));
            lit.then(words(sub, 1, depth, handler, name, complete));
            root.then(lit);
        }
        return root.build();
    }

    private static ArgumentBuilder<CommandSourceStack, ?> words(String sub, int n, int depth, Handler handler, String name,
                                                                BiFunction<CommandSender, String[], List<String>> complete) {
        String argName = "arg" + n;
        var arg = Commands.argument(argName, StringArgumentType.word())
                .suggests((ctx, b) -> {
                    List<String> typed = new ArrayList<>(List.of(sub));
                    for (int i = 1; i < n; i++) typed.add(StringArgumentType.getString(ctx, "arg" + i));
                    typed.add(b.getRemaining());
                    complete.apply(ctx.getSource().getSender(), typed.toArray(String[]::new)).forEach(b::suggest);
                    return b.buildFuture();
                })
                .executes(ctx -> {
                    List<String> args = new ArrayList<>(List.of(sub));
                    for (int i = 1; i <= n; i++) args.add(StringArgumentType.getString(ctx, "arg" + i));
                    return run(ctx, handler, name, args);
                });
        if (n < depth) arg.then(words(sub, n + 1, depth, handler, name, complete));
        return arg;
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Handler handler, String name, List<String> args) {
        handler.handle(ctx.getSource().getSender(), name, args.toArray(String[]::new));
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

}
