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
- 「开始复习 N 个单词」批量进入卡片复习，N 可在 10 / 20 / 50 / 100 之间切换（Tab 页下方「每天复习数量」）
- 队列列表：单词 + 美式音标 + 释义 + 「新词 / 已掌握 / 现在到期」标签
- 复习阶段设计：`5 分钟 → 30 分钟 → 12 小时 → 1 天 → 2 天 → 4 天 → 7 天 → 15 天 → 30 天`，
  走完 9 个阶段视为已掌握，之后 90 天再提醒

### 3. 卡片式复习（`StudyActivity`）
- 轻触卡片揭晓释义，三档自评：
  - 忘了：回到第 0 阶段，5 分钟后再来
  - 有点模糊：阶段不变，间隔减半
  - 记住了：进入下一阶段
- 音标右侧有喇叭按钮，点一下在线发音（首次联网下载，之后走本地缓存）
- 音标下方有「提示」按钮：点一下在线找一张与单词相关的配图，帮助图形记忆；再点「收起提示」收起
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
- 每行右侧喇叭可直接听发音，不用进复习页
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
│   ├── WordImporter.kt          文本解析与导入
│   ├── ReviewQuota.kt           每天复习数量（10/20/50/100）存 SharedPreferences
│   ├── online/                  在线词库：OnlineCatalog / OnlineStore / OnlineUpdater
│   ├── audio/                   单词发音：PronunciationApi / AudioCache / SpeechPlayer / PronPrefs
│   └── image/                   图形记忆：ImageFinder（找图）/ ImageCache（磁盘缓存）
└── ui/
    ├── dashboard/               数据看板
    ├── review/                  复习队列
    ├── study/                   卡片复习 Activity
    ├── quiz/                    测试 Activity
    ├── library/                 词库管理 + 单词列表 Activity + 在线词库 Activity
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

## 单词发音（联网）

### 音源与兜底顺序

1. **有道发音直链**（主音源）：`https://dict.youdao.com/dictvoice?audio=<word>&type=2`
   直接返回 `audio/mpeg`，`type=2` 为美式，国内可直连，不需要任何 key
2. **Free Dictionary API**（备选）：`https://api.dictionaryapi.dev/api/v2/entries/en/<word>`
   从返回 JSON 的 `phonetics[].audio` 里挑 `-us.mp3` 美式直链
3. **系统 TTS**（最终兜底）：没有音频、断网、文件不是合法 MP3 时，用 `TextToSpeech` 美式朗读，
   保证点喇叭一定有声音

### 缓存与播放

- 音频下载后存 `cacheDir/prons/<word>.mp3`，同一个词只联网一次，之后离线也能听
- 下载前粗校验文件头（`ID3` 或 `0xFFEx` 帧同步），避免把错误页当成音频播
- 播放用 `MediaPlayer`；换词、退出页面会 `stop()`，快速翻词不会串音
- 请求失败只写一条 `Pron` 日志，不弹窗打断复习流程

### 交互入口

| 位置 | 表现 |
|---|---|
| 学习卡片（`StudyActivity`） | 音标右侧喇叭，播放中变品牌绿，结束恢复纯黑 |
| 词库单词列表（`WordListActivity`） | 每行右侧喇叭，同一页共用一个播放器，页面销毁时释放 |
| 复习页设置行 | 「进入新单词自动朗读：开/关（点击切换）」，存 `settings/auto_pronounce`，默认开 |

## 图形记忆法（联网配图）

学习卡片上看不到释义时，点「提示」让 App 去找一张和这个词相关的图，先建立画面再回忆词义。

### 图源与兜底顺序

1. **有道词典配图**（主图源）：`https://dict.youdao.com/jsonapi?q=<word>`
   取 `pic_dict.pic[].image`，是词典编辑过的词义配图，语义最贴合（约一半的词有图）
2. **Wikimedia Commons**（备选）：`https://commons.wikimedia.org/w/api.php`
   只接受标题里含单词前 5 个字母的结果，用词干匹配压掉噪声
3. **无图就直说**：两个源都没有时显示「这个词暂时没有配图」，绝不拿无关图片凑数

### 缓存

- 图片存 `cacheDir/mnemonics/<word>.img`，同一个词只联网一次
- 校验文件头（JPEG `FFD8` / PNG `89504E47` / GIF `GIF8` / WebP `RIFF`），防止把错误页当图
- 确认无图的词写一个 `.none` 标记，避免每次点提示都重复请求
- 严格区分「确实没图」和「网络没连上」：断网时绝不写 `.none`，只提示“检查网络后重试”，
  联网后再点同一个词立刻能出图
- 解码时按控件宽度降采样（宽度 >1200px 时 2 倍 inSampleSize），避免大图 OOM

### 交互入口

| 位置 | 表现 |
|---|---|
| 学习卡片（`StudyActivity`） | 音标下方「提示」按钮，点击展开 300×180dp 圆角配图卡片，按钮变为「收起提示」 |
| 换词 | 自动清空上一张图并收起卡片，迟到的网络结果按 token 丢弃，不会串图 |

## 在线词库更新（联网）

词表托管在自己的 GitHub 仓库里，App 通过 GitHub Contents API 读取，不需要另外搭服务器。

### 仓库里的目录

```
online_books/
├── manifest.json       # 词库清单：id、name、version、count、file
├── cet4_plus.json      # 词条数组：[{word, phonetic, meaning, example}, ...]
└── daily_extra.json
```

`manifest.json`：

```json
{
  "updatedAt": "2026-11-21",
  "books": [
    { "id": "cet4_plus", "name": "四级高频扩展", "version": 3, "count": 35, "file": "cet4_plus.json" }
  ]
}
```

### App 侧流程

1. 词库页点「在线词库 · 联网更新」进入 `OnlineBooksActivity`（需要 `INTERNET` 权限）
2. 拉 `manifest.json` → 每本显示 `词数 · 版本 · 已下载 vX / 未下载`
3. 逐本「下载」或点「全部更新」：`OnlineUpdater` 先查本地是否已有对应词库（`OnlineStore` 记着 bookId），
   首次就 `createBook(name)`，之后复用同一个词库
4. 词条写入走已有的 `WordRepository.addWords`：**全库按拼写去重，只补新词**，
   已掌握状态、复习阶段、日志都不会被动
5. 版本号写进 SharedPreferences（`online_books`），下次比对就知道有没有新词

### 发版流程（加词）

1. 在 `online_books/xxx.json` 里追加词条（记得带美式音标）
2. 把 `manifest.json` 里对应 book 的 `version` 加 1、`count` 改成新总数
3. push 到 GitHub master
4. 用户在 App 里点「检查更新」，或下次进入词库页（静默检查，10 分钟内不重复请求）
   就能看到「更新到 vN」，点一下只补新增的词

### 注意事项

- 走 `api.github.com`（本机 `github.com` 主站不通，但 API 可用）；未登录时每小时 60 次限额
- 返回内容是 base64 编码的 JSON，`OnlineCatalog.fetchDecoded()` 先解码再解析
- 想换仓库/分支：改 `OnlineCatalog` 顶部的 `OWNER / REPO / BRANCH / DIR` 四个常量
- 断网、限流、404 都会在页面上给一句话提示，不会崩