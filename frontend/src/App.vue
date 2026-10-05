<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import {
  Sprout,
  Grid2X2,
  BarChart3,
  Users,
  ShieldCheck,
  Settings,
  LogOut,
  Plus,
  Search,
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  X,
  Download,
  RefreshCw,
  ExternalLink,
  Clock3,
  AlertCircle,
} from "@lucide/vue";
import { api, resetCsrf } from "./api.js";
import {
  states,
  actions,
  payload,
  localDate,
  currentAccepted,
} from "./domain.js";
import { fields, commandFields } from "./forms.js";
const lang = ref(localStorage.getItem("plottrial-language") || "zh"),
  me = ref(null),
  view = ref("trials"),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  loginForm = ref({ username: "", password: "" }),
  options = ref({}),
  directories = ref({}),
  rows = ref([]),
  total = ref(0),
  page = ref(0),
  search = ref(""),
  filter = ref(""),
  sort = ref("newest"),
  detail = ref(null),
  stats = ref({ states: {} }),
  modal = ref(null),
  form = ref({}),
  contact = ref(false),
  tab = ref("plots"),
  eventPage = ref(0),
  obsPage = ref(0);
const t = (zh, en) => (lang.value === "zh" ? zh : en),
  can = (p) => me.value?.permissions?.includes(p),
  business = ["sites", "trials"],
  adminTypes = [
    "users",
    "roles",
    "departments",
    "menus",
    "permissions",
    "dictionaries",
    "settings",
  ];
const labels = {
  trials: ["田间试验", "Field trials"],
  sites: ["田块目录", "Field sites"],
  dashboard: ["试验统计", "Statistics"],
  audit: ["操作审计", "Audit"],
  users: ["账号管理", "Accounts"],
  roles: ["角色与权限", "Roles"],
  departments: ["部门管理", "Departments"],
  menus: ["导航管理", "Navigation"],
  permissions: ["权限目录", "Permissions"],
  dictionaries: ["作物类型", "Crop types"],
  settings: ["系统参数", "Settings"],
};
const icons = {
  trials: Sprout,
  sites: Grid2X2,
  dashboard: BarChart3,
  audit: Clock3,
  users: Users,
  roles: ShieldCheck,
};
const title = computed(() =>
    t(...(labels[view.value] || ["PlotTrial", "PlotTrial"])),
  ),
  record = computed(() => detail.value?.record),
  statusName = (s) => t(...(states[s] || [s, s]));
