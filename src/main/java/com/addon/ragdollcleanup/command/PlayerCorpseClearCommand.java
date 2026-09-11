package com.addon.ragdollcleanup.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.raiiiden.ragdollifiedpc.entity.CorpseEntity;
import com.raiiiden.ragdollifiedpc.entity.ModEntities;
import com.raiiiden.ragdollifiedpc.server.PendingCorpseStore;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ragdollified Player Corpses (ragdollifiedpc) の CorpseEntity を削除するコマンド。
 *
 * CorpseEntity は ragdollified の RagdollifiedApi のような公開APIを持たないため、
 * 通常のMinecraftエンティティとして直接操作する:
 *   - 検索: ModEntities.CORPSE (登録名 "ragdollifiedpc:corpse") をキーにワールドから検索
 *   - 削除: Entity#discard() (vanilla標準の安全な削除方法)
 *
 * CorpseEntity は hurt() が無効化されており、通常の /kill では削除できない実装になっているため、
 * このコマンドは discard() を直接呼び出して強制的に削除する。
 *
 * 削除後は PendingCorpseStore (ragdollifiedpc本体の永続データ、SavedData) の
 * materialized / removedCorpseIds も更新し、/retrievecorpse やコンパスが
 * 「既に削除済み」であることを正しく認識できるようにしている。
 * これらのフィールドは ragdollifiedpc 側で public として公開されているため、
 * リフレクションを使わずに正規の方法でアクセスしている。
 *
 * 使い方:
 *   /ragdollified clearcorpses  ... 現在読み込まれている全プレイヤー死体を削除
 *   /ragdollified countcorpses  ... 現在読み込まれているプレイヤー死体の数を表示
 *
 * ※ セレクタ(<targets>)による個別指定には対応していない(全削除のみ)。
 *
 * メッセージ文言は assets/ragdollcorpsecleaner/lang/*.json で管理している
 * (バニラ同様、翻訳キーを介して言語ファイルから読み込む方式)。
 *
 * 注意: level.getEntities() はそのレベルで現在読み込まれている(ロード済みチャンク内の)
 * エンティティのみを対象とする。アンロードされたチャンクにある死体はチャンクが
 * 読み込まれるまで対象にならない。
 */
public final class PlayerCorpseClearCommand {

    private static final int PERMISSION_LEVEL = 2;

    // AABB.INFINITE は1.20.1時点ではまだ存在しないため、
    // ワールド座標の実用上限(±3000万ブロック)を大きく超える範囲を自前で用意する。
    // getEntities()はこのAABBと交差する「ロード済み」チャンクのエンティティのみを見るので、
    // 範囲を広く取っても実際の探索コストは対象チャンク数に比例する。
    private static final AABB WORLD_BOUNDS =
        new AABB(-3.0E7, -3.0E7, -3.0E7, 3.0E7, 3.0E7, 3.0E7);

    private PlayerCorpseClearCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ragdollified")
                .then(Commands.literal("clearcorpses")
                    .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                    .executes(PlayerCorpseClearCommand::clearAll))
                .then(Commands.literal("countcorpses")
                    .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                    .executes(PlayerCorpseClearCommand::count))
        );
    }

    /** 現在読み込まれている全てのプレイヤー死体を、全ディメンションから削除する。 */
    private static int clearAll(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();

        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<CorpseEntity> corpses = level.getEntities(ModEntities.CORPSE.get(), WORLD_BOUNDS, e -> true);
            for (CorpseEntity corpse : new ArrayList<>(corpses)) {
                removeCorpse(level, corpse);
                removed++;
            }
        }

        final int removedCount = removed;
        CommandSourceStack source = ctx.getSource();
        if (removedCount == 0) {
            source.sendSuccess(() ->
                Component.translatable("commands.ragdollcorpsecleaner.clearcorpses.none")
                    .withStyle(ChatFormatting.GRAY), true);
        } else {
            source.sendSuccess(() ->
                Component.translatable("commands.ragdollcorpsecleaner.clearcorpses.success", removedCount)
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return removedCount;
    }

    /** 現在読み込まれているプレイヤー死体の数を、全ディメンション合計で表示する。 */
    private static int count(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();

        int total = 0;
        for (ServerLevel level : server.getAllLevels()) {
            total += level.getEntities(ModEntities.CORPSE.get(), WORLD_BOUNDS, e -> true).size();
        }

        final int totalCount = total;
        ctx.getSource().sendSuccess(() ->
            Component.translatable("commands.ragdollcorpsecleaner.countcorpses", totalCount)
                .withStyle(ChatFormatting.AQUA), false);
        return totalCount;
    }

    /** CorpseEntityをワールドから削除し、PendingCorpseStoreの記録もあわせて整合させる。 */
    private static void removeCorpse(ServerLevel level, CorpseEntity corpse) {
        UUID corpseId = corpse.getCorpseId();

        // ワールドから実体を除去する。CorpseEntityはhurt()を無効化しているため
        // 通常のダメージ経路(=/kill)は効かない。discard()で直接除去する。
        corpse.discard();

        // /retrievecorpse やコンパスが「既に削除済み」を正しく認識できるように、
        // ragdollifiedpc本体の永続データ(PendingCorpseStore)側も更新しておく。
        if (corpseId != null) {
            PendingCorpseStore store = PendingCorpseStore.get(level);
            store.materialized.remove(corpseId);
            store.removedCorpseIds.add(corpseId);
            store.setDirty();
        }
    }
}
