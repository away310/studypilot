# Agent 开发学习路线

整理日期：2026-09-15。新增 10 篇笔记，建议按以下顺序阅读。每篇包括概念、工程建议与练习；代码示例是教学材料，并非本项目已经启用的 Agent 功能。

- [Agent 开发 01：架构选择与执行循环](../kb/agent-01-architecture-loop.md)
- [Agent 开发 02：工具契约、参数校验与幂等](../kb/agent-02-tool-contracts.md)
- [Agent 开发 03：Java 与 Spring AI 工具接入](../kb/agent-03-spring-ai-java.md)
- [Agent 开发 04：会话记忆、长期记忆与上下文管理](../kb/agent-04-memory-context.md)
- [Agent 开发 05：检查点、断点恢复与人工审批](../kb/agent-05-state-recovery.md)
- [Agent 开发 06：多 Agent 协作、任务边界与合并](../kb/agent-06-multi-agent.md)
- [Agent 开发 07：MCP、工具协议与权限安全](../kb/agent-07-mcp-security.md)
- [Agent 开发 08：Agentic RAG 与多步检索设计](../kb/agent-08-rag-planning.md)
- [Agent 开发 09：评测、运行轨迹与成本观测](../kb/agent-09-evaluation-observability.md)
- [Agent 开发 10：从学习项目到可交付应用](../kb/agent-10-production-roadmap.md)

验证问题位于 [agent-content-checks.json](../eval/agent-content-checks.json)。这是新增内容的检索检查，独立于原有 38 条评测集。evidenceHint 是辅助检查线索，不能替代语义证据核验。

官方资料链接放在各篇笔记相关章节中；未标为官方机制的设计和练习属于本项目整理建议。
