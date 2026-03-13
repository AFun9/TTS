# Android App 全面整理 — 设计说明

> 本文档为「重新整理项目」的设计结论，待确认后转为实施计划。

---

## 一、方案对比与推荐

### 方案 A：渐进式（推荐）

**顺序**：Phase 1 代码与架构 → Phase 2 文档与规范 → Phase 3 目录/模块（按需）

**优点**：每步可独立验证、风险小；先减轻 MainActivity 负担、统一字符串与偏好，再补文档和 .gitignore，最后再考虑是否拆模块。  
**缺点**：模块化若要做会晚一些。  
**适用**：希望尽快提升可维护性且不急于多模块。

### 方案 B：模块优先

**顺序**：先拆多 module（如 `:core`、`:tts`、`:app`），再在模块内做架构与文档。

**优点**：边界清晰，后续加功能时模块职责明确。  
**缺点**：改动面大，Gradle、依赖、JNI/CMake 路径要一起动；单 app 目前规模下可能过度。  
**适用**：确定会扩展多端或多产品时采用。

### 方案 C：文档与规范先行

**顺序**：先统一 README、命名、.gitignore、注释，再动代码与架构。

**优点**：先定「规矩」，再重构时有据可依。  
**缺点**：代码痛点（如 MainActivity 过长）延后解决，体感改善慢。  
**适用**：团队协作优先、希望先统一规范时。

---

**推荐采用方案 A（渐进式）**：先做代码与架构整理和字符串/偏好统一，再做文档与规范，最后视需要决定是否做目录/模块调整。

---

## 二、目标与整体优先级

- **目标**：提升可维护性、职责清晰、文档可读、为后续扩展留空间。
- **优先级**：Phase 1（代码/架构）> Phase 2（文档/规范）> Phase 3（目录/模块，可选）。

---

## 三、Phase 1 — 代码与架构

### 3.1 拆分 MainActivity

- **现状**：约 420 行，集中了权限、文件选择与复制、SharedPreferences、MediaPlayer、播放/分享/导出/重命名、最新一条历史 UI、诊断等。
- **目标**：Activity 只做「绑定 + 路由 + 观察 ViewModel」，业务与 IO 下沉。

**具体拆分：**

| 职责           | 处理方式 |
|----------------|----------|
| 权限           | 保留在 Activity，可抽成 `PermissionHelper` 或扩展函数，减少行数 |
| 文件选择/复制  | 抽到 `FilePickerHelper` 或 `DocumentCopyHelper`，在 Activity 中调用 |
| 偏好（路径、自动播放、播放速度） | 抽成 `TtsPreferences`（或 `MainPreferences`），由 ViewModel/Activity 注入或读取 |
| 播放/分享/导出/重命名 | 抽成 `LatestAudioHandler` 或按「播放」「分享/导出」「重命名」拆 2～3 个小类，在 Activity 中委托 |
| 最新一条 UI 状态 | 由 ViewModel 暴露「最新一条」StateFlow，Activity 只做绑定与调用 Handler |
| 诊断弹窗       | 保留在 Activity 或抽成 `DiagnosticsDialog` 工具方法 |

**不引入 DI 框架**：继续在 Activity 里构造 Repository/ViewModel，但偏好、文件复制、播放/分享等通过小类或扩展函数注入，便于单测与阅读。

### 3.2 统一字符串

- 所有面向用户的文案进入 `res/values/strings.xml`，去掉硬编码（如 "正在生成语音..."、"生成成功"、"生成失败: ..." 等）。
- ViewModel 中需要展示的错误/状态文案：通过 string res 或 sealed class + 资源 id 由 View 层取文案，避免 ViewModel 直接持有多语言文本（若需保持 ViewModel 纯逻辑，可由 View 根据 state 类型取 string）。

### 3.3 偏好与路径持久化

- 新增 `TtsPreferences`（或 `MainPreferences`）：封装 `SharedPreferences`，提供 `modelPath`、`tokensPath`、`lexiconPath`、`autoPlay`、`playbackSpeed` 的读写。
- `MainActivity` 启动时从 `TtsPreferences` 读入并同步到 ViewModel；路径/开关变更时写回 `TtsPreferences`。
- 可选：ViewModel 通过构造函数或 setter 接收「偏好提供者」接口，便于测试；若 YAGNI 可先只在 Activity 中读写在 `TtsPreferences`。

