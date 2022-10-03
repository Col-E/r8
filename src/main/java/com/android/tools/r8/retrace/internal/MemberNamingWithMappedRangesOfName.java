// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRangesOfName;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.utils.ListUtils;
import java.util.List;

class MemberNamingWithMappedRangesOfName {

  private final MappedRangesOfName mappedRangesOfName;
  private final MemberNaming methodMemberNaming;

  MemberNamingWithMappedRangesOfName(
      MemberNaming methodMemberNaming, MappedRangesOfName mappedRangesOfName) {
    this.methodMemberNaming = methodMemberNaming;
    this.mappedRangesOfName = mappedRangesOfName;
  }

  List<MappedRange> allRangesForLine(int line) {
    return mappedRangesOfName.allRangesForLine(line, false);
  }

  List<MappedRange> mappedRangesWithNoMinifiedRange() {
    return ListUtils.filter(
        mappedRangesOfName.getMappedRanges(), mappedRange -> mappedRange.minifiedRange == null);
  }

  List<MappedRange> getMappedRanges() {
    return mappedRangesOfName.getMappedRanges();
  }

  public MemberNaming getMemberNaming() {
    return methodMemberNaming;
  }
}
