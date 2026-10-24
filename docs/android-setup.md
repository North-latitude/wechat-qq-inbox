# Android 安装与真机验证

> v0.2.0 默认是**手机本地模式**：安装 APK、开通知使用权、加规则即可，不需要后端/电脑/Wi-Fi。本文后半部分的服务器、Token、局域网内容只适用于可选的电脑同步。

## 1. 工具链

- JDK 17（不要用 JDK 21/25 跑 AGP 8.7）
- Android SDK Platform 35、Build-Tools 35.0.0
- Android Studio 或命令行 `sdkmanager`
- 一台 Android 8.0+ 真机（微信/QQ 通知必须在真机上验证）

```powershell
# 在 android/ 目录
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

debug APK 允许明文 HTTP，方便局域网调试；**日常使用应构建 release + HTTPS**。

### Release 签名（可选）

```powershell
keytool -genkeypair -v `
  -keystore unified-inbox.jks `
  -alias unified-inbox `
  -keyalg RSA -keysize 4096 -validity 10000
```

在 `android/keystore.properties` 写入（不要提交；Gradle 会自动读取）：

```properties
storeFile=../unified-inbox.jks
storePassword=...
keyAlias=unified-inbox
keyPassword=...
```

构建：

```powershell
.\gradlew.bat :app:assembleRelease
```

> 若没有 `android/keystore.properties`，release 产物不会被签名；日常安装请用 Android Studio 的签名向导，或按上面的文件配置。

## 2. 首次配置

1. 打开 App。
2. 填后端地址：
   - 第一次家庭 Wi-Fi 测试（debug APK）：`http://<电脑局域网 IP>:8787`，例如 `http://192.168.1.23:8787`。电脑要先运行 `.\scripts\start.ps1 -Lan`。
   - 日常/外网（release APK 只允许 HTTPS）：Tailscale/Caddy 地址，例如 `https://your-machine.your-tailnet.ts.net`。
   - 不要填 `127.0.0.1` 或 `localhost`。
3. 填 Token（`backend/data/token.txt` 或 `.env` 里的 `APP_TOKEN`）。
4. 点「保存」→「测试连接」。
5. 打开「通知使用权」：系统设置 → 应用 → 特殊应用权限 → 通知使用权 → 允许本应用。
6. 添加规则（群名 / 联系人 / 关键词），确认开关为开。
7. 打开「保活设置」：
   - 忽略电池优化；
   - 允许自启动（小米/华为/OPPO/vivo/魅族/三星路径不同，App 内提供跳转）；
   - 允许后台活动 / 锁定后台（厂商差异）。

Android 13+ 不需要 `POST_NOTIFICATIONS` 来读取通知，本项目也不申请它。WorkManager 会在合并清单里加入普通的 `WAKE_LOCK`/`FOREGROUND_SERVICE` 权限；它们不授予读取通知或访问微信/QQ 数据的能力。`CAMERA` 只用于扫描电脑上的本地配置二维码，不上传照片。

## 3. 微信 / QQ 侧检查

- 微信：我 → 设置 → 新消息通知 → 确认「接收新消息通知」开启；「通知显示消息详情」开启。
- QQ：设置 → 消息通知 → 确认新消息通知、通知显示消息内容开启。
- 目标群：可以免打扰，但系统仍需生成通知；如果群设置成“完全不提醒/不显示通知”，Android 不会产生通知副本，采集端无法读取。
- 锁屏通知如果设置为“隐藏内容”，通知正文可能为空，会表现为 `parse_errors` 增加。

> 不同微信/QQ 版本的通知标题结构会变化：有时标题是群名，有时是“微信”而正文带发送者。当前解析器不会强行拆 `发送者: 内容`，避免破坏正文；如果某版本只给“微信”标题，可能只能退化到发送者作为会话名。遇到这种情况先看诊断，再决定是否调整解析。

## 4. 规则边界手测（必须人工）

准备两个账号/两台手机，或在群里让朋友配合。

| 用例 | 操作 | 预期 |
|---|---|---|
| 群名精确命中 | 规则：群名 = `家人群`；在群内发消息 | 页面出现，规则显示“群名 家人群” |
| 群名不命中 | 发到 `家人群2` | 不出现；`规则未命中` +1 |
| 大小写/空白 | 规则 `family group`，群名 `Family  Group` | 命中（忽略大小写/连续空白） |
| 联系人 | 规则：联系人 = 某个昵称；私聊发消息 | 命中；群内同名发送者不应仅因昵称命中 |
| 关键词 | 规则：关键词 = `上线`；正文含“上线了” | 命中 |
| 规则关闭 | 关掉唯一规则后发消息 | 不上传；诊断显示规则未命中/无启用规则 |
| 去重 | 同一条通知重复弹出/更新 | 只出现一条；`重复拦截` 增加 |
| 离线补传 | 开飞行模式发命中消息 → 页面队列 >0 → 恢复网络 | 自动补传，不重复 |
| 进程被杀 | 强行停止 App；等待 >3 分钟 | 页面显示离线/可能掉线 |
| 重启手机 | 重启后不打开 App | 通知监听恢复；心跳重新出现 |
| 通知无详情 | 群免打扰 + 锁屏隐藏内容 | 诊断出现解析失败/摘要跳过，而不是错误上传空内容 |

## 5. OEM 保活要点

- **小米/Redmi**：省电策略 → 无限制；自启动；后台弹出界面。
- **华为/荣耀**：应用启动管理 → 手动管理，允许自启动/关联启动/后台活动。
- **OPPO/一加/realme**：允许自动启动、允许后台运行；关闭“智能省电”对 App 的限制。
- **vivo/iQOO**：后台高耗电 → 允许；自启动。
- **三星**：电池 → 后台使用限制 → 从“休眠应用”中移除。
- **魅族**：权限管理 → 后台管理 → 允许后台运行。

系统升级、清理软件、`强行停止` 都会让监听服务失效；页面掉线提示就是为此设计的，不要把它当成“永远在线”的承诺。

## 6. 构建排错

| 现象 | 处理 |
|---|---|
| `Unsupported class file major version` | 用 JDK 17，设置 `JAVA_HOME` |
| `SDK location not found` | 在 `android/local.properties` 写 `sdk.dir=...`，或设 `ANDROID_HOME` |
| `compileSdk 35 not found` | `sdkmanager "platforms;android-35" "build-tools;35.0.0"` |
| WorkManager 初始化失败 | 不要禁用默认 `androidx.startup`；本项目使用默认初始化 |
| release 无法连 HTTP | 这是有意的；用 HTTPS 或 debug 构建 |