const actionNames = {
  submit: ["提交复核", "Submit"],
  approve: ["批准并冻结方案", "Approve plan"],
  reject: ["退回修订", "Return"],
  withdraw: ["撤回修订", "Withdraw"],
  cancel: ["取消记录", "Cancel"],
  allocate: ["生成随机布局", "Allocate plots"],
  start: ["开始试验", "Start trial"],
  end: ["结束并提交关单", "End for review"],
  abort: ["记录中止", "Abort"],
  close: ["复核并冻结数据", "Close and freeze"],
  reopen: ["退回继续观测", "Reopen"],
  assign: ["指派观测员", "Assign observer"],
  receive: ["确认收悉", "Receive"],
  plant: ["登记种植日期", "Record planting"],
  "request-exclusion": ["申请排除小区", "Request exclusion"],
  "approve-exclusion": ["核实排除", "Accept exclusion"],
  "reject-exclusion": ["不予排除", "Reject exclusion"],
  accept: ["核实观测", "Accept observation"],
};
const actionName = (a) => t(...(actionNames[a] || [a, a]));
const failures = {
  UNAUTHENTICATED: ["登录已失效，请重新登录", "Session expired"],
  FORBIDDEN: ["当前岗位无此权限", "Permission required"],
  OUT_OF_SCOPE: ["超出账号数据范围", "Outside your data scope"],
  STALE_VERSION: ["记录已变化，请刷新后重试", "Record changed. Refresh first"],
  INVALID_STATE: ["当前状态不允许操作", "Unavailable in this state"],
  INDEPENDENT_REVIEW_REQUIRED: [
    "须由未参与编辑的独立人员复核",
    "Independent reviewer required",
  ],
  ASSIGNED_REVIEWER_REQUIRED: [
    "须由指定复核人操作",
    "Assigned reviewer required",
  ],
  ASSIGNED_OBSERVER_REQUIRED: [
    "须由本小区指定观测员操作",
    "Assigned observer required",
  ],
  INCOMPLETE_TREATMENTS: [
    "至少两项处理，且恰好一个对照",
    "Include 2+ treatments and exactly one control",
  ],
  INCOMPLETE_MEASURES: ["至少启用一个必录指标", "Include a required measure"],
  CREW_NOT_READY: ["每个小区须完成指派与收悉", "Assign and receive every plot"],
  PENDING_OBSERVATIONS: [
    "仍有待处理观测，请核实或取消",
    "Resolve pending observations",
  ],
  PENDING_EXCLUSIONS: [
    "仍有待核实的小区排除申请",
    "Resolve pending exclusions",
  ],
  MISSING_REQUIRED_OBSERVATION: [
    "每个未排除小区须完成必录指标核实",
    "Accept required measures for every included plot",
  ],
  UNPLANTED_PLOT: [
    "未排除小区尚未登记种植日期",
    "Record planting for every included plot",
  ],
  ALL_PLOTS_EXCLUDED: [
    "不能正常结束全部排除的试验",
    "Cannot finish an entirely excluded trial",
  ],
  OPEN_OBSERVATION: [
    "同一小区指标已有待处理观测",
    "Resolve the existing pending observation",
  ],
  CORRECTION_BASE_REQUIRED: [
    "修订必须基于当前已核实记录，请刷新",
    "Correction must reference current accepted record",
  ],
  VALUE_AND_MISSING_REASON: [
    "数值与缺测原因只能填写一项",
    "Enter either a value or missing reason",
  ],
  OUTSIDE_MEASURE_RANGE: [
    "测量值超出方案允许范围",
    "Value outside protocol range",
  ],
  INVALID_DECIMAL: [
    "数值最多四位小数，且不得超出存储范围",
    "Use at most four decimal places",
  ],
  INVALID_DATE: [
    "日期不得早于试验／种植日期或晚于今天",
    "Date outside allowed range",
  ],
  INVALID_BLOCK_COUNT: ["区组数为4—16", "Use 4–16 blocks"],
  INELIGIBLE_ASSIGNMENT: [
    "账号已停用或没有对应权限",
    "Account disabled or missing permission",
  ],
  IMMUTABLE_IDENTITY: ["编号及关联身份不可修改", "Identity cannot change"],
  DISABLED_RESOURCE: ["田块已停用", "Site disabled"],
  REQUEST_KEY_REUSED: ["请求已被使用，请重新打开表单", "Request key reused"],
  INVALID_INPUT: ["检查必填字段与输入范围", "Check required fields"],
  LOGIN_FAILED: ["账号或密码不正确", "Incorrect credentials"],
  LOGIN_THROTTLED: ["登录尝试过多，稍后重试", "Too many attempts"],
  WEAK_PASSWORD: [
    "密码至少12位，含大小写字母和数字",
    "Use 12+ characters, upper/lower case and digits",
  ],
  LAST_ADMIN: [
    "至少保留一个启用的全范围管理员",
    "Keep an enabled full administrator",
  ],
  OLD_PASSWORD_INVALID: ["原密码不正确", "Incorrect current password"],
  CONFLICT: [
    "编号已存在或记录被其他数据引用",
    "Duplicate or referenced record",
  ],
  RECORD_LIMIT: ["已达到记录上限", "Record limit reached"],
};
function language() {
  lang.value = lang.value === "zh" ? "en" : "zh";
  localStorage.setItem("plottrial-language", lang.value);
  document.documentElement.lang = lang.value === "zh" ? "zh-CN" : "en";
}
function clearSession() {
  me.value = null;
  detail.value = null;
  modal.value = null;
  rows.value = [];
  options.value = {};
  directories.value = {};
  resetCsrf();
}
async function run(fn) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    return await fn();
  } catch (e) {
    error.value = failures[e.message]
      ? t(...failures[e.message])
      : t("操作失败：", "Action failed: ") + e.message;
    if (e.message === "UNAUTHENTICATED") clearSession();
  } finally {
    busy.value = false;
  }
}
async function loadOptions() {
  options.value = await api("/options");
  if (can("admin") && me.value.scope === "ALL")
    for (const k of ["roles", "permissions", "departments"])
      directories.value[k] = await api("/admin/" + k);
}
async function load() {
  detail.value = null;
  if (view.value === "dashboard") {
    stats.value = await api("/dashboard");
    return;
  }
  if (business.includes(view.value)) {
    const r = await api(
      "/" +
        view.value +
        "?" +
        new URLSearchParams({
          search: search.value,
          status: filter.value,
          page: String(page.value),
          size: "12",
          sort: sort.value,
        }),
    );
    rows.value = r.content;
    total.value = r.total;
  } else {
    let all = await api(
      view.value === "audit" ? "/audit" : "/admin/" + view.value,
    );
    all = all.filter((v) =>
      Object.values(v).some(
        (x) =>
          typeof x === "string" &&
          x.toLowerCase().includes(search.value.toLowerCase()),
      ),
    );
    all.sort((a, b) => (sort.value === "oldest" ? a.id - b.id : b.id - a.id));
    total.value = all.length;
    rows.value = all.slice(page.value * 12, page.value * 12 + 12);
  }
}
async function navigate(code) {
  view.value = code;
  page.value = 0;
  search.value = "";
  filter.value = "";
  rows.value = [];
  await run(load);
  window.scrollTo(0, 0);
}
async function signIn() {
  await run(async () => {
    resetCsrf();
    me.value = await api("/auth/login", "POST", loginForm.value);
    loginForm.value.password = "";
    await loadOptions();
    view.value = me.value.menus[0]?.code || "dashboard";
    await load();
  });
}
async function signOut() {
  await run(async () => {
    await api("/auth/logout", "POST", {});
    clearSession();
  });
}
async function open(row) {
  await run(async () => {
    detail.value = await api("/" + view.value + "/" + row.id);
    tab.value =
      view.value === "trials"
        ? detail.value.plots.length
          ? "plots"
          : "plan"
        : "history";
    eventPage.value = 0;
    obsPage.value = 0;
    window.scrollTo(0, 0);
  });
}
async function refresh() {
  await run(async () => {
    me.value = await api("/auth/me");
    await loadOptions();
    if (record.value)
      detail.value = await api("/" + view.value + "/" + record.value.id);
    else await load();
  });
}
const currentActions = computed(() =>
    actions(view.value, record.value, me.value, detail.value),
  ),
  events = computed(() => [...(detail.value?.events || [])].reverse()),
  eventRows = computed(() =>
    events.value.slice(eventPage.value * 10, eventPage.value * 10 + 10),
  ),
  observations = computed(() =>
    [...(detail.value?.observations || [])].reverse(),
  ),
  observationRows = computed(() =>
    observations.value.slice(obsPage.value * 12, obsPage.value * 12 + 12),
  ),
  editablePlan = computed(
    () =>
      can("trial.write") &&
      ["DRAFT", "RETURNED"].includes(record.value?.status),
  ),
  canCreate = computed(() =>
    business.includes(view.value)
      ? can(view.value === "sites" ? "site.write" : "trial.write")
      : adminTypes.includes(view.value) &&
        !["menus", "permissions", "settings"].includes(view.value),
  );
