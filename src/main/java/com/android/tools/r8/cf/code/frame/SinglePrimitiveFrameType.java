// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

public abstract class SinglePrimitiveFrameType extends SingletonFrameType
    implements PrimitiveFrameType, SingleFrameType {

  public boolean hasIntVerificationType() {
    return false;
  }

  @Override
  public final boolean isInitialized() {
    return true;
  }

  @Override
  public final boolean isPrecise() {
    return true;
  }

  @Override
  public PreciseFrameType asPrecise() {
    return this;
  }

  @Override
  public final boolean isPrimitive() {
    return true;
  }

  @Override
  public PrimitiveFrameType asPrimitive() {
    return this;
  }

  @Override
  public final SingleFrameType asSingle() {
    return this;
  }

  @Override
  public final SinglePrimitiveFrameType asSinglePrimitive() {
    return this;
  }

  @Override
  public final SingleFrameType join(SingleFrameType frameType) {
    if (this == frameType) {
      return this;
    }
    if (hasIntVerificationType()
        && frameType.isPrimitive()
        && frameType.asSinglePrimitive().hasIntVerificationType()) {
      return FrameType.intType();
    }
    return FrameType.oneWord();
  }

  @Override
  public final String toString() {
    return getTypeName();
  }
}
