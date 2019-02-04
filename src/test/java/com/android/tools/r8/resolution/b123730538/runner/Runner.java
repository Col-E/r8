// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.b123730538.runner;

public class Runner {

  static int counter = 0;

  static Object create() {
    if (counter++ % 2 == 0) {
      return new PublicClassExtender();
    } else {
      return new AnotherPublicClassExtender();
    }
  }

  public static void main(String[] args) {
    Object instance = create();
    if (instance instanceof PublicClassExtender) {
      ((PublicClassExtender) instance).delegate();
    } else {
      ((AnotherPublicClassExtender) instance).delegate();
    }
  }
}
