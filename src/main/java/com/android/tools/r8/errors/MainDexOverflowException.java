// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

/**
 * Thrown when running mono dex and not all classes can fit in a dex or when running for multidex
 * legacy and there are too many classes to fit in the main dex.
 */
public class MainDexOverflowException extends DexOverflowException {

  public MainDexOverflowException(
      boolean hasMainDexList, long numOfMethods, long numOfFields, long maxNumOfEntries) {
    super(getMessage(hasMainDexList, numOfMethods, numOfFields, maxNumOfEntries));
  }

  private static String getMessage(
      boolean hasMainDexList, long numOfMethods, long maxNumOfEntries, long numOfFields) {
    StringBuilder messageBuilder = new StringBuilder();
    // General message: Cannot fit.
    messageBuilder.append("Cannot fit requested classes in ");
    messageBuilder.append(hasMainDexList ? "the main-" : "a single ");
    messageBuilder.append("dex file");
    messageBuilder.append(" (");
    // Show the numbers of methods and/or fields that exceed the limit.
    if (numOfMethods > maxNumOfEntries) {
      messageBuilder.append("# methods: ");
      messageBuilder.append(numOfMethods);
      messageBuilder.append(" > ").append(maxNumOfEntries);
      if (numOfFields > maxNumOfEntries) {
        messageBuilder.append(" ; ");
      }
    }
    if (numOfFields > maxNumOfEntries) {
      messageBuilder.append("# fields: ");
      messageBuilder.append(numOfFields);
      messageBuilder.append(" > ").append(maxNumOfEntries);
    }
    messageBuilder.append(")");
    return messageBuilder.toString();
  }

}
