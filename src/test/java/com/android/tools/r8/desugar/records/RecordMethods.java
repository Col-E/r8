// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import java.util.Arrays;

// This class implements support methods for record desugaring. The RecordRewriter
// rewrites relevant calls to one of the following methods.
public class RecordMethods {

  public static String toString(
      Object[] recordFieldsAsObjects, String simpleName, String fieldNames) {
    // Example: "Person[name=Jane Doe, age=42]"
    String[] fieldNamesSplit = fieldNames.isEmpty() ? new String[0] : fieldNames.split(";");
    StringBuilder builder = new StringBuilder();
    builder.append(simpleName).append("[");
    for (int i = 0; i < fieldNamesSplit.length; i++) {
      builder.append(fieldNamesSplit[i]).append("=").append(recordFieldsAsObjects[i]);
      if (i != fieldNamesSplit.length - 1) {
        builder.append(", ");
      }
    }
    builder.append("]");
    return builder.toString();
  }

  public static int hashCode(Class<?> recordClass, Object[] recordFieldsAsObjects) {
    return 31 * Arrays.hashCode(recordFieldsAsObjects) + recordClass.hashCode();
  }
}
