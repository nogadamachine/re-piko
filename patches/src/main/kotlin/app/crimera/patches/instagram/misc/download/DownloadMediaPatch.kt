/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.entity.dialogbox.instagramDialogBoxEntity
import app.crimera.patches.instagram.entity.mediadata.mediaDataEntity
import app.crimera.patches.instagram.entity.originalSoundDataIntf.originalSoundDataIntfEntity
import app.crimera.patches.instagram.entity.trackDataIntf.trackDataIntfEntity
import app.crimera.patches.instagram.entity.videoData.videoDataEntity
import app.crimera.patches.instagram.misc.directMessage.saveAllMessages.saveAllMessagesPatch
import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.misc.overflowMenuButton.posts.addOverflowMenuButtonAttributes
import app.crimera.patches.instagram.misc.overflowMenuButton.posts.debugOverflowButton.debugOverflowMenuButtonPatch
import app.crimera.patches.instagram.misc.overflowMenuButton.posts.hookOverflowMenuButton
import app.crimera.patches.instagram.misc.overflowMenuButton.reels.hookReelOverflowMenuButton
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.misc.stories.handleStoryButtonPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.DOWNLOAD_DESCRIPTOR
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import app.morphe.patcher.util.smali.ExternalLabel

@Suppress("unused")
val downloadMediaPatch =
    bytecodePatch(
        name = "미디어 저장",
        description = "게시물, 릴스, 스토리, 하이라이트를 저장하는 기능을 추가합니다.",
    ) {
        dependsOn(
            settingsPatch,
            instagramDialogBoxEntity,
            mediaDataEntity,
            videoDataEntity,
            originalSoundDataIntfEntity,
            trackDataIntfEntity,
            decoderEntity,
            handleStoryButtonPatch,
            hookFlagsPatch,
            saveAllMessagesPatch,
            hookOverflowMenuButton,
            debugOverflowMenuButtonPatch,
            hookReelOverflowMenuButton,
        )
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            // These are the exact Media fields read by 449's native DM saver.
            // Fail patching if the schema changes instead of guessing a media attachment.
            listOf(
                Triple("LX/0NPu;", "A00", "LX/07mC;"),
                Triple("LX/07mC;", "A0X", "Lcom/instagram/feed/media/Media;"),
                Triple("LX/07mC;", "A0L", "LX/02Zs;"),
                Triple("LX/02Zs;", "A05", "Lcom/instagram/feed/media/Media;"),
            ).forEach { (owner, name, type) ->
                if (mutableClassDefBy(owner).fields.none { it.name == name && it.type == type }) {
                    throw PatchException("Instagram 449 DM media schema changed: $owner->$name:$type")
                }
            }

            addOverflowMenuButtonAttributes("PIKO_DOWNLOAD", "downloadOverflowButton")

            // DM media downloader.
            GetDirectThreadMediaSaverModuleNameFingerprint.apply {

                val appActivityField = classDef.fields.first { it.type == "Landroid/app/Activity;" }

                (classDef.methods
                    .singleOrNull { candidate ->
                        candidate.returnType == "V" && AccessFlags.STATIC.isSet(candidate.accessFlags) &&
                            candidate.parameterTypes.size == 5 && candidate.parameterTypes[1] == classDef.type &&
                            candidate.instructions.any {
                                it.getReference<FieldReference>()?.toString() == "Landroid/os/Build$" +
                                    "VERSION;->SDK_INT:I"
                            }
                    }?.also {
                        if ((it.implementation?.registerCount ?: 0) - it.parameterTypes.size < 2) {
                            throw PatchException("DM media saver has insufficient entry scratch registers")
                        }
                    } ?: throw PatchException("Expected one 448 DM media saver permission entry point"))
                    .apply {
                        addInstructionsWithLabels(
                            0,
                            """
                            move-object/from16 v0, p1
                            iget-object v0, v0, $appActivityField
                            move-object/from16 v1, p2
                            invoke-static {v0, v1}, $DOWNLOAD_DESCRIPTOR/MessageUtils;->messageDownloadCheck(Landroid/content/Context;Ljava/lang/Object;)Z
                            move-result v1
                            if-nez v1, :piko
                            return-void
                            """.trimIndent(),
                            ExternalLabel("piko", getInstruction(0)),
                        )
                    }
            }

            enableSettings("downloadMedia")
            addFlags("simpleOverflowMenuFlags")
        }
    }
