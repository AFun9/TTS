# Android App 全面整理（Phase 1 + 标点优化）实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 拆分 MainActivity、统一偏好与字符串、扩展标点切分，提升可维护性且不引入 DI；验收为 smoke test 通过、无回归。

**Architecture:** 新增 TtsPreferences、DocumentCopyHelper、LatestAudioHandler 等小类，MainActivity 委托给它们；所有用户可见文案进 strings.xml；lexicon.cpp 中扩展 IsUnicodePunct 覆盖多语言标点。

**Tech Stack:** Kotlin, Android SDK, JNI (C++), Gradle, SharedPreferences, Data Binding

**设计文档:** `docs/plans/2025-03-10-android-app-reorg-design.md`

---

## Task 1: 新增 TtsPreferences

**Files:**
- Create: `android/app/src/main/java/com/k2fsa/sherpa/tts/util/TtsPreferences.kt`
- Modify: `android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/MainActivity.kt`（本任务仅新增类，下一任务再替换调用）

**Step 1: 创建 TtsPreferences.kt**

在 `android/app/src/main/java/com/k2fsa/sherpa/tts/util/` 下新建 `TtsPreferences.kt`，封装 SharedPreferences，提供：
- `modelPath`, `tokensPath`, `lexiconPath`（String，空表示未选）
- `autoPlay`（Boolean，默认 true）
- `playbackSpeed`（Float，默认 1.0f）

使用与 MainActivity 中相同的 key：`model_path`, `tokens_path`, `lexicon_path`, `auto_play`, `playback_speed`。构造函数接收 `Context`，内部用 `context.getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)`。

**Step 2: 编译验证**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/k2fsa/sherpa/tts/util/TtsPreferences.kt
git commit -m "refactor(android): add TtsPreferences for path and playback settings"
```

---

## Task 2: MainActivity 使用 TtsPreferences 读写偏好

**Files:**
- Modify: `android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/MainActivity.kt`

**Step 1: 替换 prefs 与相关常量**

- 删除 companion object 中的 `KEY_MODEL_PATH`, `KEY_TOKENS_PATH`, `KEY_LEXICON_PATH`, `KEY_AUTO_PLAY`, `KEY_PLAYBACK_SPEED`。
- 将 `private val prefs by lazy { getSharedPreferences("tts_prefs", MODE_PRIVATE) }` 改为 `private val ttsPrefs by lazy { TtsPreferences(this) }`。
- `loadSavedPaths()`：从 `ttsPrefs.modelPath/tokensPath/lexiconPath` 读并 `viewModel.setModelPath` 等；从 `ttsPrefs.autoPlay`、`ttsPrefs.playbackSpeed` 读入成员变量。
- 所有 `prefs.edit().putString(KEY_MODEL_PATH, path).apply()` 改为 `ttsPrefs.modelPath = path`（在 TtsPreferences 中提供 setter）；同理 `putBoolean(KEY_AUTO_PLAY, ...)` → `ttsPrefs.autoPlay = ...`，`putFloat(KEY_PLAYBACK_SPEED, ...)` → `ttsPrefs.playbackSpeed = ...`。

**Step 2: 编译并运行**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/MainActivity.kt
git commit -m "refactor(android): MainActivity use TtsPreferences for settings"
```

---

## Task 3: 新增 DocumentCopyHelper（文件选择与复制到应用目录）

**Files:**
- Create: `android/app/src/main/java/com/k2fsa/sherpa/tts/util/DocumentCopyHelper.kt`
- Modify: `android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/MainActivity.kt`（下一任务中调用）

**Step 1: 创建 DocumentCopyHelper**

- 类接收 `Context`（或 `ContentResolver` + `LifecycleCoroutineScope`），提供 `suspend fun copyToAppDir(uri: Uri, destDir: File): String?`，行为与当前 MainActivity 中 `copyToAppDir` 一致：从 uri 取 DISPLAY_NAME，写入 destDir，返回绝对路径；失败返回 null。
- 可选：提供 `fun openPicker(mimeTypes: Array<String>, onResult: (Uri?) -> Unit)` 封装 `ActivityResultContracts.OpenDocument()` 的 launch，由 Activity 在 onCreate 里注册并传入 onResult（或保持 Activity 持有 ActivityResultLauncher，Helper 只做 copy 逻辑）。若为简化，Helper 仅做 copy，launcher 仍留在 Activity。

