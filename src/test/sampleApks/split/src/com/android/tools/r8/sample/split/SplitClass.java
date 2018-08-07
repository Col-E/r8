// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.sample.split;

import com.android.tools.r8.sample.split.R8Activity;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import com.google.common.collect.ConcurrentHashMultiset;

public class SplitClass {
  int initialValue;

  public SplitClass(int initialValue) {
    this.initialValue = initialValue;
  }

  public int calculate(int x) {
    int result = 2;
    for (int i = 0; i < 42; i++) {
      result += initialValue + x;
    }
    return result;
  }

  public int guava(int iterations) {
    for (int i = 0; i < iterations; i++) {
      int result = 0;
      ImmutableList<String> a = ImmutableList.of(
          "foo",
          "bar",
          "foobar",
          "last");
      if (a.contains("foobar")) {
        result++;
      }
      if (a.subList(0, 2).contains("last")) {
        throw new RuntimeException("WAT");
      }
      result = a.size();
      Multiset<String> set = ConcurrentHashMultiset.create();
      for (int j = 0; j < 100; j++) {
        set.add(a.get(j%4));
      }
      for (String s : a) {
        result += set.count(s);
      }
    }
    return 42;

  }

  public int largeMethod(int x, int y) {
    int a = x + y;
    int b;
    int c;
    double d;
    String s;
    if (a < 22) {
      b = a * 42 / y;
      c = b - 80;
      d = a + b + c * y - x * a;
      s = "foobar";
    } else if (a < 42) {
      b = x * 42 / y;
      c = b - 850;
      d = a + b + c * x - x * a;
      s = "foo";
    } else {
      b = x * 42 / y;
      c = b - 850;
      d = a + b + c * x - x * a;
      s = "bar";
    }
    if (s.equals("bar")) {
      d = 49;
      s += b;
    } else {
      b = 63;
      s = "barbar" + b;
    }

    if (a < 22) {
      b = a * 42 / y;
      c = b - 80;
      d = a + b + c * y - x * a;
      s = "foobar";
    } else if (a < 42) {
      b = x * 42 / y;
      c = b - 850;
      d = a + b + c * x - x * a;
      s = "foo";
    } else {
      b = x * 42 / y;
      c = b - 850;
      d = a + b + c * x - x * a;
      s = "bar";
    }
    if (s.equals("bar")) {
      d = 49;
      s += b;
    } else {
      b = 63;
      s = "barbar" + b;
    }

    if (a < 22) {
      b = a * 42 / y;
      c = b - 80;
      d = a + b + c * y - x * a;
      s = "foobar";
    } else if (a < 42) {
      b = x * 42 / y;
      c = b - 850;
      d = a + b + c * x - x * a;
      s = "foo";
    } else {
      b = x * 42 / y;
      c = b - 850;
      d = a + b + c * x - x * a;
      s = "bar";
    }
    if (s.equals("bar")) {
      d = 49;
      s += b;
    } else {
      b = 63;
      s = "barbar" + b;
    }

    return a + b - c * x;
  }

  public int callSplitLocal() {
    SplitClass split = new SplitClass(initialValue);
    for (int i = 0; i < R8Activity.ITERATIONS / R8Activity.SPLITS; i++) {
      // Ensure no dead code elimination.
      initialValue = split.calculate(i);
    }
    return initialValue;
  }


  public int callBase() {
    BaseClass base = new BaseClass(initialValue);
    for (int i = 0; i < R8Activity.ITERATIONS / R8Activity.SPLITS; i++) {
      // Ensure no dead code elimination.
      initialValue = base.calculate(i);
    }
    return initialValue;
  }

  public int callBaseLarge() {
    BaseClass base = new BaseClass(initialValue);
    for (int i = 0; i < R8Activity.ITERATIONS / R8Activity.SPLITS; i++) {
      // Ensure no dead code elimination.
      initialValue = base.largeMethod(i, i + 1);
    }
    return initialValue;
  }

}
