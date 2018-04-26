// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

/**
 * Thrown when running mono dex and not all classes can fit in a dex or when running for multidex
 * legacy and there are too many classes to fit in the main dex.
 */
public class MainDexOverflowException extends DexOverflowException {
  private final boolean hasMainDexList;
  private final long numOfMethods;
  private final long numOfFields;
  private final long maxNumOfEntries;

  public MainDexOverflowException(
      boolean hasMainDexList, long numOfMethods, long numOfFields, long maxNumOfEntries) {
    super();
    this.hasMainDexList = hasMainDexList;
    this.numOfMethods = numOfMethods;
    this.numOfFields = numOfFields;
    this.maxNumOfEntries = maxNumOfEntries;
  }

  private StringBuilder getGeneralMessage() {
    StringBuilder messageBuilder = new StringBuilder();
    // General message: Cannot fit.
    messageBuilder.append("Cannot fit requested classes in ");
    messageBuilder.append(hasMainDexList ? "the main-" : "a single ");
    messageBuilder.append("dex file");

    return messageBuilder;
  }

  private String getNumberRelatedMessage() {
    StringBuilder messageBuilder = new StringBuilder();
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

    return messageBuilder.toString();
  }

  @Override
  public String getMessage() {
    // Default message
    return getGeneralMessage()
        .append(" (")
        .append(getNumberRelatedMessage())
        .append(")")
        .toString();
  }

  @Override
  public String getMessageForD8() {
    StringBuilder messageBuilder = getGeneralMessage();
    if (!hasMainDexList) {
      messageBuilder.append(". ");
      messageBuilder.append("Try supplying a main-dex list");
    }
    messageBuilder.append(".").append(System.getProperty("line.separator"));
    messageBuilder.append(getNumberRelatedMessage());
    return messageBuilder.toString();
  }

  @Override
  public String getMessageForR8() {
    StringBuilder messageBuilder = getGeneralMessage();
    if (!hasMainDexList) {
      messageBuilder.append(". ");
      messageBuilder.append("Try supplying a main-dex list or main dex rules");
    }
    messageBuilder.append(".").append(System.getProperty("line.separator"));
    messageBuilder.append(getNumberRelatedMessage());
    return messageBuilder.toString();
  }

}
