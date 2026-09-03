package com.addon.ragdollcleanup.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.raiiiden.ragdollified.api.RagdollifiedApi;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * ragdollified の RagdollifiedApi を使って、
 * ラグドール化されたモブの死体をサーバーから削除するコマンド。
 *
 * 使い方:
 *   /ragdollified clear            ... 現在アクティブな全てのラグドール死体を削除
 *   /ragdollified clear <targets>  ... 指定したエンティティ(セレクタ)に紐づくラグドールのみ削除
 *   /ragdollified count            ... 現在アクティブなラグドール死体の数を表示
 *
 * 権限レベル: 2 (オペレーター) 以上が必要。必要に応じて requires() の数値を変更してください。
 */
public final class RagdollClearCommand {

    private static final int PERMISSION_LEVEL = 2;

    private RagdollClearCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ragdollified")
                .then(Commands.literal("clear")
                    .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                    .executes(RagdollClearCommand::clearAll)
                    .then(Commands.argument("targets", EntityArgument.entities())
                        .executes(RagdollClearCommand::clearTargets)))
                .then(Commands.literal("count")
                    .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                    .executes(RagdollClearCommand::count))
        );
    }

    /** 現在サーバー上でアクティブな全てのラグドール死体を削除する。 */
    private static int clearAll(CommandContext<CommandSourceStack> ctx) {
        // getRagdollIds() が返すSetをそのまま回しながら remove() すると
        // 内部で同じコレクションが変更されて壊れる可能性があるのでコピーしてから処理する
        Set<Integer> ragdollIds = RagdollifiedApi.getRagdollIds();
        List<Integer> ids = new ArrayList<>(ragdollIds);

        int removed = 0;
        for (int entityId : ids) {
            if (RagdollifiedApi.remove(entityId)) {
                removed++;
            }
        }

        final int removedCount = removed;
        CommandSourceStack source = ctx.getSource();
        if (removedCount == 0) {
            source.sendSuccess(() -> Component.literal("§7削除できるラグドール死体はありませんでした。"), true);
        } else {
            source.sendSuccess(() -> Component.literal("§aラグドール死体を " + removedCount + " 体削除しました。"), true);
        }
        return removedCount;
    }

    /** セレクタで指定したエンティティに紐づくラグドールのみ削除する。 */
    private static int clearTargets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");

        int removed = 0;
        int checked = 0;
        for (Entity entity : targets) {
            checked++;
            if (RagdollifiedApi.remove(entity)) {
                removed++;
            }
        }

        final int removedCount = removed;
        final int checkedCount = checked;
        CommandSourceStack source = ctx.getSource();
        if (removedCount == 0) {
            source.sendSuccess(() ->
                Component.literal("§7指定した " + checkedCount + " 体にはラグドール死体がありませんでした。"), true);
        } else {
            source.sendSuccess(() ->
                Component.literal("§a指定したエンティティのうち " + removedCount + " 体のラグドール死体を削除しました。"), true);
        }
        return removedCount;
    }

    /** 現在アクティブなラグドール死体の数を表示する。 */
    private static int count(CommandContext<CommandSourceStack> ctx) {
        int size = RagdollifiedApi.getRagdollIds().size();
        ctx.getSource().sendSuccess(() -> Component.literal("§b現在アクティブなラグドール死体: " + size + " 体"), false);
        return size;
    }
}
