// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 试验、处理、指标、观测和账号的有限表单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const fields = {
  sites: [
    ["reference", "田块编号", "Site reference"],
    ["name", "田块名称", "Site name"],
    ["departmentId", "负责部门", "Department", "id", "departments"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  trials: [
    ["reference", "试验编号", "Trial reference"],
    ["name", "试验名称", "Trial name"],
    ["siteId", "田块", "Field site", "id", "sites"],
    ["reviewerId", "独立复核人", "Independent reviewer", "id", "reviewers"],
    ["cropType", "作物类型", "Crop type", "select", "crops"],
    ["cropName", "作物名称", "Crop name"],
    ["objective", "对比目标", "Objective", "textarea"],
    ["blockCount", "区组数（4—16）", "Blocks (4–16)", "integer"],
    ["plotArea", "每小区面积（平方米）", "Plot area (m²)", "decimal"],
  ],
  treatments: [
    ["code", "处理编号", "Treatment code"],
    ["name", "品种名称", "Variety name"],
    ["description", "品种及处理说明", "Description", "textarea"],
    ["control", "对照处理", "Control", "boolean"],
    ["enabled", "列入方案", "Included", "boolean"],
  ],
  measures: [
    ["code", "指标编号", "Measure code"],
    ["name", "观测指标", "Measure name"],
    ["unit", "计量单位", "Unit"],
    ["minimum", "最小允许值", "Minimum", "decimal"],
    ["maximum", "最大允许值", "Maximum", "decimal"],
    ["required", "结束前必录", "Required before completion", "boolean"],
    ["enabled", "列入方案", "Included", "boolean"],
  ],
  observations: [
    ["plotId", "小区", "Plot", "id", "plots"],
    ["measureId", "观测指标", "Measure", "id", "measures"],
    ["value", "测量值（缺测时留空）", "Value (blank if missing)", "decimal"],
    [
      "missingReason",
      "缺测原因（有值时留空）",
      "Missing reason (blank with value)",
      "textarea",
    ],
    ["observedDate", "实际观测日期", "Observed date", "date"],
    ["note", "观测／修订说明", "Observation / correction note", "textarea"],
  ],
  users: [
    ["username", "登录名", "Username"],
    ["displayName", "姓名", "Name"],
    [
      "password",
      "新密码（编辑时留空保留）",
      "New password (optional when editing)",
      "password",
    ],
    ["roleId", "角色", "Role", "id", "roles"],
    ["departmentId", "部门", "Department", "id", "departments"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  roles: [
    ["name", "角色名称", "Role name"],
    ["scope", "数据范围", "Data scope", "select", "scope"],
    ["permissions", "接口权限", "API permissions", "permissions"],
  ],
  departments: [["name", "部门名称", "Department name"]],
  menus: [
    ["name", "中文名称", "Chinese name"],
    ["nameEn", "英文名称", "English name"],
    [
      "permissionCode",
      "所需权限",
      "Required permission",
      "select",
      "permissions",
    ],
    ["position", "排序", "Order", "integer"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  permissions: [["name", "权限说明", "Permission description"]],
  dictionaries: [
    ["type", "字典类型", "Dictionary type"],
    ["code", "编码", "Code"],
    ["name", "中文名称", "Chinese name"],
    ["nameEn", "英文名称", "English name"],
  ],
  settings: [["value", "参数值", "Value"]],
};
/** 指派和人工日期与系统事实时间分别处理。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function commandFields(action) {
  return [
    ...(action === "assign"
      ? [["observerId", "观测员", "Observer", "id", "observers"]]
      : []),
    ...(["start", "plant"].includes(action)
      ? [["date", "实际日期", "Actual date", "date"]]
      : []),
    ["note", "操作事实／原因", "Reason / facts", "textarea"],
  ];
}
