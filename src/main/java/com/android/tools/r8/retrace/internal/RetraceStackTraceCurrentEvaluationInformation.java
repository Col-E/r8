// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

public class RetraceStackTraceCurrentEvaluationInformation {

  private static final RetraceStackTraceCurrentEvaluationInformation EMPTY =
      new RetraceStackTraceCurrentEvaluationInformation(0);

  private final int removeInnerFramesCount;

  private RetraceStackTraceCurrentEvaluationInformation(int removeInnerFramesCount) {
    this.removeInnerFramesCount = removeInnerFramesCount;
  }

  public int getRemoveInnerFramesCount() {
    return removeInnerFramesCount;
  }

  public static RetraceStackTraceCurrentEvaluationInformation empty() {
    return EMPTY;
  }

  public static RetraceStackTraceCurrentEvaluationInformation.Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private int removeInnerFramesCount;
    private int position;

    public Builder incrementRemoveInnerFramesCount(int increment) {
      removeInnerFramesCount += increment;
      return this;
    }

    public int getPosition() {
      return position;
    }

    public Builder setPosition(int position) {
      this.position = position;
      return this;
    }

    RetraceStackTraceCurrentEvaluationInformation build() {
      return new RetraceStackTraceCurrentEvaluationInformation(removeInnerFramesCount);
    }
  }
}
