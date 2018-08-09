// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexMethod;
import com.google.common.base.Equivalence;

/**
 * Implements an equivalence on {@link DexMethod} that does not take the holder into account but
 * allow covariant return type: a method in a sub type can return a narrower type.
 */
public class MethodSignatureRelaxedEquivalence extends Equivalence<DexMethod> {

  private final AppInfo appInfo;

  public MethodSignatureRelaxedEquivalence(AppInfo appInfo) {
    this.appInfo = appInfo;
  }

  @Override
  protected boolean doEquivalent(DexMethod a, DexMethod b) {
    return a.name.equals(b.name) && a.proto.parameters.equals(b.proto.parameters)
        && (a.proto.returnType.equals(b.proto.returnType)
            || (a.proto.returnType.isStrictSubtypeOf(b.proto.returnType, appInfo)
                && a.getHolder().isStrictSubtypeOf(b.getHolder(), appInfo))
            || (b.proto.returnType.isStrictSubtypeOf(a.proto.returnType, appInfo)
                && b.getHolder().isStrictSubtypeOf(a.getHolder(), appInfo)));
  }

  @Override
  protected int doHash(DexMethod method) {
    return method.name.hashCode() * 31 + method.proto.parameters.hashCode();
  }
}
