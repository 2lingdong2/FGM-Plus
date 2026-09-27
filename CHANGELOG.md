# Changelog

## v1.20.1-1.4.1

**修复**

- 造型工作室状态文本超长（多个音效文件名）时不再溢出面板：按面板宽度自动换行，最多两行、超长部分以省略号截断
- 「试听」不再每次都触发资源包重载：文件集与已加载内容一致时**立即播放**（不闪资源条、不打断游戏音频）；仅当文件有新增/删除/改名时才走完整重载。同名替换文件内容属极少数场景，按 F3+T 刷新即可
- 全部界面文案接入语言文件（`fgmplus.studio.*`，简体中文 + English），语言随游戏设置切换

----

**Fixed**

- The Shape Studio status text (long sound-file lists) no longer overflows the panel: it wraps to the panel width, at most two lines with an ellipsis tail
- The preview button no longer triggers a resource-pack reload every time: when the loaded set matches the directory it plays **instantly** (no overlay flash, no audio interruption); a full reload runs only when files were added, removed or renamed. A same-name content swap keeps the cached sound until F3+T
- All UI text now goes through language keys (`fgmplus.studio.*`, Simplified Chinese + English) and follows the game language

## v1.20.1-1.4.0

**新增**

- **防穿模治本：背面削平（顶点钳制）**——如「削平背端的尖角」设想：胸部模型任何超出躯干背面基准面（+2.125px）的顶点被直接压回到该面上，越界部分呈现为贴背的压扁面而非穿出的尖角；正面几何零改动，胸部完全保持与正面的贴合。外套层与胸甲随本体一起削平且钳面依次外移，不出现层次错位或深度闪烁
- 触发范围（按 FGM 3.1 实值核算）：原版滑条拉满（bust 0.8）静止顶点 +1.61px、FGM 默认（bust 0.6）+2.04px，均不触发；更小的 bust、摇晃动画的后摆与大 scaleZ 会触发——而这些正是原版几何本就穿出背面的场景，削平即治本
- 几何基准（躯干背面变换链）已由诊断面板实机验证通过
- **退役「自动回收」平移辅助**：它移动整个模型、破坏正面贴合（与「贴合正面」诉求冲突），其职责已由逐顶点削平完全接管；造型工作室的 Auto-fit 指示随之移除

----

**Added**

- **The real anti-clip cure: back-face flattening (vertex clamping)** — as envisioned ("flatten the back corners"): any breast vertex beyond the torso-back plane (+2.125 px) is pulled straight onto it, so the overflowing part renders as a surface pressed against the back instead of spikes poking through it; front-side geometry is untouched. Jacket-wear and chestplate armor flatten along, each on a hair-wider plane so layers never z-fight
- Trigger envelope (recomputed from FGM 3.1 values): full vanilla slider (bust 0.8) tops out at +1.61 px static, FGM's default (bust 0.6) at +2.04 px — neither triggers; smaller busts, the bounce animation's backward swing and large scaleZ do — precisely the configurations where the vanilla geometry already pokes out of the back
- The geometric basis (torso-back transform chain) was verified in-game via the diagnostic panel
- **Retired the translate-based "auto recovery" aid**: it moved the whole box toward the front and broke the front fit the user wants; its job is now done per-vertex by the clamp. The studio's Auto-fit indicator is gone with it

## v1.20.1-1.3.5

**修复**

- **坐标方向纠错（重要）**：借助诊断面板的实测反馈确立——模型空间**正面 = -z、背面 = +z**（面板画在 z=-2.25px 时贴在胸口正面；1.3.0 的裁剪保留 z>-2.25px 时恰好切掉的也是正面，两条实测互证）。诊断面板已翻转到 z=+2.25px、正确贴住躯干背面
- **自动回收方向修正**：1.2.x 以来的回收平移方向一直是反的（+z 实际朝背面、加剧穿模而非缓解），现翻转为朝正面（-z）回收——与你手动「把胸部往正面推」的缓解方向一致
- Z 位置滑条修正：UI 现在真实做到「右滑 = 向前凸出」（此前文案如此、行为相反）；已存档数值语义不变、自动兼容

----

**Fixed**

- **Coordinate direction correction (important)**: the diagnostic panel's in-game feedback established that in model space the chest front is -z and the back is +z (the panel at z = -2.25 px rendered on the FRONT of the chest, and the 1.3.0 clip keeping z > -2.25 px cut away the front — two measurements corroborating each other). The panel is now flipped to z = +2.25 px, hugging the torso back correctly
- **Anti-clip recovery direction fixed**: since 1.2.x the recovery translate pointed the wrong way (+z actually faces the torso back, aggravating clipping instead of relieving it); it now pulls toward the chest front (-z) — the same direction as your manual "push the chest forward" mitigation
- Z-position slider fixed: the UI now genuinely behaves as "drag right = protrude forward" (the text claimed so, the behavior was inverted); stored values keep their meaning and migrate transparently

