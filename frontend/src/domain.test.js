// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { actions, payload, localDate, currentAccepted } from "./domain.js";
test("frozen plans show no edit transitions", () =>
  assert.deepEqual(
    actions(
      "trials",
      { status: "CLOSED" },
      { permissions: ["trial.write"], scope: "ALL" },
    ),
    [],
  ));
test("reviewer must be assigned and independent", () => {
  const row = { status: "SUBMITTED", reviewerId: 2 };
  assert.deepEqual(
    actions("trials", row, { id: 3, permissions: ["trial.review"] }),
    [],
  );
  assert.deepEqual(
    actions(
      "trials",
      row,
      { id: 2, permissions: ["trial.review"] },
      { editors: [{ actorId: 2 }] },
    ),
    ["reject"],
  );
});
test("ALL observation permission still requires assigned observer", () =>
  assert.deepEqual(
    actions(
      "plots",
      { observerId: 2, receivedAt: null },
      { id: 3, scope: "ALL", permissions: ["plot.observe"] },
      { record: { status: "ALLOCATED" } },
    ),
    [],
  ));
test("received plot cannot be received twice", () =>
  assert.deepEqual(
    actions(
      "plots",
      { observerId: 2, receivedAt: "2026-01-01" },
      { id: 2, permissions: ["plot.observe"] },
      { record: { status: "ALLOCATED" } },
    ),
    [],
  ));
test("only independent assigned reviewer gets acceptance", () => {
  assert.deepEqual(
    actions(
      "observations",
      { createdBy: 2, status: "SUBMITTED" },
      { id: 2, permissions: ["observation.review"] },
      { record: { status: "ACTIVE", reviewerId: 2 } },
    ),
    [],
  );
  assert.deepEqual(
    actions(
      "observations",
      { createdBy: 1, status: "SUBMITTED" },
      { id: 2, permissions: ["observation.review"] },
      { record: { status: "ACTIVE", reviewerId: 2 } },
    ),
    ["accept", "reject"],
  );
});
test("numeric zero differs from blank and decimals remain exact text", () => {
  const f = [["value", "", "", "decimal"]];
  assert.deepEqual(payload({ value: 0 }, f), { value: "0" });
  assert.deepEqual(payload({ value: "" }, f), { value: null });
  assert.deepEqual(payload({ value: "999999999999.9999" }, f), {
    value: "999999999999.9999",
  });
});
test("input whitelist ignores injected state and hashes", () =>
  assert.deepEqual(
    payload({ name: "A", status: "CLOSED", dataHash: "evil" }, [["name"]]),
    { name: "A" },
  ));
test("Shanghai date follows local day boundary", () =>
  assert.equal(localDate(new Date("2026-10-05T16:00:00Z")), "2026-10-06"));
test("pending correction does not supersede accepted value", () => {
  const rows = [
    { id: 1, plotId: 1, measureId: 2, revision: 1, status: "ACCEPTED" },
    { id: 2, plotId: 1, measureId: 2, revision: 2, status: "DRAFT" },
  ];
  assert.equal(currentAccepted(rows, 1, 2).id, 1);
  rows[1].status = "ACCEPTED";
  assert.equal(currentAccepted(rows, 1, 2).id, 2);
});
