/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.all.misc.resources.resourceMappingPatch

@Suppress("unused")
val themePatch =
    resourcePatch(
        name = "테마",
        description = "Android 12 이상에서 Material You와 AMOLED 설정을 제공합니다. Android 8부터 11까지는 고정 Material You 스타일 또는 AMOLED 테마를 적용합니다.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        val amoled by booleanOption(
            key = "amoled",
            default = false,
            title = "Android 8부터 11까지의 AMOLED 테마",
            description = "Android 8부터 11까지는 고정된 검은 배경을 적용합니다. Android 12 이상에서는 re-piko 설정의 AMOLED 항목을 사용하세요.",
        )

        var bytecodePatchContext: BytecodePatchContext? = null

        dependsOn(
            settingsPatch,
            sharedExtensionPatch,
            resourceMappingPatch,
            bytecodePatch {
                execute {
                    bytecodePatchContext = this
                }
            },
        )

        execute {
            try {
                preserveColorFileAliases()
                val originalApi31Base = captureApi31Base()

                forceWhiteOnMediaChrome()
                preserveCreationButtonContrast()
                preserveLightClipsComposerContrast()
                preserveLightOverflowStampBackgrounds()
                applyLegacyTheme(amoled == true)

                restoreApi31Base(
                    snapshot = originalApi31Base,
                )
                writeMaterialYouOverlay(night = false)
                writeMaterialYouOverlay(night = true)
                writeAmoledOverlay(originalApi31Base)
                writeAmoledMaterialYouOverlay()

                context(requireNotNull(bytecodePatchContext)) {
                    installComposePrismBlackRuntime(legacyAmoled = amoled == true)
                    installSystemDefaultUiModeHook()
                    installThemeLifecycleHooks()
                    installNativeThemeModeSync()
                }
            } finally {
                bytecodePatchContext = null
            }
        }
    }
