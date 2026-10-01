# P2P 附属模组计划（介质扩展）

> 决策依据：workspace `docs/adr/0001-medium-integrations-are-addons.md`（每个依赖一个附属、
> 红石例外搁置）+ 2026-09-05 grill-with-docs 第 10 轮用户决策。

## 已定决策

| 决策点 | 结论 |
|--------|------|
| 注册 API | 主模组发 **`RegisterMediumEvent`**（mod bus 事件），附属监听后注册介质 |
| 卸载兼容 | 卸载附属后频道**保留**，标记"未知介质"暂停传输；装回即恢复，数据永不丢 |
| 版本范围 | 仅 **1.20.1 Forge**（1.21.1 主模组还没有绑定模式那套功能，没有可附着的对象） |
| 首个附属 | **p2p-mekanism**，且必须覆盖 Mek 全部 5 类可传输物（用户明确要求） |

## Mekanism 10.4.x 可传输物清单（以实例 jar API 为准，2026-09-05 核实）

| 介质 | Mek API capability | 典型内容 | 传输形态 | 状态 |
|------|--------------------|----------|----------|------|
| 气体/化学品 GAS | `mekanism.api.chemical.gas.IGasHandler` | 氢、氧、蒸汽、各种化学品 | stack（mB 当量） | ✅ v0.1.0 已实现 |
| 热量 HEAT | `mekanism.api.heat.IHeatHandler` | 热量网格（连续量，无"种类"） | **连续量（double）**，导热模型 | ✅ v0.1.0 已实现 |
| 泥浆 SLURRY | `mekanism.api.chemical.slurry.ISlurryHandler` | 矿石处理的脏/净泥浆 | stack | ❌ 2026-09-06 用户决定不做 |
| 灌注材料 INFUSION | `mekanism.api.chemical.infuse.IInfusionHandler` | 碳/红石/钻石/黑曜石灌注剂 | stack | ❌ 2026-09-06 用户决定不做 |
| 色素 PIGMENT | `mekanism.api.chemical.pigment.IPigmentHandler` | 染料色素 | stack | ❌ 2026-09-06 用户决定不做 |

四类 chemical 共享 `mekanism.api.chemical.ChemicalStack` 基类与 `IChemicalHandler` 接口——
附属里**一套泛型化学传输例程**按 handler 类型参数化即可覆盖 4 种；热量单独一套（连续量，
参照现有 `EnergyTransfer` 的节流/退避写法）。另有 `BoxedChemicalStack`（跨类型合并）本版不用。

## ✅ 第 1 步已完成：主模组"介质化"重构（2026-09-06，随 0.1.0 交付）

实际落地要点（与原计划略有出入的部分）：
- 介质注册最终采用**附属构造函数直接调 `Mediums.register`**；`RegisterMediumEvent`（FORGE 总线、
  FMLLoadComplete 派发）保留为契约，但实测注解监听它不可靠（监听器注册成功却不被派发，原因未查明，
  疑与 Forge 模块化类加载/EventBus 内部机制有关）——**新附属一律用构造函数直调**。
- `Mediums.resolve` 对旧存档枚举名（ITEM/FLUID/ENERGY）做归一；未知 id 返回带原 id 的灰色
  "未知介质"占位（不传输、序列化回写原 id），附属装回即恢复。
- `Medium.backoffMaxTicks`：连续量介质（energy/heat）空闲退避上限 5t，堆式介质 20t。
- ⚠️ fg.deobf 缓存按「组:工件:版本」键控、不校验输入 jar 内容——主模组每次重构建后，
  附属构建前必须清 `~/.gradle/caches/forge_gradle/deobf_dependencies/blank/p2p/`，
  否则附属编译/运行用的是旧版主模组。

## 第 1 步：主模组"介质化"重构（附属的地基）

1. **Medium 抽象**（新包 `com.kalloer1.p2p.medium`）：
   - `record Medium(String id, int argbColor, String langKey, MediumTransfer transfer)`；
   - `MediumTransfer` 接口：`int transfer(TransferCapabilityCache cache, Channel channel,
     Map<ResourceKey<Level>, ...> levels, List<Member> inputs, List<Member> outputs, int rate, long tick)`；
   - 现有 `ItemTransfer` / `FluidTransfer` / `EnergyTransfer` 的静态方法作为三个内置介质
     的回调原样接入，**传输逻辑零改动**。
