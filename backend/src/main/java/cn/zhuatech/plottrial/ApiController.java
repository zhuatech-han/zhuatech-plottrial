// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.plottrial;

import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 试验和身份入口使用真实权限、范围和岗位校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final TrialService trial;
  final AdminService admin;
  final AccessService access;
  final Store db;

  public ApiController(TrialService trial, AdminService admin, AccessService access, Store db) {
    this.trial = trial;
    this.admin = admin;
    this.access = access;
    this.db = db;
  }

  /** 有限安全表单目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return trial.options();
  }

  /** 授权分页。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{type:sites|trials}")
  public Object list(
      @PathVariable String type,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return trial.list(type, search, status, page, size, sort);
  }

  /** 真实业务与范围内证据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{type:sites|trials}/{id}")
  public Object detail(@PathVariable String type, @PathVariable Long id) {
    return trial.detail(type, id);
  }

  /** 创建有限草稿资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/{type:sites|trials|treatments|measures}")
  public Object create(@PathVariable String type, @RequestBody TrialService.Input v) {
    return save(type, null, v);
  }

  /** 编辑草稿，冻结事实不能由客户端覆盖。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/{type:sites|trials|treatments|measures}/{id}")
  public Object edit(
      @PathVariable String type, @PathVariable Long id, @RequestBody TrialService.Input v) {
    return save(type, id, v);
  }

  private Object save(String type, Long id, TrialService.Input v) {
    return switch (type) {
      case "sites" -> trial.saveSite(id, v);
      case "trials" -> trial.saveTrial(id, v);
      default -> trial.saveDefinition(type, id, v);
    };
  }

  /** 新增原始观测或追加明确修订。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/observations")
  public Object observe(@RequestBody TrialService.ObservationInput v) {
    return trial.saveObservation(null, v);
  }

  /** 编辑尚未核实的本人观测。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/observations/{id}")
  public Object editObservation(
      @PathVariable Long id, @RequestBody TrialService.ObservationInput v) {
    return trial.saveObservation(id, v);
  }

  /** 人工审批、指派、种植、缺测、排除和关单命令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/{type:trials|plots|observations}/{id}/commands/{action}")
  public Object command(
      @PathVariable String type,
      @PathVariable Long id,
      @PathVariable String action,
      @RequestBody TrialService.Command v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    return switch (type) {
      case "trials" -> trial.trialCommand(id, action, v);
      case "plots" -> trial.plotCommand(id, action, v);
      default -> trial.observationCommand(id, action, v);
    };
  }

  /** 导出范围与页面一致，业务载荷不插入推广信息。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{type:sites|trials}/{id}/report.json")
  public ResponseEntity<Object> export(@PathVariable String type, @PathVariable Long id) {
    access.require("export");
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + type + "-" + id + ".json")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(trial.detail(type, id));
  }

  /** 观测CSV包含原始与修订状态，不把缺测写成零。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/trials/{id}/observations.csv")
  public ResponseEntity<String> csv(@PathVariable Long id) {
    access.require("export");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=trial-" + id + "-observations.csv")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(trial.csv(id));
  }

  /** 范围内的真实试验指标。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return trial.dashboard();
  }

  /** 审计目录限制到授权部门及本人范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    return db.all(AuditEvent.class).stream()
        .filter(
            e ->
                access.visible(e.departmentId)
                    && (!access.role().scope.equals("SELF")
                        || e.actor.equals(access.current().username)))
        .sorted(Comparator.comparing((AuditEvent e) -> e.id).reversed())
        .toList();
  }

  /** 管理资源真实读取。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object adminList(@PathVariable String type) {
    return admin.list(type);
  }

  /** 管理资源创建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object adminCreate(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 管理资源编辑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object adminEdit(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 未引用的管理资源删除，外键保护业务历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object adminDelete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
