// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation.Condition;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation.RewriteAction;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import java.util.List;
import java.util.OptionalInt;

public class RetraceStackTraceContextImpl implements RetraceStackTraceContext {

  private final ClassReference thrownException;
  private final OptionalInt rewritePosition;

  private RetraceStackTraceContextImpl(
      ClassReference thrownException, OptionalInt rewritePosition) {
    this.thrownException = thrownException;
    this.rewritePosition = rewritePosition;
  }

  public ClassReference getThrownException() {
    return thrownException;
  }

  RetraceStackTraceCurrentEvaluationInformation computeRewriteFrameInformation(
      List<MappedRange> mappedRanges) {
    if (mappedRanges == null || mappedRanges.isEmpty()) {
      return RetraceStackTraceCurrentEvaluationInformation.empty();
    }
    RetraceStackTraceCurrentEvaluationInformation.Builder builder =
        RetraceStackTraceCurrentEvaluationInformation.builder();
    for (MappedRange mappedRange : mappedRanges) {
      for (RewriteFrameMappingInformation rewriteInformation :
          mappedRange.getRewriteFrameMappingInformation()) {
        if (evaluateConditions(rewriteInformation.getConditions())) {
          for (RewriteAction action : rewriteInformation.getActions()) {
            action.evaluate(builder);
          }
        }
      }
    }
    return builder.build();
  }

  public boolean hasRewritePosition() {
    return rewritePosition.isPresent();
  }

  public int getRewritePosition() {
    return rewritePosition.getAsInt();
  }

  private boolean evaluateConditions(List<Condition> conditions) {
    for (Condition condition : conditions) {
      if (!condition.evaluate(this)) {
        return false;
      }
    }
    return true;
  }

  public static Builder builder() {
    return Builder.create();
  }

  public Builder buildFromThis() {
    return builder().setThrownException(thrownException).setRewritePosition(rewritePosition);
  }

  public static class Builder {

    private ClassReference thrownException;
    private OptionalInt rewritePosition = OptionalInt.empty();

    private Builder() {}

    public Builder setThrownException(ClassReference thrownException) {
      this.thrownException = thrownException;
      return this;
    }

    public Builder setRewritePosition(OptionalInt rewritePosition) {
      this.rewritePosition = rewritePosition;
      return this;
    }

    public Builder clearRewritePosition() {
      this.rewritePosition = OptionalInt.empty();
      return this;
    }

    public RetraceStackTraceContextImpl build() {
      return new RetraceStackTraceContextImpl(thrownException, rewritePosition);
    }

    public static Builder create() {
      return new Builder();
    }
  }
}
