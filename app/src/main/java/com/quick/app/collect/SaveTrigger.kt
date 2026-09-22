package com.quick.app.collect

/**
 * 「这一帧算不算一笔新结果」的判定 —— 唯一决定记录条数的地方，**纯 JVM 可单测**。
 *
 * 现场最怕两件事：**多记一笔**（操作员只按了一次保存）、**漏记一笔**（保存了却没有记录）。
 * 所以把这段逻辑从轮询循环里提出来，用测试守住，而不是靠现场试。
 *
 * 判据（每一帧只看两个值 + 一次基线判断）：
 * - **首帧只建基线**：不判定 —— 防把「连接之前仪器上遗留的结果」当成新结果
 *   （尤其是降级模式下标志寄存器可能是恒定的非 0 值）。
 * - **标志 ≠ 0**（0x1F 仪器上刚按过保存）→ 新结果；同一笔若被两帧读到（标志约保持 1 s）
 *   靠**结果签名**相同跳过，不重复入库。
 * - **标志 = 0 但签名变了** → 判定为「按了保存、本轮读晚了」的补收（见 [recoverMissedSave]）。
 *   当前周期改成固定周期 + 弹卡暂停后，这条路只在界面不在测量页时兜底。
 *
 * 签名见 `DeviceSnapshot.contentSignature`：只含「按保存才会写」的 0x20~0x23。
 */
class SaveTrigger(private val recoverMissedSave: Boolean = true) {

    private var lastFlag: Int? = null
    private var lastSig: String? = null

    /** 判定结果：控制器据此决定入库 / 跳过 / 什么都不做 */
    enum class Decision {
        /** 第一帧：只建基线，不记录 */
        BASELINE,

        /** 标志有脉冲（或标志不清零时签名变了）→ 新一笔 */
        NEW_FLAG,

        /** 标志已自清但签名变了 → 补收读晚了的那一笔 */
        RECOVERED,

        /** 与上一帧相同，什么都不做（绝大多数帧都是这个） */
        NONE
    }

    /**
     * 喂入一帧。
     *
     * @param flag 触发值（完整表 = 0x1F；降级模式 = 兜底寄存器原值）
     * @param sig 结果签名（0x20~0x23）
     * @param hasResultBlock 本帧是否含 0x1F~0x23
     * @param judgeValid 判定值是否合法（0/1/2）—— 补收通道的一个前置条件
     */
    fun next(flag: Int, sig: String, hasResultBlock: Boolean, judgeValid: Boolean): Decision {
        val baseline = lastFlag == null || lastSig == null
        val decision = when {
            baseline -> Decision.BASELINE
            flag != 0 -> if (flag != lastFlag || sig != lastSig) Decision.NEW_FLAG else Decision.NONE
            recoverMissedSave && hasResultBlock && judgeValid && sig != lastSig -> Decision.RECOVERED
            else -> Decision.NONE
        }
        // 签名只在基线/新结果时推进：不合规（NG/无效）的那一笔由控制器自己推进，
        // 否则同一笔会在下一帧再次被判成新结果
        when (decision) {
            Decision.BASELINE, Decision.NEW_FLAG, Decision.RECOVERED -> {
                lastFlag = flag
                lastSig = sig
            }
            Decision.NONE -> lastFlag = flag
        }
        return decision
    }

    /** 弹卡暂停后恢复：用恢复后的第一帧重建基线，这一帧不判定（用户 2026-09 要求） */
    fun rebaseline(flag: Int, sig: String) {
        lastFlag = flag
        lastSig = sig
    }

    /** 换读长度（降级）/重连后重建 */
    fun reset() {
        lastFlag = null
        lastSig = null
    }
}
