# Java 集合与常用类库笔记

## 一、List 与 Map 的选型

- ArrayList：底层数组，随机访问 O(1)，尾部增删快，中间插入删除慢；
- LinkedList：双向链表，头尾操作快，随机访问 O(n)；
- HashMap：数组 + 链表/红黑树，key 允许一个 null；
- LinkedHashMap：保持插入顺序，可做 LRU 缓存（accessOrder=true）；
- TreeMap：红黑树实现，按键排序；
- ConcurrentHashMap：分段/细粒度锁，读多写少的首选并发 Map。

## 二、HashMap 的原理要点

- 默认容量 16，加载因子 0.75，超过阈值（容量×因子）扩容为 2 倍；
- 哈希冲突用链地址法，链表长度 ≥8 且数组 ≥64 时转红黑树；
- 扩容时按 (hash & 旧容量) 判断元素留在原位还是挪到 高位+旧容量；
- 并发下 HashMap 会丢数据甚至死循环，多线程必须用 ConcurrentHashMap。

## 三、String 相关

- String 不可变，适合做 key 与常量；拼接用 StringBuilder；
- 大量唯一字符串可 intern 复用，但别滥用（元空间有限）；
- split/正则较慢，高频场景手写或缓存 Pattern。

## 四、Stream 与 Optional

- stream().map/filter/collect 链式处理集合，逻辑清晰；
- 并行流 parallelStream 慎用：小数据反而慢，且线程池共享；
- Optional 用于表达"可能为空"，避免 NPE 但不建议包一层做参数。

## 五、常用工具类

- Objects.equals / Objects.hash 处理可空比较；
- Collections.unmodifiableList 返回只读视图；
- Arrays.asList 返回定长列表，不能 add；
- 时间用 java.time（LocalDateTime），别再用 Date + SimpleDateFormat（线程不安全）。
