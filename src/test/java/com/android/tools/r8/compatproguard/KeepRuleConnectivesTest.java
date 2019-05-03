// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compatproguard;

public class KeepRuleConnectivesTest {

  public class JustFoo {
    public long foo() { return System.nanoTime(); }
  }

  public class JustBar {
    public long bar() { return System.nanoTime(); }
  }

  public class BothFooAndBar {
    public long foo() { return System.nanoTime(); }
    public long bar() { return System.nanoTime(); }
  }

}
