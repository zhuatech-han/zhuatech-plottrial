// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 状态显示和岗位动作；真实授权始终由服务端检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const states = {
  DRAFT: ["草稿", "Draft"],
  RETURNED: ["已退回", "Returned"],
  SUBMITTED: ["待核实", "Submitted"],
  APPROVED: ["方案冻结", "Approved"],
  ALLOCATED: ["布局就绪", "Allocated"],
  ACTIVE: ["观测中", "Active"],
  REVIEW: ["待关单复核", "Final review"],
  ABORTED: ["中止待关单", "Aborted"],
  CLOSED: ["数据冻结", "Closed"],
  CANCELLED: ["已取消", "Cancelled"],
  ACCEPTED: ["已核实", "Accepted"],
  PENDING: ["待复核", "Pending"],
  NONE: ["无排除", "None"],
  REJECTED: ["不予排除", "Rejected"],
};
/** 按指定账号和本版编辑人显示操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function actions(type, row, me, detail = {}) {
  if (!row || !me) return [];
  const has = (p) => me.permissions?.includes(p),
    result = [],
    trial = detail.record;
  if (type === "trials") {
    if (
      has("trial.write") &&
      (me.scope === "ALL" ||
        (row.departmentId === me.departmentId &&
          (me.scope !== "SELF" || row.createdBy === me.id)))
    ) {
      if (["DRAFT", "RETURNED"].includes(row.status)) result.push("submit");
      if (row.status === "SUBMITTED") result.push("withdraw");
      if (row.status === "APPROVED") result.push("allocate");
      if (row.status === "ALLOCATED") result.push("start");
      if (row.status === "ACTIVE") result.push("end", "abort");
      if (
        ["DRAFT", "RETURNED", "SUBMITTED", "APPROVED", "ALLOCATED"].includes(
          row.status,
        )
      )
        result.push("cancel");
    }
    if (has("trial.review") && me.id === row.reviewerId) {
      if (row.status === "SUBMITTED") {
        if (!(detail.editors || []).some((e) => e.actorId === me.id))
          result.push("approve");
        result.push("reject");
      }
      if (row.status === "REVIEW") result.push("reopen");
      if (
        ["REVIEW", "ABORTED"].includes(row.status) &&
        !(detail.editors || []).some((e) => e.actorId === me.id)
      )
        result.push("close");
    }
  }
  if (type === "plots" && trial) {
    if (has("plot.assign") && trial.status === "ALLOCATED")
      result.push("assign");
    if (has("plot.observe") && row.observerId === me.id) {
      if (trial.status === "ALLOCATED" && !row.receivedAt)
        result.push("receive");
      if (trial.status === "ACTIVE" && !row.excluded) {
        if (!row.plantingDate) result.push("plant");
        if (row.exclusionStatus !== "PENDING") result.push("request-exclusion");
      }
    }
    if (
      has("observation.review") &&
      trial.reviewerId === me.id &&
      trial.status === "ACTIVE" &&
      row.exclusionStatus === "PENDING"
    )
      result.push("approve-exclusion", "reject-exclusion");
  }
  if (type === "observations" && trial?.status === "ACTIVE") {
    if (has("plot.observe") && row.createdBy === me.id) {
      if (["DRAFT", "RETURNED"].includes(row.status)) result.push("submit");
      if (row.status === "SUBMITTED") result.push("withdraw");
      if (["DRAFT", "RETURNED", "SUBMITTED"].includes(row.status))
        result.push("cancel");
    }
    if (
      has("observation.review") &&
      trial.reviewerId === me.id &&
      row.createdBy !== me.id &&
      row.status === "SUBMITTED"
    )
      result.push("accept", "reject");
  }
  return result;
}
/** 只发送声明字段，保持缺测null和精确十进制文本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function payload(form, fields) {
  const out = {};
  for (const [key, , , type] of fields) {
    const v = form[key];
    out[key] = ["integer", "id"].includes(type)
      ? v == null || v === ""
        ? null
        : Number(v)
      : type === "decimal"
        ? v == null || v === ""
          ? null
          : String(v)
        : type === "boolean"
          ? Boolean(v)
          : type === "permissions"
            ? v || []
            : (v ?? "");
  }
  return out;
}
/** 上海自然日，无UTC跨日偏移。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function localDate(date = new Date()) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}
/** 当前核实版本由最高已接受修订确定，不把草稿当作新事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function currentAccepted(rows, plotId, measureId) {
  return (
    rows
      .filter(
        (o) =>
          o.plotId === plotId &&
          o.measureId === measureId &&
          o.status === "ACCEPTED",
      )
      .sort((a, b) => b.revision - a.revision)[0] || null
  );
}
