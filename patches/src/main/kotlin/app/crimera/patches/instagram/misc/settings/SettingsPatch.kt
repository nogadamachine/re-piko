/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.settings

import app.crimera.patches.instagram.entity.developerOptions.developerOptionsEntity
import app.crimera.patches.instagram.entity.dialogbox.instagramDialogBoxEntity
import app.crimera.patches.instagram.entity.instagramButton.instagramButtonEntity
import app.crimera.patches.instagram.entity.profileinfo.profileInfoEntity
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.misc.actionBar.mainFeedActionBarButton.mainFeedActionBarButtonPatch
import app.crimera.patches.instagram.misc.actionBar.mainFeedActionBarButton.hideHomeActionButtonsPatch
import app.crimera.patches.instagram.misc.actionBar.userProfileActionBarButton.userProfileActionBarButtonPatch
import app.crimera.patches.instagram.misc.extension.hooks.instagramInitHook
import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.misc.notification.fixNotificationRegistrationCrashPatch
import app.crimera.patches.instagram.misc.userProfile.userProfileButtonPatch
import app.crimera.patches.instagram.utils.decode449AnchorsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.CONSTANTS_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.LOAD_FLAGS_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.SSTS_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val settingsPatch =
    bytecodePatch(
        name = "설정 추가",
        description = "re-piko 기능을 조절하는 설정 화면을 추가합니다.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(
            decode449AnchorsPatch,
            sharedExtensionPatch,
            addSettingsActivityPatch,
            mainFeedActionBarButtonPatch,
            hideHomeActionButtonsPatch,
            userProfileActionBarButtonPatch,
            userDataEntity,
            userProfileButtonPatch,
            hookFlagsPatch,
            fixNotificationRegistrationCrashPatch,
            profileInfoEntity,
            instagramDialogBoxEntity,
            instagramButtonEntity,
            developerOptionsEntity,
            addResourcesPatch,
        )
        execute {
            addAppResources("shared")
            addAppResources("instagram")

            IgFragmentActivityOnCreate.method.apply {

                val returnVoidIndex = indexOfFirstInstruction(Opcode.RETURN_VOID)

                addInstruction(
                    returnVoidIndex,
                    """
                    invoke-static {p0}, Lapp/morphe/extension/shared/Utils;->setActivity(Landroid/app/Activity;)V
                    """.trimIndent(),
                )
            }

            instagramInitHook.fingerprint.method.apply {

                val firstInvokeSuperIndex = indexOfFirstInstruction(Opcode.INVOKE_SUPER)
                val contextRegister = getInstruction(firstInvokeSuperIndex).registersUsed[0]
                val freeRegister = findFreeRegister(firstInvokeSuperIndex, listOf(firstInvokeSuperIndex))

                addInstructions(
                    firstInvokeSuperIndex + 1,
                    """
                    new-instance v$freeRegister, Lapp/morphe/extension/crimera/CustomCrashHandler;
                    invoke-direct {v$freeRegister, v$contextRegister}, Lapp/morphe/extension/crimera/CustomCrashHandler;-><init>(Landroid/content/Context;)V
                    invoke-static {v$freeRegister}, Ljava/lang/Thread;->setDefaultUncaughtExceptionHandler(Ljava/lang/Thread${'$'}UncaughtExceptionHandler;)V
                    
                    ${SSTS_DESCRIPTOR.format("load")}
                    ${LOAD_FLAGS_DESCRIPTOR.format("load")}
                    invoke-static {}, $CONSTANTS_DESCRIPTOR/Constants;->load()V
                    """.trimIndent(),
                )
            }

            // For welcome message.
            MainFeedFragmentOnCreateFingerprint.apply {
                method.apply {
                    // 448 first returns the "launched_in_tab" String after the trace anchor.
                    // Bind to the actual Fragment context result instead of that first object.
                    val contextCall = instructions.filter {
                        it.getReference<MethodReference>()?.toString() ==
                            "Landroidx/fragment/app/Fragment;->requireContext()Landroid/content/Context;"
                    }.singleOrNull() ?: throw PatchException("Expected one main-feed requireContext call")
                    val contextIndex = contextCall.location.index + 1
                    val contextInstruction = getInstruction(contextIndex)
                    if (contextInstruction.opcode != Opcode.MOVE_RESULT_OBJECT) {
                        throw PatchException("Main-feed context result is not captured")
                    }
                    val contextRegister = contextInstruction.registersUsed[0]

                    addInstruction(
                        contextIndex + 1,
                        """
                        invoke-static/range {v$contextRegister .. v$contextRegister}, $PATCHES_DESCRIPTOR/WelcomeMessage;->openWelcomeMessage(Landroid/content/Context;)V
                        """.trimIndent(),
                    )
                }
            }
        }
    }
