// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.nonnull;

public class NonNullParamAfterInvokeInterfaceMain {

  public static void main(String[] args) {
    NotPinnedClass arg1 = new NotPinnedClass(1);
    NotPinnedClass arg2 = new NotPinnedClass(2);
    NonNullParamAfterInvokeInterface instance = new NonNullParamAfterInvokeInterface();
    NonNullParamInterfaceImpl receiver = new NonNullParamInterfaceImpl();
    instance.checkViaCall(receiver, arg1, arg2);
  }
}
