// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.plottrial;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库初始化试验岗位与目录，不生成虚构试验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${plottrial.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
  }

  /** 首次注册真实权限、岗位、菜单及随机密码管理员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var d = new Department();
    d.name = "总部";
    db.save(d);
    String[][] ps = {
      {"site.read", "查看田块"},
      {"site.write", "维护田块"},
      {"trial.read", "查看授权试验"},
      {"trial.write", "设计和推进试验"},
      {"trial.review", "指定独立试验复核"},
      {"plot.assign", "指派小区观测员"},
      {"plot.observe", "本人观测与小区异常登记"},
      {"observation.review", "指定独立观测核实"},
      {"dashboard", "试验统计"},
      {"export", "授权数据导出"},
      {"audit", "操作审计"},
      {"admin", "系统管理"}
    };
    var all = new HashSet<String>();
    for (var row : ps) {
      var p = new Permission();
      p.code = row[0];
      p.name = row[1];
      db.save(p);
      all.add(p.code);
    }
    var r = role("管理员", "ALL", all);
    role(
        "试验设计",
        "DEPARTMENT",
        Set.of(
            "site.read",
            "site.write",
            "trial.read",
            "trial.write",
            "plot.assign",
            "dashboard",
            "export",
            "audit"));
    role(
        "独立复核",
        "DEPARTMENT",
        Set.of(
            "site.read",
            "trial.read",
            "trial.review",
            "observation.review",
            "dashboard",
            "export",
            "audit"));
    role("田间观测", "SELF", Set.of("trial.read", "plot.observe", "dashboard", "export"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.roleId = r.id;
    a.departmentId = d.id;
    a.enabled = true;
    a.passwordHash = encoder.encode(password);
    db.save(a);
    String[][] ms = {
      {"trials", "田间试验", "Field trials", "trial.read"},
      {"sites", "田块目录", "Field sites", "site.read"},
      {"dashboard", "试验统计", "Statistics", "dashboard"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门管理", "Departments", "admin"},
      {"menus", "导航管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "作物类型", "Crop types", "admin"},
      {"settings", "系统参数", "Settings", "admin"}
    };
    for (int i = 0; i < ms.length; i++) {
      var m = new NavMenu();
      m.code = ms[i][0];
      m.name = ms[i][1];
      m.nameEn = ms[i][2];
      m.permissionCode = ms[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    Map.of("timezone", "Asia/Shanghai", "companyName", "PlotTrial 田间试验", "maxRecords", "1000")
        .forEach(
            (k, v) -> {
              var s = new SystemSetting();
              s.code = k;
              s.value = v;
              db.save(s);
            });
    String[][] ks = {
      {"CEREAL", "谷物", "Cereal"},
      {"LEGUME", "豆类", "Legume"},
      {"VEGETABLE", "蔬菜", "Vegetable"},
      {"OTHER", "其他", "Other"}
    };
    for (var row : ks) {
      var v = new DictionaryEntry();
      v.type = "crop";
      v.code = row[0];
      v.name = row[1];
      v.nameEn = row[2];
      db.save(v);
    }
  }

  private AccessRole role(String name, String scope, Set<String> ps) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(ps);
    return db.save(r);
  }
}
