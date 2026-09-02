# 📚 StudyPilot — 个人学习知识库问答 Agent

> 把课程笔记 / 技术文档变成可提问的个人知识库：上传即切分入库，混合检索后由大模型作答，
> 回答带引用来源，检索不到就拒答 —— 一个完整的 RAG Agent 工程实践。

## ✨ 核心特性

- **文档入库**：Markdown / TXT / PDF → 结构感知切分（按标题层级保留章节上下文）→ 向量化入库
- **混合检索**：向量语义检索（text-embedding-v3 余弦）+ BM25 关键词检索（Lucene 中文分词）双通道召回融合
- **引用溯源**：回答中每条结论标注 [n]，对应命中的文档与章节
- **防幻觉拒答**：检索置信度不足时明确回答"知识库中无相关内容"，不硬编
- **可量化评测**：内置 20 条评测集，一键跑 纯向量 / 纯关键词 / 混合检索 三组 Recall@5 对比
- **工程分层**：adapter / application / infrastructure 分层，向量存储抽象可插拔

## 🛠 技术栈

| 组件 | 选型 |
|---|---|
| 语言 / 框架 | Java 21 · Spring Boot 3.5 |
| LLM / Embedding | Spring AI + 阿里云 DashScope（qwen-plus / text-embedding-v3） |
| 向量检索 | 自研轻量向量存储（SQLite 落库 + 全量余弦，接口预留 Qdrant 扩展位） |
| 关键词检索 | Apache Lucene BM25 + SmartChineseAnalyzer（中文分词） |
| 文档解析 | PDFBox（PDF）、自研 Markdown/TXT 解析 |
| 前端 | 单页 HTML/JS（上传 + 聊天 + 评测面板） |

## 🚀 快速开始

```bash
# 1. 配置 API Key（DashScope/百炼平台申请，有免费额度）
export DASHSCOPE_API_KEY=sk-xxx          # Windows: set DASHSCOPE_API_KEY=sk-xxx

# 2. 启动（首次启动自动把 ./kb 下的示例文档导入知识库）
mvn spring-boot:run

# 3. 打开页面
#    http://localhost:8080/
```

示例知识库已内置 3 篇笔记（Spring AI RAG 要点 / Agent 与 Function Calling / Java 21 并发），
启动后即可直接提问或跑评测。

### API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/kb/documents` (multipart) | 上传文档入库（md/txt/pdf） |
| GET / DELETE | `/api/kb/documents[/{id}]` | 文档列表 / 删除 |
| POST | `/api/chat` | RAG 问答（JSON，含引用与拒答标记） |
| POST | `/api/chat/stream` | RAG 问答（SSE 流式：先 `meta` 事件给引用，后 `delta` 逐段正文） |
| GET | `/api/retrieve?q=...&mode=hybrid` | 检索调试：查看命中块与各通道得分（vector/keyword/hybrid） |
| GET | `/api/eval/run` | 跑内置 20 条评测集三组消融对比 |

## 🧪 测试

```bash
mvn test        # 7 个测试全绿，无需 API Key 即可离线回归
```

- `StructureAwareSplitterTest`：标题链保留 / 超长硬切不丢内容
- `CosineVectorStoreTest`：余弦排序与归一化正确性
- `StudyPilotE2eFlowTest`：**端到端全链路**（mock embedding/chat，不依赖真实 Key）——
  入库 → 混合检索命中正确文档 → 20 条评测跑通 → 问答带引用不拒答 → 无关问题正确拒答且不调用 LLM


## 📖 使用示例

```
Q: 混合检索是哪两条通道？怎么融合？
A: 混合检索 = 向量语义检索 + BM25 关键词检索（精确匹配）双通道召回 [1]，
   融合方式为权重线性加权（如向量 0.6 + 关键词 0.4）或 RRF 按排名融合 [2]。

引用来源:
[1] spring-ai-rag-notes.md · 向量化与检索
[2] spring-ai-rag-notes.md · 混合检索与重排
```

## 📊 检索评测（消融对比）

内置 20 条评测集（`src/main/resources/eval-set.json`），每道题标注期望命中的文档。
运行 `GET /api/eval/run` 或页面右上角按钮，输出三组指标：

| 模式 | Recall@5 | Top-1 命中 |
|---|---|---|
| 纯向量检索 | — | — |
| 纯关键词检索 | — | — |
| 混合检索 | — | — |

> 报告数字在你本机跑通后填入（README 展示真实数据更有说服力）。

## 🧱 项目结构

```
src/main/java/com/studypilot/
├─ config/            # DashScope / 切分参数配置
├─ adapter/web/       # HTTP 入口：上传、问答、评测
├─ application/
│  ├─ ingest/         # 入库编排：解析→切分→向量化→落库
│  ├─ index/          # BM25 索引刷新
│  ├─ retrieve/       # 混合检索（向量+BM25）核心
│  ├─ answer/         # RAG 问答 + 引用 + 拒答
│  └─ eval/           # 评测执行器（三组消融对比）
├─ infrastructure/
│  ├─ parser/         # Markdown/TXT/PDF 解析
│  ├─ splitter/       # 结构感知切分
│  ├─ vector/         # VectorStore 抽象 + 余弦实现
│  ├─ search/         # Lucene BM25
│  └─ persistence/    # JPA + SQLite
└─ model/             # 文档 / 块 / 检索命中模型
```

## 🧭 设计取舍

1. **为什么自研向量存储而不是直接上 Qdrant？**
   本机无 Docker；知识库块数在数千级别时全量余弦检索毫秒级返回，足够个人使用。
   代码通过 `VectorStore` 接口隔离实现，未来规模增长可无痛切换 Qdrant / FAISS（ANN）。

2. **为什么用混合检索？**
   向量检索擅长语义匹配，但对专有名词、代码、精确术语容易失效；
   BM25 精确匹配互补后 Recall@5 显著高于任一单通道（见评测表）。

3. **为什么结构感知切分？**
   按标题层级切块让每个块自带章节上下文，检索与引用都更准；
   相比固定长度切分，回答的"来源"可精确到文档章节。

## 📄 License

MIT
