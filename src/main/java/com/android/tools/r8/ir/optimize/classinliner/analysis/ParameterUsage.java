// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;

public abstract class ParameterUsage {

  abstract ParameterUsage addCastWithParameter(DexType castType);

  abstract ParameterUsage addFieldReadFromParameter(DexField field);

  abstract ParameterUsage addMethodCallWithParameterAsReceiver(InvokeMethodWithReceiver invoke);

  public NonEmptyParameterUsage asNonEmpty() {
    return null;
  }

  InternalNonEmptyParameterUsage asInternalNonEmpty() {
    return null;
  }

  abstract ParameterUsage externalize();

  /**
   * Returns true if this is an instanceof {@link BottomParameterUsage}.
   *
   * <p>Note that this does NOT imply that the parameter is <i>unused</i>, but only that it is
   * always eligible for class inlining.
   */
  public boolean isBottom() {
    return false;
  }

  /**
   * Returns true if the method may mutate the state of this parameter (i.e., mutate the value of
   * one of its instance fields).
   */
  public abstract boolean isParameterMutated();

  /**
   * Returns true if the method <i>may</i> return the parameter.
   *
   * <p>Note that this does NOT imply that the method <i>always</i> returns the method.
   */
  public abstract boolean isParameterReturned();

  /**
   * Returns true if the parameter may be used as a lock (i.e., may flow into a monitor
   * instruction).
   */
  public abstract boolean isParameterUsedAsLock();

  /**
   * Returns true if this is an instance of {@link UnknownParameterUsage}.
   *
   * <p>In this case, the parameter is never eligible for class inlining.
   */
  public boolean isTop() {
    return false;
  }

  ParameterUsage join(ParameterUsage parameterUsage) {
    if (isBottom()) {
      return parameterUsage;
    }
    if (parameterUsage.isBottom()) {
      return this;
    }
    if (isTop() || parameterUsage.isTop()) {
      return top();
    }
    return asInternalNonEmpty().join(parameterUsage.asInternalNonEmpty());
  }

  abstract ParameterUsage setParameterMutated();

  abstract ParameterUsage setParameterReturned();

  abstract ParameterUsage setParameterUsedAsLock();

  public static BottomParameterUsage bottom() {
    return BottomParameterUsage.getInstance();
  }

  public static UnknownParameterUsage top() {
    return UnknownParameterUsage.getInstance();
  }
}
