#!/usr/bin/env python3
"""
8-GPU 并行批量生成脚本：
- 调度 GPU 0~7 上的 8 个 llama-server 实例 (端口 18080~18087)
- 从 SQLite content.db 读取 IELTS 词表 (3610 个独立词头)
- 自动断点续跑，跳过已生成单词
- 动态工作队列分发，避免木桶效应
- 专用单线程事务写入 SQLite，杜绝多进程锁库冲突
- 实时记录进度、吞吐速度、耗时与 ETA
- 同步持久化原始 JSON 到 data/generated_fivesteps/ 并刷新 content.sha256
"""

import argparse
import datetime
import hashlib
import json
import os
import queue
import re
import signal
import sqlite3
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DB_PATH = ROOT / "StudyDesk/app/src/main/assets/content.db"
RAW_DATA_DIR = ROOT / "data/generated_fivesteps"
PROGRESS_JSON = ROOT / "docs/batch_generation_progress.json"
PROGRESS_TXT = ROOT / "docs/batch_progress.txt"

NUM_GPUS = 8
BASE_PORT = 18080

PROMPT_TEMPLATE = """你是一名面向高级中文英语学习者的英语词汇导师。

目标不是简单翻译，而是帮助学习者建立准确、鲜明、可长期记忆的英语语感。

目标单词：
{word}

词性与基础释义：
{definition}

已学单词：
{learned_words}

请分别输出：

【English Version】
### Step 1: Imagery Anchor
### Step 2: Semantic-Field Contrast
### Step 3: Register Perception
### Step 4: Semantic Prosody and Collocations
### Step 5: Associative Network
### Step 6: Comprehensive Example

【中文解析版本】
### 第一步：画面锚定
### 第二步：语义场对比
### 第三步：语域感知
### 第四步：语义韵与搭配
### 第五步：联想网络
### 第六步：综合示例

两个版本都完成以上六步。
英文版应使用自然英语重新解释，而不是逐句翻译中文版。
两个版本的事实、词义边界和搭配判断必须保持一致。


### 第一步：画面锚定

为这个词最核心、最值得记忆的常见义项建立一个具象、有冲击力的心理画面。
画面可以包含场景、颜色、质感、声音和动作，可以有戏剧性或幽默感。
但：
- 画面只能帮助记忆，不能反过来扭曲词义；
- 不要因为画面中存在“爆炸、压力、方向”等元素，就声称这些一定属于词义本身；
- 如果这个词有多个明显不同的常见义项，明确指出画面主要对应哪一个义项；
- 不要强行用一个统一故事解释所有义项。
最后用一句话总结：
“核心感觉：……”


### 第二步：语义场对比

选择 3～5 个真正值得比较的近义词或相关词。
根据这些词实际存在的差异，自行选择最有价值的比较维度，例如：
- 核心含义、强度、速度、主动/被动、动作方式、持续性、情感色彩、使用对象、典型语境、正式程度
重要：
不要机械比较所有维度。如果“可逆性”“强度”等维度对这些词没有真正的区分价值，就不要使用。
不要为了让表格整齐而制造不存在的差异。
最后说明：
“什么情况下目标词比其他词更自然？”


### 第三步：语域感知

判断这个词在真正相关的语域中的自然程度（日常口语、一般书面语、新闻、正式写作、学术、文学、专业领域）。
给出 1～2 个典型场景，并解释为什么自然或不自然。
避免“只能”“绝对不能”等过度概括。如果只是偏正式或不典型，就明确说“偏正式”或“不典型”。


### 第四步：语义韵与搭配

分析这个词通常给人的语用感觉。
注意：一个词的不同义项可能具有不同的语义韵，不要强迫所有义项共享同一种“气场”。
列出 2～4 个你高度确定自然、典型的搭配或句型，并说明这些搭配如何帮助理解该词。

【严格禁止】
除非输入中实际提供了语料库数据，否则：
- 不得声称内容来自 COCA、BNC 或其他语料库；
- 不得输出“COCA-backed”等表述；
- 不得编造百分比或频率排名；
- 不得声称某搭配是“最高频”；
- 不确定时宁可采用保守表述。


### 第五步：联想网络

优先从已学单词中选择最多 3～5 个真正相关的词：
{learned_words}
说明它们和目标词属于什么关系（近义、对比、程度变化、因果、上下位、动作链条等）。
组成一个简短的微型故事或逻辑链。如果没有真正相关的已学词，不要强行关联，可以使用相关概念，并明确标注。


### 第六步：综合示例

写一段 50～100 个英文单词的自然语境，正确使用目标词。
随后简要指出：
- 画面锚定如何体现；
- 为什么这里选择目标词而不是近义词；
- 属于什么语域；
- 使用了什么典型搭配；
- 与联想网络有什么关系。


### 【多义词与画面特别约束】

8. 画面锚定只是某个核心义项的记忆辅助，不是词典定义。
   不得从画面中的形状、方向、速度、压力、体积、颜色等细节，
   推导出词义本身必须具有这些属性。

9. 如果目标词有多个常见义项：
   - 先分别识别这些义项；
   - 明确当前画面主要对应哪一个义项；
   - 不要为了制造“统一底层逻辑”而强行把所有义项解释成同一个心理动作。

10. 在写“什么时候这个词比近义词更自然”之前，
    检查所列条件是否真的是该词的语义条件。
    如果只是帮助记忆的典型意象，不得写成必要条件。

11. 对多义词，宁可说：
    “在这个义项中，它强调……”
    而不要轻易说：
    “这个词的本质就是……”


### 最终准确性检查

输出前检查：
1. 是否把“通常”错误写成了“必然”；
2. 是否为了画面鲜明而夸大了词义；
3. 是否为了表格完整而创造了不存在的语义差异；
4. 是否把某个义项的特点错误推广到了所有义项；
5. 是否出现未经提供的语料数据、百分比或频率结论；
6. 是否有搭配只是“语法可能”，却被写成“典型搭配”；
7. 如果不确定，采用保守表述，不要猜测。

准确性优先于戏剧性。
语感清晰优先于华丽表达。
两版本各步骤请保持高信息密度与精炼，避免不必要的冗长赘述。"""

