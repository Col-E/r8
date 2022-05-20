// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

public abstract class WidePrimitiveFrameType extends SingletonFrameType
    implements PrimitiveFrameType, WideFrameType {

  @Override
  public boolean isInitialized() {
    return true;
  }

  @Override
  public boolean isPrecise() {
    return true;
  }

  @Override
  public PreciseFrameType asPrecise() {
    return this;
  }

  @Override
  public boolean isPrimitive() {
    return true;
  }

  @Override
  public PrimitiveFrameType asPrimitive() {
    return this;
  }

  @Override
  public boolean isWide() {
    return true;
  }

  @Override
  public WideFrameType asWide() {
    return this;
  }

  @Override
  public int getWidth() {
    return 2;
  }

  @Override
  public WideFrameType join(WideFrameType frameType) {
    return this == frameType ? this : FrameType.twoWord();
  }

  @Override
  public final String toString() {
    return getTypeName();
  }
}
