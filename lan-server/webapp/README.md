# StudyDesk 局域网学习空间

手机优先、电脑可用的个人学习网站，Android 客户端连接同一后端。学习记录集中保存在服务器 SQLite，浏览器和 App 通过同一地址同步。

当前服务器地址：**http://10.184.17.163:8765**。另一块内网网卡为 `10.0.0.5`；地址能否从手机访问取决于实际局域网路由。服务绑定 `0.0.0.0:8765`，单用户、无登录，适用于可信局域网。手机必须能访问服务器网段；服务器内的健康检查不能替代手机实际连通性。

## 已实现

- 打开即是课表：紧凑周次/日期标题旁标出“本周”或“非本周”，下方直接显示七天日期与 1–11 节时间轴；只显示所选周实际发生的课程，课程卡跨越实际节次，点卡片可看详情。手机无需横向滚动看星期；在课表左右滑动切换教学周。今日概览移至第二栏。使用说明集中在设置。
- 2026 秋课表：已录入 18 条课程，包括第 1–8 周周三第 3–4 节体育（羽毛球，胡浩，2 号巨构七楼羽毛球场）；第 7 周周日数据库课；第 18 周周四、周五线上考试；工程伦理第 1–10 周合并 9–11 节，第 11 周只显示 9–10 节。18 周日历、夏冬作息、节次冲突、单次调课/取消/恢复与循环课程隔离；新增、编辑、删除、JSON 导入。
- 显示设置：用户名可自定义或隐藏；极简模式默认开启，减少非必要信息。单词提问可选择和管理多套个人提示词模板，设置及模板随学习数据同步、备份。
- 背词：10,000 词英文释义；从现有 Android `content.db` 补充中文与预制双语深度解析。先回忆后揭示、朗读、三点认知、收藏、熟词本、重难词、拼写与每日复习。一天同一单词只计一次认知评价；跨天遗忘会缩短间隔。
- 学术写作：40 道离线练习，按基础句、进阶表达、短段落分层，使用可迁移的句式，减少论文专有名词；题目不消耗 API。草稿自动保存，可跨设备继续；本机离线草稿提供恢复入口。版本冲突不会静默覆盖。
- AI 点评：四维评分、原句逐项纠错、简明教学、自然改写、通用词汇/句式和具体重写任务。保留每次译文和点评；纠错、词汇、句式自动积累、按间隔复习、置顶和个人备注。
- AI 教练：支持结合当前练习答疑，历史保存在服务器。请求队列持久化，离开页面后任务继续；重启中断的任务明确标失败，避免重复计费。
- 备份：完整 JSON 导入导出；恢复前创建 SQLite 快照；启动时及每日备份，自动清理时保留最近 30 份。恢复学习记录不回滚 AI 调用计数。

## 本地运行

需要 **Python 3.11+**，服务端无第三方 Python 依赖。当前机器可用 `/home/CuiMinghao/envs/multimodal/bin/python`。不要使用系统的旧版 Python 3.6。

在包含 `webapp/`、`miniprogram/`、`docs/` 的项目根目录：

```bash
python3 -m webapp.server --host 0.0.0.0 --port 8765
```

Linux 也可用 `bash webapp/scripts/run.sh`。Windows 可用 `py -3.11 -m webapp.server --port 8765`。不需要 Node 构建、CDN 或公共域名。

如果 Google 请求需要本机已有代理，仅设置服务专用变量：

```bash
STUDYDESK_GEMINI_PROXY=http://127.0.0.1:17891 python3 -m webapp.server
```

### 常驻服务

```bash
python3 webapp/scripts/install_service.py --port 8765 --proxy http://127.0.0.1:17891
systemctl --user status studydesk-web.service
systemctl --user restart studydesk-web.service
journalctl --user -u studydesk-web.service -n 50 --no-pager
```

当前账号已启用 `Linger=yes`，注销后仍运行，并随系统启动。更换安装账号时，如未启用，需要该账号执行 `loginctl enable-linger`。应用不修改已有代理服务或 GPU 工作负载。

## Gemini 密钥、优先级和配额

密钥只读取**项目根目录 `.env`**；不进入网页、Android 包、日志、备份或 Git。支持动态读取 `GEMINI_API_KEY`、`GEMINI_API_KEY2`、`GEMINI_API_KEY3` 等数字后缀（也支持 `GEMINI_API_KEY23`）。根目录只有实际非空且不重复的 Key 才会参与。

```dotenv
GEMINI_API_KEY=填入第一个项目的密钥
GEMINI_API_KEY2=填入第二个项目的密钥
GEMINI_API_KEY3=填入第三个项目的密钥
```

增加新 Key 后在设置点击“重新检测”。也可以运行 `python3 webapp/scripts/probe_models.py`。密钥热读取，模型参数和提示词更改后重启服务。

路由是**模型优先、Key 次之**：先依次尝试最强模型的所有可用 Key，再进入下一模型。高级模型用尽时不会直接跳过其他 Key 的同级模型。候选顺序在 [ai_profiles.json](content/ai_profiles.json)，只有用户[配额快照](../docs/gemini_api_limits.json)包含且真实探测可用的文本模型参与，不用 `latest` 别名绕过计数。Pro 没有出现在正额度表中，因此不擅自启用。

