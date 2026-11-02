// Galanthus デスクトップ版：同梱のWebアプリを専用ウィンドウで開く
const { app, BrowserWindow, protocol, net, shell, Menu, session } = require("electron");
const path = require("path");
const { pathToFileURL } = require("url");

const WEB = path.join(__dirname, "web");

// app://galanthus/ をhttpsと同じ扱いにして、fetchやIndexedDBを普通のサイトと同じように使えるようにする
protocol.registerSchemesAsPrivileged([
  { scheme: "app", privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true } },
]);

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) app.quit();

let win;
function createWindow() {
  win = new BrowserWindow({
    width: 1280, height: 860, minWidth: 380, minHeight: 500,
    title: "Galanthus",
    icon: path.join(WEB, "icons", "icon-512.png"),
    backgroundColor: "#f6f8f5",
    autoHideMenuBar: true,
    webPreferences: { contextIsolation: true, sandbox: true, spellcheck: false },
  });
  win.loadURL("app://galanthus/index.html");
  // 外部リンク（wikiなど）は普段使いのブラウザで開く
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/.test(url)) shell.openExternal(url);
    return { action: "deny" };
  });
  win.webContents.on("will-navigate", (e, url) => {
    if (!url.startsWith("app://")) { e.preventDefault(); if (/^https?:/.test(url)) shell.openExternal(url); }
  });
}

app.on("second-instance", () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });

app.whenReady().then(() => {
  if (!gotLock) return;
  // このアプリはカメラ・マイク・位置情報・通知などを使わないので、すべて拒否する
  session.defaultSession.setPermissionRequestHandler((wc, perm, cb) => cb(false));
  session.defaultSession.setPermissionCheckHandler(() => false);
  protocol.handle("app", async (req) => {
    const notFound = () => new Response("Not found", { status: 404 });
    let rel;
    try {
      const u = new URL(req.url);
      if (u.host !== "galanthus") return notFound();
      rel = decodeURIComponent(u.pathname).replace(/^[\/\\]+/, "") || "index.html";
    } catch (e) { return notFound(); }
    const file = path.resolve(WEB, rel);
    const inside = path.relative(WEB, file);
    if (!inside || inside.startsWith("..") || path.isAbsolute(inside)) return notFound();
    try { return await net.fetch(pathToFileURL(file).toString()); } catch (e) { return notFound(); }
  });
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    { label: "表示", submenu: [
      { role: "reload", label: "再読み込み" },
      { role: "resetZoom", label: "実際のサイズ" }, { role: "zoomIn", label: "拡大" }, { role: "zoomOut", label: "縮小" },
      { type: "separator" }, { role: "togglefullscreen", label: "全画面" },
    ]},
  ]));
  createWindow();
});

app.on("web-contents-created", (_e, wc) => {
  wc.on("will-attach-webview", (e) => e.preventDefault());
});
app.on("window-all-closed", () => app.quit());
