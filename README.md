# StockAnalyzer - 智能股票分析

基于 Android 的 A 股股票分析工具，支持多种数据源，提供实时行情、K线图表、AI智能分析等功能。
这是我闲来无事用claude code接了deepseek，还是deepseek-v4-flash，花了一天多，烧了7亿多token做的一个粗制滥造的app，所以只是兴趣使然。本人以前学过一些C++，但这个项目完全是由AI开发、AI维护的、用Java开发的，我没动过一点手改过这个代码，甚至这个readme文档也是ai写的。（当然这段话还是我自己手打的）做完之后真的感慨，还好我当时没学计算机，不然真的得被ai给卷没了。

## 功能特点

- **多数据源**：支持东方财富、腾讯财经、新浪财经三种数据源自由切换
- **实时行情**：股票报价，涨跌颜色标识
- **K线图表**：支持 5分K / 60分K / 日K / 周K / 月K，点击显示价格日期
- **大盘指数**：上证指数、深证成指、创业板指、科创50
- **自选股**：收藏关注，上限10只，首页快速查看
- **市场要闻**：财经新闻聚合
- **AI 分析**：集成 DeepSeek API，提供技术面/基本面/全面分析
- **深色模式**：护眼暗色主题
- **数据缓存**：本地缓存策略减少API调用，避免风控

## 数据源切换

设置 → A 股数据源：
- **东方财富**（推荐）：行情+K线+财务数据全量，但IP可能被封
- **腾讯财经**：基础行情，接口稳定不易被封
- **新浪财经**：基础行情，备用方案

## 构建说明

```bash
# 使用本地 Gradle 构建
/c/Users/username/gradle-8.5/bin/gradle assembleDebug
# 或
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 依赖

- Android SDK 34
- Java 17+
- Gradle 8.5
- Room Database（本地存储）
- MPAndroidChart（图表）
- Retrofit + OkHttp（网络请求）
- DeepSeek API（AI 分析，需在设置中配置 Key）
