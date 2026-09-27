# FGM Plus 1.20.1-1.1.0 实施协调 spec

本文件是多子代理并行开发的**唯一契约**。所有代理必须遵守，不许改本文件。

## 0. 项目与环境事实

- 项目：`D:\Claude_ds\fps-wfg`（Forge 1.20.1 / ForgeGradle 6 / official mappings / Java 17）
- modid：`fgmplus`（已完成改名）；主类 `io.github.e33epus.fgmplus.FgmPlusMod`；配置 `io.github.e33epus.fgmplus.Config.FgmPlusConfig`（ForgeConfigSpec，`fgmplus-common.toml`）
- FGM 参考源码：`D:\Claude_ds\fgm\src\main\java\com\wildfire\`（**3.0.1**；用户实机装的是 3.1，GenderLayer 字节码已验证一致）。编译依赖 jar：build.gradle 里 `curse.maven:female-gender-neoforge-481655:4896743`
- mixin 惯例（沿用现有 GenderLayerMixin.java）：
  - 目标是 FGM/自己 mod 的类：`@Inject/@Redirect(..., remap = false)`；`@At` 指向 vanilla 方法（如 PoseStack.scale/translate）时 `@At(..., remap = true)`
  - 私有成员前缀 `fp$`；`@Unique` 必写
  - **不要改** `fgmplus.mixins.json`、`mods.toml`、`build.gradle`、`gradle.properties`、`FgmPlusMod.java` —— mixin 类写好后把「需要追加进 mixins.json 的条目」写进最终报告，由整合者合并
  - mixins.json 已有 `defaultRequire: 0`，但每个注入器仍显式写 `require = 1` 或带 fallback 分支的宽松处理，上游变更时优雅降级
- **禁止运行 gradle**（多代理并行会锁冲突）。构建由整合者统一做。写完用肉眼+grep 自查 import 与语法。
- 代码注释用英文；行为约束注释才写，不复述代码。

## 1. FGM 事实清单（已探针验证，引用可信赖）

### 同步管线
- channel：`wildfire_gender:main_channel`，protocol "2"（`WildfireSync.java:32-37`）
- `PacketGenderInfo` 是**抽象基类**：字段 + `encode(FriendlyByteBuf)`(:84-99) + `updatePlayerFromPacket(GenderPlayer)`(:101-119)
- C2S：`PacketSendGenderInfo.handle`(:39-54) 校验 uuid → `WildfireGender.getOrAddPlayerById` → `updatePlayerFromPacket` → `WildfireSync.sendToOtherClients`
- S2C：`PacketSync.handle`(:39-47) 同样走 `updatePlayerFromPacket`
- GenderPlayer 存取：`WildfireGender.CLOTHING_PLAYERS`（Map<UUID,GenderPlayer>，`WildfireGender.java:48`）、`getPlayerById/getOrAddPlayerById`(:63-69)
- `GenderPlayer.saveGenderInfo(plr)`(:207-229)：字段写回 ClientConfiguration + `config.save()` + `plr.needsSync = true`（FGM 自有 tick 检测 needsSync 并 sendToServer，`WildfireSync.java:73-78`）

### 持久化
- FGM 每玩家：`config/WildfireGender/<UUID>.json`（Gson JsonObject，`Configuration.java:43-46`，`ClientConfiguration` 构造 `(String saveLoc, String name)`）
- 客户端 join 时 `GenderPlayer.loadCachedPlayer(uuid, markForSync)`（`WildfireEventHandler.java:147-158` 调用）

### 渲染
- `GenderLayer.renderBreastWithTransforms(...)`（GenderLayer.java:228-296），左/右两次调用(:216-221)
- 胸箱体：`WildfireModelRenderer.ModelBox`（FGM 自有类），左胸顶点 x∈[-4,0]、y∈[0,5]、z∈[0,4]（1/16 单位），pivot 在箱体原点（贴胸内上后角）；右胸 x∈[0,4]（GenderLayer.java:83-89,151-152）
- 旋转顺序：bounce Y 旋转(:257) → outwardAngle Y 旋转(:281) → **写死的下垂 `-35° × totalRotation` 绕 X**(:282) → 呼吸动画(:286) → `scale(0.9995f,1,1)`(:289) → renderBreast(:291)
- armor/jacket overlay 在 renderBreast 内复用同一矩阵（pushPose 后加微小 translate/scale，:298-351）
- 已有我们的 mixin（`fp$applyRealScale`/`fp$freezeDepthSink`/`fp$freezeHangShift`）见 `mixin/GenderLayerMixin.java`——**CORE 在其上扩展，不许推翻既有行为**

### 音效
- 替换逻辑：`WildfireEventHandler.onPlaySound`（@SubscribeEvent LOWEST，PlayLevelSoundEvent.AtEntity，:170-206）；只在受害者本人客户端播（`p.hurtTime == p.hurtDuration && p.hurtTime > 0`）
- `GenderPlayer.Gender` 枚举 `getHurtSound()`(:246-274，FEMALE 返回 `WildfireSounds.FEMALE_HURT`，MALE/OTHER null)
- `FEMALE_HURT = wildfire_gender:female_hurt`，`SoundEvent.createVariableRangeEvent`，**故意不注册 Registry**，靠 `assets/wildfire_gender/sounds.json` 条目 + `playLocalSound`（WildfireSounds.java:25-27）

### GUI
- `WildfireSlider`（WildfireSlider.java:34-59 两构造：FloatConfigKey 版本和显式 min/max 版本；`save()` 在 onRelease/keyPressed 触发 onSave 回调 :84-95）
- `WildfireButton`（WildfireButton.java:29-41，纯色自绘，`setTransparent` 可关背景）
- `WildfireCharacterSettingsScreen extends BaseWildfireScreen`（背景贴图 172x144，滑条到 yPos+80，按钮到 yPos+100）；`WardrobeBrowserScreen` 背景 248x156
- 打开链：G 键 → WardrobeBrowserScreen(parent=null) → WildfireCharacterSettingsScreen(wardrobe, uuid)；返回靠 X 按钮 `setScreen(parent)`
- `BaseWildfireScreen` 持有 `parent` 与 `playerUUID`（:27-40）

## 2. 共享 API（CORE 实现，GUI/SOUND 按此签名调用 —— 签名冻结，实现细节 CORE 自定）

```java
package io.github.e33epus.fgmplus.shape;

