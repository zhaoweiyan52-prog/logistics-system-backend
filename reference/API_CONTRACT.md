# 物流管理系统 · 前后端对接说明

> 目标：把 `logistics-admin.html` 从「内置演示数据」切换成「真实后端数据」，业务代码零改动。

---

## 一、现在的状态

页面里已经内置了一层 **API 接入层**（`logistics-admin.html` 搜索 `后端对接配置` 可见），所有数据的读写都收敛到 `api.*` 方法里：

- **读**：订单列表/详情、统计、周月报表、待办任务、用户、库存、仓库、通知
- **写**：下单、签收、意见反馈、任务状态更新、凭证上传、出入库、角色分配、登录/注册/登出

数据源由页面顶部一个开关控制：

```js
const CONFIG = {
  USE_MOCK: true,                          // true=内置演示数据；false=走真实后端
  BASE: 'http://localhost:8080/api',       // 后端地址
  TOKEN: localStorage.getItem('ls_token') || '',
  TIMEOUT: 8000
};
```

切换 `USE_MOCK: false` 并改 `BASE` 即完成对接。顶栏在数据为演示态时显示黄色 `MOCK 数据` 标签，接上后端后自动消失。

---

## 二、三步完成联调

### 第 1 步：起一个后端（先用自带的 Mock 服务验证链路）

```bash
node mock-server.js            # 默认 8080；PORT=3000 可改端口
```

它零依赖，实现了本文全部接口，可当成「后端该长什么样」的参考实现。

### 第 2 步：把前端切到真实模式

```js
USE_MOCK: false,
BASE: 'http://localhost:8080/api',
```

浏览器打开 `logistics-admin.html`（建议用本地 http 服务打开，见文末「常见坑」），页面会提示「已连接后端」。

### 第 3 步：后端照接口实现，替换 BASE 为真实域名

后端实现完接口后，把 `BASE` 改成 `https://your-domain.com/api` 即可上线。

---

## 三、接口契约

### 统一响应格式

```json
{ "code": 0, "data": { ... }, "message": "错误信息" }
```

- `code === 0` 表示成功，前端取 `data`
- 非 0 时前端直接把 `message` 弹成错误提示
- HTTP 401 前端会提示「登录已失效」

### 接口清单

**认证接口（无需 token）**

| 方法 | 路径 | 参数 | 返回 data |
|---|---|---|---|
| POST | `/auth/login` | `{username, password}` | `{ token, role, name, account }` |
| POST | `/auth/register` | `{username, password, role}` | `{ token, role, name, account }`（注册成功即登录） |
| POST | `/auth/logout` | — | `{ ok:true }`（服务端失效 token） |

**业务接口（需 `Authorization: Bearer <token>`）**

| 方法 | 路径 | 参数 | 返回 data |
|---|---|---|---|
| GET | `/orders` | `kw` `status` `page` `size` | `{ list: Order[], total: number }` |
| GET | `/orders/{no}` | — | `Order`（含 traces） |
| POST | `/orders` | `{receiver, dest, goods, weight, fee}` | `Order` |
| POST | `/orders/{no}/sign` | `{code, by}` | `Order` |
| POST | `/orders/{no}/feedback` | `{type, content}` | `Feedback` |
| GET | `/stats/overview` | — | `Stats` |
| GET | `/stats/weekly` | — | `[{day, n}]` × 7 |
| GET | `/stats/monthly` | — | `[{month, count, delivered, exception, fee}]` |
| GET | `/tasks` | `status` `page` `size` | `{ list: Task[], total }` |
| POST | `/tasks/{id}/status` | `{status, note}` | `{ok:true}` |
| POST | `/tasks/{id}/voucher` | `multipart: files[], note` | `{ok:true}` |
| GET | `/inventory` | `type`（可选） | `InvRecord[]` |
| POST | `/inventory` | `{type:'in'\|'out', goods, qty, wh}` | `InvRecord` |
| GET | `/warehouses` | — | `[{name, used, cap}]` |
| GET | `/users` | `kw` `role` `page` `size` | `{ list: User[], total }` |
| PATCH | `/users/{id}/role` | `{role}` | `{ok:true}` |
| POST | `/users/batch-role` | `{role, ids[]}` | `{ok:true, count}` |
| GET | `/notifications` | — | `Notice[]` |