默认策略：完整 Flash 3.8 → 3.7 → 3.6 → 3.5 → 3 → 2.5，随后 Flash Lite 3.5 → 3.1 → 2.5，最后 Gemma 31B → 26B。这是针对语义分析和教学反馈的可调整排序，不是宣称一个统一的跨版本基准榜。实际生成仍可能遇到上游 503；短探针成功不能保证后续完整请求成功。

每次发出请求前用 SQLite 事务保留预算，包含检测、失败和成功调用，防止并发超额；记录 RPM、输入 TPM、RPD 与输出 token。429 区分明确每日额度错误和临时限速，后者冷却后恢复。连接故障和 5xx 临时冷却，探测失败模型停用直到重新检测。计数按 `America/Los_Angeles` 自动换日且处理夏令时。[官方配额规则](https://ai.google.dev/gemini-api/docs/rate-limits)说明额度按项目共享、每日在太平洋时间午夜重置。

**三个 Key 只有属于三个不同项目时才有独立免费额度。** 同项目 Key 请在 `.env` 配置相同分组，以便本地预算也共享：

```dotenv
GEMINI_API_KEY_QUOTA_GROUP=project_a
GEMINI_API_KEY2_QUOTA_GROUP=project_a
GEMINI_API_KEY3_QUOTA_GROUP=project_b
```

首次使用前配置分组。已有计数时改分组会改变本地预算归属，需要合并历史计数；不要在一天中途改组来误判剩余额度。应用无法获知其他程序消耗，也不声称本地余额等于 Google 余额。

系统提示词在 [review_system.txt](content/review_system.txt) 与 [tutor_system.txt](content/tutor_system.txt)。语义 → 语法 → 清晰度 → 适度学术语体；接受同义写法；最多四处优先修改；不强化没有证据的结论；根据上次表现安排重写。Gemini 使用 `systemInstruction`，Gemma 使用等价的前置任务规则。Gemini 3 保留[官方建议的 temperature=1.0](https://ai.google.dev/gemini-api/docs/gemini-3)，完整 Flash 的点评使用 high 思考等级。输出经过字段、分数和原句引用检查，错误结构不会保存为成功点评。

## 论文素材

来源、作者、会议与 Best Paper/Oral 依据见 [papers.json](content/papers.json)；下载校验和见 [downloads.json](content/downloads.json)。当前选择 ResNet（CVPR 2016 Best Paper）、SENet（CVPR 2018 Best Paper）、SimSiam（CVPR 2021 Oral / Best Paper Honorable Mention）、MAE（CVPR 2022 Oral）、MeanFlow（NeurIPS 2025 Oral）。会议奖项依据分别链接到 CVF、会议官网与作者页面。

题目是从论文论述功能提炼的**原创教学改写**，不是逐字原文、官方翻译或新实验结果。参考答案不是唯一正确译法。内容聚焦提出问题、说明目的、对比结果、表达证据范围、消融实验和段落衔接，允许替换方法名/任务名复用。

已下载的公开 PDF 和提取文本保存在 `webapp/runtime/papers/`，保留原版权，不随源码分发。新部署可以运行：

```bash
python3 webapp/scripts/download_papers.py  # 需要 pdftotext，只下载公开论文，不调用 Gemini
```

未下载时仍可用完整题库，并可从来源页面阅读论文。词典署名和许可可在设置中查看；旧词库和既有批处理源文件保持原样。

## Android

Android Studio 打开 `StudyDesk/`（独立 Git 仓库）。新版默认启动 `LanActivity`，使用原生服务器设置 + WebView 展示同一个网站，原生英语 TTS 和系统文档选择器负责朗读、导入/导出。**不是离线原生 Compose 重写**；联网后所有学习记录都是服务器版本，没有另一套 App 专属进度。

首次启动填 `10.184.17.163`，会自动使用 `8765` 端口；也可填完整地址和自定义端口。模拟器连接同一台电脑上启动的服务用 `10.0.2.2`；连接这台远程 Linux 服务器仍用它的局域网 IP。更换地址在网页设置中；连接失败时会出现原生修改入口。旧原生学习数据库不会自动上传；旧版本数据如需迁移，应先导出并单独转换校验，当前网页备份不冒充兼容所有旧 Android 备份。

## 验证

```bash
python3 -m unittest discover -s webapp/tests -v
node --check webapp/static/app.js
```

`webapp/tests/browser_server.py` 启动 **18765 端口的临时测试库**，用确定性点评替身测试整个网页流程，不消耗真实额度。`browser.cjs` 使用 Playwright/Chromium，验证独立浏览器上下文的草稿同步、冲突拒绝、点评归档、错题复习、背词、课表、答疑、备份以及手机/电脑布局。真实 Gemini 探测与实际教学点评另行运行，结果只保存不含密钥的模型状态和样例。

数据库：`webapp/runtime/studydesk.sqlite3`。停止服务后迁移整个 runtime 目录，或使用网页 JSON 导出；运行时不要只复制 SQLite 主文件而丢弃 WAL。自动备份使用 SQLite backup API 取得一致快照。
