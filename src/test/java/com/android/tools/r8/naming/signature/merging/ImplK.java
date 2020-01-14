// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.signature.merging;

public class ImplK implements K {

  @Override
  public void foo() {
    System.out.println("ImplK.foo");
  }

  @Override
  public void bar() {
    System.out.println("ImplK.bar");
  }
}
