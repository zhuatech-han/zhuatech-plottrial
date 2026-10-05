// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.plottrial;

import java.math.*;
import java.time.*;

/** 首次响应和MySQL DECIMAL／DATE存储使用一致范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class TrialValues {
  private TrialValues() {}

  /** 有限4位小数，不允许暗中四舍五入观测值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal decimal(BigDecimal value) {
    if (value == null
        || value.stripTrailingZeros().scale() > 4
        || value.abs().compareTo(new BigDecimal("999999999999.9999")) > 0)
      throw new Problem(400, "INVALID_DECIMAL");
    return value.setScale(4, RoundingMode.UNNECESSARY);
  }

  /** 人工事实日期不得晚于上海当日，且不得早于对应试验或种植日期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static LocalDate date(LocalDate value, LocalDate minimum, Clock clock) {
    if (value == null
        || value.isBefore(minimum == null ? LocalDate.of(2000, 1, 1) : minimum)
        || value.isAfter(LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")))))
      throw new Problem(400, "INVALID_DATE");
    return value;
  }
}
