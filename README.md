# 统一收件箱（微信 / QQ）

**手机本地模式**：Android App 自己完成通知监听、规则过滤、SQLite 存储和收件箱展示。**不需要电脑、不需要 Wi-Fi、不需要后端、不需要填 IP/Token**，打开就能用。

它只读取 Android 系统通知副本（`NotificationListenerService`），不登录微信/QQ，不扫码，不注入，不逆向协议，不修改客户端。

> **第一次用？** 看 [保姆级使用指南](HOW_TO_USE.md)。
>
> **APK**：下载 [v0.4.0-debug](https://github.com/North-latitude/wechat-qq-inbox/releases/tag/v0.4.0-debug) 里的 `UnifiedInbox-debug.apk`。
>
> **可选电脑同步**：仓库仍保留 FastAPI + Vue PWA，给想在电脑上再看一份的人用；它不是手机本地模式的前提。需要时再双击 `一键启动.bat`。

## 它解决什么

- 微信和 QQ 的特定消息在同一个页面出现，来源清晰。
- 只收命中本地规则的消息；未命中的通知连正文都不会离开手机。
- 未读清楚，支持单条 / 批量 / 按筛选全部标记已读。
- 手机掉线、通知没来、规则没命中、通知没有详情，都能在「采集诊断」里区分原因。
- 断网先存手机 SQLite，恢复网络后补传；同一条不会重复上传。

## 安全边界（硬约束）

| 允许 | 明确禁止 |
|---|---|
| `NotificationListenerService` 读取系统通知副本 | Hook、注入、Xposed/LSPosed、Frida、VirtualXposed |
| 本地规则预过滤、本地去重、本地离线队列 | `AccessibilityService`、UI 自动化、模拟点击 |
| 只上传命中规则的消息 | 登录微信/QQ、扫码授权、修改客户端 |
| HTTPS + Bearer Token | 协议接入（NapCat / OneBot / Wechaty 等）、私有数据库读取 |
| 心跳只带计数器、时间、规则类型 | 上传全量通知、上传未命中正文、上传规则的具体群名/关键词 |

**只参考开源项目的设计思路，不复制它们的协议方案。** 任何“通过协议登录/收发微信”的建议都直接拒绝并改回通知监听。详见 [设计参考](docs/design-references.md) 和 [SECURITY.md](SECURITY.md)。

## 功能范围

### 已有

- **采集端 + 本地收件箱（Android / Kotlin + Jetpack Compose）**
  - 只监听 `com.tencent.mm` 和 `com.tencent.mobileqq`。
  - 解析标题、正文、大文本、子文本、`MessagingStyle` 多消息；跳过系统折叠摘要。
  - 群名 / 联系人 / 关键词规则，逐条开关；规则 OR 匹配；空规则 = 不收。
  - 命中消息直接写入手机 SQLite，并在 App 内显示；完全不需要电脑/网络。
  - **类微信/QQ 的会话列表**：按群 / 联系人聚合，显示最后一条消息、时间和未读数。
  - **聊天记录页**：点会话进入，按时间正序显示历史消息、日期分隔、命中规则和时间。
  - 搜索、来源筛选（微信 / QQ）、只看未读、全部已读。
  - **删除**：长按消息多选删除，或清空整个会话的本地记录。
  - 本地 SHA-256 去重（7 天）；命中日志可回看最近 50 条。
  - 可选电脑同步时才有 outbox/重试/心跳；本地模式不启动这些网络逻辑。
  - 保活引导：通知使用权、电池优化、应用详情、常见 OEM 自启动页。
- **可选后端（FastAPI + SQLite）**
  - 批量接收，`client_id` 幂等；空 `matched_rules` 直接拒绝。
  - 列表、搜索、来源/会话筛选、未读、单条/批量/全部已读。
  - `/status` 汇总采集端心跳、最后消息、今日未读、可能掉线原因。
  - WebSocket 实时推送，一次性 ticket + 首帧认证，Token 不进 URL。
- **可选页面（Vue 3 + Vite PWA）**
  - 微信 / QQ 同页、来源筛选、会话筛选、关键词搜索。
  - 未读角标、单条/批量/全部已读、加载更多。
  - 顶部在线状态、最后消息、今日未读、掉线提示。
  - 采集诊断：通知数、命中数、规则未命中、重复、解析失败、折叠摘要、队列、规则类型统计。

### 明确不做

- 回复 / 发消息；登录微信/QQ；协议接入；多用户注册；AI 分类/摘要；可视化规则编排；正则；iPhone 采集端。
- 第一版不做 Web Push / Bark / ntfy 多通道并存。

## 架构

手机本地模式，默认且不需要电脑：

1. 微信 / QQ 产生系统通知；
2. `NotificationListenerService` 读取通知副本；
3. 本机解析标题、正文、会话名；
4. 本机规则匹配：
   - 未命中：丢弃，不保存正文，只加计数；
   - 命中：写入手机 SQLite（`messages` / `dedup` / `hit_log`）；
5. App 按群 / 联系人聚合会话，点进去看聊天记录。

数据流原则：

1. 规则在手机上执行；未命中消息不持久化正文。
2. 本地模式只写手机 SQLite，完全不需要网络。
3. 只有显式配置了后端并开启同步，命中消息才额外进入上传 outbox。

## 快速开始（手机本地模式）

### 1. 安装 APK

下载：

https://github.com/North-latitude/wechat-qq-inbox/releases/tag/v0.4.0-debug

或使用本地文件：

```
.\UnifiedInbox-debug.apk
```

### 2. 打开 App，开通知使用权

App 启动后直接进入本地收件箱。顶部如果提示「通知使用权未开启」，点「去开启」：

系统设置 → 通知使用权 → 允许「统一收件箱」。

### 3. 添加规则

右上角菜单 →「采集与同步设置」→「规则管理」→「添加规则」。

- 群名必须和通知标题中的会话名完全一致（只忽略大小写/连续空格）。
- 联系人按会话名匹配。
- 关键词匹配正文。
- 没有启用规则时，不会收任何消息（有意设计）。

### 4. 微信/QQ 通知设置

- 允许通知；
- 打开「通知显示消息详情」；
- 群可以免打扰，但系统必须生成通知；完全静音/不显示通知时，App 也读不到。

### 5. 验证

让朋友在目标群发一条消息 → 手机通知栏出现 → 打开 App，收件箱出现；点消息可标为已读，右上角可全部已读。

### 6. 保活

「采集与同步设置」→「保活指南」：忽略电池优化、允许自启动、允许后台运行。
Android 系统仍可能杀进程；App 不承诺 100% 常驻。

### 可选：电脑同步

只有想在电脑上再看一份时才需要，**手机本地模式不依赖它**：

```powershell
.\一键启动.bat
```

手机和电脑填同一个地址/Token 后，命中的消息会同时保存在手机和电脑；详见 [部署与 HTTPS](docs/deployment.md)。

## 规则语义（v1）

- **群名**：与通知标题中的会话名做去首尾空格、合并连续空白、忽略大小写的**完全匹配**。
- **联系人**：同样对会话名完全匹配（不拿群消息里的发送者去匹配，避免“白名单联系人出现在任何群”都触发）。
- **关键词**：正文包含该词（忽略大小写），不做正则。
- 多条规则之间是 **OR**：任一条命中即收进本地收件箱；若开启电脑同步，才额外上传。
- 每条规则可单独开关；全部关闭 = 不收。
- 空规则 = 不收，这是有意的隐私默认值。

> 为什么不做 AND/正则/可视化编排：第一版目标是稳定、少维护；规则一多，AND 组合和正则调试会迅速变成维护负担。后续如果需要再加，但要先保证当前边界可解释。

## 漏消息排查

手机本地模式按顺序检查：

1. **通知栏有没有这条通知？** 没有 → 微信/QQ 通知被关、群完全静音、系统免打扰/过滤。
2. **通知使用权开着吗？** 系统设置 → 通知使用权 → 统一收件箱。
3. **有启用规则吗？** 空规则 = 不收；群名要完全一致。
4. **通知有正文吗？** 群免打扰且不显示详情、锁屏隐藏内容时，系统通知没有正文。
5. **App 被系统杀了吗？** 打开保活指南，按厂商设置电池/自启动；被杀期间无法补抓。
6. **开了电脑同步但连不上？** 这是可选功能，不影响手机本地收件箱；检查同一 Wi-Fi/Tailscale。

完整对照见 [故障排查](docs/troubleshooting.md)（其中网络部分只适用于可选电脑同步）。

## 分享给朋友：数据隔离

手机本地模式下，每个朋友只要安装 APK：

```
朋友 A 手机 → 朋友 A 的 App 数据库
朋友 B 手机 → 朋友 B 的 App 数据库
你的手机    → 你的 App 数据库
```

不需要电脑、不需要服务器、不需要共用 Token；数据天然隔离。

如果朋友还想在电脑上看一份，再让他在**自己的电脑**上跑 `一键启动.bat`，用他自己的地址/Token。不要把别人的手机指向你的电脑。

## 测试与验收

```powershell
.\scripts\check.ps1
```

包含后端 pytest、静态安全扫描、前端构建；有 Android SDK 时再跑 Android 单测和 debug 构建。

- 后端：`backend/tests/test_api.py`（鉴权、幂等、筛选、搜索、分页、已读、心跳判决、WebSocket）。
- Android：`RuleEngineTest`、`MessageKeysTest`（JVM 纯逻辑，不依赖 Android）。
- 安全：`scripts/security_check.py`（Manifest 权限、无 Accessibility/协议库、release 禁明文、Token 不进 URL、无 `v-html` 等）。
- 电脑上试运行 UI、模拟器、adb 抓日志：[UI 测试与排查](docs/ui-testing.md)。
- 两周验收清单与真机手测步骤见 [验收与测试](docs/testing.md)。

## 目录

```
backend/         FastAPI + SQLite；API、WebSocket、静态托管
web/             Vue 3 + Vite PWA；页面、诊断、设置
android/         Kotlin NotificationListenerService 采集端
docs/            API、部署、Android、排查、测试、设计参考
scripts/         本地启动、检查、图标生成
deploy/          Caddy 示例
一键启动.bat     Windows 双击启动（可选电脑同步）
Dockerfile       多阶段构建：前端 + 后端单镜像
docker-compose.yml
HOW_TO_USE.md    保姆级使用指南（先看这个）
```

## 设计参考

只参考思路，不复制代码，不引入任何协议接入：

- [SmsForwarder](https://github.com/pppscn/SmsForwarder)：规则/通道分离、通知监听型转发工具的成熟边界。
- [noticeTransmit](https://github.com/fnthinklevi/noticeTransmit)：本地规则过滤、Header-only Token、AndroidKeyStore、HTTPS 强制。
- [message-mirror](https://github.com/Dragon-Born/message-mirror)：持久化重试队列与保活取舍。
- [Pulsar](https://github.com/Lumingtianze/Pulsar)：自托管推送服务的 PWA 壳/离线思路。

**没有参考、也不会引入**：NapCat、OneBot、Wechaty、任何微信/QQ 协议登录或收发库。详见 [设计参考](docs/design-references.md)。

## 已知限制

- 依赖通知正常生成；群免打扰且系统不显示详情时，通知副本本身没有内容。
- App 被系统杀掉会漏消息；本地模式没有后台服务器补抓，只能靠保活设置降低概率。
- 不能拉取历史记录；只能从安装并授权之后开始。
- 本地 SQLite 不加密（依赖 Android 应用沙箱）；root/被完全控制的设备不保证安全。
- 本地模式不需要网络；电脑同步是可选功能，debug APK 才允许局域网 HTTP，日常同步请用 HTTPS。

## License

本项目使用 **MIT License**，允许个人和商业使用、修改、分发，但必须保留版权声明和许可证文本。完整条款见 [LICENSE](LICENSE)。

> 说明：MIT 允许商业使用；分发时请保留版权声明和许可证文本。
