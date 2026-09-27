# 词书上架接口

将 UTF-8 JSON 文件放在本目录并重启服务，`GET /api/vocabulary/books` 会自动列出词书；网页与 Android App 均可切换。格式示例：

```json
{
  "id": "research-english",
  "title": "论文英语",
  "entries": ["mitigate", "robust", "subtle"]
}
```

`id` 使用 3–48 位小写字母、数字或连字符；`entries` 最多 50,000 词。词书内已认识的词复用原有学习记录。新词会创建稳定 ID，英文解释和例句可通过本地词典缓存补齐。服务端词书文件需要随备份一起保留。
