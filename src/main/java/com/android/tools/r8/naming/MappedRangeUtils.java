// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import java.util.List;

public class MappedRangeUtils {

  static int addAllInlineFramesUntilOutermostCaller(
      List<MappedRange> mappedRanges, int index, List<MappedRange> listToAdd) {
    assert index < mappedRanges.size();
    while (isInlineMappedRange(mappedRanges, index)) {
      listToAdd.add(mappedRanges.get(index++));
    }
    listToAdd.add(mappedRanges.get(index++));
    return index;
  }

  static boolean isInlineMappedRange(List<MappedRange> mappedRanges, int index) {
    // We are comparing against the next entry so we need a buffer of one.
    if (index + 1 >= mappedRanges.size()) {
      return false;
    }
    return isInlineMappedRange(mappedRanges.get(index), mappedRanges.get(index + 1));
  }

  static boolean isInlineMappedRange(MappedRange previous, MappedRange next) {
    if (previous.minifiedRange == null) {
      return false;
    }
    return next.getOriginalRangeOrIdentity() != null
        && next.getOriginalRangeOrIdentity().isCardinal
        && previous.minifiedRange.equals(next.minifiedRange);
  }
}
