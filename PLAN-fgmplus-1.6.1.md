# PLAN-fgmplus-1.6.1：1.21.1 双端对齐 1.21.11

> 状态：待实施（下一会话执行）。本文档自包含，审计证据已钉死，照条目做即可。
> 背景：v1.6.1 候选。用户实机反馈 1.21.1-fabric 与 1.21.1-neoforge 相对基准端
> platforms/1.21.11-fabric（已验收正本）存在：造型工作室界面完全不同、滑条数值结果
> 不同、防穿模"总是突出一点"、模型渲染异常、衣柜入口按钮风格溢出。
> 用户决策：**对齐 1.21.11**；本轮只审计不改码，修复全部在下一会话做。

## 截图证据（引用，不随仓库存）

- 截图一（NeoForge 造型工作室 = 自绘暗色窗口）：
  `C:\Users\NIUQU\.zcode\cli\image-cache\sess_9b674e09-7db5-4371-9338-c5bb948540c8\image-700b5e2fdb9e903b85d0949671994ea5.png`
- 截图二（Fabric 1.21.1 衣柜 Shape Studio 按钮 = 原版灰框且溢出面板）：
  `C:\Users\NIUQU\.zcode\cli\image-cache\sess_9b674e09-7db5-4371-9338-c5bb948540c8\image-06289a383bf3f49007e9e892c9792780.png`

## 审计已证等价、下一会话无需再查（省 token 清单）

以下经三路审计逐行比对 + 3.2.2 jar 字节码 + 离线 harness（92 quads ALL PASS）钉死：

1. **RoundBreastMesh 三端数学完全一致**（径向投影/法线/UV 归一化；3.2.x 顶点 UV 与
   FGM5 UVLayout 语义经字节码 fdiv 证实同源）。
2. **发射数学逐段等价**：pullDir、钳制深度、`Vector4f.mul(pose)`、法线 `.mul(normalMat)`、
   分层 epsilon（三端 `2.125*0.0625`、外层 +0.003）。
3. **FGM Plus 自有 8 条滑条三端定义逐字一致**（min/max/显示公式/写入）；ShapeData/JSON/
   8-float wire 三端同源；`FgmPlusConfig` 加宽表三端相同；validate 拦截三端同构
   （commit e53f1bb 已修 raw-field 坑）。
4. 3.2.x `WildfireSlider`/`WildfireButton` 与 FGM 5 控件**像素级同款**（字节码三色常量
   一致）——GUI 问题全在构图/自绘延伸层，**控件不用换**。
5. 两端 9-29 当天 run 日志零 mixin apply 失败（`[[mixins]]` 修复已生效）。

## 修复项（按优先级）

### P0-1 GUI：重写两端 ShapeStudioScreen 为基准端构图

现状 = 自绘暗色窗口（标题条+X 钮+右挂面板，`ShapeStudioScreen.java` fabric:56-65/191-217、
neoforge 同构）；基准端 = FGM5 式中央面板（`1.21.11-fabric/.../gui/ShapeStudioScreen.java:52-56,
126-164, 212-228, 240-244`）。

改法（两端同改，代码几乎相同；控件用 3.2.x 构造器
`new WildfireSlider(x,y,w,h,min,max,cur,update,msg,save)` /
`new WildfireButton(x,y,w,h,comp,onPress[,Tooltip])`，javap 已验）：

1. 布局常量照抄基准：`FULL_WIDTH=166, HALF_WIDTH=81, PANEL_FILL=0x55000000,
   FGM_BUTTON_HEIGHT=15`；`y=height/2-11`、`left=width/2-36`。
2. init()：删 X 钮；滑条行位——scale 左列 y-24/-4/+16、offset 右列 y-24/-4/+16、
   perkiness (left, y+36)、roundness (right, y+36)，宽 HALF_WIDTH 高 20；按钮 4 个
   (left|right, y+56|y+76, HALF_WIDTH, **15**)，诊断平面钮留字段引用用 setMessage 切文案。
   offsetZ 取反、persist/resolvePlayer/refreshSoundStatus 逻辑原样保留。
