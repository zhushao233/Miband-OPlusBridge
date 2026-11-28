# coui 本地维护说明

`coui/` 是上游 [COUI-Kit](https://github.com/ItosEO/COUI-Kit)（Maven Central
`io.github.itoseo:coui`）的**源码 fork**，当前基于 `0.0.1-alpha11` 导入，许可证见
`COUI-LICENSE` / `COUI-NOTICE`，上游说明见 `COUI-README.md`。

## 同步上游时必须保留的本地改动

PR #12（模糊顶栏 / 大标题折叠 / 底栏材质）修改了以下文件，覆盖上游前需要逐处合并：

- `src/main/java/com/itos/coui/backdrop/CouiBackdrop.kt`
  —— 采样与合成同帧，View 宿主通过 RenderNode 读取源图层。
- `src/main/java/com/itos/coui/effect/CouiFloatingGeometry.kt`
  —— 材质 patch 的纵向留白至少覆盖模糊 kernel（`max(11f, ceil(blurPadding) + 1f)`）。
- `src/main/java/com/itos/coui/effect/CouiFloatingSurfaces.kt`
  —— 与上述几何调整配套的 surface 参数。
- `src/main/java/com/itos/coui/topappbar/CouiCollapsingAppBarState.kt`
  —— 新增 `setCollapsedFraction()`，供 Android View 宿主按正文滚动直接同步折叠位置。

`coui-tab/` 不是上游模块，是本仓库的 Android View 宿主层（顶栏、底栏、确认框），
同步 `coui/` 时不受影响。

## 上游链接

当前没有为本 PR 的改动提交上游 issue 或 PR；若上游后续提供等价的同帧采样接口，
可优先切换到上游实现并删除上述定制。
