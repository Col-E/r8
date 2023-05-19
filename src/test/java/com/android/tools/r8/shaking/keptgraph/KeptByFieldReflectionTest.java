// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import com.android.tools.r8.Keep;
import com.android.tools.r8.NoInliningOfDefaultInitializer;

@Keep
@NoInliningOfDefaultInitializer
public class KeptByFieldReflectionTest {

  public int foo = 42;

  public static void main(String[] args) throws Exception {
    // Due to b/123210548 the object cannot be created by a reflective newInstance call.
    KeptByFieldReflectionTest obj = new KeptByFieldReflectionTest();
    System.out.println("got foo: " + KeptByFieldReflectionTest.class.getField("foo").getInt(obj));
  }
}
