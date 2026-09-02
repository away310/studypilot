# Java 21 与 Spring Boot 3 并发编程笔记

## 一、线程池

线程池解决"频繁创建线程开销大、并发数不可控"的问题。Java 里用 ThreadPoolExecutor 自定义线程池，核心参数：
- corePoolSize：核心线程数，空闲也不销毁；
- maximumPoolSize：最大线程数；
- keepAliveTime：非核心线程空闲存活时间；
- workQueue：任务队列（有界队列更安全，避免内存被打满）。

生产建议：不要用 Executors.newFixedThreadPool 的默认无界队列，最好显式传 ThreadPoolExecutor 参数并设置拒绝策略（如 CallerRunsPolicy）。

## 二、CompletableFuture 异步编排

CompletableFuture 是 Java 8 引入的异步编程工具，Spring Boot 3 里非常常用：
- supplyAsync：提交有返回值的异步任务；
- thenApply / thenCompose：串行加工前一个任务结果；
- allOf：等所有任务完成（并行执行多个独立调用后合并结果）；
- exceptionally：单个任务失败时兜底。

典型场景：多个上游接口并行查询后合并返回，把串行 RPC 的总耗时压到最长单个请求的耗时。

## 三、Virtual Threads（虚拟线程，Java 21）

Java 21 正式发布虚拟线程：轻量级线程，由 JVM 调度而不是操作系统线程。高并发 IO 密集型场景下，用虚拟线程可以"每请求一线程"而不担心线程数爆炸。
Spring Boot 3.2+ 可开启：spring.threads.virtual.enabled=true，让 Tomcat 用虚拟线程处理请求。

## 四、并发安全与上下文

- 线程池里用 ThreadLocal 要小心：线程复用会串上下文，用后必须清理；
- 并行子任务需要继承主线程上下文（traceId、租户 ID）时，要么传入参数，要么用包装 Runnable 显式传递；
- 共享状态优先用不可变对象与原子类，避免裸锁。

## 五、异步 + 消息队列

异步任务可以用 Spring @Async + 自定义线程池，也可以接消息队列（如 Kafka）做削峰与解耦：
- 异步：调用方不阻塞，任务在后台线程执行；
- MQ：生产者只管发，消费者按自己的能力消费，天然削峰；
- 两者都要考虑失败重试、幂等与最终一致。
