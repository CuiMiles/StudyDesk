# StudyDesk · 学习工作台

面向学生的原生 Android 学习工作台：西安交通大学课表、IELTS Word List 英文沉浸背词与五步深度教学解析。

原生 Kotlin + Jetpack Compose 开发（非 WebView 套壳），离线优先、隐私保护。

## 模块结构

- **工作台 (Desk)**：显示当前教学周、星期、今日课程卡片、背词进度（待复习、待学新词与学习统计）与快捷入口。
- **课表 (Timetable)**：
  - 严格适配西交大 2026-2027 秋季学期日历（2026-09-14 开学，18周，11节，夏冬作息，法定停课日）。
  - 默认包含羽毛球课（第1–8周周三第3–4节，2号巨构七楼羽毛球场，胡浩），初始化一次，导入时支持自动补入及去重。
  - 支持课程新增、编辑、确认删除；编辑校验不会破坏既有调课关系。
  - 支持单次课程调整（改期、改教室、取消本次）、恢复原安排，以及冲突检测和连续节次合并。
  - 支持旧版微信小程序课表 JSON 导入与完整备份（含调课记录）恢复。
- **背词 (Vocabulary)**：
  - 首本书以用户授权的 `IELTS Word List.txt`（3611 条有效条目）为基础，保留 48 个单元及中文词头注释。
  - 离线集成 Open English Wordnet 2025 数据，存入只读 SQLite (`content.db`)。
  - 坚持“英文释义优先，中文默认隐藏”；支持多词义切换、熟词整词跳过、生词收藏、拼写练习。
  - 艾宾浩斯间隔复习（1/3/7/14/30/60天）；不认识重置且当轮最多重试两次；重试成功当日不提前升级。
  - 预留五步法深度解析展示结构（具体画面、近义词对比、语域语境、典型搭配、联想概念、综合示例）。
- **设置与数据 (Settings)**：
  - 每日新词限额调整（0–100）；
  - Android 系统文件选择器导出/导入全部个人数据（课表、调课、背词进度、熟词、收藏、会话、统计及设置）；恢复先校验，再事务替换；
  - 个人数据双重确认彻底清空；
  - 离线协议与来源声明。

## 工程规范与运行环境

- **IDE**：Android Studio Quail 4（使用支持 AGP 8.13.2 的版本）
- **构建工具链**：
  - AGP (Android Gradle Plugin): `8.13.2`
  - Gradle: `8.13` (内置 Gradle Wrapper)
  - Kotlin: `2.1.20` + Compose Compiler Plugin
  - JDK: `JDK 17` (或兼容的新版 JDK)
  - Compile / Target SDK: `36` (Android 16)
  - Min SDK: `26` (Android 8.0+)
- **CI / GitHub Actions**：`.github/workflows/android.yml` 自动运行 Python 数据工具测试与 Gradle 单元测试并构建 Debug APK。

## 构建与测试

### 1. Python 数据与批处理测试

```bash
python3 -m unittest discover -s tests -v
```

### 2. Gradle 构建与单元测试

```bash
# 运行 core 模块单元测试与 App 构建
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

产出 APK 路径：`app/build/outputs/apk/debug/app-debug.apk`。

### 3. 五步内容离线生成工具 (CLI)

在配置好环境与凭据后运行（默认 dry-run 保护）：

```bash
python3 tools/batch_generation.py --tips sources/5steps_tips.txt --limit 20 --dry-run
```

`sources/5steps_tips.txt` 已同步用户提供的完整提示词。默认仅预演；正式运行需显式 `--no-dry-run`，接口必须由你选择。例如本地免密服务：

```bash
python3 tools/batch_generation.py --api-base http://127.0.0.1:8000/v1 --model YOUR_MODEL --api-key-env '' --limit 20 --no-dry-run
```

远程服务请用 HTTPS 地址，并通过 `--api-key-env YOUR_KEY_VARIABLE` 读取本机环境变量；不要将密钥写入源码。`--limit 0` 处理所有可匹配词，断点续跑核对模型、提示词与输入指纹；401/403 立即停止，临时错误最多三次尝试。每条生成独立提交，完成后更新词库校验和。重新构建安装 APK 后自动更新只读词库，个人记录保持独立。

目前有 3611 条书目记录、3610 个不同词头、14887 条权威词典释义；47 个词未匹配，见 `docs/dictionary-report.json`。不把未匹配词伪装成有权威释义。五步生成数据尚未批量生成，需模型地址及名称；模拟 API 测试不属于真实内容。

手机问答预留 `QuestionProvider`，默认禁用；章节/搜索词卡可复制问题和释义。不会在 APK 中存储供应商密钥。

验证记录见 [TEST-REPORT.md](docs/TEST-REPORT.md)。真实手机验收仍需在 Android Studio 运行。

## 许可证与数据来源

- 软件源码遵循 MIT 许可证。
- 字典数据来源于 [Open English Wordnet 2025](https://en-word.net/)，基于 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) 许可分发。
- 词表由用户提供并授权转录使用，归属原书编者。
