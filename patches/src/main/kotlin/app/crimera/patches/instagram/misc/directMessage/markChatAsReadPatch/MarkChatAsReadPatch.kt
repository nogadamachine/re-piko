/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.directMessage.markChatAsReadPatch

import app.crimera.patches.instagram.entity.messageInfoEntity.messageInfoEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

@Suppress("unused")
val markChatAsReadPatch =
    bytecodePatch(
        name = "DM 수동 읽음 처리",
        description = "원하는 대화를 직접 읽은 상태로 표시하는 메뉴를 추가합니다.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, messageInfoEntity)
        execute {

            // Exact 449 capture/replay contract. Reject changed reflection targets.
            val requiredMethods = listOf(
                Triple("LX/07mC;", "A0J", Pair(emptyList(), "J")),
                Triple("LX/08dx;", "AMg", Pair(listOf("Lcom/instagram/model/direct/DirectThreadKey;"), "V")),
                Triple("LX/05Xl;", "A02", Pair(listOf("LX/0Uhg;", "LX/06i8;", "LX/07gZ;", "Z"), "V")),
            )
            requiredMethods.forEach { (owner, name, signature) ->
                val method = mutableClassDefBy(owner).methods.singleOrNull {
                    it.name == name && it.parameterTypes == signature.first && it.returnType == signature.second
                } ?: throw PatchException("Instagram 449 native mark-read signature changed: $owner->$name")
                if (AccessFlags.STATIC.isSet(method.accessFlags)) {
                    throw PatchException("Instagram 449 native mark-read method access changed: $owner->$name")
                }
            }
            listOf(
                Triple("LX/05Xl;", "A00", USER_SESSION_CLASS),
                Triple("LX/05Xl;", "A03", "LX/08du;"),
                Triple("LX/07gZ;", "A00", "Ljava/lang/String;"),
                Triple("LX/06i8;", "A00", "LX/07mC;"),
                Triple("LX/06i8;", "A01", "LX/07mC;"),
            ).forEach { (owner, name, type) ->
                if (mutableClassDefBy(owner).fields.none {
                    it.name == name && it.type == type && !AccessFlags.STATIC.isSet(it.accessFlags)
                }) throw PatchException("Instagram 449 native mark-read field changed: $owner->$name")
            }

            // Log only a fixed completion-state label. Never log payloads, IDs or message text.
            val observer = mutableClassDefBy("LX/0Qpj;").methods.singleOrNull {
                it.name == "A01" && it.returnType == "V" &&
                    it.parameterTypes == listOf("LX/0PZo;", "LX/099W;")
            } ?: throw PatchException("Instagram 449 read observer signature changed")
            observer.apply {
                val stateIndex = instructions.indexOfFirst {
                    it.opcode == Opcode.IGET_OBJECT &&
                        it.getReference<FieldReference>()?.toString() == "LX/0986;->A02:Ljava/lang/String;"
                }
                if (stateIndex < 0) throw PatchException("Instagram 449 read observer state field changed")
                val register = getInstruction<TwoRegisterInstruction>(stateIndex).registerA
                addInstructions(stateIndex + 1,
                    "invoke-static {v$register}, $EXTENSION_CLASS_NAME->logMutationState(Ljava/lang/String;)V")
            }

            // -------------------------

            val markAsReadButtonFieldRef: FieldReference
            val buttonEnumClassName: String
            ThreadLongPressButtonsEnumInitFingerprint.apply {
                val strIndex = stringMatches.first().index
                buttonEnumClassName = classDef.type
                GetButtonEnumClassNameExtensionFingerprint.changeFirstString(classNameToExtension(buttonEnumClassName))

                method.apply {
                    val markAsReadButtonSPutObjectIndex = indexOfFirstInstruction(strIndex, Opcode.SPUT_OBJECT)

                    markAsReadButtonFieldRef = getInstruction(markAsReadButtonSPutObjectIndex).getReference<FieldReference>()!!
                }
            }

            ThreadLongPressMuteButtonBuilderFingerprint.method.apply {
                val listParameterIndex = parameterTypes.indexOf("Ljava/util/List;")
                addInstructions(
                    0,
                    """
                    invoke-static {p$listParameterIndex}, $EXTENSION_CLASS_NAME->addButton(Ljava/util/List;)Ljava/util/List;
                    move-result-object p$listParameterIndex
                    
                    """.trimIndent(),
                )
            }

            ThreadLongPressButtonActionFingerprint.apply {
                val directThreadKeyClassName = "Lcom/instagram/model/direct/DirectThreadKey;"
                val context = "Landroid/content/Context;"

                val userSessionFieldRef = classDef.fields.first { it.type == USER_SESSION_CLASS }
                val contextFieldRef = classDef.fields.first { it.type == context }

                method.apply {
                    val buttonParameterIndex = parameters.indexOfFirst { it.type == buttonEnumClassName } + 1
                    val directThreadKeyParameterIndex = parameters.indexOfFirst { it.type == directThreadKeyClassName } + 1
                    val threadInfoParameterIndex = directThreadKeyParameterIndex - 2

                    // Hard coding register names as these instructions
                    // will be added on the first line.
                    addInstructionsWithLabels(
                        0,
                        """
                        move-object/from16 v0, p0
                        iget-object v1, v0, $contextFieldRef
                        iget-object v2, v0, $userSessionFieldRef
                        
                        move-object/from16 v3, p$buttonParameterIndex
                        
                        move-object/from16 v4, p$threadInfoParameterIndex
                        move-object/from16 v5, p$directThreadKeyParameterIndex
                        
                        invoke-static {v1,v2,v3,v4,v5}, $EXTENSION_CLASS_NAME->buttonAction($context $USER_SESSION_CLASS Ljava/lang/Object;Ljava/lang/Object;$directThreadKeyClassName)Z
                        move-result v0
                        if-eqz v0, : piko
                        return-void
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(0)),
                    )
                }
            }

            enableSettings("markChatAsRead")
        }
    }
