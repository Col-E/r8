// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.overloadaggressively;

import java.util.Random;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class Main {
  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws Exception {
    A a = new A();
    B b = new B();
    AtomicReferenceFieldUpdater f3Updater =
        AtomicReferenceFieldUpdater.newUpdater(A.class, B.class, "f3");
    f3Updater.set(a, b);
    AtomicReferenceFieldUpdater f2Updater =
        AtomicReferenceFieldUpdater.newUpdater(A.class, Object.class, "f2");
    f2Updater.set(a, b);
    assert a.f2 instanceof B;
    assert a.f2 == a.f3;
    ((B) a.f2).f2 = a;
    assert b.f2 instanceof A;
    assert ((A) b.f2).f2 == b;

    Random random = new Random();
    int next = random.nextInt();
    AtomicIntegerFieldUpdater f1Updater =
        AtomicIntegerFieldUpdater.newUpdater(A.class, "f1");
    f1Updater.set(a, next);
    B viaF3 = a.f3;
    viaF3.f1 = next;
    int diff = viaF3.f1 - a.f1;
    System.out.println("diff: " + diff);
  }
}