const modalFields = computed(() =>
  modal.value?.kind === "command"
    ? commandFields(modal.value.action)
    : modal.value?.kind === "password"
      ? [
          ["oldPassword", "原密码", "Current password", "password"],
          ["newPassword", "新密码", "New password", "password"],
        ]
      : fields[modal.value?.type] || [],
);
function choices(key) {
  if (key === "scope")
    return ["ALL", "DEPARTMENT", "SELF"].map((value) => ({
      value,
      label: {
        ALL: t("全部", "All"),
        DEPARTMENT: t("本部门", "Department"),
        SELF: t("本人创建或获指派", "Created by self or assigned"),
      }[value],
    }));
  let list =
    directories.value[key] || options.value[key] || detail.value?.[key] || [];
  if (["reviewers", "observers"].includes(key)) {
    const permission = key === "reviewers" ? "trial.review" : "plot.observe";
    list = (options.value.people || []).filter((a) =>
      a.permissions?.includes(permission),
    );
    if (key === "reviewers")
      list = list.filter(
        (a) =>
          a.id !== me.value.id && a.permissions.includes("observation.review"),
      );
    if (key === "observers")
      list = list.filter((a) => a.id !== record.value?.reviewerId);
  }
  if (
    ["plots", "measures"].includes(key) &&
    modal.value?.type === "observations"
  ) {
    list = list.filter((v) =>
      key === "plots"
        ? v.observerId === me.value.id && !v.excluded && v.plantingDate
        : v.enabled,
    );
  }
  return list.map((v) => ({
    value: ["permissions", "crops"].includes(key) ? v.code : v.id,
    label:
      lang.value === "en" && v.nameEn
        ? v.nameEn
        : [v.reference, v.label, v.name || v.displayName || v.code]
            .filter(Boolean)
            .join(" · "),
  }));
}
function edit(type, row = null, extras = {}) {
  error.value = "";
  modal.value = {
    kind: "edit",
    type,
    id: row?.id,
    returnId: record.value?.id,
    returnType: view.value,
  };
  form.value = row
    ? { ...row }
    : {
        enabled: true,
        departmentId: me.value.departmentId,
        scope: "DEPARTMENT",
        permissions: [],
        type: "crop",
        cropType: "CEREAL",
        blockCount: 4,
        plotArea: "",
        control: false,
        required: true,
        minimum: "0",
        maximum: "",
        observedDate: localDate(),
        trialId: record.value?.id,
        ...extras,
      };
  if (type === "users") form.value.password = "";
}
function command(type, row, action) {
  error.value = "";
  modal.value = {
    kind: "command",
    type,
    id: row.id,
    version: row.version,
    action,
    returnId: record.value.id,
    returnType: view.value,
  };
  form.value = {
    note: "",
    observerId: row.observerId || "",
    date: localDate(),
  };
}
function immutable(key) {
  return (
    modal.value?.id &&
    ([
      "reference",
      "departmentId",
      "siteId",
      "trialId",
      "plotId",
      "measureId",
    ].includes(key) ||
      (modal.value.type === "dictionaries" && ["type", "code"].includes(key)))
  );
}
function observe(p) {
  const m = (detail.value.measures || []).find((m) => m.enabled);
  edit("observations", null, { plotId: p.id, measureId: m?.id });
}
function correction(o) {
  edit("observations", null, {
    plotId: o.plotId,
    measureId: o.measureId,
    supersedesId: o.id,
    value: o.value == null ? "" : String(o.value),
    missingReason: o.missingReason,
  });
}
async function save() {
  await run(async () => {
    const m = modal.value,
      body = payload(form.value, modalFields.value);
    if (m.kind === "password") {
      await api("/auth/password", "POST", body);
      clearSession();
      return;
    }
    if (m.kind === "delete")
      await api("/admin/" + m.type + "/" + m.id, "DELETE", {});
    else {
      if (!adminTypes.includes(m.type)) {
        body.version =
          m.kind === "command" ? m.version : (form.value.version ?? null);
        if (["treatments", "measures"].includes(m.type))
          body.trialId = form.value.trialId;
        if (m.type === "observations")
          body.supersedesId = form.value.supersedesId ?? null;
        const signature = JSON.stringify(body);
        if (signature !== m.signature) {
          m.requestKey = crypto.randomUUID();
          m.signature = signature;
        }
        body.requestKey = m.requestKey;
      }
      await api(
        "/" +
          (adminTypes.includes(m.type) ? "admin/" : "") +
          m.type +
          (m.id ? "/" + m.id : "") +
          (m.kind === "command" ? "/commands/" + m.action : ""),
        m.kind === "edit" && m.id ? "PUT" : "POST",
        body,
      );
    }
    modal.value = null;
    if (adminTypes.includes(m.type)) me.value = await api("/auth/me");
    await loadOptions();
    if (m.returnId && business.includes(m.returnType))
      detail.value = await api("/" + m.returnType + "/" + m.returnId);
    else await load();
    notice.value = t("已保存", "Saved");
  });
}
function formatTime(v) {
  return v
    ? new Intl.DateTimeFormat(lang.value === "zh" ? "zh-CN" : "en-GB", {
        timeZone: "Asia/Shanghai",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
      }).format(new Date(v))
    : "—";
}
const person = (id) =>
  (options.value.people || []).find((a) => a.id === id)?.name || "—";
function value(row, key) {
  if (key.endsWith("At")) return formatTime(row[key]);
  if (key === "status") return statusName(row[key]);
  if (["enabled", "required", "control"].includes(key))
    return row[key] ? t("是", "Yes") : t("否", "No");
  if (key === "permissions")
    return row.permissions.length + t(" 项权限", " permissions");
  if (key === "scope")
    return (
      choices("scope").find((c) => c.value === row[key])?.label || row[key]
    );
  const dir = {
      roleId: directories.value.roles,
      departmentId: directories.value.departments || options.value.departments,
      siteId: options.value.sites,
      reviewerId: options.value.people,
    }[key],
    entry = dir?.find((d) => d.id === row[key]);
  return entry?.name || (row[key] ?? "—");
}
const columns = computed(
  () =>
    ({
      trials: [
        ["reference", "试验编号", "Reference"],
        ["name", "试验名称", "Name"],
        ["siteName", "田块", "Site"],
        ["cropName", "作物", "Crop"],
        ["blockCount", "区组", "Blocks"],
        ["status", "状态", "Status"],
      ],
      sites: fields.sites,
      settings: [
        ["code", "参数", "Setting"],
        ["value", "参数值", "Value"],
      ],
      audit: [
        ["createdAt", "时间", "Time"],
        ["actor", "账号", "Actor"],
        ["action", "动作", "Action"],
        ["objectId", "记录", "Record"],
      ],
    })[view.value] ||
    (fields[view.value] || []).filter((f) => f[0] !== "password").slice(0, 5),
);
const ownPlot = (p) =>
  can("plot.observe") &&
  p.observerId === me.value.id &&
  record.value?.status === "ACTIVE" &&
  !p.excluded &&
  p.plantingDate;
