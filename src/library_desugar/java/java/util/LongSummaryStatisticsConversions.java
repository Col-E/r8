// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.util;

public class LongSummaryStatisticsConversions {

  private LongSummaryStatisticsConversions() {}

  public static j$.util.LongSummaryStatistics convert(java.util.LongSummaryStatistics stats) {
    throw new Error(
        "Java 8+ API desugaring (library desugaring) cannot convert"
            + " to java.util.LongSummaryStatistics");
  }

  public static java.util.LongSummaryStatistics convert(j$.util.LongSummaryStatistics stats) {
    throw new Error(
        "Java 8+ API desugaring (library desugaring) cannot convert"
            + " from java.util.LongSummaryStatistics");
  }
}
