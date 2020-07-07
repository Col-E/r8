// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.util;

public class DoubleSummaryStatisticsConversions {

  private DoubleSummaryStatisticsConversions() {}

  public static j$.util.DoubleSummaryStatistics convert(java.util.DoubleSummaryStatistics stats) {
    throw new Error(
        "Java 8+ API desugaring (library desugaring) cannot convert"
            + " to java.util.DoubleSummaryStatistics");
  }

  public static java.util.DoubleSummaryStatistics convert(j$.util.DoubleSummaryStatistics stats) {
    throw new Error(
        "Java 8+ API desugaring (library desugaring) cannot convert"
            + " from java.util.DoubleSummaryStatistics");
  }
}