### 认证相关字段

**登录 / 注册返回体 `data`**

| 字段 | 类型 | 说明 |
|---|---|---|
| token | string | 后续所有请求的凭证，前端存 `localStorage.ls_token` |
| role | enum | `customer` 客户 · `staff` 物流员工 · `admin` 系统管理员；**前端据此进入对应视角与菜单** |
| name | string | 展示用昵称，顶栏与个人中心显示 |
| account | string | 登录用户名 |

**注册入参规则**

| 字段 | 规则 |
|---|---|
| username | 4-16 位字母、数字或下划线；不可重复 |
| password | 6-20 位 |
| role | `customer` 或 `staff`；**注册不开放 `admin`**（管理员由后台直接分配） |

### 字段字典

**Order（运单）**

| 字段 | 类型 | 说明 |
|---|---|---|
| no | string | 运单号，唯一键 |
| goods / dest / receiver | string | 物品 / 收件地 / 收件人 |
| fee | number | 运费（元） |
| status | enum | `pending` 待揽收 · `transit` 运输中 · `delivering` 派送中 · `delivered` 已签收 · `exception` 异常 · `cancelled` 已取消 |
| time / eta | string(ISO) | 下单时间 / 预计送达，**统一 ISO 字符串**，前端自动转毫秒 |
| signCode | string | 签收码（演示用，生产应改为短信/身份验证） |
| traces[] | array | `{t: ISO, txt, done}` 物流轨迹，按时间正序 |
| fb | object\|null | 该运单的意见反馈（null 表示未反馈） |

**Task（员工任务）**：`id, no, type(上门揽收|派送), addr, time, status(todo|done), voucher(boolean)`
**User（用户）**：`id, name, account, role(客户|物流员工|系统管理员), active, orderCount, created`
**InvRecord（出入库）**：`no, goods, qty, wh, op, time, type(in|out)`
**Stats（概览）**：`total, delivered, exception, inTransit, pending, cancelled, revenue, fbCount, tasksTodo, tasksDone, noVoucher, monthDeliver`

### 业务约束（后端需保证）

1. **签收**：仅 `delivering` 状态可签收；`code` 校验失败返回非 0 code + 提示文案。
2. **反馈**：一个运单只允许一条，重复提交返回错误（前端会弹「该运单已提交过反馈」）。
3. **分页**：`page` 从 1 开始，`size` 默认 10，`total` 为筛选后的总数（不是当前页长度）。
4. **排序**：订单列表按 `time` 倒序（最新在前）。

---

## 四、认证与鉴权

前端自带登录注册页：未登录时全屏遮罩，登录成功后自动按 `role` 进入对应视角并拉取数据；注册成功直接免二次登录。

### 流程

1. 用户提交用户名 + 密码 → `POST /auth/login`
2. 前端存 `localStorage.ls_token`（勾选「记住登录状态」时）或 `sessionStorage`
3. 之后所有业务请求自动带 `Authorization: Bearer <token>`
4. 任一业务接口返回 **HTTP 401** → 前端清空会话、弹「登录已失效」并退回登录页
5. 退出登录调用 `POST /auth/logout`，服务端失效 token

### 后端要做的事

- 除 `/auth/login`、`/auth/register` 外，**其余接口一律校验 token**，无效/过期返回 HTTP 401
- 登录失败用业务错误码返回（如 `{"code":40101,"message":"密码错误，请重试"}`），前端会把 `message` 直接弹给用户
- 注册成功建议直接返回 token，前端省掉一次登录请求（返回结构同登录）
- 密码必须加盐哈希存储，禁止明文；接口走 HTTPS

### 演示账号（mock 服务内置）

| 用户名 | 密码 | 角色 |
|---|---|---|
| zhangsan | 123456 | 客户 |
| staff01 | 123456 | 物流员工 |
| admin | 123456 | 系统管理员 |

正式环境请删掉这三个账号。

---

