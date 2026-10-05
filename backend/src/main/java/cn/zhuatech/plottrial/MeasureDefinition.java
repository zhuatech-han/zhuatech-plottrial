// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.plottrial;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 田间试验持久化记录，操作由试验服务校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "measure_definition")
public class MeasureDefinition {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "trial_id", columnDefinition = "bigint", nullable = false)
  public Long trialId;

  @Column(name = "code", columnDefinition = "varchar(60)", nullable = false)
  public String code;

  @Column(name = "name", columnDefinition = "varchar(120)", nullable = false)
  public String name;

  @Column(name = "unit", columnDefinition = "varchar(30)", nullable = false)
  public String unit;

  @Column(name = "minimum", columnDefinition = "decimal(16,4)", nullable = false)
  public BigDecimal minimum;

  @Column(name = "maximum", columnDefinition = "decimal(16,4)", nullable = false)
  public BigDecimal maximum;

  @Column(name = "required", columnDefinition = "boolean", nullable = false)
  public boolean required = true;

  @Column(name = "enabled", columnDefinition = "boolean", nullable = false)
  public boolean enabled = true;

  @Column(name = "version", columnDefinition = "bigint", nullable = false)
  public long version = 1;
}
