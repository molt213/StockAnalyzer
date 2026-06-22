# 更新日志

## v1.0.2 (2026-06-22)

### 🐛 Bug 修复
- **AI 分析点击闪退**：修复 R8 full mode 剥离 Retrofit 接口泛型签名导致的 `Call return type must be parameterized as Call<Foo>` 崩溃。AI API 接口返回类型改为 `Call<ResponseBody>` 绕过泛型问题，同时禁用 R8 full mode 确保泛型元数据保留

### ✨ 新功能
- **分析历史可重复查看**：点击分析历史记录列表中的条目，可直接查看该次完整分析结果（评级、趋势、风险、优势、建议等全部内容），无需重新请求 AI

### 🔧 技术改进
- AI API 调用改用原始响应体手动解析，消除对 Retrofit 自动反序列化的依赖
- gradle.properties 新增 `android.enableR8.fullMode=false`，兼容 R8 正常模式优化

## v1.0.1 (2026-06-22)

### 🐛 Bug 修复
- **腾讯数据源最低价错误**：修复 TencentScraper 中最高价/最低价字段索引从 9/10 更正为 33/34（原读取的是买一价和买一量）
- **月K图无法加载**：K线数据获取增加多 URL 容错机制，月K（scale=4800）加载失败时自动降级尝试备用接口
- **深色模式导航栏不可见**：导航栏图标/文字颜色从固定色改为主题属性 `colorOnSurface`，暗色模式下显示白色

### ✨ 新功能
- **K线柱状图**：详情页 K 线从折线图改为标准阴阳烛（蜡烛图），红色阳线/绿色阴线
- **MACD 技术指标**：K 线图下方添加 MACD (12,26,9) 子图，含 DIF（蓝）、DEA（橙）线和红绿柱
- **RSI 技术指标**：添加 RSI (14) 子图，含超买线(70)、超卖线(30)、中线(50)

### 🎨 UI 优化
- K 线图卡片标题从"图表"改为"K线图"
- APK 体积从 7.9MB 缩减至 3.6MB（R8 压缩 + 资源裁剪 + 语言资源精简），功能界面完全不变

### 🔧 技术改进
- 启用 R8 代码压缩（debug 构建也开启 minifyEnabled）
- 启用资源裁剪（shrinkResources）
- 限制仅保留中文资源（resConfigs "zh"），移除 Material3 库 50+ 种语言翻译
