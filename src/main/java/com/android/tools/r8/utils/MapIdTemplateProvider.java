// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.MapIdEnvironment;
import com.android.tools.r8.MapIdProvider;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

public class MapIdTemplateProvider implements MapIdProvider {

  private static final char VARIABLE_PREFIX = '%';

  private static final Map<String, MapIdProvider> HANDLERS =
      ImmutableMap.<String, MapIdProvider>builder()
          .put(var("MAP_HASH"), MapIdEnvironment::getMapHash)
          .build();

  private static String var(String name) {
    return VARIABLE_PREFIX + name;
  }

  private static int getMaxVariableLength() {
    int max = 0;
    for (String key : HANDLERS.keySet()) {
      max = Math.max(max, key.length());
    }
    return max;
  }

  public static MapIdProvider create(String template, DiagnosticsHandler handler) {
    String cleaned = template;
    for (String variable : HANDLERS.keySet()) {
      // Maintain the same size as template so indexing remains valid for error reporting.
      cleaned = cleaned.replace(variable, ' ' + variable.substring(1));
    }
    assert template.length() == cleaned.length();
    int unhandled = cleaned.indexOf(VARIABLE_PREFIX);
    if (unhandled >= 0) {
      while (unhandled >= 0) {
        int endIndex = Math.min(unhandled + getMaxVariableLength(), template.length());
        String variablePrefix = template.substring(unhandled, endIndex);
        handler.error(
            new StringDiagnostic("Invalid template variable starting with " + variablePrefix));
        unhandled = cleaned.indexOf(VARIABLE_PREFIX, unhandled + 1);
      }
      return null;
    }
    return new MapIdTemplateProvider(template);
  }

  private final String template;
  private String cachedValue = null;

  private MapIdTemplateProvider(String template) {
    this.template = template;
  }

  @Override
  public String get(MapIdEnvironment environment) {
    if (cachedValue == null) {
      cachedValue = template;
      HANDLERS.forEach(
          (variable, getter) -> {
            cachedValue = cachedValue.replace(variable, getter.get(environment));
          });
    }
    return cachedValue;
  }
}
