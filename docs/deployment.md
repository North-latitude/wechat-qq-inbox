# 部署与 HTTPS

目标：手机能访问家里的后端，且不把 8787 端口直接暴露到公网。日常使用请走 HTTPS；第一次在家庭 Wi-Fi 测试可以用 debug APK + HTTP。

## 最快：家庭局域网（只用于第一次测试）

1. 电脑启动：

   ```powershell
   .\scripts\start.ps1 -Lan
   ```

   记下打印的 `http://192.168.x.x:8787`。

2. 手机和电脑连同一个 Wi-Fi；手机浏览器先打开这个地址。
3. Android debug APK 里填同一个地址。
4. Windows 防火墙弹窗选“专用网络允许”；没有弹窗就管理员执行：

   ```powershell
   New-NetFirewallRule -DisplayName "Unified Inbox 8787" -Direction Inbound -Protocol TCP -LocalPort 8787 -Action Allow -Profile Private
   ```

> HTTP 下 Token 和消息是明文，只适合可信家庭网络。不要端口转发到公网，不要在咖啡厅/公共 Wi-Fi 使用。

## Docker 一键部署

```powershell
Copy-Item .env.docker.example .env
.\scripts\new-token.ps1
notepad .env          # 填入 APP_TOKEN
docker compose up -d --build
```

- 数据在 `./data`；每个朋友用自己的项目目录、自己的 `.env`/Token。
- Compose 默认把 `8787` 映射到宿主机；手机访问 `http://<电脑IP>:8787`（debug）或 HTTPS 代理。
- 改端口：在 `.env` 设置 `APP_PORT=8788`，手机地址同步改。
- 停止：`docker compose down`；数据不会删除。

## 方案 A：Tailscale Serve（推荐，个人使用）

Tailscale 会给设备一个受信任的 `https://<machine>.<tailnet>.ts.net` 证书，手机安装 Tailscale 并登录同一 tailnet 即可访问；后端仍然只监听本机回环。

1. 电脑和手机安装 Tailscale，登录同一账号。
2. 启动后端：

   ```powershell
   .\scripts\start.ps1
   ```

   Docker 版直接 `docker compose up -d` 即可。

3. 另开终端：

   ```powershell
   tailscale serve --bg 8787
   tailscale serve status
   ```

   不同版本的参数可能是 `tailscale serve --bg http://127.0.0.1:8787`，以 `tailscale serve --help` 为准。

4. 手机浏览器打开 `tailscale serve status` 显示的 HTTPS 地址；Android 采集端也填这个地址。
5. PWA 安装、浏览器通知都需要 HTTPS；Tailscale 地址满足条件。

优点：不开放公网端口，证书受 Android 信任，维护量小。
注意：关掉电脑/Tailscale 后页面会离线；这正是采集端在线状态要暴露的情况。

## 方案 B：Caddy + 公网域名

有域名、且能开 80/443 时：

`deploy/Caddyfile`：

```caddyfile
inbox.example.com {
    encode zstd gzip
    reverse_proxy 127.0.0.1:8787
}
```

```powershell
caddy run --config deploy\Caddyfile
```

Caddy 自动申请 Let's Encrypt 证书。Android 系统和 App 都信任公共 CA，release 构建可直接用 HTTPS。

安全要求：

- 只代理 `127.0.0.1:8787`，不要 `reverse_proxy 0.0.0.0:8787`。
- 不要把 8787 映射到路由器公网。
- DNS 只指向需要它的地址；尽量加防火墙白名单。
- 定期更新 Caddy。

## 方案 C：Caddy 内部 CA（仅局域网，较麻烦）

```caddyfile
https://inbox.home.arpa {
    tls internal
    reverse_proxy 127.0.0.1:8787
}
```

需要把 Caddy 根证书安装到 Android。注意：**Android 7+ 的应用默认不信任用户安装的 CA**，除非 App 的 network security config 显式允许。本项目的 release 配置出于安全考虑没有允许用户 CA；debug 配置也未必适合长期使用。因此该方案只建议临时调试，长期请用 Tailscale 或公共域名证书。

## Token

- 首次启动若 `.env` 的 `APP_TOKEN` 为空，会生成 `backend/data/token.txt`。
- 修改 Token：编辑 `.env` 的 `APP_TOKEN`，重启后端；旧 Token 立即失效。手机和浏览器都要更新。
- Token 只通过 `Authorization: Bearer` 头传输；WebSocket 用一次性 ticket，首帧认证。
- 不要把 Token 放进反向代理的 URL 日志、书签或截图。

## 开机自启（Windows）

用任务计划程序：

1. 创建基本任务，触发器“登录时”。
2. 操作：启动程序 `powershell.exe`，参数：
   ```
   -NoProfile -ExecutionPolicy Bypass -File "<repo>\scripts\start.ps1"
   ```
3. 勾选“不管用户是否登录都要运行”需要保存密码；更简单是“只在用户登录时运行”。
4. 不要设置“停止现有实例”；重复启动会端口冲突。

Tailscale 也可以设为开机启动。

## 备份

- 数据：`backend/data/inbox.db`（连同 `-wal`/`-shm` 一起停服备份更稳妥）。
- 建议定期执行 SQLite 在线备份：
  ```powershell
  .\.venv\Scripts\python.exe -c "import sqlite3; s=sqlite3.connect(r'backend/data/inbox.db'); d=sqlite3.connect(r'backup/inbox-2026-10-22.db'); s.backup(d); d.close(); s.close()"
  ```
- 不要把 `backend/data`、`.env` 提交到 Git。
- 备份文件包含消息正文和群名，按敏感数据保管。

## 反向代理检查

```powershell
curl.exe -I https://your-host/
curl.exe -H "Authorization: Bearer <TOKEN>" https://your-host/api/v1/ping
```

预期：

- `/` 返回 `text/html`，带 CSP；
- `/api/v1/ping` 返回 `{"ok":true,...}`；
- 不带 Token 返回 401；
- HTTP 自动跳转 HTTPS（Caddy 默认）；
- WebSocket 能升级（页面显示“实时连接”）。