3. renderBackground：`renderTransparentBackground` → `fill(x-40, y-32, x+136, y+113,
   0x55000000)` → 标题居中 `(x - font.width(title)/2, y-82)` → 玩家小窗
   `enableScissor(x-128, y-35, x-52, y+53)` + `InventoryScreen.renderEntityInInventoryFollowsMouse(
   gfx, x-128, y-35, x-52, y+113, 70, 0.0F, mouseX, mouseY+35, ent)` + `disableScissor`
   （3.2.x 基类无 renderPlayerInFrame，须手搓；ent 按 playerUUID 查）。
4. render()：只画状态行 `drawCenteredString` 起点 `(x+48, y+94)`、换行宽 162。
5. **补 `onClose()` → `minecraft.setScreen(parent)`**（3.2.x BaseWildfireScreen 无
   onClose，ESC 现在直接退游戏不回衣柜——javap 已证）。

### P0-2 GUI：衣柜入口按钮去幽灵边框（截图二根因）

3.2.x 衣柜贴图右列在分隔带以下是"空心区"（无右边框/底边框）；我们的 mixin 自绘了凭空
灰框（`0xFFC6C6C6`）且伸到 x247（贴图右缘 246 之外）。

- fabric `WardrobeBrowserScreenMixin.java:50-79`：按钮本体 Y/尺寸是对的（女 y+16、男
  y-4，158x20），**删掉全部幽灵边框/底带 fill，只留内部填充**
  `fill(left+83, top+fillTop, left+240, bandTop, 0xFF0A0A0A)`（内部真实起点 x83）。
- neoforge `WardrobeBrowserScreenMixin.java:45-46, 67-76`：按钮 Y 压住贴图分隔带（y+8/
  y-12），改到与 fabric 统一（女 y+16、男 y-4）；自绘边框（0xFF6B6B6B 四条+底带）同样删。
- `left=(width-248)/2, top=(height-134)/2, fillTop= 女84/男64`。
- 三色右边框完全复刻版（x240=0x6B6B6B / x241..244=0xC6C6C6 / x245..246=0x555555 + 底带）
  为可选项，**先做最小修复实机验收再决定**。
- mixin 注入目标维持现状（fabric 双名 `{"init","method_25426"}/{"renderBackground",
  "method_25420"}`、neoforge 单 mojmap 名）。

### P0-3 数值：深度滑条渲染系数对齐 FGM 5（"数值结果不一样"最高嫌疑）

FGM 3.2.x 深度渲染系数 0.0625/单位，FGM 5 改为 0.0425/单位（同滑条值体型差 47%）。
我们 1.21.1 两端忠实镜像了 3.2.x → 与基准端结果不同。

- 改 `fp$freezeDepthSink` 重算式 `breastOffsetZ * 0.0625F` → `* 0.0425F`：
  `1.21.1-fabric/.../mixin/GenderLayerMixin.java:208`、
  `1.21.1-neoforge/.../mixin/GenderLayerMixin.java:215`。
- **不要动** `expectedZ` 指纹（fabric:195 / neoforge:202）——保持 0.0625 才能匹配 FGM
  传入实参，否则指纹守卫降级 vanilla。
- 语义注记：这是有意让 3.2.x 端渲染结果向 1.21.11 看齐（偏离 3.2.x 原生手感），写入
  CHANGELOG。

### P0-4 网络：neoforge WildfireSyncServerMixin 移出 client 列表

`1.21.1-neoforge/src/main/resources/fgmplus.mixins.json`：`"WildfireSyncServerMixin"`
在 `"client"` 数组 → 专用服不加载 client mixins → S2C 形状广播全灭、联机远端形状全默认。
对照两端 fabric 都在公共 `"mixins"`（目标类 `com.wildfire.main.networking.WildfireSync`
是公共类，服务端可用）。一行 JSON 挪动。

### P1-5 渲染：baby 分支矩阵组合修正（两端口）

现状 `view.scale(1/babyBodyScale); view.translate(0, +bodyYOffset/16, 0)`（fabric
GenderLayerMixin.java:142-149、neoforge:146-156）——既非正向也非真逆。FGM 正向链是
`S(s)·T(y)`（3.2.2 字节码 offset 28/44），基准端也是正向追加
（`1.21.11 GenderLayerMixin.java:102-104`）。改：`view.scale(babyBodyScale)` +
`view.translate(0, bodyYOffset/16, 0)`。成年玩家恒 false 属死代码，但必须对齐。

