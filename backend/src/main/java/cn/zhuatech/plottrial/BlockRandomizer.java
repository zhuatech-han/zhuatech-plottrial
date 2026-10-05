// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.plottrial;

import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** 可重现的完整区组内Fisher–Yates布局；种子来自服务端，不提供重抽。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class BlockRandomizer {
  public static final String ALGORITHM = "SHA256-FY32-v1";

  private BlockRandomizer() {}

  /** 每区组重新从代码排序的处理索引开始，使用拒绝采样避免余数偏差。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static List<Integer> order(String seed, int block, int count) {
    if (seed == null
        || !seed.matches("[0-9a-f]{64}")
        || block < 1
        || block > 16
        || count < 2
        || count > 12) throw new IllegalArgumentException("Invalid layout input");
    var indices = new ArrayList<Integer>();
    for (int i = 0; i < count; i++) indices.add(i);
    int counter = 0;
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      for (int i = count - 1; i > 0; i--) {
        int bound = i + 1;
        long limit = (1L << 32) - ((1L << 32) % bound);
        long draw;
        do {
          byte[] bytes =
              digest.digest(
                  (seed + ":" + block + ":" + counter++).getBytes(StandardCharsets.UTF_8));
          draw = Integer.toUnsignedLong(ByteBuffer.wrap(bytes).getInt());
        } while (draw >= limit);
        Collections.swap(indices, i, (int) (draw % bound));
      }
      return List.copyOf(indices);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** 256位种子用于公开布局复现，不作为凭据使用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String seed() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
