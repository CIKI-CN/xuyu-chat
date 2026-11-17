# 絮语 · Xuyu Chat

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3ddc84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7f52ff?logo=kotlin&logoColor=white)](https://kotlinlang.org)

一个照着 DeepSeek 官方聊天界面手感做的 **纯本地 Android AI 聊天 App**（Kotlin + Jetpack Compose）。
接 DeepSeek，也接任何 OpenAI 兼容接口（中转站 / Kimi / Qwen / 本地 Ollama…）。

> **不内置任何 API Key**，第一次用要填自己的 Key（BYOK）。
> 所有聊天记录、角色卡、Key 只存在手机本地，App 没有服务器。

- 仓库：https://github.com/CIKI-CN/xuyu-chat
- 安装包：[Releases](https://github.com/CIKI-CN/xuyu-chat/releases/latest) 里的 `xuyu-v1.7.apk`（6.26 MB），也可以自己编译
- 界面截图：`screenshots/`（Robolectric 无真机渲染：`mode / chat-light / chat-dark-bg / drawer / settings-light / settings-rp / settings-quota / settings-search / settings-context / guide / disclaimer / icon-round`）
- 包名：`app.xuyu.chat`（最低 Android 8.0 / API 26）
- 版本：1.7（versionCode 8）

---

## 下载安装

从 [Releases](https://github.com/CIKI-CN/xuyu-chat/releases/latest) 下 `xuyu-v1.7.apk`（6.26 MB，Android 8.0+）：

1. 手机点开 apk → 允许「安装未知来源应用」→ 安装
2. 首次启动：读免责声明 → 选模式（日常聊天 / 角色扮演）→ 上手指南里拿 Key
3. 设置 → 接口，填自己的 DeepSeek API Key

也可以自己编译，见下面的「构建」。

## 功能

| 功能 | 说明 |
| --- | --- |
| 两种模式 | **日常聊天**（一张人设卡，就是原来那套）与 **角色扮演**（多角色、场景、长记忆）；首次进入选一次，之后记住，数据完全分开 |
| 自定义 AI 名字 + 头像 | 头像从相册选，自动摆正、裁圆，也可以只留首字 |
| 自定义背景 | 相册选图、不透明度 5%~100% 可调，深色/浅色都保证可读 |
| 人设卡 | 名字、简介、详细设定、开场白、示例对话、温度、单次回复上限、角色专属模型 |
| 角色卡 + Key 一键导出 | 生成一段文本/深链接，对方粘贴即完整还原角色 + Key |
| 余额查询 | 直接查 DeepSeek 账户余额（总额 / 赠送 / 充值） |
| 自定义限额 | 按 每天 / 每月 设 token 上限，超了「拦着」或「只提醒」，本地统计用量 |
| 联网搜索 | 输入框左侧放大镜一键开关；默认走 DeepSeek 官方 Search 接口（同一把 Key），回答里带参考来源 |
| 免责声明 | 首次启动必须同意才能进；App 更新或条款改动后重新弹；设置里随时可看 |
| 上手指南 | 建 Key / 充值 / 价格 / 报错对照 / Key 安全，带可点的官方链接；首次启动自动弹一次 |
| AI 生成标识 | 每条模型回答底部带一个小「AI 生成」标签 |
| 流式输出 | SSE 逐字吐字 + 打字机效果，随时可停 |
| 多会话 | 抽屉式会话列表，本地保存 |
| 消息操作 | 长按：复制 / 重新生成 / 删除 |
| 思考模式 | `deepseek-reasoner` 的思考过程折叠展示 |
| 代码块 | ``` 代码块单独显示、等宽、可横向滚动 |

## 第一次使用（必须做）

1. 打开 App，聊天页底部会提示「还没有填 API Key」。
2. 去 [platform.deepseek.com](https://platform.deepseek.com/api_keys) 申请一个 Key。
3. 右上角 ⚙️ → **接口** → 粘到「API Key」→ 点「测试连接」。
4. 顺手点「查询」看一眼余额，就齐了。
5. 想让它联网：设置 → **联网搜索** → 打开开关（默认就是 DeepSeek 官方源，不用额外 Key），回聊天页点输入框左边的放大镜。

用中转站/本地模型：把「Base URL」改成对应地址（如 `http://10.0.2.2:11434/v1`）、模型名改成对方的，Key 按对方要求填。

## 余额与限额

- **余额**：`GET {BaseURL}/user/balance`，只有 DeepSeek 官方接口有这个接口；别家会提示「不支持」。
- **用量**：每次回答完成后，App 从流式响应的最后一个 chunk 里取 `usage.prompt_tokens / completion_tokens` 累加（不返回用量的接口不会计数）。
- **限额**：设置 → 用量与限额，选 每天 / 每月，填上限（预置 10 万 / 50 万 / 200 万 token，可自定义）。
  - 开「超限后禁止发送」：到上限发送键变灰，输入框上方提示。
  - 关掉：只提示，仍可继续发。
  - 计数本地按天/按月分开记，跨天自动归零，累计总量永久保留。
- 参考量级：一次中等长度的对话约 2000~8000 token。

## 联网搜索

用的是 **DeepSeek 官方 Search 接口**（`POST https://api.deepseek.com/v1/tools/search`，2026-11-13 随「全家桶应用 API」开放），和对话接口共用同一把 API Key，不用再申请别家的。

- **开关**：输入框左侧的放大镜，点一下开/关（关着不影响正常聊天）。
- **流程**：先检索 → 把每条结果的 `summary`（长篇摘要）拼进 system prompt → 模型据此回答并标 `[1][2]` → 回答下面列出参考来源，点开能跳原文。
- **计费**：按成功请求计费，**¥5 / 1000 次**（约半分钱一次），只统计有结果的请求；这部分**不占** App 里的 token 限额。
- **参数**：`count` 3/5/8 可选（接口上限 50）；`need_content=false`，只用摘要，省流量。
- **失败不挡聊**：Key 错、402 没余额、超 QPS、接口不通，都会照常回答，回答上方用灰字写明「联网搜索没成功：…」，不会假装搜过。
- **备选服务商**（设置 → 联网搜索）：博查 / Tavily / 自建 SearXNG，适合用第三方 Base URL、或者想换搜索源的人。选这几家才需要额外的搜索 Key。
- 服务地址可以自己填（中转、镜像都行），留空就用 DeepSeek 官方地址。

## 两个模式

首次进入（看完免责声明和上手指南之后）会问你选哪个，之后记住；想换：**设置 → 模式 → 切换 ›**，或者抽屉里的「切换模式」。

| | 日常聊天 | 角色扮演 |
| --- | --- | --- |
| 人设卡 | 一张，改了就改了 | 角色库，可新建 / 切换 / 删除，每个会话绑一张 |
| 额外字段 | 无 | 场景/世界观、我的角色、回复长度（短/中/长）、动作描写开关、严格不出戏 |
| 聊天操作 | 复制 / 编辑 / 继续写 / 重写 / 删除 | 同上 + 换开场白 |
| 会话 | 独立列表 | 独立列表（和日常完全分开） |
| 上下文 | 按模型窗口取比例（默认 10%） | 同左 |

**上下文长度**不再写死条数：先按模型名查窗口（`deepseek-flash` / `deepseek-v4-pro` / `deepseek-chat` = 1M，`claude` = 200K，`gpt-4o` = 128K，`qwen` = 131K…），再乘一个比例（5% / 10% / 25% / 50%），超出就从最早的消息开始丢。设置页会直接显示「当前模型 · 窗口 1M · 实际带约 100K token」并提示对应的输入费用量级。

数据隔离：会话带 `mode` 字段，抽屉只列当前模式的；角色卡分成 `personas`（日常）和 `rpPersonas`（角色扮演）两套，互不影响。老版本升级上来，原来的卡和会话会自动落到「日常聊天」。

## 上手指南（Key / 充值 / 价格）

首次启动（同意免责声明后）自动进一次，可跳过；之后从 **设置 → 接口 → 「怎么获取 Key / 怎么充值」** 随时打开。

内容：

- **流程图**：登录 → 建 Key → 充值 → 粘进来（自绘，非官网截图）。
- **创建 Key**：`platform.deepseek.com/api_keys`，强调「Key 只显示一次」，丢了就删了重建。按钮直接跳浏览器。
- **充值 / 余额**：`platform.deepseek.com/top_up`、`/usage`，按量计费、先充后用，402 就是余额不足。
- **价格表**（2026-11 官方页）：
  | 模型 | 输入（缓存未命中） | 输出 |
  | --- | --- | --- |
  | deepseek-flash | ¥1~2 / 百万 | ¥4~8 / 百万 |
  | deepseek-v4-pro | ¥4.5~9 / 百万 | ¥13.5~27 / 百万 |
  | 联网搜索 | ¥5 / 1000 次 | — |
  区间是「空闲~高峰」：工作日 9-12、14-18 点为高峰，其余半价；缓存命中输入低至 ¥0.02/百万。价格会变，界面里带了官方价格页链接。
- **报错对照**：401 Key 无效 / 402 余额不足 / 403 没权限 / 429 太频繁 / 404 地址不对。
- **Key 安全**：别发群、别截图；分享角色卡会连带 Key 一起发出去。

## 免责声明（合规）

首次启动会挡住整个界面，必须点「同意并继续」；按返回键等于不同意，直接退出。设置 → 关于里随时能翻看全文。

- **重新弹的条件**：`versionCode` 变了（App 更新）**或** `Legal.DISCLAIMER_VERSION` 变了（条款改了）。同意记录存在 `filesDir/xuyu/meta.json`。
- **改文案**：编辑 `app/src/main/java/app/xuyu/chat/data/Legal.kt`，同时把 `DISCLAIMER_VERSION` +1，老用户就会重新看到一次。
- **覆盖的七条**：AI 会出错 / 不构成专业建议 / 费用与额度自负 / 搜索内容版权归原站 / 数据只存本地但会发给模型服务商 / 禁止违法用途且未成年人需监护人 / 责任限制。
- **不署名**：文案统一用「本应用」，没有个人联系方式（要加就改 `Legal.kt`）。
- **AI 生成标识**：每条非报错的模型回答底部有一个 9sp 的小标签，符合《生成式人工智能服务管理暂行办法》对生成内容标识的要求倾向。

## 模型与思考模式

- 默认模型 **`deepseek-flash`**（V4.1-Flash），可选 **`deepseek-v4-pro`**；旧的 `deepseek-chat` / `deepseek-reasoner` 已进入退役流程，只在预置里留了一个兼容项。
- **思考模式**在「角色 → 生成参数」里，是接口参数 `thinking: {type: enabled/disabled}`，不再靠换模型名；打开后 `reasoning_content` 会折叠显示在回答上方。
- 思考模式下 `temperature` 不生效（接口静默忽略），这是 DeepSeek 的规则。

## 构建

环境：JDK 17、Android SDK（platform 34 / build-tools 34.0.0）。仓库自带 Gradle 8.9 的 wrapper，不用另装 Gradle。

```bash
# 1) 仓库根目录建 local.properties，指向你本机的 SDK
#    Windows: sdk.dir=C\:\\Android\\Sdk
#    macOS/Linux: sdk.dir=/Users/you/Library/Android/sdk
echo 'sdk.dir=/path/to/Android/Sdk' > local.properties

# 2) 编译
./gradlew assembleRelease        # macOS / Linux
gradlew.bat assembleRelease      # Windows

# 3) 跑测试（46 个，Robolectric，无需真机）
./gradlew testDebugUnitTest
```

产物：`app/build/outputs/apk/release/app-release.apk`。

## 打包签名

仓库里**不含任何 keystore**（`.gitignore` 排除了 `keystore/`）。自己生成一把：

```bash
keytool -genkeypair -v -keystore keystore/release.jks -alias xuyu \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass 你的密码 -keypass 你的密码 \
  -dname "CN=你的名字, O=你的组织, C=CN"
```

然后改 `app/build.gradle.kts` 里 `signingConfigs.release` 的四个值（storeFile / storePassword / keyAlias / keyPassword）。

**别把 jks 和密码提交进公开仓库**——签名私钥泄露，别人就能冒名给你的用户发"更新"。jks 和密码请离线备份，丢了就没法给老用户升级。

## 发布自查

1. **包里没有自己的 Key**：`ApiConfig.apiKey` 默认必须是空字符串，发布前跑
   `unzip -p app-release.apk classes*.dex | strings | findstr sk-`，应该是空的。
2. **每版记得升 `versionCode`**：免责声明靠它触发「更新后重弹」，改条款再 +1 `Legal.DISCLAIMER_VERSION`。


## 已验证

- 46 个单元/集成测试全绿：角色卡编解码与截断报错、SSE 流式解析、`null` 字段回归（DeepSeek chunk 里 `content:null` 不能变成字面量）、`stream_options` 用量解析、余额查询、限额拦截、模型窗口表与比例换算、按预算裁剪历史、两模式角色与会话隔离、四种搜索源的响应解析、搜索失败降级、端到端（检索 → 注入 prompt → 来源落盘）、免责声明确认 / 指南已读状态落盘、Prompt 拼装、11 张界面渲染。
- 真实接口实测：
  - 对话：`deepseek-flash` 流式正常，首个 token 约 0.9s；
  - 搜索：`POST /v1/tools/search` 用同一把 Key 返回 3 条结果（含 `summary`、`site_name`，`title` 可能为空——已做回退）；
  - 余额：`GET /user/balance` 正常返回。
- APK：`apksigner verify` 通过（v2），`aapt2 dump badging` 显示 minSdk 26 / targetSdk 34；`classes*.dex` 里扫描无任何 `sk-` 形态字符串。

## 公开发布前要做的两件事

1. **换成你自己的签名 keystore**，别用仓库外的默认密钥；细节见上面的「打包签名」。
2. **确认包里没有自己的 Key**：`app/src/main/java/app/xuyu/chat/data/Models.kt` 里 `ApiConfig.apiKey` 默认必须是空字符串。发布前用
   `unzip -p app-release.apk classes*.dex | strings | findstr sk-` 自查一遍。
3. **每版记得升 `versionCode`**：免责声明靠它触发「更新后重弹」，改条款再 +1 `Legal.DISCLAIMER_VERSION`。

## 数据与隐私

- 所有数据只落在应用私有目录 `filesDir/xuyu/`：`settings.json`、`sessions.json`、`usage.json`、`images/`。
- 没有账号、没有云同步、没有埋点；请求只发往用户自己填的 Base URL。
- Key 明文存在本机私有目录（未 root 的机器上别的 App 读不到），分享角色卡时会随卡片带出去——这是设计中的功能，分享前请确认。

## 已知限制

- 日常模式只有一张人设卡（角色库只在角色扮演模式里）。
- 不支持图片输入（DeepSeek 文本模型本身也不支持）。
- 没有语音播报。
- 分享走自定义 scheme，微信里点不动链接，需要复制文本再粘贴——微信的限制。
- 限额统计依赖接口返回 usage；不返回的接口显示 0。
- 联网搜索按次计费，不计入 token 限额；不配也能用，只是不联网。