stop_requested = False

def signal_handler(signum, frame):
    global stop_requested
    print("\n⚠️ 接收到退出信号，正在等待当前在跑单词完成后安全退出...")
    stop_requested = True

signal.signal(signal.SIGINT, signal_handler)
signal.signal(signal.SIGTERM, signal_handler)

def compute_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()

def update_db_checksum(db_file: Path, conn=None):
    if conn:
        try:
            conn.commit()
            conn.execute("PRAGMA wal_checkpoint(TRUNCATE);")
        except Exception:
            pass
    checksum = hashlib.sha256(db_file.read_bytes()).hexdigest()
    db_file.with_suffix(".sha256").write_text(checksum + "\n", encoding="utf-8")

def parse_steps_from_markdown(text: str, is_chinese: bool = False):
    steps = {
        "step1": "", "step2": "", "step3": "",
        "step4": "", "step5": "", "step6": "", "step6_map": ""
    }
    if is_chinese:
        patterns = [
            ("step1", r"### 第一步[：:\s]*画面锚定\s*([\s\S]*?)(?=### 第二步|$)"),
            ("step2", r"### 第二步[：:\s]*语义场对比\s*([\s\S]*?)(?=### 第三步|$)"),
            ("step3", r"### 第三步[：:\s]*语域感知\s*([\s\S]*?)(?=### 第四步|$)"),
            ("step4", r"### 第四步[：:\s]*语义韵[与和及]*搭配\s*([\s\S]*?)(?=### 第五步|$)"),
            ("step5", r"### 第五步[：:\s]*联想网络\s*([\s\S]*?)(?=### 第六步|$)"),
            ("step6", r"### 第六步[：:\s]*综合示例\s*([\s\S]*?)(?=$)")
        ]
    else:
        patterns = [
            ("step1", r"### Step 1[：:\s]*[^\n]*\s*([\s\S]*?)(?=### Step 2|$)"),
            ("step2", r"### Step 2[：:\s]*[^\n]*\s*([\s\S]*?)(?=### Step 3|$)"),
            ("step3", r"### Step 3[：:\s]*[^\n]*\s*([\s\S]*?)(?=### Step 4|$)"),
            ("step4", r"### Step 4[：:\s]*[^\n]*\s*([\s\S]*?)(?=### Step 5|$)"),
            ("step5", r"### Step 5[：:\s]*[^\n]*\s*([\s\S]*?)(?=### Step 6|$)"),
            ("step6", r"### Step 6[：:\s]*[^\n]*\s*([\s\S]*?)(?=$)")
        ]

    for key, pat in patterns:
        m = re.search(pat, text, re.IGNORECASE)
        if m:
            content = m.group(1).strip()
            if key == "step6":
                # Check for mapping sub-explanation
                parts = re.split(r"(?:\n|^)(?:体现|说明|维度分析|Points|Analysis|Mapping)[：:\s]*", content, maxsplit=1)
                steps["step6"] = parts[0].strip()
                if len(parts) > 1:
                    steps["step6_map"] = parts[1].strip()
            else:
                steps[key] = content

    return steps

