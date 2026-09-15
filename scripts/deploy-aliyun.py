"""Deploy a built jar to the inspected Alibaba Linux host. Secrets come from environment only."""
import hashlib
import json
import os
import secrets
import shlex
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
import paramiko

root = Path(__file__).resolve().parents[1]
host = os.environ.get("DEPLOY_HOST", "47.97.157.68")
release = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
remote = "/opt/studypilot/releases/" + release
jar = root / "target/studypilot-0.1.0-SNAPSHOT.jar"
key = os.environ["DASHSCOPE_API_KEY"]
password = os.environ["DEPLOY_SSH_PASSWORD"]
client = paramiko.SSHClient()
client.load_system_host_keys()
known = root / "data/studypilot-known-hosts"
if known.exists(): client.load_host_keys(str(known))
else: client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(host, username="root", password=password, timeout=20, look_for_keys=False, allow_agent=False)
server_key = client.get_transport().get_remote_server_key()
client.get_host_keys().add(host, server_key.get_name(), server_key)
client.save_host_keys(str(known))
sftp = client.open_sftp()

def run(command):
    _, out, err = client.exec_command(command, timeout=90)
    text = out.read().decode(errors="replace")
    error = err.read().decode(errors="replace")
    code = out.channel.recv_exit_status()
    if code: raise RuntimeError("Remote command failed: " + str(code) + " " + error.replace(key, "[REDACTED]"))
    return text

def put_text(path, content, mode=0o644):
    with sftp.open(path, "w") as file: file.write(content)
    sftp.chmod(path, mode)

try:
    print("Preparing isolated release " + release, flush=True)
    run("test ! -e /etc/systemd/system/studypilot.service && test ! -e /etc/studypilot/studypilot.env")
    before_hash = run("sha256sum /var/www/aivibecoding/index.html").split()[0]
    run("id studypilot >/dev/null 2>&1 || useradd --system --home-dir /var/lib/studypilot --shell /sbin/nologin studypilot")
    run("install -d -m 755 /opt/studypilot/releases /opt/studypilot/backups /etc/nginx/snippets; install -d -m 700 /etc/studypilot; install -d -o studypilot -g studypilot -m 750 /var/lib/studypilot /var/lib/studypilot/data")
    run("mkdir -p " + remote + "/kb")
    sftp.put(str(jar), remote + "/studypilot.jar")
    expected = hashlib.sha256(jar.read_bytes()).hexdigest()
    if run("sha256sum " + remote + "/studypilot.jar").split()[0] != expected: raise RuntimeError("Jar checksum mismatch")
    for doc in (root / "kb").glob("*.md"): sftp.put(str(doc), remote + "/kb/" + doc.name)
    # Use the independently validated sample DB, never the user's day-to-day DB.
    seed = root / "data/deploy-seed.db"
    with sqlite3.connect("file:" + str(root / "data/live-validation-20260914.db") + "?mode=ro", uri=True) as source:
        names = {row[0] for row in source.execute("select name from kb_document")}
        if names != {p.name for p in (root / "kb").glob("*.md")}: raise RuntimeError("Seed database is not the expected sample corpus")
        with sqlite3.connect(seed) as dest: source.backup(dest)
    run("test ! -e /var/lib/studypilot/data/studypilot.db")
    sftp.put(str(seed), "/var/lib/studypilot/data/studypilot.db")
    run("chown studypilot:studypilot /var/lib/studypilot/data/studypilot.db; chmod 640 /var/lib/studypilot/data/studypilot.db; ln -s /var/lib/studypilot/data " + remote + "/data")
    env = "DASHSCOPE_API_KEY=" + key + "\nSERVER_ADDRESS=127.0.0.1\nSERVER_PORT=18086\nSERVER_SERVLET_CONTEXT_PATH=/studypilot\nSPRING_DATASOURCE_URL=jdbc:sqlite:/var/lib/studypilot/data/studypilot.db\nAPP_KB_AUTO_SEED=false\n"
    put_text("/etc/studypilot/studypilot.env", env, 0o600)
    put_text("/etc/systemd/system/studypilot.service", (root / "deploy/studypilot.service").read_text())
    access_password = secrets.token_urlsafe(14)
    inp,out,err = client.exec_command("openssl passwd -6 -stdin")
    inp.write(access_password + "\n"); inp.flush(); inp.channel.shutdown_write()
    hashed = out.read().decode().strip()
    if out.channel.recv_exit_status() != 0 or not hashed.startswith("$6$"): raise RuntimeError("Password hash failed")
    put_text("/etc/nginx/studypilot.htpasswd", "study:" + hashed + "\n", 0o640)
    run("chown root:nginx /etc/nginx/studypilot.htpasswd")
    put_text("/etc/nginx/snippets/studypilot-location.conf", (root / "deploy/nginx-location.conf").read_text())
    config_path = "/etc/nginx/conf.d/vibecoding.conf"
    with sftp.open(config_path) as f: config = f.read().decode()
    marker = "    index index.html;"
    if config.count(marker) != 1: raise RuntimeError("Existing nginx config changed; manual review required")
    backup = "/opt/studypilot/backups/vibecoding-" + release + ".conf"
    put_text(backup, config)
    run("ln -s " + remote + " /opt/studypilot/current")
    run("systemctl daemon-reload; systemctl enable --now studypilot")
    print("Application service started", flush=True)
    put_text(config_path, config.replace(marker, marker + "\n    include /etc/nginx/snippets/studypilot-location.conf;"))
    try:
        run("nginx -t")
        run("systemctl reload nginx")
    except Exception:
        put_text(config_path, config)
        raise
    if run("sha256sum /var/www/aivibecoding/index.html").split()[0] != before_hash: raise RuntimeError("Existing website changed unexpectedly")
    access = {"url":"http://"+host+"/studypilot/", "username":"study", "password":access_password}
    (root / "data/deployment-access.json").write_text(json.dumps(access, indent=2), encoding="utf-8")
    metadata = {"host":host, "release":release, "jar_sha256":expected, "original_site_sha256":before_hash, "nginx_backup":backup}
    (root / "output/validation/deployment.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    print(json.dumps({"url":access["url"],"username":"study","credentials_file":"data/deployment-access.json","release":release}), flush=True)
finally:
    sftp.close(); client.close()
