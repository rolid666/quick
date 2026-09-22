package com.quick.app.collect

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 触发判定测试 —— 守的是现场最怕的两件事：**多记一笔** 与 **漏记一笔**。
 *
 * 场景都来自真机行为（2026-09 实测）：
 * - 0x1F 置 1 后约 1 s 自动清零，轮询周期 0.9 s，所以同一笔可能被**两帧**都读到；
 * - Wi-Fi 抖动时可能整笔错过标志窗口（只看到清零后的状态）→ 补收通道。
 */
class SaveTriggerTest {

    private fun save(flag: Int = 1, temp: Int = 351, judge: Int = 1) = TriggerFrame(flag, "$judge|$temp|67|25")

    private data class TriggerFrame(val flag: Int, val sig: String)

    private fun SaveTrigger.feed(f: TriggerFrame, hasResult: Boolean = true, judgeValid: Boolean = true) =
        next(f.flag, f.sig, hasResultBlock = hasResult, judgeValid = judgeValid)

    @Test
    fun `首帧只建基线 —— 即使标志已经是 1 也不记`() {
        val t = SaveTrigger()
        // 连接前仪器上就残留着一笔（标志还是 1）→ 绝不能把它当成"刚保存的"，否则开机就多一条
        assertEquals(SaveTrigger.Decision.BASELINE, t.feed(save()))
    }

    @Test
    fun `标志 0 到 1 且内容变化 —— 记一笔`() {
        val t = SaveTrigger()
        t.feed(save(flag = 0, temp = 0))
        assertEquals(SaveTrigger.Decision.NEW_FLAG, t.feed(save(temp = 351)))
    }

    @Test
    fun `同一笔被两帧读到（1 秒窗口跨了两个周期）—— 只记一次`() {
        val t = SaveTrigger()
        t.feed(save(flag = 0, temp = 0))
        assertEquals(SaveTrigger.Decision.NEW_FLAG, t.feed(save(temp = 351)))
        // 第二帧标志仍为 1、结果块内容一字未变 → 必须跳过（否则现场出现"多保存一笔"）
        assertEquals(SaveTrigger.Decision.NONE, t.feed(save(temp = 351)))
    }

    @Test
    fun `标志自清后内容没变 —— 不重复记`() {
        val t = SaveTrigger()
        t.feed(save(flag = 0, temp = 0))
        assertEquals(SaveTrigger.Decision.NEW_FLAG, t.feed(save(temp = 351)))
        assertEquals(SaveTrigger.Decision.NONE, t.feed(save(flag = 0, temp = 351)))
    }

    @Test
    fun `连续两笔内容完全相同 —— 靠标志脉冲分辨，两笔都记`() {
        val t = SaveTrigger()
        t.feed(save(flag = 0, temp = 0))
        assertEquals(SaveTrigger.Decision.NEW_FLAG, t.feed(save(temp = 351)))   // 第一笔
        assertEquals(SaveTrigger.Decision.NONE, t.feed(save(flag = 0, temp = 351)))
        assertEquals(SaveTrigger.Decision.NEW_FLAG, t.feed(save(temp = 351)))   // 第二笔（同温度同判定）
    }

    @Test
    fun `读晚了（标志已自清但内容变了）—— 补收`() {
        val t = SaveTrigger()
        t.feed(save(flag = 0, temp = 0))
        t.feed(save(temp = 351))
        assertEquals(SaveTrigger.Decision.RECOVERED, t.feed(save(flag = 0, temp = 340)))
    }

    @Test
    fun `补收通道关闭时 —— 读晚的那笔不记（宁可少记也不重复）`() {
        val t = SaveTrigger(recoverMissedSave = false)
        t.feed(save(flag = 0, temp = 0))
        t.feed(save(temp = 351))
        assertEquals(SaveTrigger.Decision.NONE, t.feed(save(flag = 0, temp = 340)))
    }

    @Test
    fun `降级模式（没有结果块）—— 走补收通道也不记`() {
        val t = SaveTrigger()
        t.feed(save(flag = 0, temp = 0), hasResult = false)
        assertEquals(
            SaveTrigger.Decision.NONE,
            t.feed(save(flag = 0, temp = 340), hasResult = false)
        )
    }

    @Test
    fun `判定值非法 —— 不补收`() {
        val t = SaveTrigger()
        t.feed(save(flag = 0, temp = 0))
        t.feed(save(temp = 351))
        assertEquals(
            SaveTrigger.Decision.NONE,
            t.feed(save(flag = 0, temp = 340), judgeValid = false)
        )
    }

    @Test
    fun `弹卡暂停后恢复 —— 第一帧只重建基线，暂停期间的变化不算新结果`() {
        val t = SaveTrigger()
        t.feed(save(flag = 0, temp = 0))
        t.feed(save(temp = 351))
        // 弹卡期间有人在仪器上又按了保存；恢复后第一帧拿来重建基线
        t.rebaseline(flag = 0, sig = save(temp = 340).sig)
        assertEquals(SaveTrigger.Decision.NONE, t.feed(save(flag = 0, temp = 340)))
        // 之后的一笔照常记
        assertEquals(SaveTrigger.Decision.NEW_FLAG, t.feed(save(temp = 360)))
    }

    @Test
    fun `重连后重建基线 —— 不把重连前那笔重复记一次`() {
        val t = SaveTrigger()
        t.feed(save(flag = 0, temp = 0))
        t.feed(save(temp = 351))
        t.reset()
        assertEquals(SaveTrigger.Decision.BASELINE, t.feed(save(flag = 0, temp = 351)))
    }

    /**
     * 已知的**残余漏收窗口**（README「已知边界」有记）：标志脉冲整笔错过、且这一笔的内容
     * 与上一笔完全相同 —— 没有任何可观测的差异，只能漏掉。此处固定住这个事实，
     * 免得以后误以为已完全闭环。
     */
    @Test
    fun `已知残余窗口：整笔错过标志且内容与上一笔相同 —— 无法识别`() {
        val t = SaveTrigger()
        t.feed(save(flag = 0, temp = 0))
        t.feed(save(temp = 351))                                   // 上一笔
        // 下一笔：标志窗口整笔没读到，且温度/判定与上一笔一模一样
        assertEquals(SaveTrigger.Decision.NONE, t.feed(save(flag = 0, temp = 351)))
    }
}
