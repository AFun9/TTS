# History Feature Cleanup Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在不改变当前历史页功能的前提下，继续整理共享设置、布局默认值和历史功能文档，使 `MainActivity` 与 `HistoryActivity` 的实现更一致。

**Architecture:** 这轮不再拆模块，只做低风险收敛：历史页改用 `TtsPreferences` 读取/写入播放速度；把布局中的演示性硬编码文本迁到资源；给历史相关核心类补足职责注释，降低后续继续抽象共享播放/文件操作时的理解成本。

**Tech Stack:** Android, Kotlin, XML layouts, SharedPreferences, Gradle

---

### Task 1: 历史页统一使用 TtsPreferences

**Files:**
- Modify: `android/app/src/main/java/com/k2fsa/sherpa/tts/ui/history/HistoryActivity.kt`

### Task 2: 清理布局硬编码展示文本

**Files:**
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/layout/activity_history.xml`
- Modify: `android/app/src/main/res/layout/activity_main.xml`

### Task 3: 历史相关类补充 KDoc

**Files:**
- Modify: `android/app/src/main/java/com/k2fsa/sherpa/tts/util/AudioHistoryStore.kt`
- Modify: `android/app/src/main/java/com/k2fsa/sherpa/tts/ui/history/HistoryAdapter.kt`

### Task 4: 验证

**Files:**
- Verify only
