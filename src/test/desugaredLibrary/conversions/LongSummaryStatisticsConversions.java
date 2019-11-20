// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class LongSummaryStatisticsConversions {

  private static final Field JAVA_LONG_COUNT_FIELD;
  private static final Field JAVA_LONG_SUM_FIELD;
  private static final Field JAVA_LONG_MIN_FIELD;
  private static final Field JAVA_LONG_MAX_FIELD;
  private static final Field JD_LONG_COUNT_FIELD;
  private static final Field JD_LONG_SUM_FIELD;
  private static final Field JD_LONG_MIN_FIELD;
  private static final Field JD_LONG_MAX_FIELD;

  static {
    Class<?> javaLongSummaryStatisticsClass = java.util.LongSummaryStatistics.class;
    JAVA_LONG_COUNT_FIELD = getField(javaLongSummaryStatisticsClass, "count");
    JAVA_LONG_COUNT_FIELD.setAccessible(true);
    JAVA_LONG_SUM_FIELD = getField(javaLongSummaryStatisticsClass, "sum");
    JAVA_LONG_SUM_FIELD.setAccessible(true);
    JAVA_LONG_MIN_FIELD = getField(javaLongSummaryStatisticsClass, "min");
    JAVA_LONG_MIN_FIELD.setAccessible(true);
    JAVA_LONG_MAX_FIELD = getField(javaLongSummaryStatisticsClass, "max");
    JAVA_LONG_MAX_FIELD.setAccessible(true);

    Class<?> jdLongSummaryStatisticsClass = j$.util.LongSummaryStatistics.class;
    JD_LONG_COUNT_FIELD = getField(jdLongSummaryStatisticsClass, "count");
    JD_LONG_COUNT_FIELD.setAccessible(true);
    JD_LONG_SUM_FIELD = getField(jdLongSummaryStatisticsClass, "sum");
    JD_LONG_SUM_FIELD.setAccessible(true);
    JD_LONG_MIN_FIELD = getField(jdLongSummaryStatisticsClass, "min");
    JD_LONG_MIN_FIELD.setAccessible(true);
    JD_LONG_MAX_FIELD = getField(jdLongSummaryStatisticsClass, "max");
    JD_LONG_MAX_FIELD.setAccessible(true);
  }

  private LongSummaryStatisticsConversions() {}

  private static Field getField(Class<?> clazz, String name) {
    try {
      return clazz.getDeclaredField(name);
    } catch (NoSuchFieldException e) {
      throw new Error("Failed summary statistics set-up.", e);
    }
  }

  public static j$.util.LongSummaryStatistics convert(java.util.LongSummaryStatistics stats) {
    if (stats == null) {
      return null;
    }
    j$.util.LongSummaryStatistics newInstance = new j$.util.LongSummaryStatistics();
    try {
      JD_LONG_COUNT_FIELD.set(newInstance, stats.getCount());
      JD_LONG_SUM_FIELD.set(newInstance, stats.getSum());
      JD_LONG_MIN_FIELD.set(newInstance, stats.getMin());
      JD_LONG_MAX_FIELD.set(newInstance, stats.getMax());
    } catch (IllegalAccessException e) {
      throw new Error("Failed summary statistics conversion.", e);
    }
    return newInstance;
  }

  public static java.util.LongSummaryStatistics convert(j$.util.LongSummaryStatistics stats) {
    if (stats == null) {
      return null;
    }
    java.util.LongSummaryStatistics newInstance = new java.util.LongSummaryStatistics();
    try {
      JAVA_LONG_COUNT_FIELD.set(newInstance, stats.getCount());
      JAVA_LONG_SUM_FIELD.set(newInstance, stats.getSum());
      JAVA_LONG_MIN_FIELD.set(newInstance, stats.getMin());
      JAVA_LONG_MAX_FIELD.set(newInstance, stats.getMax());
    } catch (IllegalAccessException e) {
      throw new Error("Failed summary statistics conversion.", e);
    }
    return newInstance;
  }
}
