# Cold Atom Craft — M0-R2.1 断点审查与续工说明

**类型：** 代码总监临时检查（非最终发布验收）  
**日期：** 2026-10-26  
**来源：** 用户上传的 `ColdAtomCraft-M0-R2.1.zip`  
**原包 SHA-256：** `a172f466a620cf8de5a3a6243ec0384c121750066be7cffa4de3bdf8b6937567`

## 一、简明结论

R2.1 已包含对三项前期 P1 缺陷的实际代码修复与测试：

1. `WorldEffectGate.Precheck` 绑定完整不可变 `EnergyTransferCandidate`，`WorldEffectLedger.claim` 检查候选值一致。
2. `WorldEffectLedger.claim` 将候选时间区间与已登记的 `(runId, stepIndex)` 绑定；`beginCalculation` 使用按 run 分组的 `TreeMap` 检查重叠。
3. 新增 `ServerWorldSnapshot`、`ServerEffectCommitCoordinator`，在 Claim 前验证新鲜快照、可信模型及服务端条件。**但真实 NeoForge 服务端适配器仍未实现**。

因此，这份代码是有实际价值的可继续工作基线，而非需要重做的废稿。它目前是**未完成封包的中途检查点**，不是 `M0 CLOSED`。

## 二、独立执行与提交证据

| 检查项目 | 结果 | 证据限制 |
| --- | --- | --- |
| ZIP 可解压/源码存在 | PASS | 462 个压缩条目，含大量生成缓存，不适合作为正式源码发布包 |
| 交付方最新 JUnit XML | 113/113 PASS | 107 项 experiment-core + 6 项 physics-core；直接解析归档 XML，不是本次独立 JDK 25 复跑 |
| 独立 Java smoke | 54/54 PASS | 当前环境 JDK 21 编译测试；非 Java 25 正式构建验证 |
| 独立 Python 资产测试 | 17/17 PASS | `python -m unittest discover -s verification -p 'test_*.py'` |
| RED 证据 | 存在 | `verification/M0-R2.1/red/` 含七项失败断言与 XML |
| GREEN 证据 | 存在 | R2.1 中间阶段 Gradle 日志及 JUnit XML，最新 XML 全通过 |
| 完整无缓存最终 NeoForge 构建 | 尚无本轮可核对日志 | 有中间构建 JAR，但不等于已提供最终干净构建证据 |
| 生产 JAR 独立安装 | NOT RUN/未有证据 | 延续 M0-R2 的待验状态 |
| 真实世界效果执行器 | NOT IMPLEMENTED | 不能宣称正式 TOCTOU 闭环或跨重启 exactly-once |

## 三、当前归档缺陷

- 原 ZIP 包含 `.gradle/` 和各模块 `build/` 生成文件、class、缓存以及一个大型开发用 Minecraft patched JAR；建议正式源代码交付时排除。
- 根目录 `MANIFEST_SHA256.txt` 是旧清单。比对实际文件后，有 **8 个清单内文件**与该旧哈希不符，且 R2.1 新增文件未完整纳入清单。这是归档尚未收尾的直接证据，而非自动说明源码错误。
- `README.md`、`docs/CHANGELOG.md`、`docs/WORLD_INTERACTION_CONTRACT.md` 等仍以 **M0-R2** 为主，尚未写入 R2.1 的终版变更、测试统计和限制。
- 未见 `M0_R2_1_AUDIT.md` 形式的最终验收报告。

## 四、仍需明确的设计问题

**复算语义：** 文档要求每次运行数值求解器之前调用 `beginCalculation`，且重复 `(runId, stepIndex)` 不得再次开始；另一方面，新的“过期结果被拒绝后合法重算”测试复用同一登记步骤。当前纯 Java 测试能验证候选重试后的 Claim，但**真实求解器复算**应该是保留步骤登记、创建新 run、还是有明确的 retry 子状态，仍需在 M1 生产调度器接入前约定。这不是当前未启用世界修改功能的 M0 阻断项，但不能让未来执行层自行猜测。

**真实服务器原子性：** `ServerEffectCommitCoordinator` 的 `submit()` 返回 `SETTLEMENT_CLAIMED`，只意味着取得内存 Ledger 的 Claim，不会执行 Minecraft 世界修改。未来服务端必须在同一受控上下文内完成新鲜权威读取、权限检查、Claim、修改及结果登记；客户端或外部代码提供的 boolean facts 不能充当实际权威事实。

**崩溃恢复：** 内存 Ledger 不保证跨重启幂等，且 `CLAIMED` 后到实际修改间可能丢失效果。不得宣布 exactly-once 世界修改已实现。

**性能：** 随包三轮基准的 50,000 次 `beginCalculation` 中位数，从优化前约 10,489 ms 到优化后约 15.17 ms；内存样本从约 5.20 MiB 增至约 8.25 MiB。这是**交付方基准日志**，不是本次独立性能复验；需要保留负载、JVM 和方法的口径，避免宣传为普适倍数。

## 五、建议的下一步（不依赖 DeepSeek 额度）

1. **冻结此上传包**作为 DS 本轮工作快照，勿覆盖。
2. 基于当前源码补齐 R2.1 正式变更记录、测试计划与审计报告，并生成全新 SHA 清单。
3. 在 JDK 25 + NeoForge 26.3.0.99 环境执行最终干净无缓存 `:physics-core:test :experiment-core:test :neoforge-mod:build` 和 `verifyDistributableJar`；检查源码与 JAR 对应性。
4. 如条件允许，安装构建出来的发行 JAR 到独立 NeoForge 实例（优先独立专用服务器），记录加载日志。
5. 代码总监复核后才提交 Owner 对 M0 的最终验收；**不先行实现激光、热学或生物伤害模型**。

**当前建议状态：** `CAC-M0-R2.1 = IMPLEMENTATION_SUBSTANTIALLY_PRESENT / FINAL_GATE_PENDING`。
