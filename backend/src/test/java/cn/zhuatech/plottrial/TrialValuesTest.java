// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.plottrial;

import static org.junit.jupiter.api.Assertions.*;

import java.math.*;
import java.time.*;
import org.junit.jupiter.api.Test;

/** 存储精度与观测日期边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class TrialValuesTest {
  final Clock clock =
      Clock.fixed(Instant.parse("2026-10-05T16:00:00.123456789Z"), ZoneId.of("Asia/Shanghai"));

  @Test
  void decimalDoesNotSilentlyRound() {
    assertEquals(new BigDecimal("1.2345"), TrialValues.decimal(new BigDecimal("1.234500")));
    assertThrows(Problem.class, () -> TrialValues.decimal(new BigDecimal("1.23456")));
    assertThrows(Problem.class, () -> TrialValues.decimal(new BigDecimal("1000000000000")));
  }

  @Test
  void negativeAndZeroMeasurementsAreValidNumbers() {
    assertEquals(new BigDecimal("-5.0000"), TrialValues.decimal(new BigDecimal("-5")));
    assertEquals(new BigDecimal("0.0000"), TrialValues.decimal(BigDecimal.ZERO));
  }

  @Test
  void datesUseShanghaiDayAndPlantingBoundary() {
    assertEquals(
        LocalDate.parse("2026-10-06"),
        TrialValues.date(LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-01"), clock));
    assertThrows(Problem.class, () -> TrialValues.date(LocalDate.parse("2026-10-07"), null, clock));
    assertThrows(
        Problem.class,
        () ->
            TrialValues.date(LocalDate.parse("2026-09-30"), LocalDate.parse("2026-10-01"), clock));
  }

  @Test
  void timestampsMatchDatabaseMicroseconds() {
    assertEquals(Instant.parse("2026-10-05T16:00:00.123456Z"), BusinessTime.now(clock));
  }
}
