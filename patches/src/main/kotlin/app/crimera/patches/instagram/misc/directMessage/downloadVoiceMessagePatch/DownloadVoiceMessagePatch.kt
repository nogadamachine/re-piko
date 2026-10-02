/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.directMessage.downloadVoiceMessagePatch

import app.crimera.patches.instagram.entity.messageInfoEntity.messageInfoEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.misc.directMessage.saveAllMessages.saveAllMessagesPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val downloadVoiceMessagePatch =
    bytecodePatch(
        name = "음성 메시지 저장",
        description = "DM 음성 메시지를 파일로 저장할 수 있게 합니다.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, messageInfoEntity, saveAllMessagesPatch)
        execute {
            enableSettings("downloadVoiceMessage")
        }
    }
