#!/usr/bin/env python3
"""Editorial, offline writing curriculum. No API calls and no copied paragraphs.

Source papers supply rhetorical functions and research context. Sentences below
generalize those functions for practice, rather than claiming new experimental facts.
"""
import json
from pathlib import Path

# paper | level | category | title | Chinese task | editorial English reference | template | short teaching hint
ROWS = r"""
resnet|starter|研究动机|清楚地提出困难|随着模型深度的增加，训练变得更加困难。|Training becomes harder as the model grows deeper.|As [X] increases, [Y] becomes more [adjective].|先找变化关系，再用 as 连接两个分句；注意比较级。
resnet|starter|方法介绍|一句话说明目的|我们提出一种简单的方法，以缓解这一问题。|We introduce a simple approach to alleviate this issue.|We introduce [a method] to [address a problem].|“提出”不必用复杂词；to 后接动词原形，表达目的。
resnet|core|方法介绍|强调设计的作用|这种设计使模型能够从更深的网络中获益。|This design allows the model to benefit from greater network depth.|[Design] allows [model] to benefit from [property].|allow 的宾语后跟 to do；benefit 的常见搭配是 from。
resnet|core|实验结果|用证据支持结论|实验表明，所提出的方法更容易优化。|Our experiments indicate that the proposed approach is easier to optimize.|Our experiments indicate that [finding].|“表明”可以是 show 或 indicate；不要自动升级成 prove。
resnet|core|实验结果|对比两个变化|尽管网络更深，其计算复杂度仍然较低。|Despite its greater depth, the network retains a low computational cost.|Despite [noun phrase], [main clause].|despite 后接名词短语；although 后接完整分句。
resnet|core|实验分析|谨慎排除一个解释|这一现象不能仅用过拟合来解释。|Overfitting alone does not explain this observation.|[Factor] alone does not explain [observation].|alone 限定解释范围；不要把“不能仅用”写成“完全无关”。
resnet|core|研究动机|说明研究价值|这一结果说明，网络深度值得进一步研究。|This result motivates further investigation of network depth.|[Finding] motivates further investigation of [topic].|用 motivates 连接结果与后续研究，不必夸大成重大突破。
resnet|paragraph|段落衔接|从问题到方法|更深的模型往往更难训练。为解决这一问题，我们重新设计了学习目标。实验结果支持了这一设计的有效性。|Deeper models are often harder to train. We therefore revise the learning objective. Experimental evidence supports the effectiveness of this design.|[Problem]. We therefore [action]. [Evidence] supports [claim].|三个短句依次承担问题、方案、证据；often 和 supports 保持主张强度。
senet|starter|方法介绍|说明关注点|我们关注特征之间的关系。|We examine the relationships among features.|We examine the relationship between [A] and [B].|focus on、examine 都可以；relationship 的单复数由表达对象决定。
senet|starter|方法介绍|说明模块用途|这个模块旨在增强有用的特征。|This module is designed to strengthen useful features.|[Module] is designed to [purpose].|区分设计目标和已验证的结果：is designed to 表达前者。
senet|core|方法介绍|解释如何实现目标|我们通过利用全局信息来改善特征表示。|We improve feature representations by incorporating global information.|We improve [target] by [doing something].|by 后使用动名词；information 通常不可数。
senet|core|实验结果|表达收益与代价|该模块只增加少量计算开销，却能改善模型性能。|The module improves performance with little additional computational overhead.|[Method] improves [metric] with little additional [cost].|overhead 在此通常不可数；“少量”不要写成零开销。
senet|core|方法介绍|强调兼容性|这一模块可以方便地集成到现有模型中。|The module can be readily integrated into existing models.|[Component] can be integrated into [system].|被动语态的 can be 后接过去分词，搭配 integrated into。
senet|core|实验结果|表达跨设置的表现|我们在不同模型上观察到了相似的改进。|We observe similar gains across different models.|We observe [consistent/similar] gains across [settings].|across 表示覆盖多个设置；不要将 similar 改为 identical。
senet|core|消融分析|引出设计比较|为了评估各组件的作用，我们比较了不同的设计选择。|We compare design variants to assess the contribution of each component.|To assess [factor], we compare [variants].|each 后的名词通常用单数；交代对比服务于什么问题。
senet|paragraph|段落衔接|组织模块介绍|我们引入一个轻量模块来改善特征表示。它能够与现有模型结合，并且只带来少量额外开销。|We introduce a lightweight module for better feature representations. It integrates with existing models and adds little computational overhead.|We introduce [component] for [goal]. It [compatibility] and [cost].|先说用途，再说兼容性和代价；it 的指代要明确。
simsiam|starter|研究动机|表达研究问题|我们研究一个简单的模型是否能够学习有用的表示。|We ask whether a simple model can learn useful representations.|We investigate whether [subject] can [action].|whether 引导间接疑问，不要使用疑问句倒装。
simsiam|starter|实验结果|说明意外发现|出乎意料的是，这种简单的方法表现良好。|Unexpectedly, the simple approach performs well.|Unexpectedly, [method] [observation].|用 performs well 描述表现；避免写成 performs good。
simsiam|core|方法介绍|表达不依赖某条件|该方法无需使用大批量训练。|The approach does not rely on large training batches.|[Method] does not rely on [requirement].|rely on 后接名词或动名词；不要漏掉 on。
simsiam|core|消融分析|强调关键因素|实验表明，这一步在避免模型失效方面起着关键作用。|Experiments suggest that this step is crucial for preventing model failure.|[Evidence] suggests that [factor] is crucial for [outcome].|crucial for 后接名词或动名词；保留“实验表明”的证据范围。
simsiam|core|实验分析|区分假设与事实|我们提出一个假设来解释这一现象。|We offer a hypothesis to account for this behavior.|We offer a hypothesis to explain [observation].|hypothesis 是假设；不能把提出假设写成证明结论。
simsiam|core|实验分析|说明验证方式|为了检验这个假设，我们设计了一组对照实验。|We design controlled experiments to test this hypothesis.|We conduct [experiments] to test [hypothesis].|test a hypothesis 通常比 verify 更谨慎；说明实验的目标。
simsiam|core|实验结果|避免过度声称最优|这一简单基线在多个任务上取得了有竞争力的结果。|The simple baseline yields competitive results on several tasks.|[Baseline] achieves competitive results on [tasks].|competitive 不等于最佳；results 通常用复数。
simsiam|paragraph|段落衔接|观察、解释与后续验证|一个简单模型也能取得良好结果。我们提出一种可能的解释，并通过对照实验进一步检验它。|Even a simple model can perform well. We propose a possible explanation and investigate it through controlled experiments.|[Observation]. We propose [explanation] and test it through [evidence].|保留 possible 的不确定性；“检验”不自动等于“证实”。
mae|starter|方法介绍|概述训练任务|模型根据可见的输入预测缺失的内容。|The model predicts missing content from the observed input.|[Model] predicts [target] from [input].|用 from 清楚指出信息来源；content 在这里不可数。
mae|starter|方法介绍|列举核心设计|我们的方法包含两个关键设计。|Our approach combines two key design choices.|Our approach consists of [number] key components.|consist of 不用被动语态；数字后的可数名词需要复数。
mae|core|方法介绍|简洁描述流程|训练完成后，我们移除辅助模块，仅保留主模型。|After training, we remove the auxiliary module and retain the main model.|After [stage], we remove [A] and retain [B].|用并列动词描述流程，保持主语一致。
mae|core|实验结果|并列表达两种收益|这一设计既提高了训练效率，也改善了模型性能。|This design improves both training efficiency and model performance.|[Design] improves both [A] and [B].|both...and... 的两部分尽量保持语法平行。
mae|core|实验分析|解释设计的作用|更具挑战性的任务促使模型学习更有用的表示。|A more challenging task encourages the model to learn more useful representations.|[Task] encourages [model] to learn [property].|encourage 后跟宾语和 to do；不要把 encourages 写成 guarantees。
mae|core|实验结果|报告迁移效果|学到的表示能够较好地泛化到其他任务。|The learned representations transfer well to other tasks.|[Representations] generalize well to [new settings].|区别 train on 和 generalize to；不要自动声称所有任务都有效。
mae|core|消融分析|解释超参数实验|我们改变这一比例，以研究它对模型性能的影响。|We vary this ratio to study its effect on performance.|We vary [parameter] to study its effect on [metric].|effect 是名词，affect 是动词；搭配 effect on。
mae|paragraph|段落衔接|把设计和结果接起来|我们将两个简单设计结合起来。这样既减少了计算量，也提升了学习效果。得到的模型能够适用于其他任务。|Combining two simple designs reduces computation and improves learning. The resulting model also adapts well to other tasks.|Combining [A] and [B] [benefit]. The resulting [model] [transfer].|the resulting model 指由前述过程得到的模型；两句之间要有明确联系。
meanflow|starter|研究动机|介绍本文目标|我们的目标是减少生成过程所需的步骤。|We aim to reduce the steps needed for generation.|We aim to reduce [cost] while preserving [quality].|aim to 表达研究目标；不要擅自添加“保持质量”等原句没有的条件。
meanflow|starter|方法介绍|说明训练起点|该模型可以从头开始训练。|The model can be trained from scratch.|[Model] can be trained from scratch.|from scratch 是常见固定搭配；can 表示能力，不等于已完成的事实。
meanflow|core|方法介绍|引出核心思路|核心思路是直接学习目标量。|The central idea is to learn the target quantity directly.|The key idea is to [main action].|is to 后接动词原形；避免用大量名词掩盖核心动作。
meanflow|core|方法介绍|解释理论与训练的连接|我们利用这一关系来指导模型训练。|We use this relationship to guide model training.|We use [principle] to guide [process].|用简单主谓宾说明理论如何服务于方法。
meanflow|core|实验结果|表达缩小差距|我们的方法缩小了简单模型与复杂模型之间的性能差距。|Our approach narrows the performance gap between simple and complex models.|[Method] narrows the gap between [A] and [B].|narrow the gap 不表示消除差距；between 后的两个对象要可比。
meanflow|core|实验分析|区分性能和计算预算|在相同的生成步数下，我们的方法取得了更好的结果。|Our method achieves better results with the same number of generation steps.|Under [matched condition], [method] achieves [comparison].|明确控制的是步数，不要泛化成完全相同的计算成本。
meanflow|core|研究动机|自然引出未来研究|这些结果鼓励我们重新审视现有方法的基本假设。|These findings encourage a reexamination of the assumptions underlying existing approaches.|[Findings] motivate us to revisit [assumption].|underlying 表示支撑某方法的；revisit 表示重新审视而非推翻。
meanflow|paragraph|段落衔接|目标、方案和谨慎结论|我们希望用更少的步骤完成生成。为此，我们设计了一个可从头训练的模型。结果表明，这一方向值得进一步探索。|We seek to generate outputs in fewer steps using a model trained from scratch. The results warrant further exploration of this direction.|We seek to [goal] using [method]. [Evidence] warrants further exploration.|fewer 修饰可数名词 steps；值得探索不等于已解决所有问题。
""".strip()


def build():
    rows, counts = [], {}
    for line in ROWS.splitlines():
        paper, level, category, title, zh, reference, pattern, hint = line.split("|")
        counts[paper] = counts.get(paper, 0) + 1
        section = "Abstract / Introduction"
        if category in ("实验结果", "实验分析", "消融分析"):
            section = "Experiments / Analysis"
        rows.append({"id": f"{paper}-{counts[paper]:02d}", "paperId": paper, "level": level,
                     "category": category, "title": title, "zh": zh, "reference": reference,
                     "pattern": pattern, "hints": [hint], "section": section,
                     "sourceNote": "从论文的论述功能提炼的原创教学改写，已泛化方法名和模型名；不是论文原句，也不是对原文实验结果的逐字翻译。"})
    # Start with accessible sentences across papers before progressing to longer writing.
    order = {"starter": 0, "core": 1, "paragraph": 2}
    rows.sort(key=lambda x: order[x["level"]])
    target = Path(__file__).resolve().parents[1] / "content/exercises.json"
    target.write_text(json.dumps(rows, ensure_ascii=False, indent=2) + "\n")
    print(f"Built {len(rows)} offline exercises from {len(counts)} verified papers")


if __name__ == "__main__":
    build()
