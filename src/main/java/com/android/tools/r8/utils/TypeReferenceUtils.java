// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.references.TypeReference;
import java.util.Comparator;

public class TypeReferenceUtils {

  private static final Comparator<TypeReference> COMPARATOR =
      (type, other) -> {
        // Handle null inputs (void).
        if (type == null) {
          return -1;
        }
        if (other == null) {
          return 1;
        }
        return type.getDescriptor().compareTo(other.getDescriptor());
      };

  public static Comparator<TypeReference> getTypeReferenceComparator() {
    return COMPARATOR;
  }
}
