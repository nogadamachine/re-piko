/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val SHARE_SHEET = "Linstagram/features/direct/fragment/sharesheet/DirectShareSheetFragment;"

private object ShareSheetGroupButtonFingerprint : Fingerprint(
    definingClass = SHARE_SHEET,
    name = "onViewCreated",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
    returnType = "V",
    custom = { method, _ ->
        method.implementation?.instructions?.any {
            it.getReference<FieldReference>()?.toString() ==
                "$SHARE_SHEET->createGroupButton:Landroid/view/View;"
        } == true
    },
)

@Suppress("unused")
val hideGroupCreationOnShareSheetPatch = bytecodePatch(
    name = "공유 화면 그룹 만들기 숨기기",
    description = "공유 화면의 그룹 만들기 버튼을 숨깁니다.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    dependsOn(settingsPatch)
    execute {
        val method = ShareSheetGroupButtonFingerprint.matchAll(1..1).single().method
        val returns = method.instructions.withIndex()
            .filter { it.value.opcode == Opcode.RETURN_VOID }.map { it.index }
        if (returns.size != 1) throw PatchException("Expected one 448 share-sheet initialization return")
        // Hide the actual search-row button after native layout initialization.
        // The old message-composer hook targeted the group-send control instead.
        method.addInstructions(returns.single(), """
            move-object/from16 v0, p0
            iget-object v0, v0, $SHARE_SHEET->createGroupButton:Landroid/view/View;
            invoke-static {v0}, Lapp/morphe/extension/instagram/utils/Pref;->hideShareSheetGroupButton(Landroid/view/View;)V
        """.trimIndent())
        enableSettings("hideGroupCreationOnSharesheet")
    }
}