2. **`ChannelType` 枚举降级**：改为纯 id 字符串（内置 `"item"/"fluid"/"energy"`）。
   - **NBT/网络兼容**：旧存档里的 `"ITEM"` 读入时小写归一；未知 id 保留原字符串、
     `MediumRegistry.of(id)` 返回"未知介质"占位（灰色、名称 `p2p.type.unknown`），传输时跳过。
   - 红石模式判断等 switch 同步改为 id 判断或回调。
3. **`RegisterMediumEvent`**（mod bus）：附属在监听器里 `event.register(new Medium(...))`；
   主模组自己在该事件里注册内置三种（单一事实源）。
4. **UI**：`MediumRegistry`（客户端）改为从同步过来的介质表渲染；建频道弹窗本来就是
   注册表驱动，只需接收附属介质条目；频道详情"切换介质"按钮仅在目标介质可用时开放。
5. **验收**：无附属时游戏行为与当前完全一致；旧存档加载无报错；有附属时新介质出现在
   建频道列表且能完成绑定/传输。

## 第 2 步：p2p-mekanism（首个附属，覆盖全部 5 类）

- 依赖：`p2p`（主模组）+ `mekanism`（`mods` 依赖声明）；
- 注册 5 个介质：`mekanism:gas` / `mekanism:slurry` / `mekanism:infusion` / `mekanism:pigment`
  / `mekanism:heat`；
- 传输实现：capability 端点解析（`level.getCapability(IGasHandler, pos, face)`）参照现有
  `TransferCapabilityCache`；化学四类用泛型例程，热量用连续量例程；
- 介质颜色/名称：GAS 青、SLURRY 棕、INFUSION 紫、PIGMENT 依染料、HEAT 橙红。

## 第 3 步：p2p-botania ✅（2026-09-06 构建通过，待测）

- 介质 `botania:mana`（青绿色）；INPUT=从绑定的魔力池抽、OUTPUT=向魔力池注入；
- 走 `vazkii.botania.api.mana.ManaReceiver`（方块实体直接实现接口）：抽取 `receiveMana(-n)`、
  注入 `receiveMana(+n)`、满池 `isFull()`；ManaReceiver 不暴露容量上限，注入按 isFull 分批推进；
- 支持全部分配模式；魔力发射器（spreader）不是抽放模型，不支持。
- **未部署**：主测试实例没有 Botania，装哪个实例待用户定。

## 第 4 步：p2p-arsnouveau ✅（2026-09-06 构建通过，待测）

- 介质 `arsnouveau:source`（紫色，退避 5t）；INPUT=从源质罐/供能方块抽、OUTPUT=注入；
- 走 `com.hollingsworth.arsnouveau.api.source.ISourceTile`：抽取 `removeSource(n)`、
  注入 `addSource(n)`（返回未塞入量）、上限 `getMaxSource()`、`canAcceptSource()` 门控；
- 支持全部分配模式。**未部署**（同上，实例未装 Ars Nouveau）。

## 第 5 步：p2p-ae2（语义已澄清，分两阶段）

用户澄清的需求原文：绑定 AE2 控制器的面；一个控制器每面 32 频道、最多 6 面（6×32）；
这些频道可以输出，同时还能输出 ME 能量。→ 本质是 AE2 版"量子桥"（两个网格通过 p2p 共享频道与电力）。

- **v1（简单）**：ME 能量传输——绑控制器面，走 `IAEPowerStorage` 抽/注 AE 电力，与普通能量同构；
- **v2（大工程）**：频道桥接——AE2 的频道数是网格拓扑算出来的，无法"凭空注入"：p2p 需要一个
  **专属方块实体**挂在控制器旁充当网格节点（IGridNode 宿主），两端的 p2p 节点之间建立远程
  IGridConnection 使两个网格合并/共享——需要新增方块 + 网格宿主注册 + 连接生命周期管理，
  立项前先读 AE2 源码（`_mods_ref/_mod_sources/_ae2_ref` / AppliedEnergistics）确认 API 边界。
- **待用户拍板**：v1 先行还是 v2 一起上；v2 需要新增方块（类似红石端子的讨论）。

## 明确不做

- 红石信号介质（2026-09-05 用户决定搁置；重启时参考 memory 2026-09-05 第 8 轮调研）；
- 1.21.1 版附属（主模组 1.21.1 尚无绑定模式/介质功能）。
