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
import com.android.tools.r8.utils.ListUtils;
import java.util.List;

public class RetraceStackTraceContextImpl implements RetraceStackTraceContext {

  private final ClassReference thrownException;

  private RetraceStackTraceContextImpl(ClassReference thrownException) {
    this.thrownException = thrownException;
  }

  public ClassReference getThrownException() {
    return thrownException;
  }

  RetraceStackTraceCurrentEvaluationInformation computeRewritingInformation(
      List<MappedRange> mappedRanges) {
    if (mappedRanges == null || mappedRanges.isEmpty()) {
      return RetraceStackTraceCurrentEvaluationInformation.empty();
    }
    RetraceStackTraceCurrentEvaluationInformation.Builder builder =
        RetraceStackTraceCurrentEvaluationInformation.builder();
    MappedRange last = ListUtils.last(mappedRanges);
    for (RewriteFrameMappingInformation rewriteInformation :
        last.getRewriteFrameMappingInformation()) {
      if (evaluateConditions(rewriteInformation.getConditions())) {
        for (RewriteAction action : rewriteInformation.getActions()) {
          action.evaluate(builder);
        }
      }
    }
    return builder.build();
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

  public static class Builder {

    private ClassReference thrownException;

    private Builder() {}

    public Builder setThrownException(ClassReference thrownException) {
      this.thrownException = thrownException;
      return this;
    }

    public RetraceStackTraceContextImpl build() {
      return new RetraceStackTraceContextImpl(thrownException);
    }

    public static Builder create() {
      return new Builder();
    }
  }
}