public final class ShapeData {
    public static final float MIN_SCALE = 0.5f, MAX_SCALE = 3.0f;   // 每轴缩放范围
    public static final float MIN_PERK = -30f, MAX_PERK = 60f;      // 挺拔度，单位：度
    private float scaleX = 1f, scaleY = 1f, scaleZ = 1f;            // 乘在渲染缩放上的乘数
    private float perkiness = 0f;                                   // 正值=更挺，绕X抵消-35°下垂
    // public float getScaleX()/setScaleX(float) 等 8 个（clamp 到上述范围）
    public boolean isDefault();                                      // 全默认=true（不同步存储）
    public void write(FriendlyByteBuf buf);                          // 4 个 float
    public static ShapeData read(FriendlyByteBuf buf);
    public JsonObject toJson();  public static ShapeData fromJson(JsonObject);
    public ShapeData copy();
}

public interface ShapeHolder {            // GenderPlayer 的 duck 接口
    ShapeData fgmplus$getShape();
    void fgmplus$setShape(ShapeData shape);
}

public final class ShapeStore {           // 静态工具
    public static Path getDir();          // config/fgmplus/shapes/
    public static ShapeData load(UUID);   // 无文件/损坏 → ShapeData 默认值
    public static void save(UUID, ShapeData);  // isDefault() 时删除文件
}

