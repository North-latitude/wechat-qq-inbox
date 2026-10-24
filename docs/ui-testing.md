# 在电脑上试运行 / 排查 UI

手机本地模式不依赖后端，所以电脑上只需要 Android 模拟器或真机，不需要 Python/Node。

---

## 方式 1：Android Studio 模拟器（最推荐看 UI）

### 安装

1. 安装 [Android Studio](https://developer.android.com/studio)。
2. 打开 **SDK Manager**，安装：
   - Android 15 (API 35) SDK Platform
   - Android SDK Build-Tools 35
   - Android Emulator
   - 一个系统镜像，例如 `Google APIs x86_64` API 35
3. 打开 **Device Manager** → **Create Device**：
   - 选 Pixel 7
   - 系统镜像选 API 35
   - 完成并启动模拟器

### 运行

1. Android Studio → **Open** → 选择：

   ```
   .\android
   ```

2. 等 Gradle Sync 完成。
3. 顶部运行配置选 `app`，设备选刚创建的模拟器，点绿色运行。
4. App 会直接进入「统一收件箱」。

### 造一条测试消息

debug 版才有这个菜单：

1. 收件箱右上角 **加号气泡** 图标；
2. 点它插入一条测试消息；
3. 会话列表会出现「测试群」；点进去能看到聊天记录。

用它检查：

- 卡片间距、来源标签、时间、正文、规则文字；
- 来源/会话下拉、搜索、只看未读；
- 点卡片已读、全部已读；
- 深色模式、字体放大、横屏。

> 这个按钮只插入本地 SQLite 测试数据，不经过微信/QQ，也不测试通知监听。

### 测试规则弹窗

右上角 →「采集与同步设置」→「规则管理」→「添加规则」。
检查输入框提示是否还重叠、键盘弹出是否遮挡「保存」。

---

## 方式 2：真机 + USB 调试（最接近实际）

1. 手机打开开发者选项 → USB 调试。
2. 数据线连电脑，手机弹窗选“允许 USB 调试”。
3. PowerShell：

   ```powershell
   adb devices
   adb install -r ".\UnifiedInbox-debug.apk"
   ```

4. 打开 App，按正常流程开通知使用权、加规则。

看崩溃日志：

```powershell
adb logcat -v time | Select-String "AndroidRuntime|FATAL|UnifiedInbox"
```

截图：

```powershell
cmd /c "adb exec-out screencap -p > .\.tmp\screen.png"
```

只想在电脑上看手机画面，可以装 [scrcpy](https://github.com/Genymobile/scrcpy)。

---

## 方式 3：只看布局 XML（最快，但不测运行时）

Android Studio 打开：

```
android/app/src/main/res/layout/activity_inbox.xml
android/app/src/main/res/layout/dialog_rule.xml
```

右上角切 Design / Code，改不同设备尺寸和深色模式预览。

这种方式不会执行 Kotlin、不会查数据库，只适合看间距和文字。

---

## 常见问题

| 现象 | 处理 |
|---|---|
| 模拟器启动失败 | BIOS 开虚拟化；Windows 开 Hyper-V/WHPX；Android Studio 会提示 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 旧 APK 签名不同；卸载旧版再装（会清本地数据） |
| 收件箱没有「插入测试消息」 | 装的是 release 版；用 debug APK |
| App 闪退 | 抓 `adb logcat`，把 `FATAL EXCEPTION` 那段发我 |
| 测试消息能显示，但真实微信不显示 | 这是通知监听问题：通知使用权、微信通知详情、规则、保活，见 `HOW_TO_USE.md` 第 6 节 |
| 模拟器里没有微信/QQ | 模拟器只适合看 UI；端到端通知测试建议用真机 |

---

## 当前 UI 修复说明

规则弹窗原来在同一个输入框上同时设置了 `TextInputLayout` 和 `TextInputEditText` 的 hint，
Material 组件会同时绘制两层文字，导致「会话名或关键词」重叠。

现在：

- 只保留 `TextInputLayout` 的浮动 label；
- 示例文字放到 helper text；
- 输入框改为单行。

新版 APK：`v0.4.1-debug`。
