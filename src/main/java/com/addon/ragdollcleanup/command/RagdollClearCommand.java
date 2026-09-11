package com.addon.ragdollcleanup.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.raiiiden.ragdollified.api.RagdollifiedApi;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ragdollified の RagdollifiedApi を使って、
 * ラグドール化されたモブの死体を削除するコマンド。
 *
 * 【重要】RagdollifiedApi はクラス自体が @OnlyIn(Dist.CLIENT) であり、
 * モブのラグドールは完全にクライアント側だけで管理される見た目上の表現である
 * (専用サーバーにはデータが存在しない)。そのためこのコマンドは
 * RegisterClientCommandsEvent 経由で「クライアントコマンド」として登録されており
 * (RagdollCorpseCleaner.java 参照)、実行したプレイヤー自身のクライアントが
 * 現在描画・シミュレートしているラグドールのみを対象とする。
 * サーバーコンソールからは実行できず、他プレイヤーのクライアント側の
 * ラグドールにも影響しない。
 *
 * 使い方:
 *   /ragdollified clear  ... 実行したプレイヤーのクライアントで現在アクティブな
 *                            全てのラグドール死体を削除
 *   /ragdollified count  ... 実行したプレイヤーのクライアントで現在アクティブな
 *                            ラグドール死体の数を表示
 *
 * ※ セレクタ(<targets>)による個別指定には対応していない。
 * ラグドール死体は死亡した元モブの本物のMinecraftエンティティが既に消滅した後の
 * 見た目だけの表現(クライアント側の物理シミュレーションをパケット同期したもの)のため、
 * @e[...] のようなバニラのエンティティセレクタでは対象にヒットしない。
 *
 * メッセージ文言は assets/ragdollcorpsecleaner/lang/*.json で管理している
 * (バニラ同様、翻訳キーを介して言語ファイルから読み込む方式)。
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
                    .executes(RagdollClearCommand::clearAll))
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
            source.sendSuccess(() ->
                Component.translatable("commands.ragdollcorpsecleaner.clear.none")
                    .withStyle(ChatFormatting.GRAY), true);
        } else {
            source.sendSuccess(() ->
                Component.translatable("commands.ragdollcorpsecleaner.clear.success", removedCount)
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return removedCount;
    }

    /** 現在アクティブなラグドール死体の数を表示する。 */
    private static int count(CommandContext<CommandSourceStack> ctx) {
        int size = RagdollifiedApi.getRagdollIds().size();
        ctx.getSource().sendSuccess(() ->
            Component.translatable("commands.ragdollcorpsecleaner.count", size)
                .withStyle(ChatFormatting.AQUA), false);
        return size;
    }
}
