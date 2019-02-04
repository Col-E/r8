// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.b123730538.runner;

import com.android.tools.r8.resolution.b123730538.sub.PublicClass;

public class PublicClassExtender extends PublicClass {
  void delegate() {
    // Method reference should be PublicClassExtender#foo, not the definition AbstractClass#foo
    // because package-private AbstractClass is not visible from this calling context.
    // Otherwise, we will see java.lang.IllegalAccessError.
    foo();
  }
}
