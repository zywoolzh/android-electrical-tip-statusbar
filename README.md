# 电气知识状态栏

面向 Android 13 及以上设备的 Kotlin + Jetpack Compose 应用。用户主动开启后，前台服务会在状态栏常驻显示一条数据中心电气知识，并按小时或天自动轮换。

## 功能

- 配电、MCC、冷水机组、UPS、数据中心规范五类内置知识点
- Android 前台服务常驻通知，通知内可直接“换一条”
- 自定义 1—365 小时或天的轮换间隔
- 设置页支持启停自动轮换、保存间隔、手动切换
- 开机后按已保存设置恢复服务

## 构建

需要 JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0。

```bash
export ANDROID_HOME=/workspace/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew testDebugUnitTest assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

## Android 行为说明

首次启动需授权通知权限，然后点击“保存并显示常驻通知”。系统或厂商的省电策略仍可能停止后台进程；如需长期运行，请在设备设置中允许该应用后台运行。前台服务声明为 `specialUse`，若上架 Google Play，需要在 Play Console 中说明其持续展示教育内容的用途。
