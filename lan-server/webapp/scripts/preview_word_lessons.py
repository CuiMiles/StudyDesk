#!/usr/bin/env python3
"""Stage two-word architecture comparisons without enabling the learning UI."""
import argparse
import html
import json
from pathlib import Path

from webapp.db import Database
from webapp.gemini import Gemini
from webapp.lexicon import Lexicon
from webapp.tavily import Tavily
from webapp.word_lesson import WordLesson

LABELS = {"local": "A · 本地词典＋一次生成", "grounded": "B · 定向搜索＋一次生成",
          "verified": "C · 初稿＋搜索＋独立复核"}


def card(item):
    e = html.escape
    l = item["lesson"]
    section = lambda title, body: f"<section><h3>{title}</h3>{body}</section>"
    p = lambda value: "<p>" + e(value) + "</p>"
    spectrum = "".join(p(v["word"] + " · " + v["contrast"]) for v in l["spectrum"])
    contexts = "".join(p(v["example"] + " — " + v["note"]) for v in l["contexts"])
    collocations = "".join(p(v["phrase"] + " · " + v["note"]) for v in l["collocations"])
    checks = "".join(p(v["field"] + "：" + v["claim"] + " → " + v["correction"]) for v in item["checks"])
    sources = "".join(f'<a href="{e(url, quote=True)}" target="_blank" rel="noreferrer">{e(url.split("/")[2])}</a>'
                      for url in item["sources"])
    return (f'<article class="card"><h2>{e(LABELS[item["architecture"]])}</h2>'
            + section("1 · 画面", p(l["image"]))
            + section("2 · 近义词光谱", spectrum)
            + section("3 · 语域与语境", p(l["register"]) + contexts)
            + section("4 · 语义韵与搭配", p(l["tone"]) + collocations)
            + section("5 · 联想网络", p(l["network"]))
            + section("综合示例", p(l["integrated"]["english"]) + p(l["integrated"]["chinese"]))
            + (section("复核发现", checks or p("未提出具体修订")) if item["architecture"] == "verified" else "")
            + (f'<footer>搜索依据：{sources}</footer>' if sources else "") + "</article>")


def preview_html(rows):
    words = list(dict.fromkeys(row["word"] for row in rows))
    nav = "".join(f'<a href="#{html.escape(word)}">{html.escape(word)}</a>' for word in words)
    groups = "".join(f'<section id="{html.escape(word)}"><h1>{html.escape(word)}</h1><div class="grid">'
                     + "".join(card(row) for row in rows if row["word"] == word) + "</div></section>" for word in words)
    return f'''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>词义解析架构预览</title><style>
    :root{{font-family:Inter,"PingFang SC",system-ui,sans-serif;color:#1b2941;background:#e7edf7}}*{{box-sizing:border-box}}body{{margin:0;padding:18px;line-height:1.65}}header{{max-width:1400px;margin:auto}}header h1{{margin:0;font-size:22px}}header p{{font-size:12px;margin:4px 0 10px;color:#52647e}}nav{{display:flex;gap:8px}}nav a{{background:#344967;color:white;padding:6px 14px;border-radius:10px;text-decoration:none}}main{{max-width:1400px;margin:auto}}main>section{{scroll-margin-top:10px}}main h1{{font-size:22px;margin:20px 0 8px}}.grid{{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}}.card{{background:#fbfcff;border:1px solid #ccd9eb;border-radius:16px;padding:15px;box-shadow:0 5px 20px #29466d12}}.card h2{{font-size:16px;margin:0 0 10px}}.card section{{border-top:1px solid #e1e8f2;padding:7px 0}}.card h3{{font-size:12px;color:#395777;margin:0 0 3px}}.card p{{font-size:12px;margin:3px 0;overflow-wrap:anywhere}}footer{{display:flex;gap:6px;flex-wrap:wrap;font-size:10px;border-top:1px solid #dce5f1;padding-top:7px}}footer a{{color:#375985}}@media(max-width:900px){{.grid{{grid-template-columns:1fr}}body{{padding:10px}}}}
    </style></head><body><header><h1>词义解析架构预览</h1><p>两个词，三种路线；仅供验收，尚未进入正式词卡。A 无联网搜索；B 先搜索学习词典；C 对初稿做搜索和独立复核。</p><nav>{nav}</nav></header><main>{groups}</main></body></html>'''


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("words", nargs="*", default=["rigorous", "subtle"])
    parser.add_argument("--architectures", nargs="+", default=["local", "grounded", "verified"])
    args = parser.parse_args()
    runtime = Path(__file__).resolve().parents[1] / "runtime"
    db = Database(runtime / "studydesk.sqlite3")
    lessons = WordLesson(Lexicon(runtime), Gemini(db), Tavily(db))
    rows = []
    for word in args.words:
        for architecture in args.architectures:
            result = lessons.generate(word, ["evidence", "method", "result"], architecture)
            rows.append({"word": word, **result})
            print(word, architecture, "ready", "sources", len(result.get("sources", [])),
                  "checks", len(result.get("checks", [])), flush=True)
    json_path = runtime / "word_preview.json"
    html_path = runtime / "word_preview.html"
    json_path.write_text(json.dumps(rows, ensure_ascii=False, indent=2) + "\n")
    html_path.write_text(preview_html(rows))
    json_path.chmod(0o600)
    html_path.chmod(0o600)
    print("preview files ready", flush=True)
