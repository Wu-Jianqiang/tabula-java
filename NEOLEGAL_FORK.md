# NeoLegal fork 跟踪（NeoLegal fork tracking）

> 分析基线：上游 `tabulapdf/tabula-java` master（1.0.6-SNAPSHOT）对比 fork `neolegal-fr/tabula-java` master（1.2.1-SNAPSHOT，Maven Central 最新 1.2.0）。分析日期：2026-09-17。
>
> 更新方式：重新下载两端 master 源码（`https://codeload.github.com/<org>/tabula-java/zip/refs/heads/master`），对照本节明细核对差异。

## 一句话定位（One-line positioning）

NeoLegal 的 fork 面向**边框绘制不精确**的 PDF；它把表格提取的容错能力做成**默认关闭的可选开关**，不配置时行为与上游完全一致。
The fork exists to handle PDFs whose table borders are drawn imprecisely; every behaviour is an option, off by default, so an unconfigured extractor behaves exactly like upstream.

## 依赖与构建立升级（Dependencies & build）

| 项 | 上游 1.0.6-SNAPSHOT | fork 1.2.1-SNAPSHOT |
|---|---|---|
| groupId | `technology.tabula` | `fr.neolegal` |
| Java | 8 | 17 |
| 测试框架 | JUnit 4.13.2 | JUnit 5.10.2 |
| BouncyCastle | 1.80 | 1.86 |
| slf4j | 2.0.13 | 2.0.16 |
| 发布链路 | nexus-staging（OSSRH） | central-publishing-maven-plugin（Central Portal）+ CI 发布工作流 |

注：上游 master 本身已升级 PDFBox 3.0.4；依赖层面的核心增量是 groupId 改名 + Java 17 + JUnit 5 + 新发布链路。

## 核心代码增强（Core enhancements）

### Ruling.java —— 标线合并/相交容差化
- **magnetize 吸附合并**：`collapseOrientedRulings` 额外一轮，把"平行、重叠、间距 ≤ magnetRadius"的标线合并为一条（PDF 生成器常把一条表格边框画成两段略微错开的线段，不合并会成倍产生伪单元格）；合并位置按长度加权平均，反复迭代直至稳定。
- **方向可配置膨胀量**：`nearlyIntersects`/`intersectionPoint`/`findIntersections` 重载，水平/垂直可传不同膨胀量。
- **bug 修复**：合并前先复制，避免就地改动调用方持有的 ruling（原实现会改掉 `Page` 缓存里的标线，再提取同一页就基于被改过的数据，结果不对）。
- 新增 `parallelTo`/`nearlyOverlaps`/`spans`；常量改 public。

### SpreadsheetExtractionAlgorithm —— 可配置化 + 单元格补全
- `withMaxGapBetweenAlignedHorizontalRulings/verticalRulings`：对齐间隙容差。
- `withMinColumnWidth/withMinRowHeight`：低于阈值的相邻平行线段视为同一边框合并（一条边框被画了两遍的情况）。
- `withCellAutocompletion`：`findGaps` 为每行补最左侧缺失单元格（无边框表头对齐首列）。
- `withCellTextOverflowRatio`：收集文本时按比例加宽 cell（`textArea`），捕获被右边界重叠的尾字母。
- `neolegalDefaults()`：生产配置一键预设（javadoc 已注明其非通用性）。
- 全部默认关闭 = 上游行为；`isTabular` 改用 `this` 复用配置。
- 配套 `SpreadsheetExtractionAlgorithmTest`（283 行 / 16 测试），每个选项双向断言 + 锚定"默认等于上游"。

## 工程管理（Engineering）

- `CLAUDE.md`：fork 维护契约（设计铁律、rebase 冲突面控制 815→218、两个踩坑警示）。
- CI：`release-to-maven-central.yml`（workflow_dispatch 发布 + 自动版本递增）。
- 测试文件 JUnit 4→5 迁移；Cell/Page 仅清理未使用 import。

## 可借鉴点（Ideas we can borrow）

与本 fork（缺角/外边框/孤立单元格几何判定）互补：
1. 吸附合并重复绘制的边框——处理画线缺陷。
2. `findGaps` 单元格自动补全——处理无边框表头。
3. 文本溢出收集——处理边界重叠裁字。
4. "默认与上游一致 + 选项式开启"的分支策略，降低 rebase 冲突面——值得本 fork 借鉴的设计纪律。

## 跟踪要点（Tracking notes）

- 已发布版本：`fr.neolegal:tabula:1.2.0`（Maven Central）。
- 发布链路为 Central Portal，release 永久不可撤销。
- 后续对比时重点核对：magnetize / findGaps / textArea 三处是否演进，及 `neolegalDefaults()` 生产阈值变化。