def extract_bilingual_payload(content: str):
    idx_zh = content.find("【中文解析版本】")
    if idx_zh == -1:
        idx_zh = content.find("中文解析版本")

    if idx_zh != -1:
        en_part = content[:idx_zh].replace("【English Version】", "").replace("English Version", "").strip()
        zh_part = content[idx_zh:].replace("【中文解析版本】", "").replace("中文解析版本", "").strip()
    else:
        en_part = content
        zh_part = ""

    en_steps = parse_steps_from_markdown(en_part, is_chinese=False)
    zh_steps = parse_steps_from_markdown(zh_part, is_chinese=True)

    payload = {
        "concrete_image": en_steps["step1"],
        "synonyms_comparison": en_steps["step2"],
        "register_and_contexts": en_steps["step3"],
        "collocations": en_steps["step4"],
        "associations": en_steps["step5"],
        "integrated_example": en_steps["step6"],
        "integrated_example_mapping": en_steps["step6_map"],
        "chinese_explanation": zh_part[:500] if zh_part else "",
        "concrete_image_zh": zh_steps["step1"],
        "synonyms_comparison_zh": zh_steps["step2"],
        "register_and_contexts_zh": zh_steps["step3"],
        "collocations_zh": zh_steps["step4"],
        "associations_zh": zh_steps["step5"],
        "integrated_example_zh": zh_steps["step6"],
        "integrated_example_mapping_zh": zh_steps["step6_map"],
        "en_version": en_part,
        "zh_version": zh_part
    }
    return payload

def writer_thread_func(write_queue, total_words, db_path, raw_dir, start_time):
    conn = sqlite3.connect(str(db_path))
    conn.execute("PRAGMA journal_mode = WAL;")
    conn.execute("PRAGMA synchronous = NORMAL;")

    completed_count = 0
    total_tokens = 0

    while True:
        try:
            item = write_queue.get(timeout=2.0)
        except queue.Empty:
            if stop_requested:
                break
            continue

        if item is None:  # Poison pill
            break

        (word_info, prompt_hash, input_hash, model_name,
         now_str, payload, raw_res) = item

        # Save raw backup JSON
        raw_file = raw_dir / f"{word_info['id']}.json"
        raw_file.write_text(json.dumps(raw_res, ensure_ascii=False, indent=2), encoding="utf-8")

        # Insert into SQLite
        with conn:
            conn.execute(
                """
                INSERT OR REPLACE INTO generation
                (word_id, sense_id, prompt_sha256, input_sha256, model, generated_at, status, payload_json)
                VALUES (?, ?, ?, ?, ?, ?, 'generated', ?)
                """,
                (
                    word_info["id"],
                    word_info["sense_id"],
                    prompt_hash,
                    input_hash,
                    model_name,
                    now_str,
                    json.dumps(payload, ensure_ascii=False)
                )
            )

        completed_count += 1
        total_tokens += raw_res["metrics"]["completion_tokens"]
        elapsed = time.time() - start_time
        speed = total_tokens / elapsed if elapsed > 0 else 0
        wpm = (completed_count / elapsed) * 60 if elapsed > 0 else 0
        rem_words = total_words - completed_count
        eta_sec = (rem_words / (wpm / 60)) if wpm > 0 else 0
        eta_str = str(datetime.timedelta(seconds=int(eta_sec)))

        # Update SHA256 periodically
        if completed_count % 5 == 0 or completed_count == total_words:
            update_db_checksum(db_path, conn=conn)

        # Update progress files
        prog_info = {
            "completed": completed_count,
            "total": total_words,
            "percentage": round((completed_count / total_words) * 100, 2),
            "total_tokens": total_tokens,
            "cluster_speed_tokens_per_sec": round(speed, 1),
            "words_per_minute": round(wpm, 2),
            "elapsed_seconds": int(elapsed),
            "eta": eta_str,
            "last_updated": now_str,
            "last_word": word_info["headword"]
        }
        PROGRESS_JSON.write_text(json.dumps(prog_info, ensure_ascii=False, indent=2), encoding="utf-8")
        
        status_line = (
            f"[{completed_count}/{total_words}] ({prog_info['percentage']}%) | "
            f"词: {word_info['headword']} (GPU {raw_res['gpu_id']}, {raw_res['metrics']['total_time_seconds']}s) | "
            f"集群速度: {round(speed, 1)} t/s ({round(wpm, 2)} 词/分) | 已耗时: {int(elapsed//60)}m | ETA: {eta_str}"
        )
        print(status_line, flush=True)
        PROGRESS_TXT.write_text(status_line + "\n", encoding="utf-8")

        write_queue.task_done()

    update_db_checksum(db_path, conn=conn)
    conn.close()
    print("💾 数据库写入线程已安全结束。")