function current(o) {
  return (
    currentAccepted(detail.value?.observations || [], o.plotId, o.measureId)
      ?.id === o.id
  );
}
function plotName(id) {
  return detail.value.plots.find((p) => p.id === id)?.label || id;
}
function measureName(id) {
  const m = detail.value.measures.find((m) => m.id === id);
  return m ? m.name + " · " + m.unit : id;
}
onMounted(async () => {
  document.documentElement.lang = lang.value === "zh" ? "zh-CN" : "en";
  try {
    me.value = await api("/auth/me");
    await loadOptions();
    view.value = me.value.menus[0]?.code || "dashboard";
    await load();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED")
      error.value = t("连接失败，请刷新页面", "Connection failed. Refresh");
  }
});
</script>
<template>
  <div v-if="!me" class="login-layout">
    <section class="field-art" aria-hidden="true">
      <div class="art-word">PlotTrial</div>
      <div class="art-caption">FIELD TRIALS / OBSERVATIONS</div>
      <div class="field-layout">
        <span v-for="n in 16" :key="n" :class="'field-' + (n % 3)"
          ><Sprout :size="28"
        /></span>
      </div>
      <p>
        {{
          t("田间品种对比试验与小区观测", "Variety trials & plot observations")
        }}
      </p>
    </section>
    <main class="login-panel">
      <div class="login-top">
        <img src="/brand/logo.jpg" alt="知华科技 LOGO" /><button
          class="plain"
          @click="language"
        >
          {{ lang === "zh" ? "EN" : "中文" }}
        </button>
      </div>
      <div class="login-heading">
        <span class="eyebrow">PLOTTRIAL</span>
        <h1>{{ t("田间试验与观测", "Field trials & observations") }}</h1>
        <p>{{ t("登录你的业务工作台", "Sign in to your workspace") }}</p>
      </div>
      <form @submit.prevent="signIn">
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="loginForm.username"
            autocomplete="username"
            required
            maxlength="60" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="loginForm.password"
            type="password"
            autocomplete="current-password"
            required
        /></label>
        <p v-if="error" class="error" role="alert">
          <AlertCircle :size="17" />{{ error }}
        </p>
        <button class="primary login-submit" :disabled="busy">
          {{ busy ? t("正在登录", "Signing in") : t("登录", "Sign in")
          }}<ArrowRight :size="18" />
        </button>
      </form>
      <div class="login-footer">
        <button class="plain" @click="contact = true">
          {{ t("知华科技 · 商业咨询", "ZhuaTech · Commercial enquiries") }}
        </button>
        <p>
          {{
            t("公开源码学习版／非商业源码版", "Non-commercial source edition")
          }}
        </p>
      </div>
    </main>
  </div>
  <div v-else class="workspace">
    <aside class="sidebar">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技 LOGO" />
        <div>
          <strong>PlotTrial</strong
          ><span>{{ t("田间试验与观测", "Field trials") }}</span>
        </div>
      </div>
      <nav :aria-label="t('主要导航', 'Main navigation')">
        <template v-for="(m, i) in me.menus" :key="m.code"
          ><p
            v-if="i === 0 || m.code === 'dashboard' || m.code === 'users'"
            class="nav-section"
          >
            {{
              i === 0
                ? t("试验协同", "OPERATIONS")
                : m.code === "dashboard"
                  ? t("统计与审计", "OVERVIEW")
                  : t("系统管理", "ADMINISTRATION")
            }}
          </p>
          <button
            :class="{ active: view === m.code }"
            :disabled="busy"
            @click="navigate(m.code)"
          >
            <component :is="icons[m.code] || Settings" :size="18" /><span>{{
              lang === "en" ? m.nameEn : m.name
            }}</span>
          </button></template
        >
      </nav>
      <div class="sidebar-footer">
        <button class="plain" @click="contact = true">
          <ExternalLink :size="14" />{{
            t("知华科技 · 咨询", "ZhuaTech · Enquiries")
          }}</button
        ><span>{{ t("非商业源码版 0.1.0", "Non-commercial 0.1.0") }}</span>
      </div>
    </aside>
    <div class="work-area">
      <header class="topbar">
        <span>{{ options.companyName || "PlotTrial" }}</span>
        <div class="top-actions">
          <button class="plain" @click="language">
            {{ lang === "zh" ? "EN" : "中文" }}</button
          ><button
            class="account-button"
            @click="
              modal = { kind: 'password' };
              form = {};
            "
          >
            <span class="avatar">{{ me.displayName?.slice(0, 1) }}</span
            ><span
              >{{ me.displayName }}<small>{{ me.role }}</small></span
            ></button
          ><button
            class="icon-button"
            :aria-label="t('退出', 'Sign out')"
            :disabled="busy"
            @click="signOut"
          >
            <LogOut :size="17" />
          </button>
        </div>
      </header>
      <main class="main-content">
        <div class="page-heading">
          <div>
            <span class="eyebrow">PLOTTRIAL / {{ view.toUpperCase() }}</span>
            <h1>{{ title }}</h1>
          </div>
          <div class="row-actions">
            <button
              class="icon-button"
              :aria-label="t('刷新', 'Refresh')"
              :disabled="busy"
              @click="refresh"
            >
              <RefreshCw :size="17" /></button
            ><button
              v-if="detail"
              class="secondary"
              :disabled="busy"
              @click="run(load)"
            >
              <ChevronLeft :size="16" />{{
                t("返回列表", "Back to list")
              }}</button
            ><button
              v-else-if="canCreate"
              class="primary"
              :disabled="busy"
              @click="edit(view)"
            >
              <Plus :size="17" />{{ t("新建", "New") }}
            </button>
          </div>
        </div>
        <p v-if="error" class="error" role="alert">
          <AlertCircle :size="17" />{{ error }}
        </p>
        <p v-if="notice" class="notice" role="status">{{ notice }}</p>
        <template v-if="view === 'dashboard'"
          ><section class="metric-grid">
            <article
              v-for="m in [
                ['trials', '试验', 'Trials', Sprout],
                ['plots', '小区', 'Plots', Grid2X2],
                ['accepted', '已核实指标', 'Accepted cells', ShieldCheck],
                ['missing', '明确缺测', 'Missing cells', BarChart3],
              ]"
              :key="m[0]"
              class="metric"
            >
              <component :is="m[3]" :size="22" /><span>{{ t(m[1], m[2]) }}</span
              ><strong>{{ stats[m[0]] || 0 }}</strong>
            </article>
          </section>
          <section class="panel">
            <h2>{{ t("试验状态", "Trial statuses") }}</h2>
            <div
              v-for="(count, status) in stats.states"
              :key="status"
              class="chart-row"
            >
              <span>{{ statusName(status) }}</span>
              <div>
                <i
                  :style="{
                    width: (count / Math.max(stats.trials, 1)) * 100 + '%',
                  }"
                ></i>
              </div>
              <strong>{{ count }}</strong>
            </div>
            <p v-if="!stats.trials" class="empty">
              {{ t("暂无可见试验", "No visible trials") }}
            </p>
          </section></template
        >
        <section v-else-if="detail" class="trial-detail">
          <article class="panel">
            <div class="record-heading">
              <div>
                <span class="eyebrow">{{ record.reference }}</span>
                <h2>{{ record.name }}</h2>
              </div>
              <span
                v-if="record.status"
                class="status"
                :data-status="record.status"
                >{{ statusName(record.status) }}</span
              >
            </div>
            <div class="record-meta">
              <span v-if="record.siteName"
                >{{ record.siteName }} · {{ record.cropName }}</span
              ><span v-if="record.blockCount"
                >{{ record.blockCount }} {{ t("区组", "blocks") }} ·
                {{ record.plotArea }} m²</span
              ><span v-if="record.reviewerId"
                >{{ t("复核人", "Reviewer") }} ·
                {{ person(record.reviewerId) }}</span
              ><span v-if="record.outcome"
                >{{ t("结局", "Outcome") }} ·
                {{
                  record.outcome === "FINISHED"
                    ? t("正常完成", "Finished")
                    : statusName(record.outcome)
                }}</span
              >
            </div>
            <p v-if="record.objective" class="objective">
              {{ record.objective }}
            </p>
            <div class="row-actions action-wrap">
              <button
                v-if="
                  (view === 'sites' && can('site.write')) ||
                  (view === 'trials' && editablePlan)
                "
                class="secondary"
                :disabled="busy"
                @click="edit(view, record)"
              >
                {{ t("编辑", "Edit") }}</button
              ><button
                v-for="a in currentActions"
                :key="a"
                class="secondary"
                :disabled="busy"
                @click="command(view, record, a)"
              >
                {{ actionName(a) }}</button
              ><a
                v-if="can('export')"
                class="secondary"
                :href="'/api/' + view + '/' + record.id + '/report.json'"
                ><Download :size="15" />JSON</a
              ><a
                v-if="view === 'trials' && can('export')"
                class="secondary"
                :href="'/api/trials/' + record.id + '/observations.csv'"
                ><Download :size="15" />CSV</a
              >
            </div>
          </article>
          <div v-if="view === 'trials'" class="detail-tabs">
            <button
              v-for="b in [
                ['plan', '试验方案', 'Plan'],
                ['plots', '随机布局', 'Plot layout'],
                ['observations', '观测与修订', 'Observations'],
                ['summary', '描述统计', 'Summary'],
                ['history', '证据记录', 'Evidence'],
              ]"
              :key="b[0]"
              :class="{ active: tab === b[0] }"
              @click="tab = b[0]"
            >
              {{ t(b[1], b[2]) }}
            </button>
          </div>
          <template v-if="view === 'trials' && tab === 'plan'"
            ><section
              v-for="kind in ['treatments', 'measures']"
              :key="kind"
              class="panel section-spaced"
            >
              <div class="panel-heading">
                <h2>
                  {{
                    kind === "treatments"
                      ? t("品种与对照", "Varieties & control")
                      : t("观测指标", "Measures")
                  }}
                </h2>
                <button
                  v-if="editablePlan"
                  class="secondary"
                  :disabled="busy"
                  @click="edit(kind)"
                >
                  <Plus :size="16" />{{ t("添加", "Add") }}
                </button>
              </div>
              <div
                v-for="r in detail[kind]"
                :key="r.id"
                class="definition-row"
                :class="{ excluded: !r.enabled }"
              >
                <div>
                  <strong>{{ r.code }} · {{ r.name }}</strong>
                  <p v-if="kind === 'treatments'">{{ r.description }}</p>
                  <p v-else>
                    {{ r.minimum }} — {{ r.maximum }} {{ r.unit }} ·
                    {{
                      r.required ? t("必录", "Required") : t("选录", "Optional")
                    }}
                  </p>
                  <small>{{
                    !r.enabled
                      ? t("不列入方案", "Not included")
                      : r.control
                        ? t("对照处理", "Control")
                        : t("列入方案", "Included")
                  }}</small>
                </div>
                <button
                  v-if="editablePlan"
                  class="row-button"
                  @click="edit(kind, r)"
                >
                  {{ t("编辑", "Edit") }}
                </button>
              </div>
              <p v-if="!detail[kind].length" class="empty">
                {{ t("暂无条目", "No entries") }}
              </p>
            </section></template
          >
          <section
            v-if="view === 'trials' && tab === 'plots'"
            class="panel section-spaced"
          >
            <div class="panel-heading">
              <h2>{{ t("区组与小区", "Blocks & plots") }}</h2>
              <span class="muted"
                >{{ detail.plots.length }}
                {{ t("可见小区", "visible plots") }}</span
              >
            </div>
            <div
              v-for="block in [
                ...new Set(detail.plots.map((p) => p.blockIndex)),
              ]"
              :key="block"
              class="block-section"
            >
              <h3>
                {{ t("区组", "Block") }} {{ String(block).padStart(2, "0") }}
              </h3>
              <div class="plot-grid">
                <article
                  v-for="p in detail.plots.filter(
                    (p) => p.blockIndex === block,
                  )"
                  :key="p.id"
                  class="plot-card"
                  :class="{ excluded: p.excluded, control: p.control }"
                >
                  <div class="plot-top">
                    <strong>{{ p.label }}</strong
                    ><span>{{
                      p.control ? t("对照", "Control") : p.treatmentCode
                    }}</span>
                  </div>
                  <h4>{{ p.treatmentName }}</h4>
                  <p>{{ p.treatmentDescription }}</p>
                  <dl>
                    <dt>{{ t("观测员", "Observer") }}</dt>
                    <dd>{{ person(p.observerId) }}</dd>
                    <dt>{{ t("收悉", "Received") }}</dt>
                    <dd>{{ p.receivedAt ? formatTime(p.receivedAt) : "—" }}</dd>
                    <dt>{{ t("种植日期", "Planting") }}</dt>
                    <dd>{{ p.plantingDate || "—" }}</dd>
                  </dl>
                  <p v-if="p.exclusionStatus !== 'NONE'" class="exclusion-note">
                    {{ statusName(p.exclusionStatus) }} ·
                    {{ p.exclusionReason }}
                  </p>
                  <div class="row-actions action-wrap">
                    <button
                      v-for="a in actions('plots', p, me, detail)"
                      :key="a"
                      class="row-button"
                      :disabled="busy"
                      @click="command('plots', p, a)"
                    >
                      {{ actionName(a) }}</button
                    ><button
                      v-if="ownPlot(p)"
                      class="row-button"
                      @click="observe(p)"
                    >
                      <Plus :size="14" />{{ t("录入观测", "Observe") }}
                    </button>
                  </div>
                </article>
              </div>
            </div>
            <p v-if="!detail.plots.length" class="empty">
              {{
                t("方案获批后生成一次随机布局", "Allocate once after approval")
              }}
            </p>
          </section>
          <section
            v-if="view === 'trials' && tab === 'observations'"
            class="panel list-panel section-spaced"
          >
            <div class="panel-heading">
              <h2>
                {{ t("原始观测与修订", "Original observations & revisions") }}
              </h2>
              <button
                v-if="detail.plots.some(ownPlot)"
                class="secondary"
                @click="edit('observations')"
              >
                <Plus :size="16" />{{ t("录入观测", "Observe") }}
              </button>
            </div>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("小区／指标", "Plot / measure") }}</th>
                    <th>{{ t("版次与状态", "Revision & status") }}</th>
                    <th>{{ t("测量值／缺测", "Value / missing") }}</th>
                    <th>{{ t("实际日期", "Observed") }}</th>
                    <th>{{ t("操作", "Actions") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="o in observationRows" :key="o.id">
                    <td>
                      <strong>{{ plotName(o.plotId) }}</strong
                      ><small>{{ measureName(o.measureId) }}</small>
                    </td>
                    <td>
                      <span class="status" :data-status="o.status"
                        >{{ statusName(o.status) }} · v{{ o.revision }}</span
                      ><small v-if="current(o)">{{
                        t("当前核实版本", "Current accepted revision")
                      }}</small>
                    </td>
                    <td>
                      {{ o.value ?? t("缺测", "Missing")
                      }}<small>{{ o.missingReason || o.note }}</small>
                    </td>
                    <td>{{ o.observedDate }}</td>
                    <td>
                      <div class="row-actions action-wrap">
                        <button
                          v-if="
                            ['DRAFT', 'RETURNED'].includes(o.status) &&
                            o.createdBy === me.id &&
                            record.status === 'ACTIVE'
                          "
                          class="row-button"
                          @click="edit('observations', o)"
                        >
                          {{ t("编辑", "Edit") }}</button
                        ><button
                          v-for="a in actions('observations', o, me, detail)"
                          :key="a"
                          class="row-button"
                          :disabled="busy"
                          @click="command('observations', o, a)"
                        >
                          {{ actionName(a) }}</button
                        ><button
                          v-if="
                            current(o) &&
                            o.createdBy === me.id &&
                            record.status === 'ACTIVE' &&
                            detail.plots.some(
                              (p) => p.id === o.plotId && ownPlot(p),
                            )
                          "
                          class="row-button"
                          @click="correction(o)"
                        >
                          {{ t("追加修订", "Append correction") }}
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="!observations.length" class="empty">
              {{ t("暂无观测记录", "No observations") }}
            </p>
            <div class="pagination">
              <span
                >{{ observations.length }} {{ t("条记录", "records") }}</span
              >
              <div>
                <button :disabled="obsPage === 0" @click="obsPage--">
                  <ChevronLeft :size="16" /></button
                ><span>{{ obsPage + 1 }}</span
                ><button
                  :disabled="(obsPage + 1) * 12 >= observations.length"
                  @click="obsPage++"
                >
                  <ChevronRight :size="16" />
                </button>
              </div>
            </div>
          </section>
          <section
            v-if="view === 'trials' && tab === 'summary'"
            class="panel section-spaced"
          >
            <h2>
              {{ t("按处理的描述统计", "Descriptive statistics by treatment") }}
            </h2>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("处理／指标", "Treatment / measure") }}</th>
                    <th>N</th>
                    <th>{{ t("缺测", "Missing") }}</th>
                    <th>{{ t("排除", "Excluded") }}</th>
                    <th>{{ t("未录", "Unrecorded") }}</th>
                    <th>{{ t("均值", "Mean") }}</th>
                    <th>{{ t("最小／最大", "Min / max") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="r in detail.summary"
                    :key="r.treatment + r.measure"
                  >
                    <td>{{ r.treatment }} · {{ r.measure }} ({{ r.unit }})</td>
                    <td>{{ r.n }}</td>
                    <td>{{ r.missing }}</td>
                    <td>{{ r.excluded }}</td>
                    <td>{{ r.unrecorded }}</td>
                    <td>{{ r.mean ?? "—" }}</td>
                    <td>{{ r.minimum ?? "—" }} / {{ r.maximum ?? "—" }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p class="muted">
              {{
                t(
                  "仅计算当前核实且未排除的数值，不据此判断显著性。",
                  "Includes current accepted numeric values from included plots. No significance inference.",
                )
              }}
            </p>
          </section>
          <section
            v-if="tab === 'history' || view === 'sites'"
            class="panel section-spaced"
          >
            <h2>{{ t("证据记录", "Evidence") }}</h2>
            <dl v-if="view === 'trials'" class="hashes">
              <dt>{{ t("方案摘要", "Plan hash") }}</dt>
              <dd>{{ record.planHash || "—" }}</dd>
              <dt>{{ t("布局种子", "Layout seed") }}</dt>
              <dd>{{ record.layoutSeed || "—" }}</dd>
              <dt>{{ t("布局摘要", "Layout hash") }}</dt>
              <dd>{{ record.layoutHash || "—" }}</dd>
              <dt>{{ t("数据摘要", "Data hash") }}</dt>
              <dd>{{ record.dataHash || "—" }}</dd>
              <dt>{{ t("随机算法", "Algorithm") }}</dt>
              <dd>{{ detail.algorithm }}</dd>
            </dl>
            <ol class="timeline">
              <li v-for="e in eventRows" :key="e.id">
                <i></i>
                <div>
                  <header>
                    <strong
                      >{{ actionName(e.action) }} · {{ e.objectType }} #{{
                        e.objectId
                      }}</strong
                    ><time>{{ formatTime(e.createdAt) }}</time>
                  </header>
                  <p>{{ e.note || t("记录已保存", "Record saved") }}</p>
                  <small>{{ t("操作人", "Actor") }} #{{ e.actorId }}</small>
                  <details>
                    <summary>{{ t("查看事实快照", "View snapshot") }}</summary>
                    <pre>{{ e.snapshot }}</pre>
                  </details>
                </div>
              </li>
            </ol>
            <div class="pagination">
              <span>{{ events.length }} {{ t("条记录", "records") }}</span>
              <div>
                <button :disabled="eventPage === 0" @click="eventPage--">
                  <ChevronLeft :size="16" /></button
                ><span>{{ eventPage + 1 }}</span
                ><button
                  :disabled="(eventPage + 1) * 10 >= events.length"
                  @click="eventPage++"
                >
                  <ChevronRight :size="16" />
                </button>
              </div>
            </div>
          </section>
        </section>
        <section v-else class="panel list-panel">
          <form
            class="filters"
            @submit.prevent="
              page = 0;
              run(load);
            "
          >
            <label class="search-box"
              ><Search :size="17" /><input
                v-model="search"
                :placeholder="t('搜索编号或名称', 'Search reference or name')"
                maxlength="120" /></label
            ><select
              v-if="view === 'trials'"
              v-model="filter"
              :aria-label="t('状态筛选', 'Status filter')"
            >
              <option value="">{{ t("全部状态", "All statuses") }}</option>
              <option
                v-for="s in [
                  'DRAFT',
                  'RETURNED',
                  'SUBMITTED',
                  'APPROVED',
                  'ALLOCATED',
                  'ACTIVE',
                  'REVIEW',
                  'ABORTED',
                  'CLOSED',
                  'CANCELLED',
                ]"
                :key="s"
                :value="s"
              >
                {{ statusName(s) }}
              </option></select
            ><select v-model="sort" :aria-label="t('排序', 'Sort')">
              <option value="newest">
                {{ t("最新在前", "Newest first") }}
              </option>
              <option value="oldest">
                {{ t("最早在前", "Oldest first") }}
              </option></select
            ><button class="secondary" :disabled="busy">
              {{ t("查询", "Search") }}
            </button>
          </form>
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th v-for="c in columns" :key="c[0]">{{ t(c[1], c[2]) }}</th>
                  <th v-if="view !== 'audit'">{{ t("操作", "Actions") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rows" :key="r.id">
                  <td v-for="c in columns" :key="c[0]">
                    <span
                      v-if="c[0] === 'status'"
                      class="status"
                      :data-status="r.status"
                      >{{ statusName(r.status) }}</span
                    ><template v-else>{{ value(r, c[0]) }}</template>
                  </td>
                  <td v-if="view !== 'audit'">
                    <div class="row-actions">
                      <button
                        v-if="business.includes(view)"
                        class="row-button"
                        @click="open(r)"
                      >
                        {{ t("查看", "Open") }}<ArrowRight :size="14" /></button
                      ><template v-else
                        ><button class="row-button" @click="edit(view, r)">
                          {{ t("编辑", "Edit") }}</button
                        ><button
                          v-if="
                            [
                              'users',
                              'roles',
                              'departments',
                              'dictionaries',
                            ].includes(view)
                          "
                          class="row-button danger"
                          @click="
                            modal = { kind: 'delete', type: view, id: r.id };
                            form = {};
                          "
                        >
                          {{ t("删除", "Delete") }}
                        </button></template
                      >
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-if="!rows.length" class="empty">
            {{ t("暂无符合条件的记录", "No matching records") }}
          </p>
          <div class="pagination">
            <span>{{ total }} {{ t("条记录", "records") }}</span>
            <div>
              <button
                :disabled="page === 0 || busy"
                @click="
                  page--;
                  run(load);
                "
              >
                <ChevronLeft :size="16" /></button
              ><span>{{ page + 1 }}</span
              ><button
                :disabled="(page + 1) * 12 >= total || busy"
                @click="
                  page++;
                  run(load);
                "
              >
                <ChevronRight :size="16" />
              </button>
            </div>
          </div>
        </section>
      </main>
    </div>
  </div>
  <div
    v-if="modal"
    class="modal-backdrop"
    @click.self="!busy && (modal = null)"
  >
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="
        modal.kind === 'command'
          ? actionName(modal.action)
          : t('编辑记录', 'Edit record')
      "
    >
      <header>
        <h2>
          {{
            modal.kind === "command"
              ? actionName(modal.action)
              : modal.kind === "password"
                ? t("修改密码", "Change password")
                : modal.kind === "delete"
                  ? t("删除记录", "Delete record")
                  : t("编辑记录", "Edit record")
          }}
        </h2>
        <button
          class="icon-button"
          :aria-label="t('关闭', 'Close')"
          :disabled="busy"
          @click="modal = null"
        >
          <X :size="19" />
        </button>
      </header>
      <form @submit.prevent="save">
        <p v-if="modal.kind === 'delete'" class="delete-note">
          {{
            t(
              "仅允许删除未被引用的管理记录。确认删除？",
              "Only unreferenced administrative records can be deleted. Delete?",
            )
          }}
        </p>
        <div v-else class="form-grid">
          <template v-for="f in modalFields" :key="f[0]"
            ><fieldset
              v-if="f[3] === 'permissions'"
              class="permissions-field full-width"
            >
              <legend>{{ t(f[1], f[2]) }}</legend>
              <label
                v-for="p in directories.permissions"
                :key="p.code"
                class="check"
                ><input
                  v-model="form.permissions"
                  type="checkbox"
                  :value="p.code"
                />{{ p.name }}<small>{{ p.code }}</small></label
              >
            </fieldset>
            <label v-else-if="f[3] === 'boolean'" class="check"
              ><input v-model="form[f[0]]" type="checkbox" />{{
                t(f[1], f[2])
              }}</label
            ><label v-else :class="{ 'full-width': f[3] === 'textarea' }"
              >{{ t(f[1], f[2])
              }}<select
                v-if="['id', 'select'].includes(f[3])"
                v-model="form[f[0]]"
                :disabled="immutable(f[0])"
                required
              >
                <option value="">{{ t("请选择", "Select") }}</option>
                <option
                  v-for="c in choices(f[4])"
                  :key="c.value"
                  :value="c.value"
                >
                  {{ c.label }}
                </option></select
              ><textarea
                v-else-if="f[3] === 'textarea'"
                v-model="form[f[0]]"
                rows="3"
                maxlength="1000"
                :required="!['missingReason'].includes(f[0])"
              ></textarea
              ><input
                v-else
                v-model="form[f[0]]"
                :type="
                  f[3] === 'password'
                    ? 'password'
                    : f[3] === 'date'
                      ? 'date'
                      : ['integer', 'decimal'].includes(f[3])
                        ? 'number'
                        : 'text'
                "
                :step="
                  f[3] === 'decimal'
                    ? '0.0001'
                    : f[3] === 'integer'
                      ? '1'
                      : undefined
                "
                :readonly="immutable(f[0])"
                :required="
                  !['value', 'password'].includes(f[0]) ||
                  (f[0] === 'password' && !modal.id)
                "
                :autocomplete="f[3] === 'password' ? 'new-password' : 'off'"
                maxlength="1000" /></label
          ></template>
        </div>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <footer>
          <button
            type="button"
            class="secondary"
            :disabled="busy"
            @click="modal = null"
          >
            {{ t("取消", "Cancel") }}</button
          ><button class="primary" :disabled="busy">
            {{ busy ? t("保存中", "Saving") : t("确认保存", "Save") }}
          </button>
        </footer>
      </form>
    </section>
  </div>
  <div v-if="contact" class="modal-backdrop" @click.self="contact = false">
    <section
      class="modal contact-modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('关于系统', 'About')"
    >
      <header>
        <h2>{{ t("关于 PlotTrial", "About PlotTrial") }}</h2>
        <button
          class="icon-button"
          :aria-label="t('关闭', 'Close')"
          @click="contact = false"
        >
          <X :size="19" />
        </button>
      </header>
      <img class="contact-logo" src="/brand/logo.jpg" alt="知华科技 LOGO" />
      <p>知华科技（上海如静知华信息科技有限公司）</p>
      <p>
        {{
          t(
            "公开源码学习版／非商业源码版，未经书面授权不得商用。",
            "Non-commercial source edition. Commercial use requires written authorization.",
          )
        }}
      </p>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        >https://www.zhuatech.cn/ <ExternalLink :size="15"
      /></a>
      <p>
        {{
          t(
            "商业授权、定制开发、部署与系统集成咨询",
            "Licensing, customization, deployment & integration",
          )
        }}
      </p>
      <div class="qr-grid">
        <figure v-for="wechat in ['zhuatech', 'zhuatech2']" :key="wechat">
          <img
            :src="'/brand/wechat-' + wechat + '.png'"
            :alt="'微信 ' + wechat + ' 二维码'"
          />
          <figcaption>{{ wechat }}</figcaption>
        </figure>
      </div>
      <p class="license-note">
        ZhuaTech Non-Commercial Source License 1.0 · v0.1.0
      </p>
    </section>
  </div>
</template>
