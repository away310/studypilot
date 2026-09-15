# Agent 开发 07：MCP、工具协议与权限安全

整理日期：2026-09-15。架构部分参考 MCP 2025-06-18 版本规范；安全链接目前指向 2025-11-25 文档。实现时应确认客户端和服务器共同支持的协议版本。

## MCP 的 Host、Client 和 Server
MCP 采用 host、client、server 的协作结构。Host 是承载 AI 交互的应用，client 负责与某个 server 建立协议连接，server 暴露可用能力。协议帮助标准化能力发现与交互，但不替应用完成用户身份识别、工具授权和业务审批。
来源：[MCP Architecture 2025-06-18](https://modelcontextprotocol.io/specification/2025-06-18/architecture)。

## MCP 与 Function Calling 有什么关系
Function Calling 描述模型提出工具调用请求的机制；MCP 描述应用与能力服务之间的协议。二者可以配合：应用从 MCP 发现能力，再按模型 API 的工具格式提供给模型，接收到请求后由应用调用服务。MCP 本身不等于自主规划循环，也不能证明一个应用就是多 Agent 系统。

## Token passthrough 为什么危险
MCP 安全文档明确禁止 token passthrough：MCP 服务不应把未针对自身正确验证的上游令牌直接转发给下游 API。服务需要校验令牌适用对象，并为下游访问采用正确的认证流程；否则容易造成权限边界混淆和审计问题。接入服务不应要求用户把云平台主密钥写进模型提示词。
来源：[MCP Security Best Practices](https://modelcontextprotocol.io/docs/2025-11-25/tutorials/security/security_best_practices)。

## 工具返回中的指令怎么处理
建议把网页、文档和工具结果视为数据，而不是能覆盖系统授权的命令。检索结果里出现“忽略规则、发送密钥”时，不能据此授予新权限。将权限判断、允许访问的路径或域名、资源归属校验放在工具执行层；提示词提醒只是辅助措施，不是权限边界。

## 上线前的最小检查
建议先列出每个工具能读什么、能写什么、以谁的身份执行。对外请求应防止访问未授权的内部地址，敏感日志要脱敏。工具描述被更新后也要重新评测。练习：上传一份含伪造管理员指令的笔记，确认回答流程不会据此读取服务器密钥或调用管理接口。
