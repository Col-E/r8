// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.field;

/**
 * Information about the way a constructor initializes an instance field on the newly created
 * instance.
 *
 * <p>For example, this can be used to represent that a constructor always initializes a particular
 * instance field with a constant, or with an argument from the constructor's argument list.
 */
public abstract class InstanceFieldInitializationInfo {

  public boolean isArgumentInitializationInfo() {
    return false;
  }

  public InstanceFieldArgumentInitializationInfo asArgumentInitializationInfo() {
    return null;
  }

  public boolean isUnknown() {
    return false;
  }
}
