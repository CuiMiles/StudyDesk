# StudyDesk · 局域网同步学习工作台

**1.1.0-lan** 将手机 App、手机网页和电脑网页连接到同一个个人学习空间：打开即是完整七天课表，左右滑动切换教学周；今日、背词、论文英语中译英、AI 点评与错题积累均使用服务器上的学习记录。第 1–8 周周三第 3–4 节的体育（羽毛球）课已补全。

Android 客户端使用 **Kotlin 原生连接设置 + WebView + 原生 TTS / 系统文件选择器**。默认启动 `LanActivity`；现有离线 Compose 模块保留在源码中，但不再是新版默认入口。新版不在 APK 中存储 Gemini 密钥，也不维护一份与网页分离的学习进度。

## Android Studio 打开与运行

1. 克隆 `https://github.com/CuiMiles/StudyDesk.git`，在 Android Studio 打开仓库根目录。
2. 使用 JDK 17，安装 Android SDK 36，等待 Gradle 同步，然后运行 `app`。
3. 首次启动输入服务器局域网 IP，例如 **`10.184.17.163`**；默认端口为 `8765`。手机必须与服务器在可互通的局域网。
4. 如果服务运行在 Android Studio 所在电脑上，Android 模拟器输入 `10.0.2.2`。连接远程 Linux 服务器则仍使用服务器真实的局域网 IP。
5. 更换地址在网页“设置 → 连接与使用”中完成。正常连接时不显示额外的原生地址栏；只有连接失败时才出现修改入口。原生英语语音需要设备已安装的 TTS 引擎和英语语音。

在“设置”可以改用户名、隐藏用户名、启用极简模式，并保存多套单词提问模板；这些设置在手机、电脑和 App 间同步。

WebView 页面需要运行 App/模拟器才能预览；它不属于 Compose `@Preview` 静态预览。当前服务器已部署的网站也可以直接在电脑浏览器查看。

## 一并提供的服务端

完整后端、网页、静态题库和词典切片在 [lan-server/](lan-server/README.md)，Python 3.11+，无第三方运行时依赖。进入 `lan-server`，将 `.env.example` 复制为 `.env` 并填入自己的 Key，然后：

```bash
python3 -m webapp.server --host 0.0.0.0 --port 8765
```

多 Key 按“**同一强模型遍历所有 Key → 下一等级模型**”调度；调用次数持久化、按太平洋时间换日。Gemini 额度按项目共享，同项目 Key 不会增加上游配额。系统提示词与参数可以编辑，参考 [完整部署/教学/配额说明](lan-server/webapp/README.md)。

学习记录和真实 Key 不在仓库中。论文 PDF 可用下载脚本获取；40 道练习已经离线打包。服务器默认读取现有 `app/src/main/assets/content.db` 中的预制词汇教学内容。旧版 App 的离线学习记录不会自动合并到新版服务器，迁移前请保留旧备份。

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。CI 也会生成可下载的 Debug APK。

---

## 历史 0.2 离线版本说明

面向学生的原生 Android 学习工作台：西安交通大学课表、IELTS Word List 英文沉浸背词与五步深度教学解析。

以下描述对应保留在仓库中的历史原生 Kotlin + Jetpack Compose 模块，旧版离线行为与新版局域网同步入口不同。

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
  - 先显示单词、自动英语朗读和目标词加粗的例句；判断认识/不认识后再显示英文释义，中文仍默认隐藏；确认下一词才推进。支持多词义、熟词整词跳过、收藏和拼写。
  - 个性化间隔重复：认知值0–3，认识+1、不认识−1不低于0；达到3后默认90天再复习。可设置基础/长期间隔，依据难度和隔天遗忘缩短间隔；重难点词库与四级认知分类，详见 [自适应背词规则](docs/ADAPTIVE-LEARNING.md)。
  - 点击评分后的词卡、认知值、历史遗忘、重难点状态、自动朗读标记全部持久化；无英语离线语音时提供安装入口。
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

当前例句共17497条记录，其中新增474条 Princeton WordNet 原句；2446个词头至少有两条例句。词义按历史标注频次排序，缺失频次时回退原词典顺序；这不是当代雅思精确频率。来源与覆盖见 [example-coverage.json](docs/example-coverage.json)。

目前有 3611 条书目记录、3610 个不同词头、14887 条权威词典释义；47 个词未匹配，见 `docs/dictionary-report.json`。不把未匹配词伪装成有权威释义。五步生成数据尚未批量生成，需模型地址及名称；模拟 API 测试不属于真实内容。

手机问答预留 `QuestionProvider`，默认禁用；章节/搜索词卡可复制问题和释义。不会在 APK 中存储供应商密钥。

验证记录见 [TEST-REPORT.md](docs/TEST-REPORT.md)。真实手机验收仍需在 Android Studio 运行。

## 许可证与数据来源

- 软件源码遵循 MIT 许可证。
- 字典数据来源于 [Open English Wordnet 2025](https://en-word.net/)，基于 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) 许可分发。
- 词表由用户提供并授权转录使用，归属原书编者。

## 重建例句与词义排序

先运行 `tools/import_dictionary.py` 生成基础词库，再用已有 OEWN JSON 目录和 Princeton WordNet 3.0 数据包补充：

```bash
python3 tools/enrich_dictionary.py --wordnet-json /path/to/oewn/json --princeton-zip /path/to/wordnet.zip
```

本次 Princeton 数据来自 [NLTK 分发的 WordNet 数据包](https://raw.githubusercontent.com/nltk/nltk_data/gh-pages/packages/corpora/wordnet.zip)，校验和记录于覆盖报告，许可证随 APK 分发。只按稳定 sense key 匹配原句和历史计数，不替换词典原句中的同义词。[频次含义与局限](https://wordnet.princeton.edu/documentation/cntlist5wn)。
