## Context

#5 后端 PRD CRUD + #6 输入门槛 + #7 文件上传都已就绪;前端只缺把 `#page-list` 和 `#page-edit` 真接通。属于"全是前端 + 复用现有 API"的轻量补丁。

## Goals / Non-Goals

**Goals:**
- 列表页拉真数据,新建/编辑/删除/提交评审 4 个核心操作可用
- 上传文档后能直接跳进编辑器看 AI 摘要
- 错误消息(#6 拦截)用户能直接看到

**Non-Goals:**
- 不实现富文本编辑器(用 textarea + markdown)
- 不实现搜索/过滤(后端 API 不支持)
- 不实现评审流程页(#17+)
- 不动样式系统,只把内容由静态改动态

## Decisions

### D1:路由用 location.hash,不引入路由库
浏览器原生 `window.location.hash` + `hashchange` 事件,零依赖。格式:
- `#list` — 列表(默认,navTo('list') 设置)
- `#edit` — 新建
- `#edit?id=123` — 编辑现有(query 参数手工 parse)

### D2:编辑器用 textarea(markdown 输入),不接富文本工具栏
现有 `editor-toolbar` 的 B/I/U/H1 按钮全是装饰用,无 onclick。本 change 不接,直接给用户一个 `<textarea>` 让他写 markdown 即可。`#6` 章节校验就是看 markdown 标题,textarea 与之天然对齐。

### D3:乐观锁版本透传
进入编辑器时把 `currentPrd.version` 存在闭包变量,保存时带回去给 PUT。后端检测到冲突会返回 30004,前端 alert 提示"已被修改,请刷新"。

### D4:状态徽标颜色统一映射
```js
const STATUS_BADGE = {
    DRAFT:        { label: '草稿',     cls: 'badge-draft' },
    SUBMITTED:    { label: '待评审',   cls: 'badge-reviewing' },
    UNDER_REVIEW: { label: '评审中',   cls: 'badge-reviewing' },
    APPROVED:     { label: 'PRD 通过', cls: 'badge-pass' },
    REJECTED:     { label: '不通过',   cls: 'badge-fail' },
    INITIALIZING: { label: '初始化中', cls: 'badge-draft' }
};
```
复用 app.html 已有的 badge-* 类。INITIALIZING 在列表 API 已被后端过滤,这里只是兜底。

### D5:上传后跳转
`prd-upload.js` 当前是 alert 后 `navTo('list')`。改为:
```js
location.hash = '#edit?id=' + prd.id;
```
hashchange 事件会触发 router → 进编辑页 → loadPrd(id) → 自动回填。

## Risks / Trade-offs

- **[hash 路由与 navTo() 共存]**:原型用 `navTo(name, navEl)` 切 page,本 change 加 hash 路由相当于"侧支"。Mitigation:在 navTo 进入 list/edit 时同步 hash;在 hashchange 监听里反过来调 navTo,避免循环触发用 flag 守护
- **[文件上传后跳编辑器,但 AI 摘要可能很短]** 不达 #6 门槛 → 用户提交时被拦。Mitigation:编辑器底部 hint "AI 摘要可能不够完整,建议补充内容/章节后再提交"
- **[textarea 不支持 markdown 预览]** 用户写 `# 背景` 看不到渲染效果。Mitigation:本 change 不引入 markdown 渲染库;后续如需要可加 `marked.js`(轻量 ~30KB)
