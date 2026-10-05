// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.plottrial;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 田间试验持久化记录，操作由试验服务校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "trial_plan")
public class TrialPlan {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reference", columnDefinition = "varchar(60)", nullable = false)
  public String reference;

  @Column(name = "name", columnDefinition = "varchar(120)", nullable = false)
  public String name;

  @Column(name = "site_id", columnDefinition = "bigint", nullable = false)
  public Long siteId;

  @Column(name = "site_name", columnDefinition = "varchar(120)", nullable = false)
  public String siteName;

  @Column(name = "department_id", columnDefinition = "bigint", nullable = false)
  public Long departmentId;

  @Column(name = "created_by", columnDefinition = "bigint", nullable = false)
  public Long createdBy;

  @Column(name = "reviewer_id", columnDefinition = "bigint", nullable = false)
  public Long reviewerId;

  @Column(name = "crop_type", columnDefinition = "varchar(60)", nullable = false)
  public String cropType;

  @Column(name = "crop_name", columnDefinition = "varchar(120)", nullable = false)
  public String cropName;

  @Column(name = "objective", columnDefinition = "varchar(1000)", nullable = false)
  public String objective;

  @Column(name = "block_count", columnDefinition = "int", nullable = false)
  public int blockCount;

  @Column(name = "plot_area", columnDefinition = "decimal(16,4)", nullable = false)
  public BigDecimal plotArea;

  @Column(name = "status", columnDefinition = "varchar(30)", nullable = false)
  public String status;

  @Column(name = "outcome", columnDefinition = "varchar(30)")
  public String outcome;

  @Column(name = "start_date", columnDefinition = "date", nullable = true)
  public LocalDate startDate;

  @Column(name = "created_at", columnDefinition = "timestamp(6)", nullable = false)
  public Instant createdAt;

  @Column(name = "approved_at", columnDefinition = "timestamp(6)", nullable = true)
  public Instant approvedAt;

  @Column(name = "started_at", columnDefinition = "timestamp(6)", nullable = true)
  public Instant startedAt;

  @Column(name = "ended_at", columnDefinition = "timestamp(6)", nullable = true)
  public Instant endedAt;

  @Column(name = "closed_at", columnDefinition = "timestamp(6)", nullable = true)
  public Instant closedAt;

  @Column(name = "plan_hash", columnDefinition = "varchar(64)", nullable = true)
  public String planHash;

  @Column(name = "layout_seed", columnDefinition = "varchar(64)", nullable = true)
  public String layoutSeed;

  @Column(name = "layout_hash", columnDefinition = "varchar(64)", nullable = true)
  public String layoutHash;

  @Column(name = "data_hash", columnDefinition = "varchar(64)", nullable = true)
  public String dataHash;

  @Column(name = "version", columnDefinition = "bigint", nullable = false)
  public long version = 1;
}
