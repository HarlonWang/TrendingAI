# trendingai-tinyui

TrendingAI 里用 [TinyUI](https://github.com/tiny-ui/tinyui) 写的页面，包名 `trendingai`（页面 `trendingai/subscription`），可热下发。

```
tinyui.config.json  包名、验签公钥（私钥在 GitHub secret TINYUI_SIGNING_KEY，备份在 HarlonWang/secrets）、默认语言
i18n/               页面的中英文案（服务端 pro_paywall 缺时的兜底）
src/pages/          页面，一个文件一个页面
src/api.ts          TrendingAI 后端：拉价、下单、/api/me、app-config；要带用户身份的请求走宿主的 app 通道
src/analytics.ts    页面的埋点事件，词汇表见 TrendingAI docs/telemetry-vocabulary.md
src/generated/      `pnpm i18n` 生成的文案 key 类型（入库）
```

页面只依赖 TrendingAI 宿主的：`app` 网络通道（登录态与安装标识在里面）、用户语言、登录会话、埋点出口、链接打开器，以及一个 `trendingai.checkout.opened` 事件（宿主据此对账）。其余（请求、存储、文案、toast、图标）都是 TinyUI 框架自带的，改它们只发本仓（tinyui docs/adr-007-host-boundary.md）。图标直接 import `@material-symbols/svg-400` 的 SVG。

## 发布

- 合进 main → `staging.yml` 构建、签名、发 `staging`。`HOST_VERSION` 抄 TrendingAI 的同名常量；`publish` 对照热下发服务上该版本的宿主快照核对，快照不存在或页面用到的东西快照里没有即拒绝
- `production.yml` 手动跑：`promote` / `rollout`，要过 `production` environment 的人工批准
- 回滚：在本仓 revert 合入 main（发一个内容等于旧版的新版本），再 promote。指针只往前走，所有设备都收得到
- 用商店版 App 看 staging：关于页连点版本号 7 次，把「页面更新通道」切到 staging（切换即下载），重启一次生效

依赖的 `tinyui-*` 三包版本须与目标宿主版本的 tinyui 下限兼容：major 相同且不高于它（下限是 TrendingAI `shared/tinyui-host/<HOST_VERSION>.txt` 的 `tinyui` 行，`publish` 据此核对）。运行时随 App，包里只有页面；TrendingAI 单纯升 tinyui 不需要本仓跟着升，页面要用新版运行时的东西时，先等 TrendingAI 加 `HOST_VERSION`、抬下限（tinyui docs/adr-006-hot-updates.md §2.11）。

## 本地

```sh
pnpm install
pnpm i18n             # 改了 i18n/ 之后重新生成 key 类型
pnpm typecheck
```

TrendingAI 的内置包由它自己的发版冒烟脚本从 production 刷新（`tinyui pull`），本仓不管。本地改页面要在 App 里看，走 staging（合 main → 切通道），或临时 `pnpm exec tinyui build` 后把 dist 复制过去、不提交。
