// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.plottrial;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 试验冻结、随机布局、指派观测、修订和独立核实的事务边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class TrialService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final ObjectMapper json;

  public TrialService(Store db, AccessService access, Clock clock, ObjectMapper json) {
    this.db = db;
    this.access = access;
    this.clock = clock;
    this.json = json;
  }

  /** 草稿字段不接受状态、随机种子、实际审核人或软件事实时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Input(
      String requestKey,
      Long version,
      String reference,
      String name,
      Long departmentId,
      Boolean enabled,
      Long siteId,
      Long reviewerId,
      String cropType,
      String cropName,
      String objective,
      Integer blockCount,
      BigDecimal plotArea,
      Long trialId,
      String code,
      String description,
      Boolean control,
      String unit,
      BigDecimal minimum,
      BigDecimal maximum,
      Boolean required) {}

  /** 数值与明确缺测分别提交，修订指向当前已核实版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record ObservationInput(
      String requestKey,
      Long version,
      Long plotId,
      Long measureId,
      BigDecimal value,
      String missingReason,
      String note,
      LocalDate observedDate,
      Long supersedesId) {}

  /** 明确命令说明、指派账号和人工日期，事实时间只由服务端生成。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(
      String requestKey, Long version, String note, Long observerId, LocalDate date) {}

  private static final Set<String> EDITABLE = Set.of("DRAFT", "RETURNED");
  private static final Set<String> PENDING = Set.of("DRAFT", "RETURNED", "SUBMITTED");

  private void check(boolean ok, String code) {
    if (!ok) throw new Problem(409, code);
  }

  private String text(String s, int n) {
    return AdminService.text(s, n);
  }

  private Instant now() {
    return BusinessTime.now(clock);
  }

  private Long who() {
    return access.current().id;
  }

  private void lock() {
    db.lock(Department.class, 1L);
    var a = access.current();
    db.refresh(a);
    db.refresh(db.get(AccessRole.class, a.roleId));
    access.current();
  }

  private void version(long actual, Long input) {
    check(input != null && input == actual, "STALE_VERSION");
  }

  private boolean scope(Long dept, Long creator) {
    return access.visible(dept)
        && (!access.role().scope.equals("SELF") || Objects.equals(who(), creator));
  }

  private boolean observerOnly() {
    var ps = access.role().permissions;
    return ps.contains("plot.observe")
        && Collections.disjoint(
            ps,
            Set.of("trial.write", "trial.review", "plot.assign", "observation.review", "admin"));
  }

  private boolean siteVisible(FieldSite s) {
    return !observerOnly() && scope(s.departmentId, s.createdBy);
  }

  private FieldSite site(Long id) {
    var s = db.get(FieldSite.class, id);
    if (!siteVisible(s)) throw new Problem(403, "OUT_OF_SCOPE");
    return s;
  }

  private List<TrialPlot> plots(Long id) {
    return db.query(
        TrialPlot.class, "from TrialPlot where trialId=?1 order by blockIndex,position", id);
  }

  private List<Treatment> treatments(Long id) {
    return db.query(Treatment.class, "from Treatment where trialId=?1 order by code", id);
  }

  private List<MeasureDefinition> measures(Long id) {
    return db.query(
        MeasureDefinition.class, "from MeasureDefinition where trialId=?1 order by code", id);
  }

  private List<Observation> observations(Long id) {
    return db.query(
        Observation.class,
        "from Observation where plotId in (select id from TrialPlot where trialId=?1) order by id",
        id);
  }

  private boolean visible(TrialPlan t) {
    return Objects.equals(who(), t.reviewerId)
        || plots(t.id).stream().anyMatch(p -> Objects.equals(who(), p.observerId))
        || (!observerOnly() && scope(t.departmentId, t.createdBy));
  }

  private TrialPlan trial(Long id) {
    var t = db.get(TrialPlan.class, id);
    if (!visible(t)) throw new Problem(403, "OUT_OF_SCOPE");
    return t;
  }

  private TrialPlot plot(Long id) {
    var p = db.get(TrialPlot.class, id);
    trial(p.trialId);
    if (observerOnly() && !Objects.equals(who(), p.observerId))
      throw new Problem(403, "OUT_OF_SCOPE");
    return p;
  }

  private void writer(TrialPlan t) {
    access.require("trial.write");
    if (!scope(t.departmentId, t.createdBy)) throw new Problem(403, "OUT_OF_SCOPE");
  }

  private void reviewer(TrialPlan t, String permission) {
    access.require(permission);
    if (!Objects.equals(who(), t.reviewerId)) throw new Problem(403, "ASSIGNED_REVIEWER_REQUIRED");
  }

  private void observer(TrialPlot p) {
    access.require("plot.observe");
    if (!Objects.equals(who(), p.observerId)) throw new Problem(403, "ASSIGNED_OBSERVER_REQUIRED");
  }

  private void eligible(Long id, String permission) {
    var a = db.get(Account.class, id);
    check(
        a.enabled && db.get(AccessRole.class, a.roleId).permissions.contains(permission),
        "INELIGIBLE_ASSIGNMENT");
  }

  private void editMark(TrialPlan t) {
    if (db.query(TrialEditor.class, "from TrialEditor where trialId=?1 and actorId=?2", t.id, who())
        .isEmpty()) {
      var e = new TrialEditor();
      e.trialId = t.id;
      e.actorId = who();
      db.save(e);
    }
  }

  private boolean independent(TrialPlan t) {
    return db.query(
            TrialEditor.class, "from TrialEditor where trialId=?1 and actorId=?2", t.id, who())
        .isEmpty();
  }

  private void limit(Class<?> type, int cap) {
    check(db.all(type).size() < cap, "RECORD_LIMIT");
  }

  private int cap() {
    return Integer.parseInt(
        db.query(SystemSetting.class, "from SystemSetting where code=?1", "maxRecords")
            .getFirst()
            .value);
  }

  private String encode(Object o) {
    return json.writeValueAsString(o);
  }

  private String hash(Object o) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(encode(o).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Object write(String key, Object payload, Supplier<Object> action) {
    try {
      if (key == null || !UUID.fromString(key).toString().equals(key))
        throw new Problem(400, "INVALID_REQUEST_KEY");
    } catch (IllegalArgumentException e) {
      throw new Problem(400, "INVALID_REQUEST_KEY");
    }
    String fp = hash(List.of(who(), payload));
    var prior = db.query(CommandRecord.class, "from CommandRecord where requestKey=?1", key);
    if (!prior.isEmpty()) {
      check(prior.getFirst().fingerprint.equals(fp), "REQUEST_KEY_REUSED");
      return json.readerFor(Map.class)
          .with(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .readValue(prior.getFirst().responseJson);
    }
    var result = action.get();
    db.flush();
    var c = new CommandRecord();
    c.requestKey = key;
    c.fingerprint = fp;
    c.responseJson = encode(result);
    db.save(c);
    return result;
  }

  private void event(String type, Long id, String action, String note, Object snapshot, Long dept) {
    var e = new BusinessEvent();
    e.objectType = type;
    e.objectId = id;
    e.actorId = who();
    e.action = action;
    e.note = note;
    e.snapshot = encode(snapshot);
    e.createdAt = now();
    db.save(e);
    access.audit(type + "_" + action, id, dept);
  }

  private List<BusinessEvent> events(String type, Long id) {
    return db.query(
        BusinessEvent.class,
        "from BusinessEvent where objectType=?1 and objectId=?2 order by id",
        type,
        id);
  }

  /** 田块身份不变，名称和启停版本化，不删除历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveSite(Long id, Input v) {
    lock();
    access.require("site.write");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var prior = id == null ? null : site(id);
    access.department(v.departmentId);
    return write(
        v.requestKey,
        List.of("site", id == null ? 0 : id, v),
        () -> {
          var s = prior == null ? new FieldSite() : prior;
          if (prior == null) {
            limit(FieldSite.class, cap());
            s.reference = text(v.reference, 60);
            s.departmentId = db.get(Department.class, v.departmentId).id;
            s.createdBy = who();
          } else {
            version(s.version, v.version);
            check(
                Objects.equals(s.reference, v.reference)
                    && Objects.equals(s.departmentId, v.departmentId),
                "IMMUTABLE_IDENTITY");
            s.version++;
          }
          s.name = text(v.name, 120);
          s.enabled = Boolean.TRUE.equals(v.enabled);
          if (prior == null) db.save(s);
          event("sites", s.id, "SAVE", "", s, s.departmentId);
          return s;
        });
  }

  /** 草稿试验必须指派独立复核人，田块归属和编号保持不变。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveTrial(Long id, Input v) {
    lock();
    access.require("trial.write");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var prior = id == null ? null : trial(id);
    if (prior != null) writer(prior);
    var s = site(v.siteId);
    return write(
        v.requestKey,
        List.of("trial", id == null ? 0 : id, v),
        () -> {
          check(s.enabled, "DISABLED_RESOURCE");
          eligible(v.reviewerId, "trial.review");
          eligible(v.reviewerId, "observation.review");
          check(!Objects.equals(v.reviewerId, who()), "INDEPENDENT_REVIEW_REQUIRED");
          var t = prior == null ? new TrialPlan() : prior;
          if (prior == null) {
            limit(TrialPlan.class, cap());
            t.reference = text(v.reference, 60);
            t.siteId = s.id;
            t.departmentId = s.departmentId;
            t.createdBy = who();
            t.createdAt = now();
            t.status = "DRAFT";
          } else {
            version(t.version, v.version);
            check(EDITABLE.contains(t.status), "INVALID_STATE");
            check(
                Objects.equals(t.reference, v.reference) && Objects.equals(t.siteId, s.id),
                "IMMUTABLE_IDENTITY");
            t.version++;
          }
          t.siteName = s.name;
          t.name = text(v.name, 120);
          t.reviewerId = v.reviewerId;
          t.cropType = text(v.cropType, 60);
          check(
              !db.query(
                      DictionaryEntry.class,
                      "from DictionaryEntry where type='crop' and code=?1",
                      t.cropType)
                  .isEmpty(),
              "INVALID_CROP_TYPE");
          t.cropName = text(v.cropName, 120);
          t.objective = text(v.objective, 1000);
          if (v.blockCount == null || v.blockCount < 4 || v.blockCount > 16)
            throw new Problem(400, "INVALID_BLOCK_COUNT");
          t.blockCount = v.blockCount;
          t.plotArea = TrialValues.decimal(v.plotArea);
          check(t.plotArea.signum() > 0, "INVALID_AREA");
          if (prior == null) db.save(t);
          editMark(t);
          event("trials", t.id, "SAVE", "", t, t.departmentId);
          return t;
        });
  }

  /** 处理与观测指标只在方案草稿内可改；排除条目保留身份。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveDefinition(String type, Long id, Input v) {
    lock();
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var t = trial(v.trialId);
    writer(t);
    if (id != null) {
      Long owner =
          type.equals("treatments")
              ? db.get(Treatment.class, id).trialId
              : db.get(MeasureDefinition.class, id).trialId;
      check(Objects.equals(owner, t.id), "IMMUTABLE_IDENTITY");
    }
    return write(
        v.requestKey,
        List.of(type, id == null ? 0 : id, v),
        () -> {
          check(EDITABLE.contains(t.status), "INVALID_STATE");
          Object result;
          if (type.equals("treatments")) {
            var r = id == null ? new Treatment() : db.get(Treatment.class, id);
            if (id == null) {
              check(treatments(t.id).size() < 12, "DEFINITION_LIMIT");
              r.trialId = t.id;
            } else {
              version(r.version, v.version);
              r.version++;
            }
            r.code = text(v.code, 60);
            r.name = text(v.name, 120);
            r.description = text(v.description, 500);
            r.control = Boolean.TRUE.equals(v.control);
            r.enabled = Boolean.TRUE.equals(v.enabled);
            result = id == null ? db.save(r) : r;
          } else {
            var r = id == null ? new MeasureDefinition() : db.get(MeasureDefinition.class, id);
            if (id == null) {
              check(measures(t.id).size() < 20, "DEFINITION_LIMIT");
              r.trialId = t.id;
            } else {
              version(r.version, v.version);
              r.version++;
            }
            r.code = text(v.code, 60);
            r.name = text(v.name, 120);
            r.unit = text(v.unit, 30);
            r.minimum = TrialValues.decimal(v.minimum);
            r.maximum = TrialValues.decimal(v.maximum);
            check(r.minimum.compareTo(r.maximum) <= 0, "INVALID_MEASURE_RANGE");
            r.required = Boolean.TRUE.equals(v.required);
            r.enabled = Boolean.TRUE.equals(v.enabled);
            result = id == null ? db.save(r) : r;
          }
          t.version++;
          editMark(t);
          event(
              "trials", t.id, "SAVE_" + type.toUpperCase(Locale.ROOT), "", result, t.departmentId);
          return result;
        });
  }

  private Object definitions(TrialPlan t) {
    return Map.of(
        "plan",
        Map.of(
            "reference",
            t.reference,
            "name",
            t.name,
            "siteId",
            t.siteId,
            "siteName",
            t.siteName,
            "crop",
            t.cropName,
            "cropType",
            t.cropType,
            "objective",
            t.objective,
            "blocks",
            t.blockCount,
            "plotArea",
            t.plotArea,
            "reviewerId",
            t.reviewerId),
        "treatments",
        treatments(t.id).stream().filter(r -> r.enabled).toList(),
        "measures",
        measures(t.id).stream().filter(r -> r.enabled).toList());
  }

  private void completePlan(TrialPlan t) {
    var ts = treatments(t.id).stream().filter(r -> r.enabled).toList();
    var ms = measures(t.id).stream().filter(r -> r.enabled).toList();
    check(
        ts.size() >= 2 && ts.size() <= 12 && ts.stream().filter(r -> r.control).count() == 1,
        "INCOMPLETE_TREATMENTS");
    check(!ms.isEmpty() && ms.stream().anyMatch(r -> r.required), "INCOMPLETE_MEASURES");
    eligible(t.reviewerId, "trial.review");
    eligible(t.reviewerId, "observation.review");
    check(independent(t), "INDEPENDENT_REVIEW_REQUIRED");
  }

  private Observation currentAccepted(List<Observation> rows, Long plot, Long measure) {
    return rows.stream()
        .filter(
            o ->
                Objects.equals(o.plotId, plot)
                    && Objects.equals(o.measureId, measure)
                    && o.status.equals("ACCEPTED"))
        .max(Comparator.comparingInt(o -> o.revision))
        .orElse(null);
  }

  private void readyToEnd(TrialPlan t) {
    var ps = plots(t.id);
    var os = observations(t.id);
    check(os.stream().noneMatch(o -> PENDING.contains(o.status)), "PENDING_OBSERVATIONS");
    check(ps.stream().noneMatch(p -> p.exclusionStatus.equals("PENDING")), "PENDING_EXCLUSIONS");
    check(ps.stream().anyMatch(p -> !p.excluded), "ALL_PLOTS_EXCLUDED");
    for (var p : ps) {
      if (p.excluded) continue;
      check(p.plantingDate != null, "UNPLANTED_PLOT");
      for (var m : measures(t.id))
        if (m.enabled && m.required)
          check(currentAccepted(os, p.id, m.id) != null, "MISSING_REQUIRED_OBSERVATION");
    }
  }

  /** 冻结方案后只随机分配一次，结束和中止由独立指定复核人关单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object trialCommand(Long id, String action, Command v) {
    lock();
    var t = trial(id);
    if (Set.of("approve", "reject", "close", "reopen").contains(action))
      reviewer(t, "trial.review");
    else writer(t);
    return write(
        v.requestKey,
        List.of("trial-command", id, action, v),
        () -> {
          version(t.version, v.version);
          String note = text(v.note, 1000);
          switch (action) {
            case "submit" -> {
              check(EDITABLE.contains(t.status), "INVALID_STATE");
              var ts = treatments(id).stream().filter(r -> r.enabled).toList();
              check(
                  ts.size() >= 2 && ts.stream().filter(r -> r.control).count() == 1,
                  "INCOMPLETE_TREATMENTS");
              check(
                  measures(id).stream().anyMatch(r -> r.enabled && r.required),
                  "INCOMPLETE_MEASURES");
              t.status = "SUBMITTED";
            }
            case "withdraw" -> {
              check(t.status.equals("SUBMITTED"), "INVALID_STATE");
              t.status = "DRAFT";
            }
            case "reject" -> {
              check(t.status.equals("SUBMITTED"), "INVALID_STATE");
              t.status = "RETURNED";
            }
            case "approve" -> {
              check(t.status.equals("SUBMITTED"), "INVALID_STATE");
              completePlan(t);
              check(db.get(FieldSite.class, t.siteId).enabled, "DISABLED_RESOURCE");
              t.siteName = db.get(FieldSite.class, t.siteId).name;
              t.planHash = hash(definitions(t));
              t.approvedAt = now();
              t.status = "APPROVED";
            }
            case "allocate" -> {
              check(t.status.equals("APPROVED"), "INVALID_STATE");
              check(plots(id).isEmpty(), "LAYOUT_ALREADY_EXISTS");
              var ts = treatments(id).stream().filter(r -> r.enabled).toList();
              t.layoutSeed = BlockRandomizer.seed();
              for (int block = 1; block <= t.blockCount; block++) {
                var order = BlockRandomizer.order(t.layoutSeed, block, ts.size());
                for (int pos = 1; pos <= order.size(); pos++) {
                  var r = ts.get(order.get(pos - 1));
                  var p = new TrialPlot();
                  p.trialId = id;
                  p.blockIndex = block;
                  p.position = pos;
                  p.label = t.reference + String.format(Locale.ROOT, "-B%02d-P%02d", block, pos);
                  p.treatmentId = r.id;
                  p.treatmentCode = r.code;
                  p.treatmentName = r.name;
                  p.treatmentDescription = r.description;
                  p.control = r.control;
                  db.save(p);
                }
              }
              t.layoutHash =
                  hash(
                      plots(id).stream()
                          .map(
                              p ->
                                  Map.of(
                                      "block",
                                      p.blockIndex,
                                      "position",
                                      p.position,
                                      "treatment",
                                      p.treatmentCode))
                          .toList());
              t.status = "ALLOCATED";
            }
            case "start" -> {
              check(t.status.equals("ALLOCATED"), "INVALID_STATE");
              eligible(t.reviewerId, "trial.review");
              eligible(t.reviewerId, "observation.review");
              for (var p : plots(id)) {
                check(p.observerId != null && p.receivedAt != null, "CREW_NOT_READY");
                eligible(p.observerId, "plot.observe");
              }
              t.startDate = TrialValues.date(v.date, null, clock);
              t.startedAt = now();
              t.status = "ACTIVE";
            }
            case "end" -> {
              check(t.status.equals("ACTIVE"), "INVALID_STATE");
              readyToEnd(t);
              t.status = "REVIEW";
              t.outcome = "FINISHED";
              t.endedAt = now();
            }
            case "abort" -> {
              check(t.status.equals("ACTIVE"), "INVALID_STATE");
              check(
                  observations(id).stream().noneMatch(o -> PENDING.contains(o.status)),
                  "PENDING_OBSERVATIONS");
              check(
                  plots(id).stream().noneMatch(p -> p.exclusionStatus.equals("PENDING")),
                  "PENDING_EXCLUSIONS");
              t.status = "ABORTED";
              t.outcome = "ABORTED";
              t.endedAt = now();
            }
            case "reopen" -> {
              check(t.status.equals("REVIEW"), "INVALID_STATE");
              t.status = "ACTIVE";
              t.outcome = null;
              t.endedAt = null;
            }
            case "close" -> {
              check(Set.of("REVIEW", "ABORTED").contains(t.status), "INVALID_STATE");
              check(independent(t), "INDEPENDENT_REVIEW_REQUIRED");
              if (t.status.equals("REVIEW")) readyToEnd(t);
              t.dataHash = hash(dataset(t));
              t.closedAt = now();
              t.status = "CLOSED";
            }
            case "cancel" -> {
              check(
                  Set.of("DRAFT", "RETURNED", "SUBMITTED", "APPROVED", "ALLOCATED")
                      .contains(t.status),
                  "INVALID_STATE");
              t.status = "CANCELLED";
              t.outcome = "CANCELLED";
            }
            default -> throw new Problem(400, "INVALID_ACTION");
          }
          t.version++;
          event("trials", id, action, note, t, t.departmentId);
          return t;
        });
  }

  /** 指派清除收悉，启用后固定观测员；排除须独立核实且不删除数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object plotCommand(Long id, String action, Command v) {
    lock();
    var p = plot(id);
    var t = trial(p.trialId);
    if (action.equals("assign")) {
      access.require("plot.assign");
      if (!scope(t.departmentId, t.createdBy)) throw new Problem(403, "OUT_OF_SCOPE");
    } else if (Set.of("approve-exclusion", "reject-exclusion").contains(action))
      reviewer(t, "observation.review");
    else observer(p);
    return write(
        v.requestKey,
        List.of("plot-command", id, action, v),
        () -> {
          version(p.version, v.version);
          String note = text(v.note, 1000);
          switch (action) {
            case "assign" -> {
              check(t.status.equals("ALLOCATED"), "INVALID_STATE");
              eligible(v.observerId, "plot.observe");
              check(!Objects.equals(v.observerId, t.reviewerId), "INDEPENDENT_REVIEW_REQUIRED");
              p.observerId = v.observerId;
              p.receivedAt = null;
            }
            case "receive" -> {
              check(t.status.equals("ALLOCATED") && p.receivedAt == null, "INVALID_STATE");
              p.receivedAt = now();
            }
            case "plant" -> {
              check(
                  t.status.equals("ACTIVE") && !p.excluded && p.plantingDate == null,
                  "INVALID_STATE");
              p.plantingDate = TrialValues.date(v.date, t.startDate, clock);
            }
            case "request-exclusion" -> {
              check(
                  t.status.equals("ACTIVE") && !p.excluded && !p.exclusionStatus.equals("PENDING"),
                  "INVALID_STATE");
              p.exclusionStatus = "PENDING";
              p.exclusionReason = note;
              p.exclusionRequestedBy = who();
              p.exclusionReviewedBy = null;
            }
            case "approve-exclusion" -> {
              check(
                  t.status.equals("ACTIVE") && p.exclusionStatus.equals("PENDING"),
                  "INVALID_STATE");
              check(!Objects.equals(who(), p.exclusionRequestedBy), "INDEPENDENT_REVIEW_REQUIRED");
              p.excluded = true;
              p.exclusionStatus = "ACCEPTED";
              p.exclusionReviewedBy = who();
            }
            case "reject-exclusion" -> {
              check(
                  t.status.equals("ACTIVE") && p.exclusionStatus.equals("PENDING"),
                  "INVALID_STATE");
              check(!Objects.equals(who(), p.exclusionRequestedBy), "INDEPENDENT_REVIEW_REQUIRED");
              p.exclusionStatus = "REJECTED";
              p.exclusionReviewedBy = who();
            }
            default -> throw new Problem(400, "INVALID_ACTION");
          }
          p.version++;
          t.version++;
          event("plots", id, action, note, p, t.departmentId);
          return p;
        });
  }

  /** 观测修订只追加，旧核实数据在新版被核实前仍是当前数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveObservation(Long id, ObservationInput v) {
    lock();
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var prior = id == null ? null : db.get(Observation.class, id);
    var p = plot(v.plotId);
    var t = trial(p.trialId);
    observer(p);
    var m = db.get(MeasureDefinition.class, v.measureId);
    check(Objects.equals(m.trialId, t.id) && m.enabled, "INVALID_MEASURE");
    if (prior != null) {
      check(
          Objects.equals(prior.plotId, p.id) && Objects.equals(prior.measureId, m.id),
          "IMMUTABLE_IDENTITY");
      if (!Objects.equals(prior.createdBy, who()))
        throw new Problem(403, "OBSERVATION_AUTHOR_REQUIRED");
    }
    return write(
        v.requestKey,
        List.of("observation", id == null ? 0 : id, v),
        () -> {
          check(
              t.status.equals("ACTIVE") && !p.excluded && p.plantingDate != null, "INVALID_STATE");
          var cell =
              db.query(
                  Observation.class,
                  "from Observation where plotId=?1 and measureId=?2 order by revision desc",
                  p.id,
                  m.id);
          var accepted = currentAccepted(cell, p.id, m.id);
          var o = prior == null ? new Observation() : prior;
          if (prior == null) {
            limit(Observation.class, 10000);
            check(cell.stream().noneMatch(x -> PENDING.contains(x.status)), "OPEN_OBSERVATION");
            check(
                Objects.equals(v.supersedesId, accepted == null ? null : accepted.id),
                "CORRECTION_BASE_REQUIRED");
            o.plotId = p.id;
            o.measureId = m.id;
            o.revision = cell.isEmpty() ? 1 : cell.getFirst().revision + 1;
            o.supersedesId = v.supersedesId;
            o.createdBy = who();
            o.recordedAt = now();
            o.status = "DRAFT";
          } else {
            version(o.version, v.version);
            check(EDITABLE.contains(o.status), "INVALID_STATE");
            check(Objects.equals(o.supersedesId, v.supersedesId), "IMMUTABLE_IDENTITY");
            o.version++;
          }
          o.note = text(v.note, 1000);
          o.observedDate = TrialValues.date(v.observedDate, p.plantingDate, clock);
          String missing = v.missingReason == null ? "" : v.missingReason.trim();
          if (v.value == null) {
            o.missingReason = text(missing, 1000);
            o.value = null;
          } else {
            check(missing.isEmpty(), "VALUE_AND_MISSING_REASON");
            o.value = TrialValues.decimal(v.value);
            check(
                o.value.compareTo(m.minimum) >= 0 && o.value.compareTo(m.maximum) <= 0,
                "OUTSIDE_MEASURE_RANGE");
            o.missingReason = "";
          }
          if (prior == null) db.save(o);
          t.version++;
          event("observations", o.id, "SAVE", o.note, o, t.departmentId);
          return o;
        });
  }

  /** 独立核实不允许作者自审；取消或退回也保留原始证据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object observationCommand(Long id, String action, Command v) {
    lock();
    var o = db.get(Observation.class, id);
    var p = plot(o.plotId);
    var t = trial(p.trialId);
    if (Set.of("accept", "reject").contains(action)) {
      reviewer(t, "observation.review");
      if (Objects.equals(who(), o.createdBy)) throw new Problem(403, "INDEPENDENT_REVIEW_REQUIRED");
    } else {
      observer(p);
      if (!Objects.equals(o.createdBy, who()))
        throw new Problem(403, "OBSERVATION_AUTHOR_REQUIRED");
    }
    return write(
        v.requestKey,
        List.of("observation-command", id, action, v),
        () -> {
          version(o.version, v.version);
          check(t.status.equals("ACTIVE"), "INVALID_STATE");
          String note = text(v.note, 1000);
          switch (action) {
            case "submit" -> {
              check(EDITABLE.contains(o.status) && !p.excluded, "INVALID_STATE");
              o.status = "SUBMITTED";
            }
            case "accept" -> {
              check(o.status.equals("SUBMITTED") && !p.excluded, "INVALID_STATE");
              var cell =
                  db.query(
                      Observation.class,
                      "from Observation where plotId=?1 and measureId=?2",
                      p.id,
                      o.measureId);
              var old = currentAccepted(cell, p.id, o.measureId);
              check(
                  Objects.equals(o.supersedesId, old == null ? null : old.id),
                  "CORRECTION_BASE_REQUIRED");
              o.status = "ACCEPTED";
              o.reviewedBy = who();
              o.acceptedAt = now();
            }
            case "reject" -> {
              check(o.status.equals("SUBMITTED"), "INVALID_STATE");
              o.status = "RETURNED";
            }
            case "withdraw" -> {
              check(o.status.equals("SUBMITTED"), "INVALID_STATE");
              o.status = "DRAFT";
            }
            case "cancel" -> {
              check(PENDING.contains(o.status), "INVALID_STATE");
              o.status = "CANCELLED";
            }
            default -> throw new Problem(400, "INVALID_ACTION");
          }
          o.version++;
          t.version++;
          event("observations", id, action, note, o, t.departmentId);
          return o;
        });
  }

  private Object dataset(TrialPlan t) {
    var os = observations(t.id);
    var ps = plots(t.id);
    var data = new ArrayList<Object>();
    for (var p : ps)
      for (var m : measures(t.id)) {
        if (!m.enabled) continue;
        var o = currentAccepted(os, p.id, m.id);
        var row = new LinkedHashMap<String, Object>();
        row.put("plot", p.label);
        row.put("block", p.blockIndex);
        row.put("position", p.position);
        row.put("treatment", p.treatmentCode);
        row.put("excluded", p.excluded);
        row.put("measure", m.code);
        row.put("unit", m.unit);
        row.put("observation", o);
        data.add(row);
      }
    return Map.of(
        "planHash",
        t.planHash,
        "layoutHash",
        t.layoutHash,
        "algorithm",
        BlockRandomizer.ALGORITHM,
        "outcome",
        t.outcome == null ? "UNFINISHED" : t.outcome,
        "rows",
        data);
  }

  /** 详情与证据按同一范围过滤，纯观测岗位即使ALL也只看本人小区。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(String type, Long id) {
    access.require(type.equals("sites") ? "site.read" : "trial.read");
    if (type.equals("sites")) return Map.of("record", site(id), "events", events(type, id));
    var t = trial(id);
    var ps =
        plots(id).stream()
            .filter(p -> !observerOnly() || Objects.equals(p.observerId, who()))
            .toList();
    var ids = new HashSet<>(ps.stream().map(p -> p.id).toList());
    var os = observations(id).stream().filter(o -> ids.contains(o.plotId)).toList();
    var ev = new ArrayList<BusinessEvent>(events("trials", id));
    for (var p : ps) ev.addAll(events("plots", p.id));
    for (var o : os) ev.addAll(events("observations", o.id));
    ev.sort(Comparator.comparing(e -> e.id));
    return Map.of(
        "record",
        t,
        "treatments",
        treatments(id),
        "measures",
        measures(id),
        "plots",
        ps,
        "observations",
        os,
        "events",
        ev,
        "editors",
        db.query(TrialEditor.class, "from TrialEditor where trialId=?1", id),
        "algorithm",
        BlockRandomizer.ALGORITHM,
        "summary",
        summary(t, ps, os));
  }

  private Object summary(TrialPlan t, List<TrialPlot> ps, List<Observation> os) {
    var rows = new ArrayList<Object>();
    for (var treatment : treatments(t.id)) {
      if (!treatment.enabled) continue;
      for (var m : measures(t.id)) {
        if (!m.enabled) continue;
        var values = new ArrayList<BigDecimal>();
        int missing = 0, excluded = 0, unrecorded = 0;
        for (var p : ps) {
          if (!Objects.equals(p.treatmentId, treatment.id)) continue;
          if (p.excluded) {
            excluded++;
            continue;
          }
          var o = currentAccepted(os, p.id, m.id);
          if (o == null) unrecorded++;
          else if (o.value == null) missing++;
          else values.add(o.value);
        }
        var row = new LinkedHashMap<String, Object>();
        row.put("treatment", treatment.code);
        row.put("measure", m.code);
        row.put("unit", m.unit);
        row.put("n", values.size());
        row.put("missing", missing);
        row.put("excluded", excluded);
        row.put("unrecorded", unrecorded);
        row.put(
            "mean",
            values.isEmpty()
                ? null
                : values.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP));
        row.put("minimum", values.stream().min(BigDecimal::compareTo).orElse(null));
        row.put("maximum", values.stream().max(BigDecimal::compareTo).orElse(null));
        rows.add(row);
      }
    }
    return rows;
  }

  /** 授权列表先过滤范围再有界分页，不泄露其他部门记录数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(String type, String search, String status, int page, int size, String sort) {
    if (search.length() > 120
        || page < 0
        || page > 10000
        || size < 1
        || size > 100
        || !Set.of("oldest", "newest").contains(sort)) throw new Problem(400, "INVALID_INPUT");
    String q = search.toLowerCase(Locale.ROOT);
    access.require(type.equals("sites") ? "site.read" : "trial.read");
    List<?> data =
        type.equals("sites")
            ? db.all(FieldSite.class).stream()
                .filter(this::siteVisible)
                .filter(s -> (s.reference + " " + s.name).toLowerCase(Locale.ROOT).contains(q))
                .toList()
            : db.all(TrialPlan.class).stream()
                .filter(this::visible)
                .filter(
                    t ->
                        (t.reference + " " + t.name + " " + t.cropName)
                                .toLowerCase(Locale.ROOT)
                                .contains(q)
                            && (status.isBlank() || t.status.equals(status)))
                .toList();
    var rows = new ArrayList<>(data);
    if (sort.equals("newest")) Collections.reverse(rows);
    long from = (long) page * size;
    return Map.of(
        "content",
        rows.subList((int) Math.min(from, rows.size()), (int) Math.min(from + size, rows.size())),
        "total",
        rows.size());
  }

  /** 表单只返回安全姓名与授权业务；不公开其他观测员名单或密码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.current();
    var result = new LinkedHashMap<String, Object>();
    result.put(
        "companyName",
        db.query(SystemSetting.class, "from SystemSetting where code=?1", "companyName")
            .getFirst()
            .value);
    result.put(
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList());
    result.put("crops", db.query(DictionaryEntry.class, "from DictionaryEntry where type='crop'"));
    boolean designer =
        access.role().permissions.contains("trial.write")
            || access.role().permissions.contains("plot.assign");
    if (!observerOnly())
      result.put("sites", db.all(FieldSite.class).stream().filter(this::siteVisible).toList());
    if (designer)
      result.put(
          "people",
          db.all(Account.class).stream()
              .filter(a -> a.enabled)
              .map(
                  a ->
                      Map.of(
                          "id",
                          a.id,
                          "name",
                          a.displayName,
                          "permissions",
                          db.get(AccessRole.class, a.roleId).permissions))
              .toList());
    else {
      var ids = new HashSet<Long>();
      ids.add(who());
      for (var t : db.all(TrialPlan.class)) {
        if (!visible(t)) continue;
        ids.add(t.reviewerId);
        for (var p : plots(t.id))
          if (p.observerId != null && (!observerOnly() || Objects.equals(p.observerId, who())))
            ids.add(p.observerId);
      }
      result.put(
          "people",
          db.all(Account.class).stream()
              .filter(a -> ids.contains(a.id))
              .map(a -> Map.of("id", a.id, "name", a.displayName))
              .toList());
    }
    return result;
  }

  /** 指标和完成量来自当前可见数据，空库保持零。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var ts =
        access.role().permissions.contains("trial.read")
            ? db.all(TrialPlan.class).stream().filter(this::visible).toList()
            : List.<TrialPlan>of();
    Map<String, Long> states = new TreeMap<>();
    long plotCount = 0, accepted = 0, missing = 0;
    for (var t : ts) {
      states.merge(t.status, 1L, Long::sum);
      var ps =
          plots(t.id).stream()
              .filter(p -> !observerOnly() || Objects.equals(p.observerId, who()))
              .toList();
      plotCount += ps.size();
      var os = observations(t.id);
      for (var p : ps)
        for (var m : measures(t.id)) {
          if (!m.enabled || p.excluded) continue;
          var o = currentAccepted(os, p.id, m.id);
          if (o != null) {
            accepted++;
            if (o.value == null) missing++;
          }
        }
    }
    return Map.of(
        "trials",
        ts.size(),
        "plots",
        plotCount,
        "accepted",
        accepted,
        "missing",
        missing,
        "states",
        states);
  }

  /** 输出所有授权修订，独立CSV字段保留缺测和当前核实标记。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String csv(Long id) {
    access.require("trial.read");
    var t = trial(id);
    var ps =
        plots(id).stream()
            .filter(p -> !observerOnly() || Objects.equals(p.observerId, who()))
            .toList();
    var ids = new HashSet<>(ps.stream().map(p -> p.id).toList());
    var os = observations(id).stream().filter(o -> ids.contains(o.plotId)).toList();
    var out =
        new StringBuilder(
            "\uFEFFtrial,plot,block,position,treatment,excluded,measure,unit,revision,status,currentAccepted,value,missingReason,observedDate,recordedAt,note\r\n");
    for (var o : os) {
      var p = db.get(TrialPlot.class, o.plotId);
      var m = db.get(MeasureDefinition.class, o.measureId);
      var current = currentAccepted(os, p.id, m.id);
      Object[] values = {
        t.reference,
        p.label,
        p.blockIndex,
        p.position,
        p.treatmentCode,
        p.excluded,
        m.code,
        m.unit,
        o.revision,
        o.status,
        current != null && Objects.equals(current.id, o.id),
        o.value,
        o.missingReason,
        o.observedDate,
        o.recordedAt,
        o.note
      };
      for (int i = 0; i < values.length; i++) {
        if (i > 0) out.append(',');
        out.append(csvCell(values[i]));
      }
      out.append("\r\n");
    }
    return out.toString();
  }

  private String csvCell(Object value) {
    if (value == null) return "";
    if (value instanceof Number) return value.toString();
    String s = value.toString();
    String start = s.stripLeading();
    if (!start.isEmpty() && "=+-@".indexOf(start.charAt(0)) >= 0) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }
}
