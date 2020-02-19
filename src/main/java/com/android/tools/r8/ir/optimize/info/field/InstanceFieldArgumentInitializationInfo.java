// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.field;

/**
 * Used to represent that a constructor initializes an instance field on the newly created instance
 * with argument number {@link #argumentIndex} from the constructor's argument list.
 */
public class InstanceFieldArgumentInitializationInfo implements InstanceFieldInitializationInfo {

  private final int argumentIndex;

  /** Intentionally package private, use {@link InstanceFieldInitializationInfoFactory} instead. */
  InstanceFieldArgumentInitializationInfo(int argumentIndex) {
    this.argumentIndex = argumentIndex;
  }

  public int getArgumentIndex() {
    return argumentIndex;
  }

  @Override
  public boolean isArgumentInitializationInfo() {
    return true;
  }

  @Override
  public InstanceFieldArgumentInitializationInfo asArgumentInitializationInfo() {
    return this;
  }
}