def worker_thread_func(gpu_id, port, task_queue, write_queue, prompt_template):
    while not stop_requested:
        try:
            word_info = task_queue.get(timeout=2.0)
        except queue.Empty:
            break

        word = word_info["headword"]
        prompt = prompt_template.format(
            word=word,
            definition=f"{word_info['gloss_zh']} ({word_info['definition_en']})",
            learned_words=", ".join(word_info["learned_words"])
        )
        prompt_hash = compute_hash(prompt)
        input_hash = compute_hash(f"{word}:{word_info['sense_id']}:{word_info['definition_en']}")

        payload = {
            "model": "qwen3.8-27b-q4",
            "messages": [{"role": "user", "content": prompt}],
            "stream": True,
            "stream_options": {"include_usage": True},
            "temperature": 1.0,
            "top_p": 0.95,
            "top_k": 20,
            "min_p": 0.0,
            "presence_penalty": 0.0,
            "repeat_penalty": 1.0,
            "max_tokens": 6500,
            "chat_template_kwargs": {"enable_thinking": True, "preserve_thinking": True},
            "reasoning_effort": "medium"
        }

        req = urllib.request.Request(
            f"http://127.0.0.1:{port}/v1/chat/completions",
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )

        success = False
        for attempt in range(3):
            if stop_requested:
                break
            try:
                thinking_chunks = []
                content_chunks = []
                usage_info = {}
                start_time = time.time()
                first_token_time = None

                with urllib.request.urlopen(req, timeout=900) as resp:
                    for line in resp:
                        line = line.decode("utf-8").strip()
                        if line.startswith("data: ") and line != "data: [DONE]":
                            chunk = json.loads(line[6:])
                            if first_token_time is None:
                                first_token_time = time.time()
                            if "usage" in chunk and chunk["usage"]:
                                usage_info.update(chunk["usage"])
                            choices = chunk.get("choices", [])
                            if choices:
                                delta = choices[0].get("delta", {})
                                reasoning = delta.get("reasoning_content")
                                content = delta.get("content")
                                if reasoning: thinking_chunks.append(reasoning)
                                if content: content_chunks.append(content)

                total_time = time.time() - start_time
                ttft = (first_token_time - start_time) if first_token_time else 0
                full_thinking = "".join(thinking_chunks).strip()
                full_content = "".join(content_chunks).strip()

                parsed_payload = extract_bilingual_payload(full_content)
                now_str = datetime.datetime.utcnow().isoformat() + "Z"

                raw_res = {
                    "word": word,
                    "gpu_id": gpu_id,
                    "port": port,
                    "prompt": prompt,
                    "thinking": full_thinking,
                    "content": full_content,
                    "metrics": {
                        "total_time_seconds": round(total_time, 2),
                        "ttft_seconds": round(ttft, 2),
                        "completion_tokens": usage_info.get("completion_tokens", len(full_content.split())),
                        "prompt_tokens": usage_info.get("prompt_tokens", 0)
                    }
                }

                write_queue.put((
                    word_info, prompt_hash, input_hash, "qwen3.8-27b-q4",
                    now_str, parsed_payload, raw_res
                ))
                success = True
                break

            except Exception as e:
                print(f"⚠️ [GPU {gpu_id}] 单词 {word} 处理重试 ({attempt+1}/3): {e}", file=sys.stderr)
                time.sleep(3)

        if not success and not stop_requested:
            print(f"❌ [GPU {gpu_id}] 单词 {word} 失败，重新入队。", file=sys.stderr)
            task_queue.put(word_info)

        task_queue.task_done()

