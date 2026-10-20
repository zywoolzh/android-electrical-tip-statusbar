# 电气知识卡片

Android 13+ 桌面知识大卡片。Kotlin + Compose 管理页，原生 RemoteViews 桌面组件，SQLite 本地知识库，WorkManager 自动轮换。v2 替代 v1 的前台常驻通知方案，不需要通知权限或常驻服务。

## 使用

安装 APK 后打开“电气知识卡片” → 添加桌面大卡片 → 确认。也可长按桌面空白处 → 小组件/添加工具 → 电气知识大卡片。

- 默认 4×4，支持缩放（实际大小由桌面决定）。浅色圆角卡片显示分类、标题和分段摘要。
- 上页/下页浏览长摘要，上一条/下一条换知识；点标题阅读完整正文；点分类进入管理页面。
- 五类共 15 条扩写知识，支持手机新增/编辑/删除、搜索、收藏，以及自定义分类。
- 设置 1—365 小时/天轮换或暂停，支持选择参与轮换的分类。
- 所有卡片共享当前知识和分类；分页位置独立。最后一个桌面组件移除后取消定时任务。
- 省电策略可能延迟后台更新；手动换条可立即请求更新。组件不显示在锁屏或其他应用上方。

## CSV 知识库

先导出知识库作为编辑模板，或使用 [knowledge-template.csv](docs/knowledge-template.csv)。在 Excel 中编辑后另存为 **CSV UTF-8**，再从知识库页面导入。

| 列 | 中文表头 | 规则 |
| --- | --- | --- |
| id | 编号 | 可选；1—80 位字母、数字、短横线或下划线。同编号更新，空编号新建 |
| category | 分类 | 必填，最多 30 字，可自定义 |
| title | 标题 | 必填，最多 80 字 |
| summary | 摘要 | 必填，最多 2000 字；建议 150—250 字，组件自动分页 |
| body | 正文 | 必填，最多 20000 字 |
| source | 来源 | 可选，最多 500 字，建议填写资料名称/版本/适用条件 |
| favorite | 收藏 | 可选，0/1 或 true/false；更新已有条目时保留本机收藏选择 |

支持 UTF-8 BOM、带引号的逗号、换行及双引号。每次最多 2000 条/2 MB。导入前预览新增/更新数量；任一记录格式错误则不写入；未出现在导入文件中的原有记录不会删除。

数据库保存在本机，无账户、无云端同步。卸载/换机前请导出备份。内置内容为原理学习笔记，不是标准原文或现场操作文件；可编辑补充正式出处。

## 开发与验证

JDK 17、Gradle Wrapper 8.9（含发行包校验）、AGP 8.7.3、Kotlin 2.0.21、compile/target SDK 35。

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

云工作区的工具链位于 `/workspace/jdk17` 与 `/workspace/android-sdk`，使用：

```bash
export JAVA_HOME=/workspace/jdk17
export ANDROID_HOME=/workspace/android-sdk
export ANDROID_USER_HOME=/workspace/.android
export GRADLE_USER_HOME=/workspace/.gradle-home
export JAVA_TOOL_OPTIONS="-Duser.home=/workspace -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080"
./gradlew --no-daemon --max-workers=2 testDebugUnitTest lintDebug assembleDebug
```

上面的代理和信任库设置仅用于该云环境，普通本地开发无需照搬。APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

单元/集成测试覆盖 CSV 格式与回读、数据库持久化、整批导入失败、更新去重、收藏保留、分类轮换、删除后的回退、组件布局加载与分页、后台轮换及暂停。Robolectric 在模拟 Android API 33 的 JVM 环境执行；不能替代真机桌面兼容性验证。

## 发布

推送版本标签（例如 `v2.0.0`）触发 GitHub Actions；也可手动指定已有标签。工作流检出该标签、运行测试和 Lint、构建 APK 并上传至 Release，附 CSV 模板与 SHA-256。

目前发布 Debug 测试包；不同 Runner 的调试签名可能不同。与旧包签名冲突时需先导出数据，再卸载旧包安装。正式版本需配置受保护且固定的签名密钥，不应将私钥提交到源码仓库。
