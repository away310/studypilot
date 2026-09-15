# Agent 开发 03：Java 与 Spring AI 工具接入

整理日期：2026-09-15。参考 Spring AI 1.1 分支文档；该在线分支目前显示 1.1.8，StudyPilot 依赖是 1.1.2，示例需在自己的依赖版本中编译验证，不混用 2.x 的新增编排接口。

## @Tool 与 ChatClient.tools 的作用
Spring AI 支持用 @Tool 标注 Java 方法，并通过 ChatClient 请求上的 tools(...) 提供工具对象。工具说明和参数 Schema 会用于模型调用；应用框架负责把调用分发到 Java 方法，再把结果传回模型。ToolCallback 表示可调用工具，ToolCallingManager 管理工具执行生命周期。
来源：[Spring AI 1.1 Tool Calling](https://docs.spring.io/spring-ai/reference/1.1/api/tools.html)。

## 一个只读工具的教学示例
以下代码用于解释接入方式，未作为本项目的运行功能启用；假设已有一个配置好的 ChatClient：
```java
class NotesTool {
    @org.springframework.ai.tool.annotation.Tool(
        description = "返回示例知识库允许查询的分类，仅用于教学")
    public String categories() {
        return "Java, RAG, Agent 开发";
    }
}
String answer = chatClient.prompt()
    .user("示例知识库有哪些分类？")
    .tools(new NotesTool())
    .call().content();
```
上线前应将硬编码结果替换为受授权约束的业务服务，并确认模型与提供商支持所需的工具调用能力。

## 工具对象与权限范围
建议按请求的授权范围提供工具，避免把管理员写工具全局注册给所有会话。工具 Bean 如果是单例，不要把某个用户的身份或临时参数存在实例字段里，以免请求间串用。将查询、写操作、审批流程分别建模，业务校验保留在应用服务层。

## Spring AI 工具回调的测试重点
建议分别测试直接调用 Java 方法、参数映射、模型请求分发和结果回传。只验证最终答案中出现关键词，无法证明工具真的运行过。可用可观察的计数器或测试替身断言工具名称、参数和调用次数；涉及真实模型时另外记录提供商与模型标识，避免把 mock 的正确性当成模型选工具的准确率。

## 如何从 StudyPilot 扩展
建议先提供只读 searchNotes 工具，再让模型选择是否检索；保留原有固定 RAG 路径作基线。添加调用步数限制、记录工具轨迹后，再评估多次检索是否值得增加延迟。当前 StudyPilot 的知识库问答仍是固定 RAG 流程，本文讲的是后续实现方向。
