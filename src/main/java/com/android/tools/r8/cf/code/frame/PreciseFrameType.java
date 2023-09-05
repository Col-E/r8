// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.graph.DexType;
import java.util.function.Function;

public interface PreciseFrameType extends FrameType {

  @Override
  @SuppressWarnings("ReferenceEquality")
  default PreciseFrameType map(Function<DexType, DexType> fn) {
    assert !isInitializedNonNullReferenceTypeWithInterfaces();
    if (isInitializedNonNullReferenceTypeWithoutInterfaces()) {
      DexType type = asInitializedNonNullReferenceTypeWithoutInterfaces().getInitializedType();
      DexType newType = fn.apply(type);
      if (type != newType) {
        return FrameType.initializedNonNullReference(newType);
      }
    } else if (isUninitializedNew()) {
      DexType type = getUninitializedNewType();
      DexType newType = fn.apply(type);
      if (type != newType) {
        return FrameType.uninitializedNew(getUninitializedLabel(), newType);
      }
    }
    return this;
  }
}