## v1.20.1-1.3.4

**更改**

- 防穿模自动回收上限从 4px 放宽到 6px（更大数值下的后背穿模减轻；根治方案见下）
- 造型工作室新增「诊断平面」开关：开启后以绿色半透明面板画出 FGM Plus 计算的躯干背面基准面——它是下一代防穿模方案（背面削平/顶点钳制，贴合正面、不溢出背部）的几何基准；面板若从任意角度（站立/下蹲/转身/游泳）都贴合背部，即证明变换链正确、钳制可信

----

**Changed**

- The anti-clip auto-recovery cap is widened from 4 px to 6 px (less back poking at extreme values; the real cure is below)
- The Shape Studio gains a "diagnostic plane" toggle: a translucent green panel showing FGM Plus's computed torso-back plane — the geometric basis of the next-generation anti-clip design (back-face flattening / vertex clamping: keep the chest fitted to the front while never poking out of the back). If the panel hugs the torso back from every angle (standing, sneaking, turning, swimming), the transform chain is proven and the clamp can be trusted

## v1.20.1-1.3.3

**修复**

- 自定义受伤音效改为「目录即音效组」：`config/fgmplus/sounds/` 里的**每一个** `.ogg` 都会生效，播放时随机选一个（与 FGM 原版 female_hurt 挂两个 ogg 的行为一致）；不再限定 `hurt.ogg` 单文件名，中文/空格/大写文件名均支持
- 热重载真正生效：试听按钮现在执行完整资源重载（自动识别新增/删除/改名文件，无需重启游戏）；状态文字相应改为三态（未找到 / 待生效 / 已启用 N 个音效）
- 根因备注：此前 `SoundManager#reload()` 只清音频缓存、从不重建 sounds.json 条目表（1.20.1 字节码实测），因此旧实现的轻量重载对文件增删永远无效

----

**Fixed**