**Step 2: 编译**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/k2fsa/sherpa/tts/util/DocumentCopyHelper.kt
git commit -m "refactor(android): add DocumentCopyHelper for document copy to app dir"
```

---

## Task 4: MainActivity 使用 DocumentCopyHelper 执行复制

**Files:**
- Modify: `android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/MainActivity.kt`

**Step 1: 委托复制逻辑**

- 在 MainActivity 中创建 `private val documentCopyHelper by lazy { DocumentCopyHelper(this) }`（若 Helper 需要 scope，传入 `lifecycleScope`）。
- 将 `pickModel`、`pickTokens`、`pickLexicon` 的 `copyToAppDir(uri, modelDir) { path -> ... }` 改为调用 `documentCopyHelper.copyToAppDir(uri, modelDir)`（在 lifecycleScope 内 launch），得到 path 后仍执行 `viewModel.setModelPath(path)`、`ttsPrefs.modelPath = path`、Toast 等。保留现有 ActivityResultLauncher 的注册与 launch。

**Step 2: 编译**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/MainActivity.kt
git commit -m "refactor(android): MainActivity use DocumentCopyHelper for file copy"
```

---

## Task 5: 新增 LatestAudioHandler（播放、分享、导出、重命名）

**Files:**
- Create: `android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/LatestAudioHandler.kt`
- Modify: `android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/MainActivity.kt`（下一任务中委托）

**Step 1: 创建 LatestAudioHandler**

- 接收 `Activity`（或 Context + FragmentActivity），封装：
  - 播放 WAV：给定 path，用 MediaPlayer 播放，支持设置 playbackSpeed（通过 playbackParams），播放结束回调（用于通知 ViewModel 停止状态）。
  - 分享：给定 path，用 FileProvider 生成 Uri，启动 ACTION_SEND 的 Chooser。
  - 导出：给定 path 与 CreateDocument 的 result Uri，将文件写入 contentResolver.openOutputStream(uri)。
  - 重命名：给定 path，弹出 AlertDialog 输入新名称，校验非空与不重名后 renameTo，并返回新 path（或回调）以便 Activity 更新 AudioHistoryStore 与 UI。
- 播放需持有 MediaPlayer 引用并在适当时 release；Activity 的 onDestroy 时由 Activity 调用 handler.release() 或 handler.stop()。接口设计为：`play(path, playbackSpeed, onCompletion)`, `share(path)`, `export(srcPath, destUri)`, `rename(path, onRenamed: (newPath) -> Unit)`，内部用 Activity 的 contentResolver/packageName 做 FileProvider。

**Step 2: 编译**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/LatestAudioHandler.kt
git commit -m "refactor(android): add LatestAudioHandler for play/share/export/rename"
```

---

## Task 6: MainActivity 委托 LatestAudioHandler 并移除内联播放/分享/导出/重命名

**Files:**
- Modify: `android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/MainActivity.kt`

**Step 1: 替换为 Handler 调用**

- 移除 MainActivity 内 `mediaPlayer`、`playbackSpeed` 成员中与播放相关的直接操作（保留 `autoPlayEnabled`、`playbackSpeed` 用于 UI 绑定与传给 Handler）。
- 创建 `private val latestAudioHandler by lazy { LatestAudioHandler(this) }`。
- `playWav(wavPath)` 改为 `latestAudioHandler.play(wavPath, playbackSpeed) { viewModel.stopPlayback() }`。
- 分享、导出、重命名按钮的 onClick 改为调用 `latestAudioHandler.share(latestAudioPath!!)` 等；重命名成功后仍由 Activity 更新 `AudioHistoryStore` 与 `updateLatest`。
- 在 `onDestroy` 中调用 `latestAudioHandler.release()` 或等效方法。

**Step 2: 编译**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/MainActivity.kt
git commit -m "refactor(android): MainActivity delegate play/share/export/rename to LatestAudioHandler"
```

---

## Task 7: 统一字符串到 strings.xml

