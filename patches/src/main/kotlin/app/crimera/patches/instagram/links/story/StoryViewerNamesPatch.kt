package app.crimera.patches.instagram.links.story

import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val HOOK = "$PATCHES_DESCRIPTOR/story/StoryViewerNames;"

@Suppress("unused")
val storyViewerNamesPatch = bytecodePatch(
    name = "스토리 조회자 아이디와 이름 표시",
    description = "스토리 조회자 목록에 아이디와 이름을 함께 표시합니다. 계정 유형을 변경하거나 서버의 상세 인사이트 권한을 활성화하지 않습니다.",
) {
    dependsOn(settingsPatch, userDataEntity)
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute {
        val viewer = "LX/0D06;->A07:Lcom/instagram/user/model/User;"
        val title = "LX/0NVs;->A0F:Landroid/widget/TextView;"
        val binder = object : Fingerprint(
            definingClass = "LX/0D07;",
            name = "bindView",
            parameters = listOf("I", "Landroid/view/View;", "Ljava/lang/Object;", "Ljava/lang/Object;"),
            returnType = "V",
        ) {}.method
        if (binder.instructions.none { it.getReference<FieldReference>()?.toString() == viewer } ||
            binder.instructions.none { it.getReference<FieldReference>()?.toString() == title }
        ) throw PatchException("Unexpected story dashboard viewer model")
        val returns = binder.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }
        if (returns.size != 1) throw PatchException("Unexpected story dashboard return path")
        // The native title ID is anchored by the holder assignment in createView.
        // Scratch registers are dead at the sole return, including wide values.
        val end = returns.single().index
        binder.replaceInstruction(end, "move-object/from16 v0, p2")
        binder.addInstructions(end + 1, """
            move-object/from16 v1, p3
            invoke-static {v0, v1}, $HOOK->bindViewer(Landroid/view/View;Ljava/lang/Object;)V
            return-void
        """.trimIndent())

        // The redesigned interaction section has a separate per-viewer binder.
        // Change its name field only, leaving replies and reactions untouched.
        val modern = object : Fingerprint(
            definingClass = "LX/0Yc4;",
            name = "bindView",
            parameters = listOf("I", "Landroid/view/View;", "Ljava/lang/Object;", "Ljava/lang/Object;"),
            returnType = "V",
        ) {}.method
        val native = modern.instructions.toList()
        val nameField = native.indices.singleOrNull {
            native[it].getReference<FieldReference>()?.toString() ==
                "LX/0VF4;->A04:Ljava/lang/String;"
        } ?: throw PatchException("Unexpected redesigned story viewer label")
        val setText = (nameField + 1 until nameField + 6).singleOrNull {
            native[it].getReference<MethodReference>()?.toString() ==
                "Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V"
        } ?: throw PatchException("Missing redesigned story viewer title assignment")
        modern.addInstructions(setText + 1, """
            iget-object v0, v11, LX/0VF4;->A01:Lcom/instagram/user/model/User;
            invoke-static {v2, v0}, $HOOK->bindTitle(Landroid/widget/TextView;Ljava/lang/Object;)V
        """.trimIndent())
        enableSettings("storyViewerNames")
    }
}
