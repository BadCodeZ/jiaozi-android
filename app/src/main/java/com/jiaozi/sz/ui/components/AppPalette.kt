package com.jiaozi.sz.ui.components

import androidx.compose.ui.graphics.Color

/**
 * 硬编码色集中登记（2026-09-30 挑刺 P0/P1 收敛）：
 * 原散落 Color(0x..) 字面全部收编为命名常量，零视觉变化、集中可维护。
 * 纯白/黑改用 Color.White/Color.Black；语义化（明暗双态）见 AppColors，留作后续专项。
 */
object AppPalette {
    val c_52000000 = Color(0x52000000)
    val c_ff071b2e = Color(0xff071b2e)
    val c_ff0b2e5c = Color(0xff0b2e5c)
    val c_ff121212 = Color(0xff121212)
    val c_ff13283f = Color(0xff13283f)
    val c_ff14291f = Color(0xff14291f)
    val c_ff14334f = Color(0xff14334f)
    val c_ff14375f = Color(0xff14375f)
    val c_ff1a1a1a = Color(0xff1a1a1a)
    val c_ff1c1c1e = Color(0xff1c1c1e)
    val c_ff1f1f1f = Color(0xff1f1f1f)
    val c_ff232030 = Color(0xff232030)
    val c_ff242424 = Color(0xff242424)
    val c_ff2a2118 = Color(0xff2a2118)
    val c_ff2a2a2a = Color(0xff2a2a2a)
    val c_ff2a5b96 = Color(0xff2a5b96)
    val c_ff2aa294 = Color(0xff2aa294)
    val c_ff2c2c2c = Color(0xff2c2c2c)
    val c_ff2d2d2d = Color(0xff2d2d2d)
    val c_ff2e0603 = Color(0xff2e0603)
    val c_ff2e1a18 = Color(0xff2e1a18)
    val c_ff2f9e6e = Color(0xff2f9e6e)
    // 🔴 2026-10-01 十五校新增：暗态 outlineVariant（分隔线 / 描边）。
    //   卡片底由 #1A1A1A 上移到 #242424 后，原 #2A2A2A 与本色的差从 **16 阶塌到 6 阶**
    //   ⇒ 全站 16 处画在卡片上的分隔线（设置项 / 列表行 / 目录竖线）会整体消失。
    //   本值 = 新卡片底 + 15 阶，把「分隔线比容器亮一档」这条**原有关系**原样保住。
    val c_ff333333 = Color(0xff333333)
    // 🔴 2026-10-01 十五校新增：暗态 surfaceContainerHighest。
    //   十五校把卡片层次整体上移一档（#1A1A1A→#242424、#242424→#2C2C2C），本值是最高档的递推结果。
    val c_ff363636 = Color(0xff363636)
    val c_ff3b8cf7 = Color(0xff3b8cf7)
    val c_ff404040 = Color(0xff404040)
    val c_ff410002 = Color(0xff410002)
    // 🔴 2026-10-01 十五校新增：暗态主色（替换原 #6E96BF 灰蓝）。
    //   取 UFIPanel 深色模式实测选中蓝 #267AF7 的同族鲜蓝，按本工程暗底 #121212 的对比度微调：
    //   #4C9DF8 在 #121212 上 6.68:1（原 #6E96BF 为 6.05:1）⇒ 更亮更饱和但**不刺眼**，
    //   与浅色档 #3B8CF7 同色相（S 差 6%），全站「一蓝」跨明暗依然成立。
    val c_ff4c9df8 = Color(0xff4c9df8)
    val c_ff4caf50 = Color(0xff4caf50)
    val c_ff4dbe8c = Color(0xff4dbe8c)
    val c_ff4fc9ba = Color(0xff4fc9ba)
    val c_ff505050 = Color(0xff505050)
    val c_ff5070b0 = Color(0xff5070b0)
    val c_ff5ea8fb = Color(0xff5ea8fb)
    val c_ff666666 = Color(0xff666666)
    val c_ff6e96bf = Color(0xff6e96bf)
    val c_ff7090b0 = Color(0xff7090b0)
    val c_ff7c6bb0 = Color(0xff7c6bb0)
    val c_ff84a6c8 = Color(0xff84a6c8)
    val c_ff9dbbda = Color(0xff9dbbda)
    val c_ff9e8fcb = Color(0xff9e8fcb)
    val c_ffb0b0b0 = Color(0xffb0b0b0)
    val c_ffd64b3f = Color(0xffd64b3f)
    val c_ffd6e6f2 = Color(0xffd6e6f2)
    val c_ffd98a1f = Color(0xffd98a1f)
    val c_ffd9d9d9 = Color(0xffd9d9d9)
    val c_ffe0a94a = Color(0xffe0a94a)
    val c_ffe0e0e0 = Color(0xffe0e0e0)
    val c_ffe67e22 = Color(0xffe67e22)
    val c_ffe6e6e6 = Color(0xffe6e6e6)
    val c_ffe8e8e8 = Color(0xffe8e8e8)
    val c_ffe94634 = Color(0xffe94634)
    val c_ffe97266 = Color(0xffe97266)
    val c_ffeaf1fe = Color(0xffeaf1fe)
    val c_ffececec = Color(0xffececec)
    val c_ffedf7f2 = Color(0xffedf7f2)
    val c_ffefefef = Color(0xffefefef)
    val c_fff0f0f0 = Color(0xfff0f0f0)
    val c_fff1f6fe = Color(0xfff1f6fe)
    val c_fff2f0f8 = Color(0xfff2f0f8)
    val c_fff2f2f2 = Color(0xfff2f2f2)
    val c_fff7f7f7 = Color(0xfff7f7f7)
    val c_fffcf0ee = Color(0xfffcf0ee)
    val c_fffdf6ec = Color(0xfffdf6ec)
    val c_fffdf6f4 = Color(0xfffdf6f4)
    val c_ffff6b61 = Color(0xffff6b61)
    val c_ffffdad6 = Color(0xffffdad6)
}