- Custom hurt sounds are now directory-based: **every** `.ogg` in `config/fgmplus/sounds/` plays (one picked at random per play, mirroring FGM's own female_hurt with its two damage oggs); the single fixed `hurt.ogg` name is no longer required, and Chinese/space/uppercase file names are supported
- Hot reload actually works now: the preview button performs a full resource reload that picks up added/removed/renamed files without restarting the game; the status text gained a third state (not found / pending reload / N sound(s) enabled)
- Root-cause note: `SoundManager#reload()` only clears the audio buffer cache and never rebuilds the sounds.json entry map (verified in 1.20.1 bytecode), so the old lightweight reload could never pick up file changes

## v1.20.1-1.3.2

**修复**

- 回退 1.3.0 引入的后背真剔除：其裁剪平面几何基于未实机验证的空间推导，实测会把原版/默认造型的胸部整体裁没（表现为模型内嵌躯干、正面无法突出）。渲染路径恢复到 1.2.1 已验证行为（真实缩放 + 偏移 + 平移式防穿模自动回收）
- 1.3.1 的新版工作室预览、试听兜底等其余改动不受影响

----

**Fixed**

- Reverted the true back-clip culling introduced in 1.3.0: its clip-plane geometry rested on a spatial derivation never validated in-game, and in practice it clipped away whole default-shape breasts (model sinking into the torso, front unable to protrude). The render path is back to the verified 1.2.1 behavior (real scaling + offsets + translate-based auto recovery)
- The 1.3.1 studio preview, sound preview fallback and all other changes are unaffected

## v1.20.1-1.3.1

**更改**

- 造型工作室预览改为 FGM 外观设置同款：右侧半透明面板 + 左侧真身大比例实时预览，不再使用框式立绘与专属背景纹理
- 回退 1.3.0 的立绘拖拽手势，调整方式回归滑条

----

**Changed**

- The Shape Studio preview now matches FGM's breast customization screen: translucent panel on the right and a large live preview of the actual player on the left, replacing the boxed portrait and the dedicated background texture
- Reverted the 1.3.0 portrait drag gestures; shaping is back to sliders only

## v1.20.1-1.3.0

**新增**

- 后背穿模真剔除：对胸部/外套/护甲的每个面片对躯干背面平面做 CPU 多边形裁剪，极端数值下从背后也看不到模型穿出（不依赖着色器，Iris/OptiFine 环境同样生效）；原有自动回收保留作辅助
- 造型工作室立绘直接拖拽（Blender 式）：左键拖=左右/上下移动、Shift+拖=前后、滚轮=三轴整体缩放、Ctrl+拖=宽度/高度、Ctrl+Shift+拖=深度；立绘框底部有操作提示，拖拽实时预览、松手落盘同步，滑条保留共存
- 试听兜底：未放 hurt.ogg 时试听按钮改播 FGM 原版女声，按钮永远有反馈；新增诊断日志

**修复**

- 状态文字与 Auto-fit 指示改为面板中心对齐（此前偏左）

----

**Added**

- True back-clip culling: every breast/jacket/armor quad is CPU-clipped against the torso-back plane (Sutherland–Hodgman), so extreme values never show the model poking out of the back — no custom shaders involved, works identically under Iris/OptiFine; the existing auto-recovery stays as a secondary aid
- Blender-style direct dragging in the Shape Studio portrait: left-drag = move X/Y, Shift+drag = depth, wheel = uniform scale, Ctrl+drag = width/height, Ctrl+Shift+drag = depth scale; live preview while dragging, persisted and synced on release; sliders remain side by side
- Preview fallback: with no hurt.ogg present the preview button plays FGM's default female hurt sound, plus diagnostic logging

**Fixed**

- Status text and the auto-fit indicator are now centered on the panel instead of the screen

## v1.20.1-1.2.1

**修复**

- 衣柜入口按钮：按实机 3.1 纹理实测绘制与 FGM 面板无缝衔接的延伸板，按钮不再悬在框外
- 造型工作室换用专属背景纹理（FGM 女性衣柜纹理加高面板），所有控件都在边框内
- 位置 Z 语义修正：正值 = 向前凸（与实测一致），负值 = 向身体收回，tooltip 同步更正
- 防穿模按实测坐标系重写：下垂把箱体底边甩向背后、挺拔度抵消、负 Z 偏移计入穿透；回收方向翻转为向前拉，并给 ±0.25 格免费额度（微调不被抵消）
- 音效状态文字、试听 tooltip 中文化

----

**Fixed**

- Wardrobe entry button: draws a seamless panel extension matched to the actual 3.1 textures (measured from the shipped jar), no longer floating outside the frame
- Shape Studio uses a dedicated background texture (FGM's female wardrobe texture with a taller panel); every control now sits inside the frame
- Position Z semantics corrected: positive = protrude forward (matches in-game behavior), negative = pull back toward the torso; tooltip updated
- Anti-clip rewritten on the verified coordinate system: droop swings the box's bottom edge backward, perkiness counters it, negative Z offsets count as penetration; the recovery direction is flipped to pull forward, with a ±0.25-block free allowance so small adjustments are never cancelled
- Hurt-sound status text and preview tooltip localized to Chinese

## v1.20.1-1.2.0

**新增**

- 造型数据新增三轴位置偏移（±1 格）：身体空间整体平移胸部模型，正 Z 向身体收回（贴合不靠压扁深度）、负 Z 向前凸，与缩放互不干扰；造型工作室右侧新增"位置"滑条列（与缩放列并排）
- 同步格式升级为 7 float：1.1.x 客户端读前 4 个 float 忽略尾部，新客户端读旧包自动补默认偏移，混合版本互不炸
- 位置偏移为用户显式调整，不参与自动防穿模（防止微调被抵消；过度后移从背面露出时实时可见，工作室界面随时可调）

----

**Added**

- Three-axis position offsets (±1 block) in the shape data: shifts the whole breast model in body space — positive Z pulls it back toward the torso (a tighter fit without flattening depth), negative Z pushes it forward; the studio gains a "position" slider column beside the scale column
- Sync format upgraded to 7 floats: 1.1.x clients read the leading 4 floats and ignore the tail, new clients reading old packets fall back to default offsets — mixed versions coexist safely
- Position offsets are explicit user adjustments and are exempt from the automatic anti-clip (so small fine-tuning is not cancelled); an excessive backward pull visibly exits the back and can be corrected live in the studio

## v1.20.1-1.1.1

**修复**

- 造型工作室改为 FGM 衣柜式布局（248x156 背景、右侧控件列、左侧角色立绘），美术风格与原版统一
- 滑块拖动中实时应用到模型（立绘与第三人称同步刷新），松手才落盘同步；修复"首次修改后才生效、之后不再更新"的编辑丢失
- 衣柜入口按钮移入右侧按钮流（按性别对齐最后一个按钮下方），不再悬在空白处
- 三轴缩放下限 0.5 → 0.1，胸围拉满后可把前凸压回贴合身体

----

**Fixed**

- Shape Studio now uses the FGM wardrobe layout (248x156 background, right-hand control column, entity portrait on the left), visually matching vanilla FGM screens
- Slider edits apply to the model in real time while dragging (portrait and third-person refresh live); disk write and sync happen on release, fixing edits only applying on the first change
- The wardrobe entry button now sits in the right-hand button flow (aligned below the last button per gender) instead of floating in empty space
- Per-axis scale lower bound lowered from 0.5 to 0.1, enough to pull the front protrusion back in at max bust size

## v1.20.1-1.1.0

**新增**

- 每玩家造型数据（挺拔度 + 三轴缩放）接入 FGM 同步管线，你的造型所有人可见，持久化于 `config/fgmplus/shapes/<uuid>.json`
- 造型工作室 GUI：衣柜界面新增入口按钮，内含宽/高/深缩放（0.5–3.0）与挺拔度（-30°–+60°）滑条、重置、防穿模回收指示、受伤音效管理
- 垂直角度/挺拔度：新增绕 X 轴的用户角度，可抵消或反转原版写死的 35° 下垂，实现"更挺"
- 自动防穿模：缩放/角度超出安全范围时自动向前回收穿透量（上限 4px），工作室界面实时显示回收比例
- 自定义受伤音效：把 `hurt.ogg` 放进 `config/fgmplus/sounds/` 即替换 FGM 的女性受伤音（本地生效，试听按钮可预览）

**更改**

- mod 更名 Bust Scale → FGM Plus（modid `bustscale` → `fgmplus`，配置文件 `fgmplus-common.toml`）
- 退役全局缩放配置键 `bustScaleGain` / `bustWidthScaleGain` / `bustScaleMax`，缩放改由每玩家造型数据接管
- 超上限真实缩放的宽度（X 轴）只由造型滑条控制，默认与 1.0.0 一致不变宽

**说明**

- 未安装本 mod 的客户端/服务器不受影响：多余同步字节被忽略，对方看到原版造型

----

**Added**

- Per-player shape data (perkiness + per-axis scaling) rides FGM's sync pipeline so everyone sees your shape; persisted to `config/fgmplus/shapes/<uuid>.json`
- Shape Studio GUI: new entry button in the wardrobe screen with width/height/depth scale (0.5–3.0) and perkiness (-30°–+60°) sliders, reset, anti-clip recovery indicator, and hurt sound management
- Vertical angle/perkiness: a user-controlled X-axis rotation that can cancel or reverse the hardcoded 35° droop
- Automatic anti-clip: when scaling/angle would poke through the torso, the excess is pulled back automatically (capped at 4px), with a live recovery readout in the studio
- Custom hurt sound: drop a `hurt.ogg` into `config/fgmplus/sounds/` to replace FGM's female hurt sound (local; preview button included)

**Changed**

- Renamed Bust Scale → FGM Plus (modid `bustscale` → `fgmplus`, config file `fgmplus-common.toml`)
- Retired the global scale keys `bustScaleGain` / `bustWidthScaleGain` / `bustScaleMax`; scaling is now per-player shape data
- Sideways (X) growth of the above-cap real scale is controlled only by the shape slider and stays off by default, matching 1.0.0

**Notes**

- Clients/servers without this mod are unaffected: extra sync bytes are ignored and they see the vanilla look

## v1.20.1-1.0.0

**说明**

- Bust Scale 首个独立版本。前身为 Rinko1231 的 Female Plastic Surgery（已停更），在其 Forge 1.20.1 版基础上重写并独立发布；modid 为 `bustscale`，配置文件 `bustscale-common.toml`。

**新增**

- 分轴真实缩放：超过原版上限（0.8）的胸围对胸部模型施加真实缩放（围绕贴胸壁的角落缩放，接触面不动），并冻结两处"位置造假"位移（深度下沉与垂坠偏移）
- 深度（前后）与高度（垂直）随缩放全量增长；宽度（左右）默认不放大（每侧已占满半个躯干宽度，放大必溢出轮廓）
- 配置 `Real Bust Scale`：`bustScaleGain`（默认 1.0）、`bustScaleMax`（默认 3.0）、`bustWidthScaleGain`（默认 0）
- 保留原附属功能：FGM 滑条上限/下限放宽（胸围、偏移、弹跳/柔软倍率），均可在配置调整
- 盔甲与外套覆盖层随缩放同步放大，避免穿模
- 依赖 `wildfire_gender`（FGM）≥ 1.20.1-3.0.1

----

**Added**

- First standalone release of Bust Scale, rewritten from Rinko1231's Female Plastic Surgery (upstream unmaintained) on its Forge 1.20.1 version; modid `bustscale`, config file `bustscale-common.toml`.
- Axis-split real scaling: bust sizes above the vanilla cap (0.8) now apply a true scale to the breast model (pivoted on the chest-contact corner), freezing the base mod's two fake positional offsets (depth sink and hang shift)
- Depth (front/back) and height scale fully; width stays fixed by default (each breast already spans the full half-torso width, so sideways growth overflows the silhouette)
- `Real Bust Scale` config: `bustScaleGain` (default 1.0), `bustScaleMax` (default 3.0), `bustWidthScaleGain` (default 0)
- Keeps the original addon's features: relaxed FGM slider limits (bust size, offsets, bounce/floppy multipliers)
- Armor and jacket overlays scale together with the breast model
- Requires `wildfire_gender` (FGM) >= 1.20.1-3.0.1
