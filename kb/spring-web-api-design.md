# Spring Boot Web 与接口设计笔记

## 一、Controller 分层规范

请求路径 Controller → 业务 Service → 数据访问 Repository，三层的职责：
- Controller：参数校验、调用 Service、组装响应；
- Service：业务规则、事务边界、编排；
- Repository：数据读写。

不要把 SQL、业务逻辑直接写在 Controller 里，否则难测试、难复用。

## 二、统一响应与异常

- 统一响应体 {code, message, data}，code=0 表示成功；
- 用 @RestControllerAdvice + @ExceptionHandler 做全局异常兜底：
  - 业务异常 → 对应 code；
  - 参数错误 → 400；
  - 未捕获异常 → 500 + 日志，不把堆栈裸返回前端；
- 枚举参数用 @RequestParam 默认值兜底，避免空指针。

## 三、接口幂等与防重

写操作（下单、支付回调）要做幂等：
- 前端传幂等键（Idempotency-Key），后端 Redis setnx 判重；
- 或数据库唯一索引兜底；
- 重复请求返回第一次的结果，而不是报错。

## 四、SSE 与流式接口

- SSE（Server-Sent Events）：服务端单向推送，HTTP 长连接，EventSource 消费；
- 适合 LLM 流式回答、进度通知这类"服务端连续产出"的场景；
- 实现：Content-Type: text/event-stream，事件格式 event/data 分段；
- 注意：代理层别缓存、要关压缩缓冲，否则浏览器收不到增量。

## 五、上传与文件

- MultipartFile 接收上传，限制大小与类型白名单；
- 文件先落临时目录再转存，防止半截文件；
- 解析类任务（文档解析）异步处理，避免阻塞请求线程。
