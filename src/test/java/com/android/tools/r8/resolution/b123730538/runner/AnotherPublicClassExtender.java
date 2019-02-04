// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.b123730538.runner;

import com.android.tools.r8.resolution.b123730538.sub.AnotherPublicClass;

// To make AbstractPublicClass not mergeable for both R8 and Proguard.
class AnotherPublicClassExtender extends AnotherPublicClass {
  void delegate() {
    foo();
  }
}
