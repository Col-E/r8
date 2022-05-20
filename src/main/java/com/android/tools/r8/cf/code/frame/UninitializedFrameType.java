// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

public abstract class UninitializedFrameType extends BaseFrameType
    implements PreciseFrameType, SingleFrameType {

  @Override
  public boolean isObject() {
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
  public SingleFrameType asSingle() {
    return this;
  }

  @Override
  public boolean isUninitialized() {
    return true;
  }

  @Override
  public UninitializedFrameType asUninitialized() {
    return this;
  }
}
