package com.addon.ragdollcleanup;

import com.addon.ragdollcleanup.command.PlayerCorpseClearCommand;
import com.addon.ragdollcleanup.command.RagdollClearCommand;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ragdollified / ragdollifiedpc (by raiiiden) 用のアドオンMOD。
 * ragdollifiedが公開しているAPI (RagdollifiedApi) や、
 * ragdollifiedpcのCorpseEntity/PendingCorpseStoreを利用して、
 * ラグドール化されたモブの死体・プレイヤーの死体をコマンドで削除できるようにする。
 *
 * 重要: RagdollifiedApi はクラス自体が @OnlyIn(Dist.CLIENT) と定義されており、
 * モブのラグドールは完全にクライアント側だけで管理される見た目上の表現である
 * (専用サーバー上にはデータが一切存在しない)。そのため /ragdollified clear と
 * /ragdollified count は「クライアントコマンド」として登録し、サーバー側の
 * RegisterCommandsEvent 経由では絶対に実行させないようにしている。
 * (サーバー側でRagdollifiedApiクラスを読み込もうとするとRuntimeDistCleanerに
 *  よってブロックされ、コマンド実行時にクラッシュする)
 *
 * 一方、プレイヤーの死体(CorpseEntity)はragdollifiedpcが管理する通常の
 * サーバーエンティティなので、こちらは従来通りサーバーコマンドとして登録する。
 */
@Mod(RagdollCorpseCleaner.MOD_ID)
public class RagdollCorpseCleaner {

    public static final String MOD_ID = "ragdollcorpsecleaner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public RagdollCorpseCleaner() {
        // コマンド登録などForgeの汎用イベントはEVENT_BUSに登録する
        MinecraftForge.EVENT_BUS.register(this);
    }

    /** サーバーコマンド: プレイヤーの死体(ragdollifiedpc)関連のみ登録する。 */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        PlayerCorpseClearCommand.register(event.getDispatcher());
        LOGGER.info("[RagdollCorpseCleaner] /ragdollified clearcorpses コマンドを登録しました。");
    }

    /**
     * クライアントコマンド: モブのラグドール(ragdollified)関連はこちらで登録する。
     * RagdollifiedApiがクライアント専用のため、専用サーバーからは絶対に実行できない。
     * このイベント自体、実際にクライアントとして起動した時にしか発火しないので、
     * 専用サーバー上でこのメソッドが呼ばれてクラッシュすることはない。
     */
    @SubscribeEvent
    public void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        RagdollClearCommand.register(event.getDispatcher());
        LOGGER.info("[RagdollCorpseCleaner] /ragdollified clear / count コマンド(クライアント側)を登録しました。");
    }
}
