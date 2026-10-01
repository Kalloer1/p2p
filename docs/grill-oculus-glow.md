# Grill 记录：光影下面光可见性（2026-09-04）

> 模式 B（grill-with-docs）留痕。项目：`mods/p2p-1.20.1-forge`（Forge 1.20.1）。
> 规则：一次一题；能自查的事实不盘问；达成共识前不动代码。

## 背景（Bug A 修复现状）
- 原症状：绑定模式红(INPUT)/蓝(OUTPUT)/黄(hover) 面光在 Oculus 光影下完全不可见。
- 修复（Claude Code 已实现、`gradlew compileJava --rerun-tasks` 通过、未提交）：
  `ClientForgeEvents.java` → `RenderLevelStageEvent.Stage.AFTER_LEVEL`（GameRenderer.java:1128，
  renderLevel 返回后唯一派发点，fabulous/Iris 合成已在其前完成）+ 立即模式
  `RenderSystem.setShader(GameRenderer::getPositionColorShader)` + Tesselator POSITION_COLOR QUADS。
  `XRAY_THROUGH_WALLS = true`（真 X 光；改 false 恢复旧遮挡观感）。颜色/alpha 全部保留
  （IN 红 1/0.15/0.15、OUT 蓝 0.25/0.45/1、hover 黄 1/0.95/0.35 a0.6、当前频道 a0.7/其他 a0.22/
  hover 叠加 0.85、红框 a0.35、FACE_EPS 0.015）。手动重建 modelview = XP(pitch)+YP(yaw+180)，
  顶点 3 参相机相对坐标，depthMask(false)。

## 已确认事实
- Q1 答：用户跑的是**最新编译**（非旧 jar）→ 残留是真 bug。
- 新复现细节（用户原话要点）：向下看绑定脚下整片方块（全是红色），抬头到约水平，
  颜色直接消失；预期心智模型 = "x-ray 方框只要玩家视角能看见就不会消失"。

## Q3 已答（2026-09-04）
- 用户实测：**关光影同操作不消失**（只要视角里有方块）；**开光影稍偏角度就消失**。
- → 几何掠射角假说 H1 被否。光影特定问题（时序/GL 状态/合成次序）。
- 用户给出金标准参照：**SFM 标签枪方块高亮在光影开/关下都能透视显示** → 研究方向：
  查 SFM 源码渲染手法照搬（下一步）。

## 待判别假设（Q3 已结）
- 假说 H1（几何掠射角）：绑定面多为**顶面（水平面）**，单面薄片光在水平视角下
  投影面积→0，与 X 光无关（盒子有 6 面所以总有可见面，单面没有）。
- 假说 H2（HDR 混合对比度）：alpha 0.22 面光在光影 HDR/曝光后的亮背景（天空/发光体）前
  被 alpha 混合稀释到不可见。
- 判别问题：消失临界是否≈掠射角？同一操作关光影是否同样消失？

## 开放决策（未拍板）
1. ~~面光视觉形态~~ → **Q4 已确认（2026-09-04）：照抄 SFM 镜像配方**——保留 FaceQuad 收集与全部颜色/alpha，提交层改为 bufferSource + 自定义 RenderType（`RenderType.create` POSITION_COLOR/QUADS、纹理=方块图集 `TextureAtlas.LOCATION_BLOCKS`、depthTest always(519)、透明度 SRC_ALPHA→ONE 加法混合、createCompositeState(true)），阶段从 AFTER_LEVEL 切回 **AFTER_PARTICLES**，X 光恒定、删 `XRAY_THROUGH_WALLS` 开关。
   - 证据：SFM `ItemWorldRenderer.java` L47-75/L84-91/L203-228（TeamDman/SuperFactoryManager 1.20.1 分支，克隆于 %TEMP%\sfm-src），AFTER_PARTICLES + 同配方光影内外可见。
   - 用户确认语：接受加法混合观感（淡面暗处≈原样、亮处发光、重叠更亮）。
2. 混合方式：已随 Q4 定为加法（原"标准 vs 加法"决策关闭）。
3. `XRAY_THROUGH_WALLS`：随 Q4 删除（depthAlways 恒定穿墙）。
4. 图标优先级反向（同维 live pick 优先 → 否则 blockId）：建议采纳，待确认。
5. 挂起：跨维图标实测残留（老成员 blockId=null 属设计降级，重新绑定即有）；"%s钻石桶"名字问题与当前代码不符（zh_cn `p2p.hud.block`="方块: " 无 %s），疑似旧构建残留，待复测。
6. 提交节奏：**Q5 进行中**（选项：A 先提交 B/C 两个 commit、A 验证后单 commit [推荐] / B 全部验证后一次分 3 commit）。
7. 修复完成后按 Bug A/B/C 分三个 commit（7 文件未提交，工作区 M 状态）。
