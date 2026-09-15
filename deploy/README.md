# Alibaba Cloud deployment

Public route: `http://47.97.157.68/studypilot/` (in-app login page).
The pre-existing website at `/` remains unchanged.

- Service: `studypilot.service`, automatically started by systemd.
- Runtime: existing Alibaba Dragonwell Java 21; Java 11 system default is unchanged.
- Application bind: `127.0.0.1:18086`, servlet context `/studypilot`.
- Releases: `/opt/studypilot/releases/`; current symlink `/opt/studypilot/current`.
- Persistent SQLite data: `/var/lib/studypilot/data/studypilot.db`.
- Model key + login credentials: `/etc/studypilot/studypilot.env`, root-owned, mode 0600.
- Reverse proxy: `/etc/nginx/snippets/studypilot-location.conf`, included in the existing `vibecoding.conf` server.
- Original Nginx configuration backup: `/opt/studypilot/backups/`.
- Local web login details: `data/deployment-access.json` (ignored by Git).

## Authentication (in-app login)

Nginx Basic auth has been removed. The application now serves its own login page:

- Login page: `/login.html` (public), everything else requires a session.
- Credentials come from environment variables in `/etc/studypilot/studypilot.env`:
  - `STUDYPILOT_AUTH_USER` (default `away`)
  - `STUDYPILOT_AUTH_PASSWORD` (must be set; login is refused when empty)
- Session: `HttpSession`, cookie `HttpOnly` + `SameSite=Lax`, 7-day idle timeout.
- Brute-force guard: 8 consecutive failures per IP → 60 s lockout.
- Guards: `AuthController`, `AuthInterceptor`, `WebAuthConfig`
  (API returns `401 JSON`, pages redirect to `/login.html`).

### Change the password

```bash
NEW_PASSWORD='your-new-password'
ssh root@47.97.157.68 "sed -i 's|^STUDYPILOT_AUTH_PASSWORD=.*|STUDYPILOT_AUTH_PASSWORD=${NEW_PASSWORD}|' /etc/studypilot/studypilot.env && systemctl restart studypilot"
```

> Note: the password is sent over plain HTTP. Configure TLS (hostname + certificate)
> before treating this deployment as secure.

## Operations over SSH

```bash
systemctl status studypilot
journalctl -u studypilot -n 100 --no-pager
systemctl restart studypilot
nginx -t
```

`../scripts/deploy-aliyun.py` performs the first installation on this inspected server. It intentionally refuses an existing StudyPilot installation. It reads `DEPLOY_SSH_PASSWORD` and `DASHSCOPE_API_KEY` from the current process environment, without writing them into the repository. Do not rerun it as an upgrade script.

## Upgrade procedure (used for the in-app login release)

```bash
NEW=/opt/studypilot/releases/$(date -u +%Y%m%dT%H%M%SZ)
mkdir -p $NEW
# upload the freshly built jar
scp target/studypilot-0.1.0-SNAPSHOT.jar root@47.97.157.68:$NEW/studypilot.jar
ssh root@47.97.157.68 "ln -sfn /var/lib/studypilot/data $NEW/data && cp -a /opt/studypilot/current/kb $NEW/kb && chown -R studypilot:studypilot $NEW"
ssh root@47.97.157.68 "ln -sfn $NEW /opt/studypilot/current && systemctl restart studypilot"
```

Keep the previous release directory for rollback: point `current` back and restart.
