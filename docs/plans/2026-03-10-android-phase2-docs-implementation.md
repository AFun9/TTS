# Android Phase 2 Docs Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 Android 工程补齐 README、忽略规则和关键类注释，降低新成员上手成本并减少构建产物误提交。

**Architecture:** 不改业务逻辑，只补工程文档和规范文件。README 聚焦构建、运行、目录和调试；`.gitignore` 只覆盖本地/构建产物；KDoc 仅补关键职责说明，保持简洁。

**Tech Stack:** Android, Kotlin, Gradle, JNI/C++, Markdown, Git

---

### Task 1: 新增 Android README

**Files:**
- Create: `android/README.md`

### Task 2: 添加仓库根 `.gitignore`

**Files:**
- Create: `.gitignore`

### Task 3: 补充关键类 KDoc

**Files:**
- Modify: `android/app/src/main/java/com/k2fsa/sherpa/tts/util/TtsPreferences.kt`
- Modify: `android/app/src/main/java/com/k2fsa/sherpa/tts/util/DocumentCopyHelper.kt`
- Modify: `android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/LatestAudioHandler.kt`
- Modify: `android/app/src/main/java/com/k2fsa/sherpa/tts/ui/main/MainViewModel.kt`

### Task 4: 验证

**Files:**
- Verify only
