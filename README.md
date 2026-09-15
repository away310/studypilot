# StudyPilot — 个人学习知识库问答系统

Java 21 / Spring Boot 3.5 / Spring AI 的个人 RAG 工程实践：上传笔记，混合检索，再根据参考片段生成回答。当前是固定检索生成管线，没有自主工具规划或多轮 Agent 执行。

## 功能

- Markdown / TXT / 文本型 PDF 解析，按标题与字符窗口切分。
- 标题链和正文共同参与 embedding 与 Lucene BM25 检索。
- SQLite 保存文档、片段和向量；余弦扫描与 BM25 加权融合。
- 同名上传：先完成向量化，再事务替换旧文档；模型调用失败保留旧版本。
- 关键词索引在数据库提交后刷新；重建采用新索引替换，刷新失败时下次检索重试。
- SSE 流式回答、停止生成、引用原文展开、多文件依次上传。
- 相似度不足直接拒答；生成结束检查引用编号，缺失或越界则不接受该答案。

## 启动

需要 Java 21、Maven，以及 DashScope API Key。

PowerShell：

```powershell
$env:DASHSCOPE_API_KEY="你的 Key"
mvn spring-boot:run
```

Bash：

```bash
export DASHSCOPE_API_KEY="你的 Key"
mvn spring-boot:run
```

打开 http://localhost:8080/ 。默认导入 `kb/` 下 8 篇示例笔记。模型调用会使用你的 API 配额。
文件上传上限默认 20MB，可在 `application.yml` 调整。

**已有数据升级：** 此次检索加入了标题语义，旧向量不会自动变化。请重新上传已有文档以重新生成向量，再运行评测。启动导入会跳过同名文档。更换 embedding 模型后也需要重新导入全部文档；向量维度不匹配会显式报错，同维度的不同模型仍不能混用。

## API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/kb/documents` | multipart 单文件上传，页面依次发送多个文件 |
| GET | `/api/kb/documents` | 文档列表 |
| DELETE | `/api/kb/documents/{id}` | 删除文档及向量 |
| POST | `/api/chat` | JSON：`{"question":"问题"}` |
| POST / GET | `/api/chat/stream` | SSE，POST 使用 JSON，GET 使用 question 参数 |
| GET | `/api/retrieve?q=...&mode=hybrid` | vector / keyword / hybrid 检索调试 |
| GET | `/api/eval/run` | 内置检索评测，调用 embedding API |

SSE 事件的 `data` 均为 JSON：

- `meta`：问题信息。
- `delta`：`{"text":"正文片段"}`，多行与 JSON 形状的正文可安全传输。
- `done`：最终 `AnswerResult`，包含 answer、citations、rejected；前端以此替换生成中的草稿。普通 JSON 接口使用相同结构。
- `error`：`{"message":"错误说明"}`。前端标记未完成片段，不自动重发模型请求。只有流式接口返回 404/405 时才回退普通接口。

流式接口协议已更新，自定义客户端应以 `done` 作为完成标记。最终引用只包含实际使用的片段，编号按出现顺序重排。`citations[].chunk` 包含 docName、headingPath 和 content，可展开核对原文。

## 评测口径

`src/main/resources/eval-set.json` 当前有 **38 条正例**，覆盖 8 篇示例笔记。

- **Hit@5**：前五个检索片段中，只要有一个来自任一期望文档，本题记为命中。
- **Hit@1**：第一片段来自任一期望文档。
- API 新增 `hitAt5`；保留旧字段 `recallAt5` 作为同值兼容别名。旧字段名称不能解释成多文档召回率。
- `referenceAnswer` 供人工核对，当前没有参与自动打分。

该指标衡量文档命中，不能证明正确章节已命中、回答准确或拒答可靠。38 道题的小样本不支持泛化结论。旧版 README 中的百分比对应旧检索实现，已移除；请在重新入库后运行当前评测，记录语料版本、模型、参数及结果再做比较。

## 验证

```powershell
mvn -B test
```

离线测试用 mock embedding/chat 验证：事务回滚、同名覆盖、索引失败重试、标题检索、空库与非空库无关问题拒答、引用编号校验、HTTP 参数校验、实际 MVC SSE 编码、余弦排序和切分边界。这些测试不证明真实模型的语义质量。

浏览器冒烟使用真实 Chromium 和模拟 API，避免调用付费服务：

```powershell
# 终端一，项目根目录
python -m http.server 18765 --bind 127.0.0.1 --directory src/main/resources/static

# 终端二，项目根目录；每轮使用新浏览器会话
npx --yes --package @playwright/cli playwright-cli -s=studypilot-smoke open http://127.0.0.1:18765
npx --yes --package @playwright/cli playwright-cli -s=studypilot-smoke run-code --filename=scripts/browser-smoke.js
npx --yes --package @playwright/cli playwright-cli -s=studypilot-smoke close
```

覆盖发送、多行流式内容、引用展开、HTML 转义、普通接口回退、异常中断、多文件上传、删除失败、评测面板和手机布局。截图输出到 `output/playwright/`。

## 真实模型验证

配置好环境变量后，可以使用独立数据库运行验证，避免覆盖日常知识库：

```powershell
mvn spring-boot:run '-Dspring-boot.run.arguments=--server.port=18085 --server.address=127.0.0.1 --spring.datasource.url=jdbc:sqlite:./data/live-validation.db --app.kb.auto-seed=false'
# 在另一个终端执行，应用需要保持运行
python scripts/live-validation.py
```

脚本上传 8 篇示例文档，执行 38 条三组检索评测、3 个问答场景和 1 个流式问答，报告默认写入 `output/validation/live-result.json`。会消耗应用所配置模型的 API 配额。脚本不接收或保存 Key；Key 仅由应用环境变量提供。报告记录语料 SHA-256、延迟和原始结果，问答场景仅作冒烟验证。

## 设计边界

- 相似度是相关性信号，不是“资料足以回答”的证明；阈值 0.50 仅是待校准初值。KEYWORD 模式没有向量置信度，供检索调试和评测使用。
- 引用编号校验只检查形式与范围，不验证每条结论是否被原文支持。用户应核对片段；后续质量评测需要有库无答案、同主题干扰、章节级证据和人工答案标注。
- 固定 BM25 饱和映射 `score / (score + k)` 默认 k=1，再按 0.6/0.4 融合。得分不是概率，也没有实现 RRF 或 reranker。
- 向量检索每次读取并解码全部向量，用大小为 topK 的堆保留结果；扫描成本约 O(ND + N log K)，数据库读取与内存消耗仍随数据量增长。没有“数千块毫秒级”的压测保证。
- 单进程个人使用：入库写事务串行执行，不支持多个应用实例共享 SQLite 的并发入库协调。索引刷新失败时数据库可能已成功提交，检索会重试刷新。
- PDF 当前不做 OCR、页码溯源或复杂版面还原；Markdown 超长段落仍可能切断代码或表格。
- 当前无用户认证、知识库隔离及调用配额控制，适用本地个人环境；公开服务需要另行实现这些功能。

## 项目结构

`adapter/web` 为 HTTP 接口；`application` 编排入库、检索、问答与评测；`infrastructure` 实现解析、切分、SQLite 和 Lucene；`model` 保存文档及检索模型。

## License

MIT
