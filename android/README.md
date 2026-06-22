# 抖音模式 — Android WebView App

把 `tiktok-mode.js` 的逻辑搬到了一个原生 Android WebView App 里，去掉了油猴脚本里 iframe + postMessage 那套迂回方案：每条视频现在是一个**独立的顶层 WebView 导航**，而不是嵌入 iframe，所以天然不受 `X-Frame-Options` / `frame-ancestors` 限制。

## 已实现

- **默认直接进入抖音模式**：启动后后台加载 `missav.ai`，抓取链接后立即进入全屏滑动 Feed
- **一键切换普通模式**：Feed 左上角「← 普通模式」切回网页本身样式（Material 3 风格浏览器：后退 / 圆角地址栏 / 刷新 / 菜单）；普通模式右下角「抖音模式」FAB 可再切回
- **内置广告拦截**：基于 host 后缀名单（`assets/ad_hosts.txt`，覆盖 ExoClick/JuicyAds/PopAds/Adsterra 等常见弹窗广告网络）在 `shouldInterceptRequest` 拦截；菜单里可一键开关。另外注入 JS 屏蔽 `window.open` 弹窗劫持
- Material 3 深色主题，矢量图标，圆角控件，底部渐变遮罩 + 标题，右侧操作栏（静音/重载），加载用 ProgressBar 圆形进度
- `ViewPager2` 纵向滑动 Feed，每页一个独立 WebView 直接加载详情页（非 iframe）
- 点击屏幕：先取消静音播放，再点切换暂停/继续；滑动切页当前页静音自动播、其余页暂停
- 返回键：普通模式有历史则后退，否则回到抖音 Feed；Feed 下再按返回退出
- `HeaderStrippingWebViewClient`：广告拦截 + 用 OkHttp 代理子资源请求并剥离 `X-Frame-Options`/`Content-Security-Policy`
- 使用桌面级 Chrome UA，降低 Cloudflare 拦截概率

## 播放修复（重要）

之前"无法播放"的根因：注入的 CSS 用 `body > * { display:none }` 把整页直接子元素都隐藏了，而 `<video>` 嵌在某个子容器深处——父级 `display:none` 时，子级再设 `display:block` 也无法显示，所以视频永远不渲染。

现在改为**把 `<video>` 节点整体搬进我们自建的全屏 `#__tk_stage` 容器**（移动节点而非重建，站点的 hls.js 播放器仍然控制同一个元素），再隐藏其余内容；同时会递归进入同源 iframe 处理。MissAV 的实测结构是：m3u8 地址打包在主页面的混淆 JS 里，播放器在主文档内建 `<video>`，因此该方案适用。

## 获取 APK

由于本仓库的构建环境无法访问 Google 的 Android SDK / Maven 服务器，APK 通过 **GitHub Actions** 自动构建：每次推到本分支会触发 `.github/workflows/android.yml`，构建产物有两个下载入口：

1. 对应 workflow run 页面底部的 **Artifacts → `tiktok-mode-debug-apk`**
2. 自动创建的 **Release**（标签 `apk-build-<run编号>`）里直接附带 `app-debug.apk`

下载 `app-debug.apk` 装到手机即可（需允许"安装未知来源应用"）。

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
