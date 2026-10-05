// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.plottrial;

import jakarta.persistence.*;
import java.time.*;

/** 田间试验持久化记录，操作由试验服务校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "trial_plot")
public class TrialPlot {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "trial_id", columnDefinition = "bigint", nullable = false)
  public Long trialId;

  @Column(name = "block_index", columnDefinition = "int", nullable = false)
  public int blockIndex;

  @Column(name = "position", columnDefinition = "int", nullable = false)
  public int position;

  @Column(name = "label", columnDefinition = "varchar(100)", nullable = false)
  public String label;

  @Column(name = "treatment_id", columnDefinition = "bigint", nullable = false)
  public Long treatmentId;

  @Column(name = "treatment_code", columnDefinition = "varchar(60)", nullable = false)
  public String treatmentCode;

  @Column(name = "treatment_name", columnDefinition = "varchar(120)", nullable = false)
  public String treatmentName;

  @Column(name = "treatment_description", columnDefinition = "varchar(500)", nullable = false)
  public String treatmentDescription;

  @Column(name = "control", columnDefinition = "boolean", nullable = false)
  public boolean control = false;

  @Column(name = "observer_id", columnDefinition = "bigint", nullable = true)
  public Long observerId;

  @Column(name = "received_at", columnDefinition = "timestamp(6)", nullable = true)
  public Instant receivedAt;

  @Column(name = "planting_date", columnDefinition = "date", nullable = true)
  public LocalDate plantingDate;

  @Column(name = "excluded", columnDefinition = "boolean", nullable = false)
  public boolean excluded = false;

  @Column(name = "exclusion_status", columnDefinition = "varchar(30)", nullable = false)
  public String exclusionStatus = "NONE";

  @Column(name = "exclusion_reason", columnDefinition = "varchar(1000)", nullable = true)
  public String exclusionReason;

  @Column(name = "exclusion_requested_by", columnDefinition = "bigint", nullable = true)
  public Long exclusionRequestedBy;

  @Column(name = "exclusion_reviewed_by", columnDefinition = "bigint", nullable = true)
  public Long exclusionReviewedBy;

  @Column(name = "version", columnDefinition = "bigint", nullable = false)
  public long version = 1;
}
