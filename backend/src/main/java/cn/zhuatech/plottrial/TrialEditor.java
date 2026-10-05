// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.plottrial;

import jakarta.persistence.*;
import java.time.*;

/** 田间试验持久化记录，操作由试验服务校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "trial_editor")
public class TrialEditor {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "trial_id", columnDefinition = "bigint", nullable = false)
  public Long trialId;

  @Column(name = "actor_id", columnDefinition = "bigint", nullable = false)
  public Long actorId;
}
