# 设计参考与取舍

本项目是“微信 / QQ 特定消息统一收件箱”，不是通用通知转发平台。下面这些项目只用于参考**工程思路**；没有复制代码，也没有引入它们的协议接入方案。

## 参考了什么

| 项目 | 参考点 | 本项目落地 |
|---|---|---|
| [SmsForwarder](https://github.com/pppscn/SmsForwarder) | 通知监听型工具的边界：规则与发送通道分离，采集端只做本地筛选，转发失败可重试 | `RuleEngine` 与 `UploadCoordinator` 分离；规则未命中只加计数；outbox 持久化 |
| [noticeTransmit](https://github.com/fnthinklevi/noticeTransmit) | 隐私优先：本地处理、Header-only Token、强制 HTTPS、规则逐条开关、历史记录 | Token 只走 `Authorization` 头；release 禁明文；每条规则独立开关；本地命中日志 |
| [message-mirror](https://github.com/Dragon-Born/message-mirror) | 断网/进程被杀时不丢消息：持久化队列、重试、保活与在线状态 | SQLite outbox + WorkManager 指数退避；心跳 + 页面掉线提示 |
| [Pulsar](https://github.com/Lumingtianze/Pulsar) | 自托管推送服务的 PWA 外壳、离线可用、单页收件箱 | Vite PWA、service worker 只缓存壳、WebSocket + 轮询降级 |

## 明确没有采用

- **没有采用 noticeTransmit 的 IF-THEN / AND-OR 规则编辑器。** 第一版只需要群名、联系人、关键词的 OR 匹配；增加 AND 组合和优先级会提高配置成本，也让“为什么没命中”更难解释。
- **没有采用正则。** 需求明确第一版不做正则；正则的边界和性能问题会拖慢排查。
- **没有采用前台服务 + WakeLock 强行保活。** 通知监听服务由系统绑定；常驻通知对个人日常使用打扰大，也容易让“掉线”被掩盖。当前选择是心跳 + 明确掉线提示 + 保活引导；如果实测发现被杀严重，再单独评估前台服务，而不是悄悄加。
- **没有采用 SQLCipher / AES 数据库加密。** 这会引入密钥管理和迁移复杂度；当前依赖 Android 应用沙箱 + `allowBackup=false`。如果设备会root或被他人长期控制，应升级到 SQLCipher/AndroidKeyStore 方案。
- **没有采用证书固定（pinning）。** 自托管证书可能轮换；默认固定会把“证书过期”变成“永久掉线”。需要时再按明确轮换流程加。
- **没有采用多通道推送。** 只保留浏览器通知这一种可选通道，避免同时接 Bark/ntfy/Web Push 带来的维护面。

## 绝对禁止的参考

以下方案**不是参考对象**，发现即拒绝：

- NapCat、OneBot、Wechaty、ItChat、任何“扫码登录 / 协议收发消息”的桥接。
- Hook、Xposed/LSPosed、Frida、VirtualXposed、Magisk 模块、方法替换、注入。
- AccessibilityService、UI 自动化、模拟点击、读取微信/QQ 私有数据库。

原因：这些方案会触碰客户端内部或协议，既违反本项目的硬约束，也会把“封号风险”从“系统通知副本”扩大到“客户端行为”。本项目的唯一采集入口是 Android `NotificationListenerService`。

## 许可证说明

上述项目各自使用不同许可证；本项目没有复制其代码，因此不包含它们的许可证文本。若未来要复用代码，必须先核对许可证并保留归属。
