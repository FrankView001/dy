# 抖音模式 — Android WebView App

把 `tiktok-mode.js` 的逻辑搬到了一个原生 Android WebView App 里，去掉了油猴脚本里 iframe + postMessage 那套迂回方案：每条视频现在是一个**独立的顶层 WebView 导航**，而不是嵌入 iframe，所以天然不受 `X-Frame-Options` / `frame-ancestors` 限制。

## 已实现

- 地址栏 + 首页 WebView，默认打开 `missav.ai`
- 右下角「🎵 抖音模式」按钮：在当前页面（白小唐 `baixiaotangtop.com` 或 MissAV 系列域名）收集视频/详情链接
- `ViewPager2` 纵向滑动 Feed，每页一个独立 WebView 直接加载详情页（非 iframe）
- 进入详情页后注入 JS：隐藏页面其余 UI、把 `<video>` 撑满全屏、静音自动播放
- 点击屏幕：先取消静音播放，再点切换暂停/继续
- 滑动切页：当前页静音自动播，其余页暂停
- `HeaderStrippingWebViewClient`：用 OkHttp 代理子资源请求并剥离 `X-Frame-Options`/`Content-Security-Policy`，用于站点播放器内部自己嵌的跨域 iframe（如视频 CDN 播放器）

## 目录结构

```
android/
  app/
    src/main/java/com/dy/tiktokmode/
      MainActivity.kt              入口、地址栏、模式切换
      SiteConfig.kt                两个站点的链接收集 JS + 详情页样式/播放控制 JS
      VideoPagerAdapter.kt         ViewPager2 的 WebView 列表适配器
      HeaderStrippingWebViewClient.kt  剥离 framing 相关响应头
    src/main/res/layout/           activity_main.xml, item_video_page.xml
```

## 构建

这个仓库里没有提交 Gradle Wrapper 的二进制（`gradle-wrapper.jar`），因为当前环境没有 Android SDK/网络去生成它。用 Android Studio 打开 `android/` 目录即可（File → Open），Android Studio 会自动补全 wrapper 并下载依赖。

或者本地已有 Gradle 的话：

```bash
cd android
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

生成的 APK 在 `app/build/outputs/apk/debug/app-debug.apk`，装到手机上即可。

## 已知限制 / 后续要做的事

- 没有做 DRM（Widevine）支持判断——这两个站点不需要
- `collectLinksJs` 里的两套规则是从原油猴脚本逐字搬过来的；如果站点改版，DOM 选择器要跟着改
- 没做下载/缓存/历史记录之类的浏览器外围功能，目前只聚焦"抖音式刷視頻"这一个功能
- 未做实机测试（当前环境没有 Android 设备/模拟器），建议先用 Android Studio 跑一下模拟器验证两个站点的链接收集和播放是否符合预期
