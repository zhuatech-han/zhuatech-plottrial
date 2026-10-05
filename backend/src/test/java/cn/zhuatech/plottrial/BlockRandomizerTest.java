// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.plottrial;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** 可复现算法与完整区组约束。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class BlockRandomizerTest {
  @Test
  void fixedProtocolVector() {
    assertEquals(List.of(3, 2, 1, 0), BlockRandomizer.order("0".repeat(64), 1, 4));
  }

  @Test
  void everyBlockContainsEachTreatmentOnce() {
    for (int n = 2; n <= 12; n++)
      for (int b = 1; b <= 16; b++) {
        var row = BlockRandomizer.order("0123456789abcdef".repeat(4), b, n);
        assertEquals(n, new HashSet<>(row).size());
        assertEquals(
            java.util.stream.IntStream.range(0, n).boxed().toList(),
            row.stream().sorted().toList());
      }
  }

  @Test
  void sameSeedReproducesIndependentBlocks() {
    var seed = BlockRandomizer.seed();
    assertTrue(seed.matches("[a-f0-9]{64}"));
    assertEquals(BlockRandomizer.order(seed, 12, 9), BlockRandomizer.order(seed, 12, 9));
    assertNotEquals(
        BlockRandomizer.order("0".repeat(64), 1, 12), BlockRandomizer.order("0".repeat(64), 2, 12));
  }

  @Test
  void invalidProtocolBounds() {
    assertThrows(IllegalArgumentException.class, () -> BlockRandomizer.order("bad", 1, 4));
    assertThrows(IllegalArgumentException.class, () -> BlockRandomizer.order("0".repeat(64), 0, 4));
    assertThrows(
        IllegalArgumentException.class, () -> BlockRandomizer.order("0".repeat(64), 1, 13));
  }
}
