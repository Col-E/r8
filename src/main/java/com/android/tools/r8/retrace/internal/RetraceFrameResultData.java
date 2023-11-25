// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.retrace.internal.RetraceClassResultImpl.RetraceClassElementImpl;
import java.util.List;
import java.util.OptionalInt;

class RetraceFrameResultData {

  private final RetraceClassElementImpl retraceClassElement;
  private final List<MemberNamingWithMappedRangesOfName> memberNamingWithMappedRanges;
  private final OptionalInt position;

  RetraceFrameResultData(
      RetraceClassElementImpl retraceClassElement,
      List<MemberNamingWithMappedRangesOfName> memberNamingWithMappedRanges,
      OptionalInt position) {
    this.retraceClassElement = retraceClassElement;
    this.memberNamingWithMappedRanges = memberNamingWithMappedRanges;
    this.position = position;
  }

  @SuppressWarnings("ReferenceEquality")
  boolean isAmbiguous() {
    if (memberNamingWithMappedRanges == null) {
      return false;
    }
    if (memberNamingWithMappedRanges.size() > 1) {
      return true;
    }
    assert !memberNamingWithMappedRanges.isEmpty();
    List<MappedRange> methodRanges = memberNamingWithMappedRanges.get(0).getMappedRanges();
    if (methodRanges != null && !methodRanges.isEmpty()) {
      MappedRange initialRange = methodRanges.get(0);
      for (MappedRange mappedRange : methodRanges) {
        if (isMappedRangeAmbiguous(mappedRange)) {
          return true;
        }
        if (mappedRange != initialRange
            && (mappedRange.minifiedRange == null
                || !mappedRange.minifiedRange.equals(initialRange.minifiedRange))) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isMappedRangeAmbiguous(MappedRange mappedRange) {
    if (mappedRange.originalRange == null || mappedRange.originalRange.span() == 1) {
      // If there is no original position or all maps to a single position, the result is not
      // ambiguous.
      return false;
    }
    return mappedRange.minifiedRange == null
        || mappedRange.minifiedRange.span() != mappedRange.originalRange.span();
  }

  public RetraceClassElementImpl getRetraceClassElement() {
    return retraceClassElement;
  }

  public List<MemberNamingWithMappedRangesOfName> getMemberNamingWithMappedRanges() {
    return memberNamingWithMappedRanges;
  }

  public OptionalInt getPosition() {
    return position;
  }
}