def main():
    parser = argparse.ArgumentParser(description="8-GPU Qwen 单词深度解析并行批处理")
    parser.add_argument("--limit", type=int, default=0, help="处理词数上限 (0 为全量跑完 3610 词)")
    parser.add_argument("--offset", type=int, default=0, help="起始偏移量")
    parser.add_argument("--gpus", type=int, default=8, help="启用的 GPU 数量 (默认: 8)")
    args = parser.parse_args()

    RAW_DATA_DIR.mkdir(parents=True, exist_ok=True)
    PROGRESS_JSON.parent.mkdir(parents=True, exist_ok=True)

    # 1. 检查数据库与已有进度
    conn = sqlite3.connect(str(DB_PATH))
    existing_generated = set(r[0] for r in conn.execute("SELECT word_id FROM generation WHERE status = 'generated'").fetchall())

    # 2. 查询全部词头
    query = """
    SELECT w.id, w.headword, b.chapter, b.position, b.gloss_zh, s.id, s.definition_en
    FROM book_entry b
    JOIN word w ON b.word_id = w.id
    LEFT JOIN sense s ON w.id = s.word_id AND s.id = (
        SELECT s2.id FROM sense s2 WHERE s2.word_id = w.id
        ORDER BY COALESCE(s2.frequency, 0) DESC, s2.source_order, s2.id LIMIT 1
    )
    GROUP BY w.id
    ORDER BY MIN(b.position) ASC
    """
    all_words = conn.execute(query).fetchall()
    conn.close()

    total_in_book = len(all_words)
    print(f"📚 词库总词数: {total_in_book}, 已完成生成: {len(existing_generated)}")

    # 3. 构建待处理列表并附带已学词上下文
    task_items = []
    seen_history = []
    for wid, headword, chapter, pos, gloss_zh, sense_id, defn_en in all_words:
        learned_context = seen_history[-5:] if seen_history else ["seed", "growth", "flourish"]
        seen_history.append(headword)

        if wid in existing_generated:
            continue

        task_items.append({
            "id": wid,
            "headword": headword,
            "chapter": chapter,
            "position": pos,
            "gloss_zh": gloss_zh or "",
            "sense_id": sense_id or f"synset-{wid}",
            "definition_en": defn_en or gloss_zh or "",
            "learned_words": learned_context
        })

    if args.offset > 0:
        task_items = task_items[args.offset:]
    if args.limit > 0:
        task_items = task_items[:args.limit]

    print(f"🎯 本次待处理单词数: {len(task_items)} (使用 {args.gpus} 张 GPU 并行)")
    if not task_items:
        print("✅ 所有单词均已生成完毕！无需额外处理。")
        return

    # 4. 初始化工作队列与写入队列
    task_queue = queue.Queue()
    for item in task_items:
        task_queue.put(item)

    write_queue = queue.Queue()
    start_time = time.time()

    # 5. 启动专用写入线程
    writer_thread = threading.Thread(
        target=writer_thread_func,
        args=(write_queue, len(task_items), DB_PATH, RAW_DATA_DIR, start_time),
        daemon=True
    )
    writer_thread.start()

    # 6. 启动工作线程
    worker_threads = []
    for i in range(args.gpus):
        port = BASE_PORT + i
        t = threading.Thread(
            target=worker_thread_func,
            args=(i, port, task_queue, write_queue, PROMPT_TEMPLATE),
            daemon=True
        )
        t.start()
        worker_threads.append(t)

    print("🚀 集群工作线程已启动，开始全速生成...\n")

    # 7. 等待队列清空或停止
    while not stop_requested and any(t.is_alive() for t in worker_threads):
        time.sleep(1)
        if task_queue.unfinished_tasks == 0 and write_queue.unfinished_tasks == 0:
            break

    # 8. 收尾等待
    for t in worker_threads:
        t.join(timeout=2.0)

    write_queue.put(None)
    writer_thread.join(timeout=10.0)

    print("\n🏁 处理流程结束。")

if __name__ == "__main__":
    main()
