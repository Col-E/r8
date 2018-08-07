// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.sample.split;

import android.app.Activity;
import android.os.Bundle;
import com.android.tools.r8.sample.split.R;
import com.android.tools.r8.sample.split.SplitClass;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import com.google.common.collect.ConcurrentHashMultiset;
import java.util.ArrayList;
import java.util.List;

/**
 * Benchmarking of splits. Generally the naming is like this:
 * Base -> Split (no postfix)
 * Base -> Base (Local, to get the corresponding performance if no split involved)
 * Split -> Split (SplitLocal)
 * Split -> Base (SplitCallback)
 * Base no call overhead (Baseline)
 * In most of these cases we have the methods duplicated between the SplitClass and this class.
 * This is to enable output of the split and not split version of the code without having to
 * run in two different build configurations (i.e., with and without using split code)
 */
public class R8Activity extends Activity {
  // Enables easy splitting of iterations to better see effect of jit in later versions of art
  public static final int ITERATIONS = 100000;
  public static final int SPLITS = 1;

  private int res = 0;

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setTheme(android.R.style.Theme_Light);
    setContentView(R.layout.main);
    long total = 0;
    for (int i = 0; i < SPLITS; i++) {
      total += benchmarkCallBaseline();
    }
    System.out.println("CallBaseline Total: " + total);

    total = 0;
    for (int i = 0; i < SPLITS; i++) {
      total += benchmarkCall();
    }
    System.out.println("Call Total: " + total);


    total = 0;
    for (int i = 0; i < SPLITS; i++) {
      total += benchmarkCallSplitLocal();
    }
    System.out.println("CallSplitLocal Total: " + total);


    total = 0;
    for (int i = 0; i < SPLITS; i++) {
      total += benchmarkCallLocal();
    }
    System.out.println("CallLocal Total: " + total);

    total = 0;
    for (int i = 0; i < SPLITS; i++) {
      total += benchmarkSplitCallback();
    }
    System.out.println("Callback Total: " + total);

    total = 0;
    for (int i = 0; i < SPLITS; i++) {
      total += benchmarkConstructor();
    }
    System.out.println("Constructor Total: " + total);

    total = 0;
    for (int i = 0; i < SPLITS; i++) {
      total += benchmarkConstructorLocal();
    }
    System.out.println("ConstructorLocal Total: " + total);

    total = 0;
    for (int i = 0; i < SPLITS; i++) {
      total += benchmarkInheritanceConstructor();
    }
    System.out.println("InheritanceConstructor Total: " + total);

    total = 0;
    for (int i = 0; i < SPLITS; i++) {
      total += benchmarkLargeMethodCall();
    }
    System.out.println("LargeMethodCall Total: " + total);

    total = 0;
    for (int i = 0; i < SPLITS; i++) {
      total += benchmarkSplitCallbackLong();
    }
    System.out.println("CallbackLarge Total: " + total);

    total = 0;
    for (int i = 0; i < SPLITS; i++) {
      total += benchmarkLargeMethodCallLocally();
    }
    System.out.println("LargeMethodCallLocal Total: " + total);

    total = 0;
    for (int i = 0; i < SPLITS; i++) {
      total += benchmarkGuava();
    }
    System.out.println("Guava Total: " + total);


    total = 0;
    for (int i = 0; i < SPLITS; i++) {
      total += benchmarkGuavaLocal();
    }
    System.out.println("GuavaLocal Total: " + total);

  }

  private long benchmarkCall() {
    SplitClass split = new SplitClass(3);
    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS / SPLITS; i++) {
      // Ensure no dead code elimination.
      res = split.calculate(i);
    }
    long finish = System.nanoTime();
    long timeElapsed = (finish - start) / 1000;
    return timeElapsed;
  }

  private long benchmarkGuava() {
    SplitClass split = new SplitClass(3);
    long start = System.nanoTime();
    res = split.guava(ITERATIONS / SPLITS / 10);
    long finish = System.nanoTime();
    long timeElapsed = (finish - start) / 1000;
    return timeElapsed;
  }

  private long benchmarkGuavaLocal() {
    long start = System.nanoTime();
    res = guava(ITERATIONS / SPLITS / 10);
    long finish = System.nanoTime();
    long timeElapsed = (finish - start) / 1000;
    return timeElapsed;
  }

  private long benchmarkCallLocal() {
    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS / SPLITS; i++) {
      // Ensure no dead code elimination.
      res = calculate(i);
    }
    long finish = System.nanoTime();
    long timeElapsed = (finish - start) / 1000;
    return timeElapsed;
  }

  private long benchmarkCallSplitLocal() {
    SplitClass split = new SplitClass(3);
    long start = System.nanoTime();
    split.callSplitLocal();
    long finish = System.nanoTime();
    long timeElapsed = (finish - start) / 1000;
    return timeElapsed;
  }

  private long benchmarkCallBaseline() {
    SplitClass split = new SplitClass(3);
    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS / SPLITS; i++) {
      int result = 2;
      for (int j = 0; j < 42; j++) {
        result += result + i;
      }
      res = result;
    }
    long finish = System.nanoTime();
    long timeElapsed = (finish - start) / 1000;
    return timeElapsed;
  }

  private long benchmarkInheritanceConstructor() {
    List<SplitInheritBase> instances = new ArrayList<SplitInheritBase>();
    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS / SPLITS; i++) {
      instances.add(new SplitInheritBase(i));
    }
    long finish = System.nanoTime();
    long timeElapsed = (finish - start) / 1000;
    return timeElapsed;
  }

  private long benchmarkConstructor() {
    List<SplitClass> instances = new ArrayList<SplitClass>();
    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS / SPLITS; i++) {
      instances.add(new SplitClass(i));
    }
    long finish = System.nanoTime();
    long timeElapsed = (finish - start) / 1000;
    return timeElapsed;
  }

  private long benchmarkConstructorLocal() {
    List<BaseClass> instances = new ArrayList<BaseClass>();
    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS / SPLITS; i++) {
      instances.add(new BaseClass(i));
    }
    long finish = System.nanoTime();
    long timeElapsed = (finish - start) / 1000;
    return timeElapsed;
  }

  private long benchmarkLargeMethodCall() {
    SplitClass split = new SplitClass(3);
    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS / SPLITS; i++) {
      // Ensure no dead code elimination.
      res = split.largeMethod(i, i + 1);
    }
    long finish = System.nanoTime();
    long timeElapsed = (finish - start) / 1000;
    return timeElapsed;
  }

  private long benchmarkSplitCallback() {
    SplitClass split = new SplitClass(3);
    long start = System.nanoTime();
    res = split.callBase();
    long finish = System.nanoTime();
    long timeElapsed = (finish - start) / 1000;
    return timeElapsed;
  }

  private long benchmarkSplitCallbackLong() {
    SplitClass split = new SplitClass(3);
    long start = System.nanoTime();
    res = split.callBaseLarge();
    long finish = System.nanoTime();
    long timeElapsed = (finish - start) / 1000;
    return timeElapsed;
  }

  private long benchmarkLargeMethodCallLocally() {
    BaseClass base = new BaseClass(3);
    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS / SPLITS; i++) {
      // Ensure no dead code elimination.
      res = base.largeMethod(i, i + 1);
    }
    long finish = System.nanoTime();
    long timeElapsed = (finish - start) / 1000;
    return timeElapsed;
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

  public int calculate(int x) {
    int result = 2;
    for (int i = 0; i < 42; i++) {
      result += res + x;
    }
    return result;
  }
}