### P1-6 渲染：钳制窗口生命周期加固（"突出一点"首要可检验假设）

现状：`fp$capture` 的 `catch (Exception ignored)` 静默吞 + `fp$drawDebugPlane` 在 TAIL
**无条件消费窗口**（fabric:333-337 / neoforge:346-349）→ 一旦捕获失败或时序有出入，
窗口变 null、后续静默回退 vanilla renderBox（无圆度无削背）→ 平直盒整体穿出，恰好同时
表现为"突出一点"+"渲染不一致"。

- 把 TAIL 无条件清窗改为"debugPlane 实际绘制才消费"（或对齐基准端按命令消费的语义）。
- `fp$flattenBack` 入口 + capture catch 处各加一行 debug 日志（命中/回退计数）——
  实机靠日志判案：flatten 命中 0 而 mesh 可见 = 窗口活着，D2 排除。
- 实机验证口诀：开「诊断平面」看平面是否贴躯干背（倾斜/悬空 = 窗口矩阵错）。

### P2-7（可选，记录在案）

- **mesh 缓存 key**：neoforge 端 physics breastSize 逐帧 lerp → WeakHashMap key 每帧
  更换 → 每帧重建 mesh（性能非错形）。可按"box 尺寸特征+roundness"做 key。
- **FGM 原版滑条步进吸附**：FGM 5 有 step/mouseStep，3.2.x 连续（方向键一跳 25%）。
  对齐成本高（要给 3.2.x WildfireSlider 写 mixin），用户未点名，先不做。
- **fabric 端 3.2.1 注记核证**：ordinal 4/7 与 record u()/v() 只有 javadoc 注记。把
  fgm-3.2.1.jar（实例 mods 里有：`D:\Myworld\.minecraft\versions\1.21.1-CCB\mods\
  Female-Gender-Mod-fabric-3.2.1+1.21.jar`）放进 `platforms/1.21.1-fabric/_probe/jar/`
  javap 复核 + RoundMeshHarness 换真实 3.2.1 box 重跑（neoforge 侧命令：
  `java -cp "out;jar;../build/classes/java/main;lib/*" RoundMeshHarness` 已 ALL PASS 可参照）。
- forge 1.20.1 端 ShapeStudioScreen 与 1.21.1 系出同门（同款自绘构图），用户未点名；
  若 P0-1 验收后用户想把 forge 一并对齐，另行开项。

## 实施顺序与验证

1. P0-4（一行）→ P0-3（两行）→ P1-5（两行）→ P1-6 → P0-2 → P0-1（最大块）。
   每步独立 build 两端（`cd platforms/1.21.1-* && ./gradlew build`）。
2. 部署：`Mechanomania-航空学\mods`（neo）、`1.21.1-CCB\mods`（fabric）+ `dist/1.6.1/`。
3. dev 冒烟可省（改动多为 GUI/常量）；重点实机验收清单：
   - 造型工作室构图与 1.21.11 一致（中央面板/15px 按钮/标题居中/玩家小窗）；ESC 回衣柜
   - 衣柜 Shape Studio 按钮无灰框不溢出（两端）
   - 深度滑条同值与 1.21.11 同体型；胸部大小/圆度同值同形
   - 「诊断平面」绿面贴背；日志 flatten 命中数 > 0
   - 圆度 64% 背视/侧视与 1.21.11 对照；甲+showJacket 外层不闪不凸
   - （若联机）远端玩家形状正常（P0-4）
4. 完成后：CHANGELOG v1.6.1 双语段 + commit + 部署 + 记忆回写。

## 风险与回归线

- `[[mixins]]` 声明与 manifest `MixinConfigs`（commit 6e6be36）不许动——动了注入全灭。
- `defaultRequire: 0` 是四端惯例（静默失败），所以每个注入都已带 `require = 1`；
  新增/修改注入点必须保持 require=1 且别把关键注入改成可静默跳过。
- P0-3 改的是**我们重算式**，指纹守卫（expectedZ）原样保留——守卫降级只会丢我们的
  增强，不会错位。
- 深度系数属有意偏离 3.2.x 上游：升级 FGM 3.2.x → 更新版本时需重核该系数。
