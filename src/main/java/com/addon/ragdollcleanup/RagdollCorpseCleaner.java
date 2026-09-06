package com.addon.ragdollcleanup;

import com.addon.ragdollcleanup.command.PlayerCorpseClearCommand;
import com.addon.ragdollcleanup.command.RagdollClearCommand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ragdollified / ragdollifiedpc (by raiiiden) 用のアドオンMOD。
 * ragdollifiedが公開しているサーバーAPI (RagdollifiedApi) や、
 * ragdollifiedpcのCorpseEntity/PendingCorpseStoreを利用して、
 * ラグドール化されたモブの死体・プレイヤーの死体をコマンドで削除できるようにする。
 */
@Mod(RagdollCorpseCleaner.MOD_ID)
public class RagdollCorpseCleaner {

    public static final String MOD_ID = "ragdollcorpsecleaner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public RagdollCorpseCleaner() {
        // コマンド登録などForgeの汎用イベントはEVENT_BUSに登録する
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        RagdollClearCommand.register(event.getDispatcher());
        PlayerCorpseClearCommand.register(event.getDispatcher());
        LOGGER.info("[RagdollCorpseCleaner] /ragdollified clear / clearcorpses コマンドを登録しました。");
    }
}
