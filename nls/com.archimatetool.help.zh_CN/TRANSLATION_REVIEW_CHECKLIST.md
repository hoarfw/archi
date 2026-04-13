# 帮助文档翻译人工审核清单

## 当前完成状态（2026-02-26）
- 范围：`nls/com.archimatetool.help.zh_CN/nl/zh_CN/help/Text`（96 页）与 `nls/com.archimatetool.help.zh_CN/nl/zh_CN/hints`（109 页）。
- 机器翻译提示文案：已清理（未发现“本页由机器翻译生成”等说明）。
- 纯英文正文节点：`help/Text = 0`，`hints = 0`（按文本节点扫描，忽略脚本/样式）。

## 审核优先级
- P1（高）：术语误译、语义错误、标题错误。
- P2（中）：表达生硬、繁简混用、术语一致性。
- P3（低）：标点、格式、措辞润色。

## 建议审核流程
1. 先处理 `translation_review_report.csv` 中全部 `P1` 项。
2. 再处理 `P2` 项，统一术语与行文风格。
3. 抽检 `help/Text` 每章首末页，确认术语在正文与 `hints` 间一致。
4. 完成后运行 product 打包并在 UI 中打开帮助页做最终验收。

## 重点术语建议（先统一）
- `Driver`：驱动因素（不要翻成“赛车手”）
- `Goal`：目标（不要翻成“入球”）
- `Constraint`：约束（不要翻成“禁锢”）
- `Business Interaction`：业务交互
- `Communication Network`：通信网络
- `View Ref`：视图引用
- `Motivation`：动机（不要翻成“振奋”）

## 审核产物
- 自动审查报告：`nls/com.archimatetool.help.zh_CN/translation_review_report.csv`
- 本清单：`nls/com.archimatetool.help.zh_CN/TRANSLATION_REVIEW_CHECKLIST.md`
