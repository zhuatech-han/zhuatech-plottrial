// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.plottrial;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 实际HTTP/JPA区组、冻结、权限、缺测、修订、并发和导出验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrialIntegrationTest {
  static final String PASSWORD = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("plottrial.admin-password", () -> PASSWORD);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate sql;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, review, observer, other, outside;
  long reviewerId, observerId, otherId;
  String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();

  String key() {
    return UUID.randomUUID().toString();
  }

  MvcResult req(MockHttpSession who, String method, String path, Object value) throws Exception {
    var b =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    if (who != null) b.session(who);
    b.with(csrf());
    if (value != null) b.contentType("application/json").content(json.writeValueAsString(value));
    return mvc.perform(b).andReturn();
  }

  JsonNode ok(MockHttpSession who, String method, String path, Object value) throws Exception {
    var r = req(who, method, path, value);
    assertEquals(
        200, r.getResponse().getStatus(), path + " " + r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void fail(MockHttpSession who, String method, String path, Object value, int status, String code)
      throws Exception {
    var r = req(who, method, path, value);
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertEquals(code, json.readTree(r.getResponse().getContentAsString()).path("code").asString());
  }

  MockHttpSession login(String user) throws Exception {
    var r = req(null, "POST", "/auth/login", Map.of("username", user, "password", PASSWORD));
    assertEquals(200, r.getResponse().getStatus());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  Map<String, Object> cmd(JsonNode row) {
    return new HashMap<>(
        Map.of("requestKey", key(), "version", row.path("version").asLong(), "note", "TEST 事实记录"));
  }

  JsonNode command(MockHttpSession who, String type, JsonNode row, String action) throws Exception {
    return ok(
        who, "POST", "/" + type + "/" + row.path("id").asLong() + "/commands/" + action, cmd(row));
  }

  JsonNode detail(JsonNode t, MockHttpSession who) throws Exception {
    return ok(who, "GET", "/trials/" + t.path("id").asLong(), null);
  }

  JsonNode current(JsonNode t) throws Exception {
    return detail(t, admin).path("record");
  }

  long role(String name, String scope, Set<String> ps) throws Exception {
    return ok(
            admin,
            "POST",
            "/admin/roles",
            Map.of("name", name + key(), "scope", scope, "permissions", ps))
        .path("id")
        .asLong();
  }

  JsonNode user(String name, long role, long dept) throws Exception {
    return ok(
        admin,
        "POST",
        "/admin/users",
        Map.of(
            "username",
            name,
            "displayName",
            name,
            "password",
            PASSWORD,
            "roleId",
            role,
            "departmentId",
            dept,
            "enabled",
            true));
  }

  @BeforeAll
  void setup() throws Exception {
    admin = login("admin");
    long rr =
        role(
            "Review",
            "ALL",
            Set.of("trial.read", "trial.review", "observation.review", "dashboard", "export"));
    long ob = role("Observer", "ALL", Set.of("trial.read", "plot.observe", "dashboard", "export"));
    String suffix = key().substring(0, 8);
    var r = user("rev" + suffix, rr, 1);
    reviewerId = r.path("id").asLong();
    review = login(r.path("username").asString());
    var o = user("obs" + suffix, ob, 1);
    observerId = o.path("id").asLong();
    observer = login(o.path("username").asString());
    var x = user("oth" + suffix, ob, 1);
    otherId = x.path("id").asLong();
    other = login(x.path("username").asString());
    long d =
        ok(admin, "POST", "/admin/departments", Map.of("name", "Outside" + suffix))
            .path("id")
            .asLong();
    long dr =
        role(
            "Outside",
            "DEPARTMENT",
            Set.of("trial.read", "site.read", "site.write", "trial.write", "export", "dashboard"));
    var out = user("out" + suffix, dr, d);
    outside = login(out.path("username").asString());
  }

  JsonNode draft() throws Exception {
    var s =
        ok(
            admin,
            "POST",
            "/sites",
            Map.of(
                "requestKey",
                key(),
                "reference",
                "S" + key(),
                "name",
                "TEST 田块",
                "departmentId",
                1,
                "enabled",
                true));
    return ok(
        admin,
        "POST",
        "/trials",
        Map.of(
            "requestKey",
            key(),
            "reference",
            "T" + key(),
            "name",
            "TEST 品种试验",
            "siteId",
            s.path("id").asLong(),
            "reviewerId",
            reviewerId,
            "cropType",
            "CEREAL",
            "cropName",
            "TEST 谷物",
            "objective",
            "TEST 对比记录",
            "blockCount",
            4,
            "plotArea",
            12.5));
  }

  JsonNode definitions(JsonNode t) throws Exception {
    long id = t.path("id").asLong();
    for (int i = 0; i < 2; i++)
      ok(
          admin,
          "POST",
          "/treatments",
          Map.of(
              "requestKey",
              key(),
              "trialId",
              id,
              "code",
              i == 0 ? "A" : "B",
              "name",
              "TEST 品种" + i,
              "description",
              "TEST 处理描述",
              "control",
              i == 0,
              "enabled",
              true));
    ok(
        admin,
        "POST",
        "/measures",
        Map.of(
            "requestKey",
            key(),
            "trialId",
            id,
            "code",
            "HEIGHT",
            "name",
            "TEST 株高",
            "unit",
            "cm",
            "minimum",
            0,
            "maximum",
            100,
            "required",
            true,
            "enabled",
            true));
    return current(t);
  }

  JsonNode allocated() throws Exception {
    var t = definitions(draft());
    t = command(admin, "trials", t, "submit");
    t = command(review, "trials", t, "approve");
    return command(admin, "trials", t, "allocate");
  }

  JsonNode active() throws Exception {
    var t = allocated();
    for (var p : detail(t, admin).path("plots")) {
      var v = cmd(p);
      v.put("observerId", observerId);
      var assigned = ok(admin, "POST", "/plots/" + p.path("id").asLong() + "/commands/assign", v);
      command(observer, "plots", assigned, "receive");
    }
    var v = cmd(current(t));
    v.put("date", today);
    t = ok(admin, "POST", "/trials/" + t.path("id").asLong() + "/commands/start", v);
    for (var p : detail(t, admin).path("plots")) {
      v = cmd(p);
      v.put("date", today);
      ok(observer, "POST", "/plots/" + p.path("id").asLong() + "/commands/plant", v);
    }
    return current(t);
  }

  Map<String, Object> measurement(JsonNode p, JsonNode m, Object value, String missing, Long base) {
    var v = new HashMap<String, Object>();
    v.put("requestKey", key());
    v.put("plotId", p.path("id").asLong());
    v.put("measureId", m.path("id").asLong());
    v.put("value", value);
    v.put("missingReason", missing);
    v.put("note", "TEST 观测记录");
    v.put("observedDate", today);
    v.put("supersedesId", base);
    return v;
  }

  JsonNode accepted(JsonNode p, JsonNode m, Object value, String missing) throws Exception {
    var o = ok(observer, "POST", "/observations", measurement(p, m, value, missing, null));
    o = command(observer, "observations", o, "submit");
    return command(review, "observations", o, "accept");
  }

  @Test
  void anonymousAndCsrfAreDenied() throws Exception {
    assertEquals(401, req(null, "GET", "/trials", null).getResponse().getStatus());
    assertEquals(
        403,
        mvc.perform(post("/api/sites").session(admin).contentType("application/json").content("{}"))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void incompletePlanCannotBeSubmitted() throws Exception {
    var t = draft();
    fail(
        admin,
        "POST",
        "/trials/" + t.path("id").asLong() + "/commands/submit",
        cmd(t),
        409,
        "INCOMPLETE_TREATMENTS");
  }

  @Test
  void assignedIndependentReviewIsRequired() throws Exception {
    var t = definitions(draft());
    t = command(admin, "trials", t, "submit");
    fail(
        admin,
        "POST",
        "/trials/" + t.path("id").asLong() + "/commands/approve",
        cmd(t),
        403,
        "ASSIGNED_REVIEWER_REQUIRED");
    t = command(review, "trials", t, "reject");
    assertEquals("RETURNED", t.path("status").asString());
    t = command(admin, "trials", t, "submit");
    t = command(admin, "trials", t, "withdraw");
    assertEquals("DRAFT", t.path("status").asString());
  }

  @Test
  void approvedPlanAndDefinitionsAreImmutable() throws Exception {
    var t = allocated();
    var d = detail(t, admin);
    var r = d.path("treatments").get(0);
    var v = json.convertValue(r, Map.class);
    v.put("requestKey", key());
    fail(admin, "PUT", "/treatments/" + r.path("id").asLong(), v, 409, "INVALID_STATE");
    assertTrue(t.path("planHash").asString().matches("[a-f0-9]{64}"));
  }

  @Test
  void layoutIsCompleteAndReproducible() throws Exception {
    var t = allocated();
    var ps = detail(t, admin).path("plots");
    assertEquals(8, ps.size());
    for (int b = 1; b <= 4; b++) {
      var actual = new ArrayList<String>();
      for (var p : ps)
        if (p.path("blockIndex").asInt() == b) actual.add(p.path("treatmentCode").asString());
      assertEquals(Set.of("A", "B"), new HashSet<>(actual));
      var expected =
          BlockRandomizer.order(t.path("layoutSeed").asString(), b, 2).stream()
              .map(i -> i == 0 ? "A" : "B")
              .toList();
      assertEquals(expected, actual);
    }
    fail(
        admin,
        "POST",
        "/trials/" + t.path("id").asLong() + "/commands/allocate",
        cmd(t),
        409,
        "INVALID_STATE");
  }

  @Test
  void startRequiresAllAssignmentsAndReceipts() throws Exception {
    var t = allocated();
    var v = cmd(t);
    v.put("date", today);
    fail(
        admin,
        "POST",
        "/trials/" + t.path("id").asLong() + "/commands/start",
        v,
        409,
        "CREW_NOT_READY");
  }

  @Test
  void allScopeObserverSeesOnlyAssignedPlots() throws Exception {
    var t = allocated();
    var ps = detail(t, admin).path("plots");
    var v = cmd(ps.get(0));
    v.put("observerId", observerId);
    ok(admin, "POST", "/plots/" + ps.get(0).path("id").asLong() + "/commands/assign", v);
    v = cmd(ps.get(1));
    v.put("observerId", otherId);
    ok(admin, "POST", "/plots/" + ps.get(1).path("id").asLong() + "/commands/assign", v);
    assertEquals(1, detail(t, observer).path("plots").size());
    assertEquals(1, detail(t, other).path("plots").size());
    fail(
        other,
        "POST",
        "/plots/" + ps.get(0).path("id").asLong() + "/commands/receive",
        cmd(ps.get(0)),
        403,
        "OUT_OF_SCOPE");
    assertFalse(ok(observer, "GET", "/options", null).toString().contains("password"));
  }

  @Test
  void departmentCannotReadOrExportOutsideData() throws Exception {
    var t = draft();
    fail(outside, "GET", "/trials/" + t.path("id").asLong(), null, 403, "OUT_OF_SCOPE");
    fail(
        outside,
        "GET",
        "/trials/" + t.path("id").asLong() + "/report.json",
        null,
        403,
        "OUT_OF_SCOPE");
    assertEquals(0, ok(outside, "GET", "/trials", null).path("total").asInt());
  }

  @Test
  void repeatedRequestReplaysExactResponseAndDifferentPayloadFails() throws Exception {
    var t = definitions(draft());
    var v = cmd(t);
    var a = req(admin, "POST", "/trials/" + t.path("id").asLong() + "/commands/submit", v);
    var b = req(admin, "POST", "/trials/" + t.path("id").asLong() + "/commands/submit", v);
    assertEquals(200, a.getResponse().getStatus());
    assertEquals(a.getResponse().getContentAsString(), b.getResponse().getContentAsString());
    v.put("note", "different");
    fail(
        admin,
        "POST",
        "/trials/" + t.path("id").asLong() + "/commands/submit",
        v,
        409,
        "REQUEST_KEY_REUSED");
  }

  @Test
  void staleVersionDoesNotConsumeRequestKey() throws Exception {
    var t = definitions(draft());
    var v = cmd(t);
    v.put("version", 0);
    fail(
        admin,
        "POST",
        "/trials/" + t.path("id").asLong() + "/commands/submit",
        v,
        409,
        "STALE_VERSION");
    v.put("version", t.path("version").asLong());
    assertEquals(
        "SUBMITTED",
        ok(admin, "POST", "/trials/" + t.path("id").asLong() + "/commands/submit", v)
            .path("status")
            .asString());
  }

  @Test
  void missingAndNumericZeroRemainDistinct() throws Exception {
    var t = active();
    var d = detail(t, admin);
    var m = d.path("measures").get(0);
    var a = accepted(d.path("plots").get(0), m, 0, "");
    var b = accepted(d.path("plots").get(1), m, null, "TEST 无法测量");
    assertEquals(0, a.path("value").decimalValue().signum());
    assertTrue(b.path("value").isNull());
    var rows = detail(t, admin).path("summary");
    int count = 0, missing = 0;
    for (var r : rows) {
      count += r.path("n").asInt();
      missing += r.path("missing").asInt();
    }
    assertEquals(1, count);
    assertEquals(1, missing);
  }

  @Test
  void valueAndMissingReasonCannotCoexist() throws Exception {
    var t = active();
    var d = detail(t, admin);
    fail(
        observer,
        "POST",
        "/observations",
        measurement(d.path("plots").get(0), d.path("measures").get(0), 5, "TEST 缺测", null),
        409,
        "VALUE_AND_MISSING_REASON");
  }

  @Test
  void observationBoundsAndDateAreEnforced() throws Exception {
    var t = active();
    var d = detail(t, admin);
    var v = measurement(d.path("plots").get(0), d.path("measures").get(0), 101, "", null);
    fail(observer, "POST", "/observations", v, 409, "OUTSIDE_MEASURE_RANGE");
    v.put("value", 1.23456);
    fail(observer, "POST", "/observations", v, 400, "INVALID_DECIMAL");
    v.put("value", 10);
    v.put("observedDate", "1999-01-01");
    fail(observer, "POST", "/observations", v, 400, "INVALID_DATE");
  }

  @Test
  void pendingObservationBlocksDuplicateAndEnd() throws Exception {
    var t = active();
    var d = detail(t, admin);
    var v = measurement(d.path("plots").get(0), d.path("measures").get(0), 12, "", null);
    ok(observer, "POST", "/observations", v);
    v.put("requestKey", key());
    fail(observer, "POST", "/observations", v, 409, "OPEN_OBSERVATION");
    fail(
        admin,
        "POST",
        "/trials/" + t.path("id").asLong() + "/commands/end",
        cmd(current(t)),
        409,
        "PENDING_OBSERVATIONS");
  }

  @Test
  void correctionPreservesOriginalUntilAccepted() throws Exception {
    var t = active();
    var d = detail(t, admin);
    var p = d.path("plots").get(0);
    var m = d.path("measures").get(0);
    var a = accepted(p, m, 10, "");
    var b = ok(observer, "POST", "/observations", measurement(p, m, 20, "", a.path("id").asLong()));
    var s = detail(t, admin).path("summary");
    for (var row : s)
      if (row.path("treatment").asString().equals(p.path("treatmentCode").asString()))
        assertEquals(10, row.path("mean").decimalValue().intValue());
    b = command(observer, "observations", b, "submit");
    b = command(review, "observations", b, "accept");
    assertEquals(2, b.path("revision").asInt());
    var all = detail(t, admin).path("observations");
    assertEquals("ACCEPTED", all.get(0).path("status").asString());
    assertEquals(10, all.get(0).path("value").decimalValue().intValue());
    for (var row : detail(t, admin).path("summary"))
      if (row.path("treatment").asString().equals(p.path("treatmentCode").asString()))
        assertEquals(20, row.path("mean").decimalValue().intValue());
  }

  @Test
  void correctionMustPointAtCurrentAcceptedRevision() throws Exception {
    var t = active();
    var d = detail(t, admin);
    var p = d.path("plots").get(0);
    var m = d.path("measures").get(0);
    accepted(p, m, 10, "");
    fail(
        observer,
        "POST",
        "/observations",
        measurement(p, m, 20, "", null),
        409,
        "CORRECTION_BASE_REQUIRED");
  }

  @Test
  void observerCannotAcceptOwnObservation() throws Exception {
    var t = active();
    var d = detail(t, admin);
    var o =
        ok(
            observer,
            "POST",
            "/observations",
            measurement(d.path("plots").get(0), d.path("measures").get(0), 10, "", null));
    o = command(observer, "observations", o, "submit");
    fail(
        observer,
        "POST",
        "/observations/" + o.path("id").asLong() + "/commands/accept",
        cmd(o),
        403,
        "FORBIDDEN");
    o = command(review, "observations", o, "reject");
    assertEquals("RETURNED", o.path("status").asString());
    o = command(observer, "observations", o, "cancel");
    assertEquals("CANCELLED", o.path("status").asString());
  }

  @Test
  void exclusionRequiresIndependentDecisionAndPreservesHistory() throws Exception {
    var t = active();
    var d = detail(t, admin);
    var p = d.path("plots").get(0);
    accepted(p, d.path("measures").get(0), 100, "");
    p = command(observer, "plots", p, "request-exclusion");
    fail(
        admin,
        "POST",
        "/trials/" + t.path("id").asLong() + "/commands/end",
        cmd(current(t)),
        409,
        "PENDING_EXCLUSIONS");
    p = command(review, "plots", p, "approve-exclusion");
    assertTrue(p.path("excluded").asBoolean());
    assertEquals(1, detail(t, admin).path("observations").size());
    for (var row : detail(t, admin).path("summary")) assertEquals(0, row.path("n").asInt());
  }

  @Test
  void unfinishedRequiredMeasurementsBlockEnd() throws Exception {
    var t = active();
    fail(
        admin,
        "POST",
        "/trials/" + t.path("id").asLong() + "/commands/end",
        cmd(t),
        409,
        "MISSING_REQUIRED_OBSERVATION");
  }

  @Test
  void completeTrialReopensThenFreezesAllData() throws Exception {
    var t = active();
    var d = detail(t, admin);
    for (var p : d.path("plots")) accepted(p, d.path("measures").get(0), 12, "");
    t = command(admin, "trials", current(t), "end");
    t = command(review, "trials", t, "reopen");
    assertEquals("ACTIVE", t.path("status").asString());
    t = command(admin, "trials", t, "end");
    t = command(review, "trials", t, "close");
    assertEquals("CLOSED", t.path("status").asString());
    assertEquals("FINISHED", t.path("outcome").asString());
    assertTrue(t.path("dataHash").asString().matches("[a-f0-9]{64}"));
    fail(
        admin,
        "POST",
        "/trials/" + t.path("id").asLong() + "/commands/start",
        cmd(t),
        409,
        "INVALID_STATE");
  }

  @Test
  void abortedTrialClosesWithDistinctOutcome() throws Exception {
    var t = active();
    t = command(admin, "trials", t, "abort");
    t = command(review, "trials", t, "close");
    assertEquals("ABORTED", t.path("outcome").asString());
    assertEquals("CLOSED", t.path("status").asString());
  }

  @Test
  void concurrentObservationCreationHasOneWinner() throws Exception {
    var t = active();
    var d = detail(t, admin);
    var p = d.path("plots").get(0);
    var m = d.path("measures").get(0);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var fs =
          pool.invokeAll(
              List.of(
                  () ->
                      req(observer, "POST", "/observations", measurement(p, m, 10, "", null))
                          .getResponse()
                          .getStatus(),
                  () ->
                      req(observer, "POST", "/observations", measurement(p, m, 20, "", null))
                          .getResponse()
                          .getStatus()));
      var statuses = new ArrayList<Integer>();
      for (var f : fs) statuses.add((Integer) f.get());
      Collections.sort(statuses);
      assertEquals(List.of(200, 409), statuses);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void csvKeepsMissingReasonAndEscapesFormulaStrings() throws Exception {
    var t = active();
    var d = detail(t, admin);
    var v = measurement(d.path("plots").get(0), d.path("measures").get(0), null, "=TEST(1)", null);
    var o = ok(observer, "POST", "/observations", v);
    o = command(observer, "observations", o, "submit");
    command(review, "observations", o, "accept");
    var r = req(admin, "GET", "/trials/" + t.path("id").asLong() + "/observations.csv", null);
    assertEquals(200, r.getResponse().getStatus());
    var csv = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(csv.startsWith("\uFEFFtrial,"));
    assertTrue(csv.contains("\"'=TEST(1)\""));
    assertFalse(csv.contains("zhuatech2"));
  }

  @Test
  void paginationAndSchemaAreBounded() throws Exception {
    fail(admin, "GET", "/trials?size=101", null, 400, "INVALID_INPUT");
    assertEquals(
        2,
        sql.queryForObject(
            "select count(*) from flyway_schema_history where success=true and version in ('1','2')",
            Integer.class));
  }

  @Test
  void invalidKeysAndDuplicateReferencesFailWithoutExtraData() throws Exception {
    var v =
        new HashMap<String, Object>(
            Map.of(
                "requestKey",
                "bad",
                "reference",
                "TEST UNIQUE" + key(),
                "name",
                "TEST",
                "departmentId",
                1,
                "enabled",
                true));
    fail(admin, "POST", "/sites", v, 400, "INVALID_REQUEST_KEY");
    v.put("requestKey", key());
    ok(admin, "POST", "/sites", v);
    v.put("requestKey", key());
    fail(admin, "POST", "/sites", v, 409, "CONFLICT");
  }

  @Test
  void lastAdministratorCannotBeDisabled() throws Exception {
    var a = ok(admin, "GET", "/admin/users", null).get(0);
    var v = json.convertValue(a, Map.class);
    v.put("enabled", false);
    fail(admin, "PUT", "/admin/users/" + a.path("id").asLong(), v, 409, "LAST_ADMIN");
  }

  @Test
  void disabledAccountImmediatelyLosesExistingSession() throws Exception {
    long r = role("Temporary", "SELF", Set.of("trial.read"));
    var a = user("tmp" + key().substring(0, 8), r, 1);
    var session = login(a.path("username").asString());
    var v = json.convertValue(a, Map.class);
    v.put("enabled", false);
    ok(admin, "PUT", "/admin/users/" + a.path("id").asLong(), v);
    fail(session, "GET", "/auth/me", null, 401, "UNAUTHENTICATED");
  }

  @Test
  void referencedCropAndDepartmentCannotBeDeleted() throws Exception {
    draft();
    var ds = ok(admin, "GET", "/admin/dictionaries", null);
    for (var d : ds)
      if (d.path("code").asString().equals("CEREAL"))
        fail(
            admin,
            "DELETE",
            "/admin/dictionaries/" + d.path("id").asLong(),
            Map.of(),
            409,
            "CONFLICT");
    fail(admin, "DELETE", "/admin/departments/1", Map.of(), 409, "BUILTIN_RESOURCE");
  }

  @Test
  void disabledSiteCannotBeUsedForNewTrial() throws Exception {
    var t = draft();
    var site = ok(admin, "GET", "/sites/" + t.path("siteId").asLong(), null).path("record");
    var v = json.convertValue(site, Map.class);
    v.put("requestKey", key());
    v.put("enabled", false);
    ok(admin, "PUT", "/sites/" + site.path("id").asLong(), v);
    t = definitions(t);
    t = command(admin, "trials", t, "submit");
    fail(
        review,
        "POST",
        "/trials/" + t.path("id").asLong() + "/commands/approve",
        cmd(t),
        409,
        "DISABLED_RESOURCE");
  }

  @Test
  void reviewerWhoEditedTreatmentCannotApprovePlan() throws Exception {
    long rr =
        role(
            "MixedReview",
            "ALL",
            Set.of("trial.read", "trial.write", "trial.review", "observation.review"));
    var a = user("mixed" + key().substring(0, 8), rr, 1);
    var actor = login(a.path("username").asString());
    var t = definitions(draft());
    var v = json.convertValue(t, Map.class);
    v.put("requestKey", key());
    v.put("reviewerId", a.path("id").asLong());
    t = ok(admin, "PUT", "/trials/" + t.path("id").asLong(), v);
    var r = detail(t, admin).path("treatments").get(0);
    v = json.convertValue(r, Map.class);
    v.put("requestKey", key());
    v.put("description", "TEST 复核人参与编辑");
    ok(actor, "PUT", "/treatments/" + r.path("id").asLong(), v);
    t = command(admin, "trials", current(t), "submit");
    fail(
        actor,
        "POST",
        "/trials/" + t.path("id").asLong() + "/commands/approve",
        cmd(t),
        409,
        "INDEPENDENT_REVIEW_REQUIRED");
  }
}
