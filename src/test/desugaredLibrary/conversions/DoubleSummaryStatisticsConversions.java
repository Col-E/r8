// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.util;

import java.lang.reflect.Field;

public class DoubleSummaryStatisticsConversions {

  private static final Field JAVA_LONG_COUNT_FIELD;
  private static final Field JAVA_DOUBLE_SUM_FIELD;
  private static final Field JAVA_DOUBLE_MIN_FIELD;
  private static final Field JAVA_DOUBLE_MAX_FIELD;
  private static final Field JD_LONG_COUNT_FIELD;
  private static final Field JD_DOUBLE_SUM_FIELD;
  private static final Field JD_DOUBLE_MIN_FIELD;
  private static final Field JD_DOUBLE_MAX_FIELD;

  static {
    Class<?> javaDoubleSummaryStatisticsClass = java.util.DoubleSummaryStatistics.class;
    JAVA_LONG_COUNT_FIELD = getField(javaDoubleSummaryStatisticsClass, "count");
    JAVA_LONG_COUNT_FIELD.setAccessible(true);
    JAVA_DOUBLE_SUM_FIELD = getField(javaDoubleSummaryStatisticsClass, "sum");
    JAVA_DOUBLE_SUM_FIELD.setAccessible(true);
    JAVA_DOUBLE_MIN_FIELD = getField(javaDoubleSummaryStatisticsClass, "min");
    JAVA_DOUBLE_MIN_FIELD.setAccessible(true);
    JAVA_DOUBLE_MAX_FIELD = getField(javaDoubleSummaryStatisticsClass, "max");
    JAVA_DOUBLE_MAX_FIELD.setAccessible(true);

    Class<?> jdDoubleSummaryStatisticsClass = j$.util.DoubleSummaryStatistics.class;
    JD_LONG_COUNT_FIELD = getField(jdDoubleSummaryStatisticsClass, "count");
    JD_LONG_COUNT_FIELD.setAccessible(true);
    JD_DOUBLE_SUM_FIELD = getField(jdDoubleSummaryStatisticsClass, "sum");
    JD_DOUBLE_SUM_FIELD.setAccessible(true);
    JD_DOUBLE_MIN_FIELD = getField(jdDoubleSummaryStatisticsClass, "min");
    JD_DOUBLE_MIN_FIELD.setAccessible(true);
    JD_DOUBLE_MAX_FIELD = getField(jdDoubleSummaryStatisticsClass, "max");
    JD_DOUBLE_MAX_FIELD.setAccessible(true);
  }

  private DoubleSummaryStatisticsConversions() {}

  private static Field getField(Class<?> clazz, String name) {
    try {
      return clazz.getDeclaredField(name);
    } catch (NoSuchFieldException e) {
      throw new Error("Failed summary statistics set-up.", e);
    }
  }

  public static j$.util.DoubleSummaryStatistics convert(java.util.DoubleSummaryStatistics stats) {
    if (stats == null) {
      return null;
    }
    j$.util.DoubleSummaryStatistics newInstance = new j$.util.DoubleSummaryStatistics();
    try {
      JD_LONG_COUNT_FIELD.set(newInstance, stats.getCount());
      JD_DOUBLE_SUM_FIELD.set(newInstance, stats.getSum());
      JD_DOUBLE_MIN_FIELD.set(newInstance, stats.getMin());
      JD_DOUBLE_MAX_FIELD.set(newInstance, stats.getMax());
    } catch (IllegalAccessException e) {
      throw new Error("Failed summary statistics conversion.", e);
    }
    return newInstance;
  }

  public static java.util.DoubleSummaryStatistics convert(j$.util.DoubleSummaryStatistics stats) {
    if (stats == null) {
      return null;
    }
    java.util.DoubleSummaryStatistics newInstance = new java.util.DoubleSummaryStatistics();
    try {
      JAVA_LONG_COUNT_FIELD.set(newInstance, stats.getCount());
      JAVA_DOUBLE_SUM_FIELD.set(newInstance, stats.getSum());
      JAVA_DOUBLE_MIN_FIELD.set(newInstance, stats.getMin());
      JAVA_DOUBLE_MAX_FIELD.set(newInstance, stats.getMax());
    } catch (IllegalAccessException e) {
      throw new Error("Failed summary statistics conversion.", e);
    }
    return newInstance;
  }
}
