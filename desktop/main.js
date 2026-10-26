// Galanthus デスクトップ版：同梱のWebアプリを専用ウィンドウで開く
const { app, BrowserWindow, protocol, net, shell, Menu } = require("electron");
const path = require("path");
const { pathToFileURL } = require("url");

const WEB = path.join(__dirname, "web");

// app://galanthus/ をhttpsと同じ扱いにして、fetchやIndexedDBを普通のサイトと同じように使えるようにする
protocol.registerSchemesAsPrivileged([
  { scheme: "app", privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true } },
]);

if (!app.requestSingleInstanceLock()) app.quit();

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
  protocol.handle("app", (req) => {
    const { pathname } = new URL(req.url);
    const rel = decodeURIComponent(pathname).replace(/^\/+/, "") || "index.html";
    const file = path.normalize(path.join(WEB, rel));
    if (!file.startsWith(WEB)) return new Response("Not found", { status: 404 });
    return net.fetch(pathToFileURL(file).toString());
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

app.on("window-all-closed", () => app.quit());
