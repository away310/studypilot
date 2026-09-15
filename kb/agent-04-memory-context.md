# Agent 开发 04：会话记忆、长期记忆与上下文管理

整理日期：2026-09-15。记忆设计需要同时考虑用途、作用域和可靠来源。

## 短期状态和长期记忆怎么区分
LangGraph 将 checkpointer 用于某个 thread 的图状态与检查点，将 store 用于跨 thread 的应用数据。前者可支持会话连续性和恢复，后者适合用户偏好或长期事实。内存实现随进程结束丢失，不能当成持久化数据库。
来源：[LangGraph Persistence](https://docs.langchain.com/oss/python/langgraph/persistence)。

## 上下文不是把历史全部塞进去
上下文工程关注当前推理所需的信息选择与组织。官方文章讨论了上下文压缩、结构化笔记和子任务上下文隔离等方法。保留更多 token 并不自动意味着更可靠；无关历史和重复工具返回可能挤占真正有用的证据。
来源：[Effective context engineering](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents)。

## 给学习助手设计记忆字段
建议把明确表达的偏好存成结构化数据，例如 language=zh-CN、level=beginner，并附 source、updatedAt、expiresAt。将会话摘要与用户长期偏好分开；模型猜测的爱好不要直接写成确定事实。工作流运行状态另存 taskId、已完成步骤、等待审批项和产物引用。

## 会话隔离与删除
建议记忆查询同时受 tenantId、userId 和 conversationId 约束；这些字段来自可信会话。不要只凭用户输入的 conversationId 读取历史。删除会话时应明确哪些数据被删除、哪些跨会话偏好仍保留。若用户撤销某项记忆，摘要、索引和缓存也需要按产品规则处理。

## 压缩历史时应该保留什么
建议优先保留用户目标、明确约束、已确认事实、重要决策、失败尝试和未完成工作。工具返回可保存到外部存储，并在上下文里放摘要与定位标识；恢复时按需读取原文。练习：压缩前后分别询问一个来自较早约束的问题，检查信息是否仍正确；同时确认新会话不会读取其他用户的数据。
