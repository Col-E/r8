// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class IntSummaryStatisticsConversions {

  private static final Field JAVA_LONG_COUNT_FIELD;
  private static final Field JAVA_LONG_SUM_FIELD;
  private static final Field JAVA_INT_MIN_FIELD;
  private static final Field JAVA_INT_MAX_FIELD;
  private static final Field JD_LONG_COUNT_FIELD;
  private static final Field JD_LONG_SUM_FIELD;
  private static final Field JD_INT_MIN_FIELD;
  private static final Field JD_INT_MAX_FIELD;

  static {
    Class<?> javaIntSummaryStatisticsClass = java.util.IntSummaryStatistics.class;
    JAVA_LONG_COUNT_FIELD = getField(javaIntSummaryStatisticsClass, "count");
    JAVA_LONG_COUNT_FIELD.setAccessible(true);
    JAVA_LONG_SUM_FIELD = getField(javaIntSummaryStatisticsClass, "sum");
    JAVA_LONG_SUM_FIELD.setAccessible(true);
    JAVA_INT_MIN_FIELD = getField(javaIntSummaryStatisticsClass, "min");
    JAVA_INT_MIN_FIELD.setAccessible(true);
    JAVA_INT_MAX_FIELD = getField(javaIntSummaryStatisticsClass, "max");
    JAVA_INT_MAX_FIELD.setAccessible(true);

    Class<?> jdIntSummaryStatisticsClass = j$.util.IntSummaryStatistics.class;
    JD_LONG_COUNT_FIELD = getField(jdIntSummaryStatisticsClass, "count");
    JD_LONG_COUNT_FIELD.setAccessible(true);
    JD_LONG_SUM_FIELD = getField(jdIntSummaryStatisticsClass, "sum");
    JD_LONG_SUM_FIELD.setAccessible(true);
    JD_INT_MIN_FIELD = getField(jdIntSummaryStatisticsClass, "min");
    JD_INT_MIN_FIELD.setAccessible(true);
    JD_INT_MAX_FIELD = getField(jdIntSummaryStatisticsClass, "max");
    JD_INT_MAX_FIELD.setAccessible(true);
  }

  private IntSummaryStatisticsConversions() {}

  private static Field getField(Class<?> clazz, String name) {
    try {
      return clazz.getDeclaredField(name);
    } catch (NoSuchFieldException e) {
      throw new Error("Failed summary statistics set-up.", e);
    }
  }

  public static j$.util.IntSummaryStatistics convert(java.util.IntSummaryStatistics stats) {
    if (stats == null) {
      return null;
    }
    j$.util.IntSummaryStatistics newInstance = new j$.util.IntSummaryStatistics();
    try {
      JD_LONG_COUNT_FIELD.set(newInstance, stats.getCount());
      JD_LONG_SUM_FIELD.set(newInstance, stats.getSum());
      JD_INT_MIN_FIELD.set(newInstance, stats.getMin());
      JD_INT_MAX_FIELD.set(newInstance, stats.getMax());
    } catch (IllegalAccessException e) {
      throw new Error("Failed summary statistics conversion.", e);
    }
    return newInstance;
  }

  public static java.util.IntSummaryStatistics convert(j$.util.IntSummaryStatistics stats) {
    if (stats == null) {
      return null;
    }
    java.util.IntSummaryStatistics newInstance = new java.util.IntSummaryStatistics();
    try {
      JAVA_LONG_COUNT_FIELD.set(newInstance, stats.getCount());
      JAVA_LONG_SUM_FIELD.set(newInstance, stats.getSum());
      JAVA_INT_MIN_FIELD.set(newInstance, stats.getMin());
      JAVA_INT_MAX_FIELD.set(newInstance, stats.getMax());
    } catch (IllegalAccessException e) {
      throw new Error("Failed summary statistics conversion.", e);
    }
    return newInstance;
  }
}
