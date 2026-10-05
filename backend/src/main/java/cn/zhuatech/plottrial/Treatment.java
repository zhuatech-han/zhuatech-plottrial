// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.plottrial;

import jakarta.persistence.*;
import java.time.*;

/** 田间试验持久化记录，操作由试验服务校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "treatment")
public class Treatment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "trial_id", columnDefinition = "bigint", nullable = false)
  public Long trialId;

  @Column(name = "code", columnDefinition = "varchar(60)", nullable = false)
  public String code;

  @Column(name = "name", columnDefinition = "varchar(120)", nullable = false)
  public String name;

  @Column(name = "description", columnDefinition = "varchar(500)", nullable = false)
  public String description;

  @Column(name = "control", columnDefinition = "boolean", nullable = false)
  public boolean control = false;

  @Column(name = "enabled", columnDefinition = "boolean", nullable = false)
  public boolean enabled = true;

  @Column(name = "version", columnDefinition = "bigint", nullable = false)
  public long version = 1;
}
