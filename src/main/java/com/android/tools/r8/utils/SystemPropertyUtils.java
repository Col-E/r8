// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.Version;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SystemPropertyUtils {

  public static <T> T applySystemProperty(
      String propertyName, Function<String, T> propertyFoundFn, Supplier<T> propertyNotFoundFn) {
    if (isSystemPropertySet(propertyName)) {
      return propertyFoundFn.apply(System.getProperty(propertyName));
    } else {
      return propertyNotFoundFn.get();
    }
  }

  public static String getSystemPropertyOrDefault(String propertyName, String defaultValue) {
    return System.getProperty(propertyName, defaultValue);
  }

  public static String getSystemPropertyForDevelopment(String propertyName) {
    return Version.isDevelopmentVersion() ? System.getProperty(propertyName) : null;
  }

  public static String getSystemPropertyForDevelopmentOrDefault(
      String propertyName, String defaultValue) {
    return Version.isDevelopmentVersion()
        ? System.getProperty(propertyName, defaultValue)
        : defaultValue;
  }

  public static boolean hasSystemPropertyThatMatches(
      String propertyName, Predicate<String> predicate) {
    String propertyValue = System.getProperty(propertyName);
    return propertyValue != null && predicate.test(propertyValue);
  }

  public static boolean hasSystemPropertyForDevelopmentThatMatches(
      String propertyName, Predicate<String> predicate) {
    String propertyValue = getSystemPropertyForDevelopment(propertyName);
    return propertyValue != null && predicate.test(propertyValue);
  }

  public static boolean isSystemPropertySet(String propertyName) {
    return hasSystemPropertyThatMatches(propertyName, alwaysTrue());
  }

  public static boolean isSystemPropertyForDevelopmentSet(String propertyName) {
    return hasSystemPropertyForDevelopmentThatMatches(propertyName, alwaysTrue());
  }

  public static boolean parseSystemPropertyOrDefault(String propertyName, boolean defaultValue) {
    return internalParseSystemPropertyForDevelopmentOrDefault(
        propertyName, System.getProperty(propertyName), defaultValue);
  }

  public static boolean parseSystemPropertyForDevelopmentOrDefault(
      String propertyName, boolean defaultValue) {
    return internalParseSystemPropertyForDevelopmentOrDefault(
        propertyName, getSystemPropertyForDevelopment(propertyName), defaultValue);
  }

  private static boolean internalParseSystemPropertyForDevelopmentOrDefault(
      String propertyName, String propertyValue, boolean defaultValue) {
    if (propertyValue == null) {
      return defaultValue;
    }
    if (StringUtils.isFalsy(propertyValue)) {
      return false;
    }
    if (StringUtils.isTruthy(propertyValue)) {
      return true;
    }
    throw new IllegalArgumentException(
        "Expected value of " + propertyName + " to be a boolean, but was: " + propertyValue);
  }

  public static int parseSystemPropertyOrDefault(String propertyName, int defaultValue) {
    String propertyValue = System.getProperty(propertyName);
    return propertyValue != null ? Integer.parseInt(propertyValue) : defaultValue;
  }

  public static int parseSystemPropertyForDevelopmentOrDefault(
      String propertyName, int defaultValue) {
    String propertyValue = getSystemPropertyForDevelopment(propertyName);
    return propertyValue != null ? Integer.parseInt(propertyValue) : defaultValue;
  }
}
