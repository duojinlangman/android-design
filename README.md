# 单词记忆 App（dancijiyi）

一个基于 Android Studio / Kotlin 的离线单词记忆应用，用艾宾浩斯遗忘曲线安排复习节奏，
包含数据看板、复习队列、单词测试、词库导入与生词本四大模块。

- 包名：`com.example.dancijiyi`
- 语言：Kotlin（无 ViewBinding，`findViewById` + `Fragment(R.layout.xxx)`）
- 数据：原生 `SQLiteOpenHelper`（无 Room、无 KSP、无第三方图表库）
- 最低版本：minSdk 24 / targetSdk 34 / compileSdk 34，Java 17
- 内置词库：四级核心词 156 词 + 日常高频词 69 词，全部带**美式音标**

## 功能

### 1. 数据看板（看板 Tab）
- 问候语与日期、连续打卡天数、待复习数量
- 三张指标卡：待复习 / 今日复习（含今日新增）/ 今日正确率
- 掌握进度环（自绘 `RingProgressView`）：已掌握 / 学习中 / 未开始
- 近 7 天学习趋势双柱图（自绘 `TrendChartView`）：复习次数 + 新增词数

### 2. 艾宾浩斯复习队列（复习 Tab）
- 统计条：待复习 / 今日已复习 / 已掌握
- 「开始复习 50 个单词」批量进入卡片复习
- 队列列表：单词 + 美式音标 + 释义 + 「新词 / 已掌握 / 现在到期」标签
- 复习阶段设计：`5 分钟 → 30 分钟 → 12 小时 → 1 天 → 2 天 → 4 天 → 7 天 → 15 天 → 30 天`，
  走完 9 个阶段视为已掌握，之后 90 天再提醒

### 3. 卡片式复习（`StudyActivity`）
- 轻触卡片揭晓释义，三档自评：
  - 忘了：回到第 0 阶段，5 分钟后再来
  - 有点模糊：阶段不变，间隔减半
  - 记住了：进入下一阶段
- 评分后直接显示「第 N 阶段 · X 后复习」，关闭时弹「结束本轮？」确认

### 4. 单词测试模式（测试 Tab，`QuizActivity`）
- 10 题 / 20 题两种模式，看英文四选一挑中文释义，优先抽到期待复习的词
- 作答即时着色反馈；答对推进一个阶段，答错回到第 0 阶段（计入复习计划）
- 结果页：正确率、答对/答错数、需要再看一遍的错词（含美式音标）

### 5. 词库导入与生词本（词库 Tab，`WordListActivity`）
- 生词本（系统词库，不可删除）+ 内置词库 + 自建词库列表，带掌握进度
- 导入 `.txt / .csv`：支持逗号、制表符、竖线、中文逗号、分号分隔；
  两列 = 单词,释义；三列 = 单词,音标,释义；自动跳过表头；UTF-8 失败回退 GB18030
- 「导入示例」可直接加载内置 `assets/import_sample.csv` 体验
- 添加生词：单词 + 美式音标（可选）+ 释义，跨词库自动去重
- 单词列表：搜索、标星、删除、查看详情、手动标记已掌握 / 重置进度
- 长按词库可重命名或删除（生词本除外）

## 目录结构

```
app/src/main/java/com/example/dancijiyi/
├── DanciApp.kt                  Application，全局 WordRepository
├── MainActivity.kt              底部导航 + 四个 Fragment
├── data.kt                      原有占位类（改名为 AppData，避免与 data 包冲突）
├── data/
│   ├── Models.kt                Word / WordBook / DashboardStats / ReviewPlan 等
│   ├── AppDb.kt                 建表 + 内置词库 seed
│   ├── BuiltInWords.kt          四级核心词、日常高频词（含美式音标）
│   ├── WordRepository.kt        词库/单词/复习调度/统计/出题
│   └── WordImporter.kt          文本解析与导入
└── ui/
    ├── dashboard/               数据看板
    ├── review/                  复习队列
    ├── study/                   卡片复习 Activity
    ├── quiz/                    测试 Activity
    ├── library/                 词库管理 + 单词列表 Activity
    ├── widget/                  RingProgressView、TrendChartView（自绘图表）
    ├── AddWordDialog.kt / WordDetailDialog.kt / Ui.kt
```

## 构建与运行

```bash
# 需要 JDK 17+（Android Studio 自带 JBR 即可）
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.dancijiyi/.MainActivity
```

在本机（Windows + Android Studio JBR 21）实测通过：看板首屏 225 词待复习、复习队列
224 到期 / 今日已复习 1、卡片复习阶段推进、10 题测试与结果页、示例词库导入 10 词、
添加生词与跨词库去重。

## 数据库

`dancijiyi.db`（version 1）：

- `books(id, name, is_system, created_at)`
- `words(id, book_id, word, phonetic, meaning, example, stage, next_review_at, created_at,
  last_review_at, correct_count, wrong_count, mastered, starred)`
  - `UNIQUE INDEX idx_word_book(book_id, word)`、`idx_word_next(next_review_at)`
- `logs(id, word_id, ts, correct, mode)`，`mode` = `review` / `quiz`

生词本即 `is_system = 1` 的那本词库，首次启动时自动创建。
