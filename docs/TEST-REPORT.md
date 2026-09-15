# 验证记录

本文件取代此前仅凭 Python 对照测试给出的“全部完成”结论。Python 中的 `test_core_parity.py` 是独立对照示例，不替代 Kotlin 或 Android 测试。

## 已实现和验证的范围

- 词表解析保留全部 3611 条书目记录（3610 个不同词头）、48 章及来源行。原文件 3722 行不等于 3722 个单词。
- SQLite 中 14887 条 OEWN 释义，3563 个匹配词头、47 个未匹配词头；完整性、外键和文件 SHA-256 校验通过。
- 新增生成流程测试覆盖默认不联网、401 立即停止、模拟 API 写入、内容校验和更新及断点续跑。模拟输出仅写临时数据库，不进入 APK。
- Kotlin 回归测试覆盖备份往返、重启后队列顺序及每日额度、熟词收藏与拼写统计、无效恢复输入。
- Android Robolectric 测试实际使用应用资产和数据库，验证关闭重开、重复答题不重复计数、完整恢复、非法恢复不改动原数据及删除默认课程后不重加。

## 本地执行（2026-09-15）

工具链：Temurin JDK 17、Gradle 8.13、Android SDK 36、Build Tools 35.0.0。工具安装在临时目录；项目不包含本机路径或代理配置。

```sh
python3 -m unittest discover -s tests -v
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
python3 tools/batch_generation.py --dry-run --limit 0
```

最终结果：

- Python：19 项通过。
- Kotlin core：27 项通过，0 失败/跳过。
- Android Robolectric：3 项通过，0 失败/跳过。
- `assembleDebug`：通过，生成 `app/build/outputs/apk/debug/app-debug.apk`。
- `lintDebug`：通过，0 错误；保留依赖新版提示、备份配置建议及状态装箱提示，不为消除提示升级已固定工具链。
- 五步批处理预演：3563 个可匹配词头，未发真实模型请求。
- `git diff --check`：通过。

## 尚需外部输入/设备验证

- 五步解析真实批量生成：等待用户指定模型地址、名称及凭据环境变量。当前 generation 为 0，预演和模拟测试不计为真实生成。
- 47 个未匹配词：公开报告缺口，不使用虚构“权威释义”补齐。
- Android Studio Quail 4 及真实手机：验证字体缩放、横竖屏、键盘、文件选择器和长课名布局；服务器构建与 Robolectric 不等于真机验收。
- 手机在线问答默认关闭，已提供 QuestionProvider 接口和问题/词义复制入口。未接通的服务不会显示假答案。

## 手机验收路径

1. 打开新仓库根目录，Gradle JDK 选择 17，安装 Android SDK 36 并同步。
2. 首次启动查看周三第 1–8 周第 3–4 节羽毛球；第 9 周不出现。
3. 新增/编辑课程，调整单次日期或取消，在管理页恢复；确认基础课程没有被单次调课改写。
4. 完成几次背词及拼写，加入熟词/收藏，退出进程后重开，核对队列与统计。
5. 导出全量 JSON 后清空并恢复，核对课表、调课、熟词、收藏和学习会话；非法文件应保留原数据。
6. 浏览章节或搜索单词，确认中文默认隐藏；熟词跳过后可重学；无释义词不会阻塞拼写。