## 五、从单文件走向工程化（生产建议）

单文件 HTML 适合演示和快速验证，正式项目建议这样拆：

```
src/
  api/            # axios 实例 + 按模块拆分（orders.js / users.js / tasks.js）
  views/          # 13 个页面组件
  components/     # 表格单元格、统计卡、图表
  router/         # 路由 + 角色守卫（客户不能访问 /admin/*）
  store/          # Pinia：user / orders / dict
  utils/request.js# 拦截器：token 注入、401 跳转、错误统一提示
```

要点：

- **环境变量**：`.env.development` / `.env.production` 分别配 `VITE_API_BASE`，别把地址写死在代码里。
- **开发代理**：Vite `server.proxy` 把 `/api` 代理到后端，彻底避开跨域。
- **权限**：侧边栏菜单由后端返回（`/me/menus`），前端不硬编码三套菜单。
- **字典**：状态枚举、仓库列表走 `/dict` 接口，避免前后端各维护一份。
- **上传**：`/tasks/{id}/voucher` 用 multipart，注意 Nginx `client_max_body_size`。
- **分页/筛选**：参数与本文一致即可，前端已按服务端分页实现。

---

## 六、常见坑

| 现象 | 原因 | 解决 |
|---|---|---|
| 控制台 CORS 报错 | 后端未放行跨域 | 加 `Access-Control-Allow-Origin`，或开发期用 Vite 代理 |
| 双击 HTML 打开后接口 404 | `file://` 协议下相对路径/跨域受限 | 用 `npx serve` 或任意静态服务打开（`python -m http.server` 也行） |
| 时间显示 `Invalid Date` | 后端返回了非 ISO 的时间字符串 | 统一返回 ISO 8601（`2026-10-25T06:30:00.000Z`） |
| 页面一直在转圈 | 请求超时（默认 8s）或未起后端 | 检查后端是否运行、BASE 是否正确 |
| 数据改了但列表没刷新 | 写接口成功后前端会重新拉取，若后端没落库则会回退 | 确认写接口真的持久化了 |
| 刷新页面后跳回登录页 | 前端从 `localStorage` 恢复会话，但 token 在后端已失效 | 属正常行为；若要免登录，延长 token 有效期或加 refresh 机制 |
| 登录后立刻又弹「登录已失效」 | 业务接口也要求鉴权，但登录返回的 token 服务端没存 | 确认 `issueToken` 类逻辑把 token 存进了可用存储（mock 服务用 Map 演示） |

---

## 七、已验证

无头浏览器实测通过（mock 模式 + 真实 HTTP 模式各一轮）：

- 13 个视图全部渲染，统计从接口取数（522 单 / 401 已签收 / 6 个月报表）
- 真实 HTTP 模式下 54 次请求全部 200，ISO 时间正确归一化为毫秒
- 核心闭环：下单 → 签收 → 反馈（重复提交被拒）→ 任务状态更新（待办 15→14）→ 角色分配 PATCH
- 认证链路：未登录拦截 401 → 错误密码拒绝 → 注册成功自动登录 → 登出 → 重名/弱密码/非法用户名拒绝 → 新账号可登录
- 控制台 0 报错

---

## 八、后端交付自查清单

实现完接口后，对着这份清单过一遍即可确认联调无障碍：

- [ ] 20 个接口全部实现（3 个认证 + 17 个业务），路径与本文一致
- [ ] 统一响应体 `{ code, data, message }`，成功固定 `code: 0`
- [ ] 除 `/auth/login`、`/auth/register` 外均校验 token，无效返回 **HTTP 401**
- [ ] 允许跨域（或前端走 Vite 代理）；OPTIONS 预检返回 204
- [ ] 时间字段统一 ISO 8601（`2026-10-25T06:30:00.000Z`）
- [ ] 分页从 `page=1` 起算，`total` 为筛选后总数
- [ ] 订单按 `time` 倒序；一单只允许一条反馈
- [ ] 签收仅 `delivering` 状态可执行
- [ ] 密码加盐哈希，注册不开放 `admin` 角色
- [ ] 演示账号（zhangsan / staff01 / admin）已删除
