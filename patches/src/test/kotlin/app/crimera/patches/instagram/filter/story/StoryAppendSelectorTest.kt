package app.crimera.patches.instagram.filter.story
import java.io.File
import kotlin.random.Random

fun main(args: Array<String>) {
    require(args.size == 1) { "Pass the fresh 448_story_parser.tsv" }
    val fixture = File(args[0]).readLines().filter { it.isNotBlank() }.map {
        val p = it.split('\t')
        StoryInstruction(StoryInstructionKind.valueOf(p[0]), p[1].toInt(), p[2].toInt(), p[3].toInt())
    }
    var passed = 0
    fun test(name: String, block: () -> Unit) { block(); passed++; println("PASS $name") }
    fun reject(f: List<StoryInstruction>, anchor: Int = 178) {
        check(runCatching { selectStoryAppendHook(f, anchor) }.exceptionOrNull() is IllegalArgumentException)
    }
    fun changed(index: Int, change: (StoryInstruction) -> StoryInstruction): List<StoryInstruction> =
        fixture.toMutableList().also { it[index] = change(it[index]) }
    fun insert(f: List<StoryInstruction>, at: Int, extra: List<StoryInstruction>): List<StoryInstruction> {
        val shifted = f.map { if (it.targetIndex >= at) it.copy(targetIndex = it.targetIndex + extra.size) else it }
        return shifted.take(at) + extra + shifted.drop(at)
    }
    test("actual448_guard199_object_v1_loop188") {
        check(selectStoryAppendHook(fixture,178)==StoryAppendHook(199,1,201))
        check(fixture[199].targetIndex==188 && fixture[201].targetIndex==188)
    }
    test("actual448_previous_boolean_v3_is_not_selected") {
        check(fixture[166].register==3 && fixture[165].kind==StoryInstructionKind.OTHER)
    }
    test("decoy_pretray_never_wins_over_actual_tray") {
        val f=insert(fixture,178,fixture.subList(196,202))
        check(selectStoryAppendHook(f,184).guardIndex==205)
    }
    test("no_legacy_fallback_when_real_tray_is_broken") {
        val broken=changed(197) { it.copy(kind=StoryInstructionKind.OTHER) }
        reject(insert(broken,178,fixture.subList(196,202)),184)
    }
    test("two_guarded_appends_in_tray_rejected") {
        reject(insert(fixture,202,fixture.subList(196,202)))
    }
    for (index in 196..201) test("missing_contract_instruction_$index") {
        reject(changed(index) { StoryInstruction(StoryInstructionKind.OTHER) })
    }
    test("wrong_parser_receiver") { reject(changed(197) { it.copy(receiverRegister=3) }) }
    test("wrong_object_result_register") { reject(changed(198) { it.copy(register=2) }) }
    test("wrong_add_argument_register") { reject(changed(200) { it.copy(register=2) }) }
    test("collection_receiver_aliases_item") { reject(changed(200) { it.copy(receiverRegister=1) }) }
    test("missing_collection_receiver") { reject(changed(200) { it.copy(receiverRegister=-1) }) }
    test("null_guard_and_goto_diverge") { reject(changed(199) { it.copy(targetIndex=189) }) }
    test("goto_missing_target") { reject(changed(201) { it.copy(targetIndex=-1) }) }
    for (target in listOf(-2,-1,0,178,196,199,201,9999)) test("invalid_loop_destination_$target") {
        val f=fixture.toMutableList();f[199]=f[199].copy(targetIndex=target);f[201]=f[201].copy(targetIndex=target);reject(f)
    }
    test("external_branch_into_append_is_rejected") { reject(changed(100) { it.copy(targetIndex=200) }) }
    test("next_json_key_ends_candidate_scope") { reject(changed(196) { StoryInstruction(StoryInstructionKind.STRING) }) }
    for (anchor in listOf(-1,0,177,179,fixture.size)) test("invalid_anchor_$anchor") { reject(fixture,anchor) }
    for (register in listOf(0,15,16,255)) test("legal_single_register_range_$register") {
        val f=fixture.toMutableList()
        for (i in 198..200) f[i]=f[i].copy(register=register)
        check(selectStoryAppendHook(f,178).itemRegister==register)
    }
    for (register in listOf(-1,256,65535)) test("illegal_guard_register_$register") {
        val f=fixture.toMutableList();for(i in 198..200)f[i]=f[i].copy(register=register);reject(f)
    }
    test("truncated_method_rejected") { reject(fixture.take(201)) }
    test("mutation_1000_seeded_prefix_decoys_preserve_actual_tray_selection") {
        val random=Random(448)
        repeat(1000) {
            val at=random.nextInt(0,178)
            val extra=List(random.nextInt(1,20)) {
                StoryInstruction(StoryInstructionKind.values()[random.nextInt(8)],register=random.nextInt(-1,256))
            }
            val f=insert(fixture,at,extra)
            check(selectStoryAppendHook(f,178+extra.size)==StoryAppendHook(199+extra.size,1,201+extra.size))
        }
    }
    println("$passed/$passed cases passed; one case contains 1000 seeded mutations. ONLY selector tests, not Android/Piko runtime.")
}
