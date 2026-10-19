package com.zywoolzh.electricaltip

data class KnowledgeTip(val category: String, val text: String)

object KnowledgeContent {
    val tips = listOf(
        KnowledgeTip("配电", "低压配电系统检修前，应执行停电、验电、接地和挂牌上锁。"),
        KnowledgeTip("配电", "三相负载应尽量均衡，持续关注中性线电流与接点温升。"),
        KnowledgeTip("配电", "选择性保护要求下级故障优先由最接近故障点的保护装置切除。"),
        KnowledgeTip("MCC", "MCC抽屉回路检修前，应确认主回路与控制回路均已可靠隔离。"),
        KnowledgeTip("MCC", "电机频繁启动会增加绕组温升，应核对允许启动次数和间隔。"),
        KnowledgeTip("MCC", "接触器异常噪声常与线圈欠压、铁芯污损或短路环故障有关。"),
        KnowledgeTip("冷水机组", "冷水机组启停前应确认冷冻水、冷却水流量及阀门状态。"),
        KnowledgeTip("冷水机组", "冷凝温度每升高1℃，通常会增加压缩机能耗并降低制冷量。"),
        KnowledgeTip("冷水机组", "冷冻水供回水温差偏小，需排查旁通、流量过大和末端换热。"),
        KnowledgeTip("UPS", "UPS并机系统维护前，应核对旁路同步、负载率和冗余容量。"),
        KnowledgeTip("UPS", "蓄电池内阻趋势比单次电压更能反映电池健康状态。"),
        KnowledgeTip("UPS", "UPS输入谐波与功率因数应纳入上游变压器和发电机选型校核。"),
        KnowledgeTip("数据中心规范", "关键供电链路应消除单点故障，并定期验证切换逻辑。"),
        KnowledgeTip("数据中心规范", "容量管理应同时校核IT负载、制冷能力和配电路径余量。"),
        KnowledgeTip("数据中心规范", "变更作业应具备步骤、风险、回退方案和现场复核记录。"),
    )

    fun normalizedIndex(index: Int): Int = Math.floorMod(index, tips.size)
}

object IntervalCalculator {
    const val MIN_VALUE = 1
    const val MAX_VALUE = 365

    fun toMillis(value: Int, unit: IntervalUnit): Long {
        val safeValue = value.coerceIn(MIN_VALUE, MAX_VALUE)
        val hours = if (unit == IntervalUnit.HOURS) safeValue.toLong() else safeValue.toLong() * 24
        return hours * 60 * 60 * 1000
    }
}

enum class IntervalUnit { HOURS, DAYS }