public final class ShapeRenderState {     // 渲染→GUI 的单帧指示
    public static volatile float lastRecovery;    // 自动防穿模回收量（0~1，1=完全回收）
    public static volatile boolean antiClipActive;
}
```

## 3. 工作包边界（谁改什么，越界视为事故）

### CORE（核心：造型数据 + 同步 + 渲染）
- 新建：`shape/ShapeData.java`、`shape/ShapeHolder.java`、`shape/ShapeStore.java`、`shape/ShapeRenderState.java`、`mixin/GenderPlayerMixin.java`、`mixin/PacketGenderInfoMixin.java`
- 修改：`mixin/GenderLayerMixin.java`（挺拔度 + 每玩家三轴缩放 + 防穿模自动回收）、`Config/FgmPlusConfig.java`（退役 bustScaleGain/bustWidthScaleGain/bustScaleMax 三个键——从 SPEC builder 移除；**保留** bustSizeMax/Breast Height/Depth/Bounce/Floppy 等 FGM 滑条上限键）
- 行为规格：
  1. `PacketGenderInfoMixin`：`@Inject(method="encode", at=@At("TAIL"))` 追加 shape 4 float；`@Inject(method="updatePlayerFromPacket", at=@At("TAIL"))`——**decode 不能在这里做**（updatePlayerFromPacket 拿不到 buffer）。改在两个具体包上拦：对 `PacketGenderInfo.decode` 不可行（抽象类无独立 decode，encode/decode 在基类）。可行方案：mixin 基类 `decode`（若基类有 decode 方法则 TAIL 读 shape，`buffer.readableBytes() < 16` 时保持默认，包一层 try-catch）；若基类没有 decode（子类各自实现），则给 PacketSendGenderInfo/PacketSync 各写一个 TAIL mixin。**先 Read 编译依赖里两个子类的实际结构再定**，报告里说明选了哪条路。读出后 `((ShapeHolder)plr).fgmplus$setShape(...)`（plr 从 packet 的 uuid 字段取：`WildfireGender.getOrAddPlayerById`；若字段 private 用 accessor mixin 或 reflect 一次缓存）。
  2. `GenderPlayerMixin` 实现 `ShapeHolder`（@Unique 字段 fp$shape，默认 `new ShapeData()`）。
  3. 渲染（GenderLayerMixin）：capture 阶段多取 `ShapeData`（从 `((ShapeHolder)plr)` 取——在 renderBreastWithTransforms 里没有 plr，需要另外在 `render` 方法 capture 或把 shape 存到字段；现有 capture 在 renderBreastWithTransforms HEAD，plr 不在参数里 → 在 `render` 方法 HEAD 注入 capture entity 的 shape 到 fp$ 字段）。`fp$applyRealScale` 改为：`stack.mulPose(rotationXYZ(perkiness * PI/180, 0, 0))` 先应用挺拔度，然后 `stack.scale(x*(1+(s-1))*shapeScaleX, y*s*shapeScaleY, z*s*shapeScaleZ)`（s 仍来自 bustSize 的真实增长公式，保留既有 fp$realScale 的 param 反演逻辑；全局 widthGain 键退役后乘数来自 shape）。
  4. 防穿模自动回收：以箱体几何（y 高 5px、下垂角+挺拔角、各轴 scale）估算后背穿透深度，超出部分用 z 向 translate 回收（在 scale 前后选择正确的矩阵序），回收比例写 `ShapeRenderState`。几何估算允许保守（宁可多回收），但**回收量要有上限**（如 ≤0.25 block）避免模型整体飞出。
  5. 本地玩家持久化：join 时机（客户端 PlayerLoggedInEvent 或复用 FGM onPlayerJoin 之后）`ShapeStore.load(uuid)` → setShape。本 mod 不需要自己写 C2S 发包代码：GUI 改完设 `plr.needsSync = true`，FGM 自己的 tick 会 sendToServer，encode TAIL mixin 自动带上 shape 字节。

### GUI（造型工作室界面）
- 新建：`gui/ShapeStudioScreen.java`、`mixin/WildfireCharacterSettingsScreenMixin.java`
- 行为规格：
  1. `WildfireCharacterSettingsScreenMixin`：`@Inject(method="init", at=@At("TAIL"), remap=false)` 加一个 `WildfireButton`「造型工作室 Shape Studio」→ `Minecraft.getInstance().setScreen(new ShapeStudioScreen(this, playerUUID))`。**先 Read 原界面布局算像素预算**：背景 172x144、现有控件到 yPos+100；放不下就改挂在 `WardrobeBrowserScreen`（248x156）上（同样 TAIL mixin），报告里说明选点。
  2. `ShapeStudioScreen extends BaseWildfireScreen`（构造 `(Component, Screen parent, UUID)`，照抄父类模式，X/完成按钮 `setScreen(parent)`）：复用 `WildfireSlider`（显式 min/max 构造）三轴缩放 + 挺拔度；用 `ShapeHolder` 读写本地玩家 shape；onSave 回调里 `ShapeStore.save(uuid, shape)` + `plr.needsSync = true`。左右胸不对称本期不做，界面不出现左右分列。
  3. 防穿模指示：render 循环里读 `ShapeRenderState.lastRecovery`，>0 时画一行小字「Auto-fit: 收回 X%」。
  4. 音效区（对接 SOUND 的 API）：「打开音效文件夹」按钮 → `HurtSoundManager.openFolder()`；「试听」→ `HurtSoundManager.testPlay()`；当前文件状态文本 → `HurtSoundManager.getStatusText()`。这三个静态方法由 SOUND 代理提供——**GUI 按 §4 签名调用**，即使 SOUND 代理没跑完你也要照签名写（整合者保证编译）。
  5. 重置按钮：shape 置默认值 + 存盘 + needsSync。

### SOUND（自定义受伤音效）
- 新建：`sound/HurtSoundManager.java`、`mixin/WildfireEventHandlerMixin.java`
- 行为规格：
  1. 音源：`config/fgmplus/sounds/hurt.ogg`（存在才启用）。**运行时资源注入**方案：把该目录包装成资源包（FolderPackResources / 自实现 Pack + RepositorySource，或 Forge `AddPackFindersEvent`）暴露 `assets/fgmplus/sounds.json`（条目 `custom_hurt`，category player）+ `assets/fgmplus/sounds/hurt.ogg`；默认启用该 pack（包未变化时不强制重载）。文件新增/替换后提供 `reload()`（内部调 `Minecraft.getInstance().reloadResourcePacks()` 或更轻的 `getSoundManager().reload()`，选可行的并说明）。**这是本工作包最大技术风险，若资源包方案在编译期验证不通，退而求其次允许方案 B：完全绕开 FGM 播放路径，`event.setCanceled(true)` 后用 `SoundManager` 的自定义 SoundInstance 直接指向资源位置播**——但注意 FGM 的 handler 在 LOWEST 优先级会再处理已取消事件造成双播，方案 B 必须 mixin 拦截 FGM onPlaySound 整体（@Inject HEAD + isCanceled 检查 + cancelable 逻辑复刻），并在报告里说明取舍。
  2. `SoundEvent CUSTOM_HURT = SoundEvent.createVariableRangeEvent(new ResourceLocation("fgmplus","custom_hurt"))`——照抄 FGM 的不注册模式（WildfireSounds.java:25-27）。
  3. `WildfireEventHandlerMixin`：`@Mixin(WildfireEventHandler.class)`，`@Redirect` 其 `onPlaySound` 里的 `plr.getGender().getHurtSound()` 调用（`Lcom/wildfire/main/GenderPlayer$Gender;getHurtSound()Lnet/minecraft/sounds/SoundEvent;`, remap=false）：文件存在且启用 → 返回 CUSTOM_HURT；否则返回原值。这样 FGM 的 hasHurtSounds 开关、FEMALE 性别判断、音量音调全部自然保留。redirect 前先 javap 或 Read 确认调用点存在（`javap -c -p -classpath <fgm jar>` 可用 `D:\Claude_ds\atomchat-jars` 或 gradle 缓存里的 FGM jar——用 `find ~/.gradle -name "*female*"` 定位；找不到就把 redirect 目标写成 `getHurtSound` 并在报告标注需编译期验证）。
  4. 提供 GUI 三个静态方法（签名冻结）：
     ```java
     public static void openFolder();        // 打开 config/fgmplus/sounds/（不存在则建），Desktop.open，失败静默
     public static void testPlay();          // 本地玩家位置播一次 CUSTOM_HURT（资源未就绪时静默失败）
     public static Component getStatusText(); // 文件存在: "hurt.ogg active" / 不存在: "no hurt.ogg found"
     ```

## 4. 验收（整合者执行，代理自查对照）

1. 编译零错误；jar 内 refmap 含新增注入点的 SRG 条目
2. 混合版本矩阵推演（写进报告即可，不用实测）：无我方 mod 的服务器/客户端不炸（多余字节被忽略/缺失字节走默认）
3. 默认参数下渲染行为与 bustscale 1.0.0 完全一致（shape 全默认 = 原样）
4. code-reviewer 审查通过后部署

---

# 追加契约：1.3.0 轮（用户实测反馈修复）

## 实测坐标系（最高优先级，覆盖此前一切推导）
- FGM 渲染空间：**+z = 向前（凸出方向）、+y = 向下**（用户拖拽实测）
- 防穿模已按此重写（1.2.1）：behindPx = 5·sy·max(0,-sin(α)) + max(0,-offsetZ)·16，α = perk − droop
- 试听无声根因：用户未放 hurt.ogg（预期行为），需兜底

## 工作包 R：真剔除（CPU 多边形裁剪，禁用自定义着色器——Iris/OptiFine 免疫）
- 目标：胸相关箱体（乳房箱/外套/护甲）任何顶点区域不得渲染到躯干背平面之后（z_body < -0.140625，即 -2.25px，含 0.25px 余量）
- 手段：@Inject(HEAD + cancel) 进 `GenderLayer.renderBox`（FGM 私有方法，单名 remap=false；先 javap 3.1 jar 确认存在与签名），对每个 4 顶点 quad 做平面裁剪后按原路径提交：
  1. 平面：body 空间 (0,0,1,-0.140625)；body←view 矩阵在 `fp$captureSize`（renderBreastWithTransforms HEAD）处捕获 `matrixStack.last().pose()` 的**拷贝**存入 fp$ 字段；把 body 空间平面三点变换到当前裁剪空间（用捕获矩阵），得到 view 空间平面
  2. 每个 quad 的 4 顶点先用**当前** matrixStack pose 变换到同一空间，Sutherland–Hodgman 裁剪，结果（0/3/4/5 顶点）以 triangle fan 提交；UV/法线/光照沿裁剪边线性插值；提交用的 pose/normal 矩阵与 FGM 原逻辑完全一致（顶点仍以局部坐标提交，GPU 变换一致）
  3. 提交流水线复用调用者传入的 bufferSource/type（不换 RenderType、不注册 shader）
  4. 兜底：捕获矩阵为 null（异常路径）时不 cancel，走原渲染
- 边界：只许改 `mixin/GenderLayerMixin.java` + 新建 mixin 类；如需 GenderPlayer 之外的新侵入点先在报告里说明。mixins.json 条目写报告，禁改
- 验证义务：javap 3.1 jar 引用 renderBox 真实签名；报告中给出平面数学与退化 case（全保留/全裁剪/切角）

## 工作包 D：GUI 拖拽 + 试听兜底 + 对齐
- `gui/ShapeStudioScreen.java`：
  1. 立绘拖拽（立绘框屏幕区域：x-120..-44，y-59..+73）：左键拖 = offsetX += dx/200、offsetY += dy/200；Shift+拖 = offsetZ += dy/200；滚轮 = 三轴缩放 ±0.05/格；Ctrl+水平拖 = scaleX、Ctrl+垂直拖 = scaleY、Ctrl+Shift+水平拖 = scaleZ。拖拽中实时写 live shape（不落盘），释放时 persist + rebuildWidgets（滑条回读）。拖拽灵敏度符号若与视觉方向不符在报告标注待实测翻转
  2. 立绘框底部黑区（脚下方，y+52..y+70）画一行操作提示小字
  3. 状态文字/Auto-fit 以**面板中心**对齐：cx = (width-248)/2 + 161
- `sound/HurtSoundManager.java`：testPlay 兜底——文件缺失时改播 `WildfireSounds.FEMALE_HURT`（FGM 未注册 SoundEvent 直接 playLocalSound 的既有模式），状态文案区分"已启用/未找到（试听播原版）"；加 LOGGER.debug 诊断行
- 边界：只许改上述两个文件；不碰 GenderLayerMixin/mixin 包/纹理