### 3.4 标点符号优化

- **现状**：`app/src/main/cpp/lexicon.cpp` 中 `SplitWords` 按空白和标点切分，避免 "word," 导致词典未命中。`IsUnicodePunct` 仅包含硬编码的中文/少量标点（，。！？；：、…—–（）《》【】「」『』），ASCII 标点用 `std::ispunct`；其他语言（如俄语、法语）的标点可能未被识别为分隔符，导致 "слово," 等未正确切分。
- **目标**：扩展并统一标点处理，使多语言输入下词与标点都能被正确切分，且不把标点误当词条查词典。
- **做法**：
  1. **扩展 Unicode 标点集合**：在 `lexicon.cpp` 中扩充 `IsUnicodePunct`（或改为基于 Unicode 类别 / 码点区间），覆盖常见西里尔、拉丁、全角等标点（如 `« » ‹ › ‚ „ “ ” ‘ ’` 及俄语/法语常用标点）。
  2. **统一入口**：保持「按空白+标点切分、标点作为单独 token」的语义；若某标点未在 token_table 中，前端可选择丢弃该 token 或保留由后续逻辑处理，与现有 `TextToTokenIds` 行为一致（未命中词/字符时不写入 id）。
  3. **不在此阶段**：不在 UI 增加「朗读标点」开关（espeak 的 speak_punctuation）；若后续需要再加。

### 3.5 测试

- 现有 `TtsRuEspeakSmokeTest` 等保持不变，Phase 1 完成后跑通并确保无回归。
- 若有精力可为 `TtsPreferences`、`LatestAudioHandler` 等新增单元测试（可选）。
- 标点优化后：用含俄语/中文/英文标点的短句跑一次 smoke test，确认无崩溃、生成正常。

---

## 四、Phase 2 — 文档与规范

### 4.1 文档

- **README**：在 `android/` 或 `android/app/` 下增加 README，说明：如何构建、依赖（ONNX Runtime、espeak-ng 等）、主要包结构、如何运行与调试、常见问题（如 JNI/模型路径）。
- **关键类注释**：对 `TTSEngine`、`TTSRepository`、`MainViewModel`、`TtsPreferences` 等保留或补充简短 KDoc，说明职责与主要用法。

### 4.2 规范

- **命名**：统一现有命名风格（如 UI 状态、资源 id、布局名），与 Android 官方风格一致（如有不一致处列一张小表在 README 或 CONTRIBUTING 中说明）。
- **.gitignore**：在 `android/` 或仓库根目录确保 `.gitignore` 覆盖 `build/`、`.gradle/`、`*.iml`、`local.properties`、`.cxx/` 等，避免构建产物和本地配置被提交。

### 4.3 不做的

- 不在此阶段引入代码风格自动格式化（如 ktlint）的强制 CI，仅建议；若已有配置则保留。

---

## 五、Phase 3 — 目录与模块（可选）

### 5.1 策略

- **默认**：保持当前单 module `app`，仅通过包结构区分 `data`/`engine`/`repository`/`ui`/`util`；Phase 1 中新增的类按职责放入对应包（如 `util` 下 `TtsPreferences`、`DocumentCopyHelper` 等）。
- **若后续需要多模块**：再考虑拆成例如：
  - `:core`：数据类、偏好、通用 util
  - `:tts`：TTSEngine、TTSRepository、EspeakDataHelper
  - `:app`：UI、Application、依赖 :core 与 :tts

Phase 3 是否执行，在 Phase 1、2 完成并稳定后再定。

---

## 六、验收与后续

- **Phase 1 验收**：MainActivity 行数明显减少、所有用户可见文案来自 strings.xml、偏好集中到 `TtsPreferences`、标点切分扩展（多语言标点）且 smoke test 通过、现有 UI 与测试无回归。
- **Phase 2 验收**：README 可让新人按步骤构建并运行；.gitignore 正确；关键类有注释。
- **Phase 3**：按需评估，单独排期。

设计确认后，将使用 writing-plans 技能生成 **Phase 1 的实施计划**（分步任务与文件清单），再进入执行。
