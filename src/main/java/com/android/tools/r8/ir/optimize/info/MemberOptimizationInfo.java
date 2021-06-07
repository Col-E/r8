// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.utils.AndroidApiLevel;

public interface MemberOptimizationInfo<
    T extends MemberOptimizationInfo<T> & MutableOptimizationInfo> {

  default boolean isMutableOptimizationInfo() {
    return false;
  }

  default MutableMethodOptimizationInfo asMutableMethodOptimizationInfo() {
    return null;
  }

  default MutableFieldOptimizationInfo asMutableFieldOptimizationInfo() {
    return null;
  }

  default boolean hasApiReferenceLevel() {
    return false;
  }

  AndroidApiLevel getApiReferenceLevel(AndroidApiLevel minApi);

  T toMutableOptimizationInfo();
}
