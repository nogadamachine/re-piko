package app.crimera.patches.instagram.utils

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getMutableMethod
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

// 449 stores several parser keys in pure integer-to-string switch tables.
// Restore only the inspected anchors, preserving the exact returned string.
val decode449AnchorsPatch = bytecodePatch {
    execute {
        val anchors = mapOf(
            "LX/0000;" to mapOf(294 to "in_feed_survey", 912 to "suggested_users", 1725 to "suggested_businesses"),
            "LX/0G47;" to mapOf(413 to "profile_to_share_url"),
            "LX/0222;" to mapOf(196 to "ARG_HIDE_APPROVE_BUTTON", 197 to "ARG_SHOW_SUGGESTED_USERS"),
            "LX/0120;" to mapOf(304 to "SaveMedia"),
        )
        for ((owner, values) in anchors) {
            val decoder = mutableClassDefBy(owner).methods.single {
                it.name == "A00" && it.parameterTypes == listOf("I") && it.returnType == "Ljava/lang/String;"
            }
            val code = decoder.instructions.toList()
            val allowed = setOf(Opcode.PACKED_SWITCH, Opcode.PACKED_SWITCH_PAYLOAD,
                Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO, Opcode.RETURN_OBJECT, Opcode.NOP)
            if (code.any { it.opcode !in allowed } || code.first().opcode != Opcode.PACKED_SWITCH) {
                throw PatchException("449 anchor decoder is not a pure string table: $owner")
            }
            val offsets = mutableMapOf<Int, Int>()
            var pc = 0
            code.forEachIndexed { index, instruction -> offsets[pc] = index; pc += instruction.codeUnits }
            val payload = code[offsets[(code.first() as OffsetInstruction).codeOffset]!!] as SwitchPayload
            for ((key, value) in values) {
                val offset = payload.switchElements.single { it.key == key }.offset
                val index = offsets[offset]!!
                if (code[index].getReference<StringReference>()?.string != value ||
                    code[index + 1].opcode != Opcode.RETURN_OBJECT) {
                    throw PatchException("449 decoded anchor changed: $owner key $key")
                }
            }
        }
        val targets = mutableListOf<Pair<Method, List<Pair<Int, String>>>>()
        classDefForEach { cls ->
            cls.methods.forEach { method ->
                val code = method.implementation?.instructions?.toList() ?: return@forEach
                val sites = code.withIndex().mapNotNull { (index, instruction) ->
                    val ref = instruction.getReference<MethodReference>() ?: return@mapNotNull null
                    val values = anchors[ref.definingClass] ?: return@mapNotNull null
                    if (instruction.opcode !in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) ||
                        ref.name != "A00" || ref.parameterTypes != listOf("I") ||
                        ref.returnType != "Ljava/lang/String;" || index == 0) return@mapNotNull null
                    val prior = code[index - 1]
                    val literal = prior as? WideLiteralInstruction ?: return@mapNotNull null
                    if (prior.registersUsed != instruction.registersUsed ||
                        code.getOrNull(index + 1)?.opcode != Opcode.MOVE_RESULT_OBJECT) return@mapNotNull null
                    val text = values[literal.wideLiteral.toInt()] ?: return@mapNotNull null
                    index to text
                }
                if (sites.isNotEmpty()) targets += method to sites
            }
        }
        if (targets.isEmpty()) throw PatchException("No inspected 449 encoded anchor calls found")
        for ((method, sites) in targets) {
            val mutable = method.getMutableMethod()
            for ((index, text) in sites.asReversed()) {
                val destination = mutable.getInstruction(index + 1).registersUsed.single()
                mutable.replaceInstruction(index, "const-string v$destination, \"$text\"")
                mutable.replaceInstruction(index + 1, "nop")
            }
        }
    }
}