**Files:**
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/MainActivity.kt`
- Modify: `android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/MainViewModel.kt`（若错误文案改为由 View 根据 state 取 string，则 ViewModel 可只保留 key 或枚举，由 Activity 取 res）

**Step 1: 在 strings.xml 中新增缺失条目**

已有：`status_loading`（正在生成语音…）、`status_success`（生成成功）等。补充或确认存在：
- 与 "生成失败: ..."、"正在播放"、"已停止"、"暂无诊断信息。"、诊断建议文案 等对应的 string name；若已有则复用。
- 为 ViewModel 中错误信息预留：可用 `error_generate_failed`、`error_engine_create_failed` 等，或由 View 层根据 `MainUiState.Error` 的 message 做简单映射到 string res（若保持 ViewModel 返回中文，也可先统一为 string res id 的 key 在 View 层取）。

**Step 2: 替换 MainActivity 中的硬编码**

- 将所有 `"正在生成语音..."`、`"生成成功"`、`"生成失败: ${state.message}"`、`"正在播放"`、`"已停止"` 等改为 `getString(R.string.xxx)`。
- 诊断弹窗中的 "暂无诊断信息。" 及建议文案改为 strings.xml 中的条目并在 Activity 中 `getString(R.string.diagnostics_empty)` 等。

**Step 3: ViewModel 错误文案**

- 方案 A：ViewModel 仍 emit 中文 message，Activity 展示时直接使用（不新增 res）。方案 B：ViewModel 改为 emit 错误类型（枚举或 sealed），Activity 根据类型取 `getString(R.string.error_xxx)`。本任务采用方案 A 即可，仅保证 Activity 与 res 中无硬编码；ViewModel 内错误文案可后续再迁到 res。

**Step 4: 编译**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add android/app/src/main/res/values/strings.xml android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/MainActivity.kt
git commit -m "refactor(android): move UI strings to strings.xml, remove hardcoded text in MainActivity"
```

---

## Task 8: 标点符号优化 — 扩展 lexicon.cpp 中的 Unicode 标点

**Files:**
- Modify: `android/app/src/main/cpp/lexicon.cpp`
- Test: 手动或 `TtsRuEspeakSmokeTest` 跑一次含标点的输入

**Step 1: 扩展 IsUnicodePunct**

- 在 `IsUnicodePunct` 的 `kPunct` 集合中增加常见西里尔/拉丁/全角标点，例如：`«`, `»`, `‹`, `›`, `‚`, `„`, `"`, `"`, `'`, `'`, `´`, ```, `‐`, `‑`, `¡`, `¿`, `·`，以及俄语常用标点（若与现有不重复）。保持现有中文标点不变。
- 或：改为基于 Unicode 码点区间（如 General Category P*）判断，避免硬编码列表过长；若 C++ 侧无现成 Unicode 库，则继续用扩展后的 set。

**Step 2: 编译 native**

Run: `cd android && ./gradlew :app:externalNativeBuildDebug`
Expected: BUILD SUCCESSFUL

**Step 3: 跑 Android 测试**

Run: `cd android && ./gradlew :app:connectedDebugAndroidTest`（需连接设备或模拟器）
Expected: 至少现有 `TtsRuEspeakSmokeTest` 通过。

**Step 4: Commit**

```bash
git add android/app/src/main/cpp/lexicon.cpp
git commit -m "fix(tts): extend Unicode punctuation set in lexicon split for multi-language input"
```

---

## Task 9: 可选 — 权限与诊断抽成 Helper

**Files:**
- Create: `android/app/src/main/java/com/k2fsa/sherpa/tts/util/PermissionHelper.kt`（可选）
- Create: `android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/DiagnosticsDialog.kt`（可选）
- Modify: `android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/MainActivity.kt`

若 MainActivity 行数仍偏高，可将权限申请与诊断弹窗抽成扩展函数或小类；否则可跳过，以 Task 1–8 为 Phase 1 完成标准。

---

## Task 10: 验收与回归

**Step 1: 完整构建**

Run: `cd android && ./gradlew clean :app:assembleDebug :app:connectedDebugAndroidTest`
Expected: BUILD SUCCESSFUL，测试通过（在设备/模拟器可用前提下）。

**Step 2: 确认清单**

- MainActivity 行数较原先明显减少（目标约 250 行以内或更少）。
- 所有用户可见文案来自 `strings.xml`。
- 偏好读写均经 `TtsPreferences`。
- 标点：lexicon 中多语言标点已扩展，smoke test 通过。
- 无新增 lint 错误。

**Step 3: 若全部通过则 Commit 任何未提交的改动并标记 Phase 1 完成**

```bash
git add -A && git status
# 若有未提交文件，按逻辑分 commit
git commit -m "chore(android): Phase 1 reorg verification"  # 如需要
```

---

## 执行方式说明

- **Subagent-Driven（本会话）**：按 Task 1 → Task 10 顺序，每完成一个 Task 做一次小提交，你可随时要求暂停或调整。
- **Parallel Session（新会话）**：在新会话中打开本计划文档，使用 executing-plans 技能在独立 worktree 中批量执行并在检查点复核。

请选择：**1）本会话内按任务执行** 或 **2）仅生成计划，我自行/另会话执行**。若选 1，我将从 Task 1 开始实施。
