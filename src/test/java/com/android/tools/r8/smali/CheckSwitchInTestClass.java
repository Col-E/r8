// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.smali;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class CheckSwitchInTestClass {
  public static void main(String[] args) throws Exception {
    // Load the generated Jasmin class, and get the test method.
    Class<?> test = CheckSwitchInTestClass.class.getClassLoader().loadClass("Test");
    Method method = test.getMethod("test", int.class);

    // Get keys and default value from arguments.
    List<Integer> keys = new ArrayList<>();
    for (int i = 0; i < args.length - 1; i++) {
      keys.add(Integer.parseInt(args[i]));
    }
    int defaultValue = Integer.parseInt(args[args.length - 1]);

    // Run over all keys and test a small interval around each.
    long delta = 2;
    for (Integer key : keys) {
      for (long potential = key - delta; potential < key + delta; potential++) {
        if (Integer.MIN_VALUE <= potential && potential <= Integer.MAX_VALUE) {
          int testKey = (int) potential;
          int result = ((Integer) method.invoke(null, testKey));
          int expect = defaultValue;
          if (keys.contains(testKey)) {
            expect = testKey;
          }
          if (result != expect) {
            System.out.println("Expected " + expect + " but got " + result);
            System.exit(1);
          }
        }
      }
    }
  }
}
