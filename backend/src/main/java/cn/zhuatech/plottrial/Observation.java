// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.plottrial;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 田间试验持久化记录，操作由试验服务校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "observation")
public class Observation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "plot_id", columnDefinition = "bigint", nullable = false)
  public Long plotId;

  @Column(name = "measure_id", columnDefinition = "bigint", nullable = false)
  public Long measureId;

  @Column(name = "revision", columnDefinition = "int", nullable = false)
  public int revision;

  @Column(name = "value", columnDefinition = "decimal(16,4)", nullable = true)
  public BigDecimal value;

  @Column(name = "missing_reason", columnDefinition = "varchar(1000)", nullable = false)
  public String missingReason = "";

  @Column(name = "note", columnDefinition = "varchar(1000)", nullable = false)
  public String note;

  @Column(name = "observed_date", columnDefinition = "date", nullable = false)
  public LocalDate observedDate;

  @Column(name = "status", columnDefinition = "varchar(30)", nullable = false)
  public String status;

  @Column(name = "created_by", columnDefinition = "bigint", nullable = false)
  public Long createdBy;

  @Column(name = "recorded_at", columnDefinition = "timestamp(6)", nullable = false)
  public Instant recordedAt;

  @Column(name = "reviewed_by", columnDefinition = "bigint", nullable = true)
  public Long reviewedBy;

  @Column(name = "accepted_at", columnDefinition = "timestamp(6)", nullable = true)
  public Instant acceptedAt;

  @Column(name = "supersedes_id", columnDefinition = "bigint", nullable = true)
  public Long supersedesId;

  @Column(name = "version", columnDefinition = "bigint", nullable = false)
  public long version = 1;
}
