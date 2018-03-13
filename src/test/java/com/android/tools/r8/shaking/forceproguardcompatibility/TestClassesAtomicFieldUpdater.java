// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.forceproguardcompatibility;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

class TestClassWithVolatileFields {
  volatile int intField;
  volatile long longField;
  volatile Object objField;
}

class TestMainWithAtomicFieldUpdater {
  public static void main(String[] args) throws Exception {
    TestClassWithVolatileFields instance = new TestClassWithVolatileFields();
    AtomicIntegerFieldUpdater<TestClassWithVolatileFields> iUpdater =
        AtomicIntegerFieldUpdater.newUpdater(TestClassWithVolatileFields.class, "intField");
    iUpdater.set(instance, 8);

    AtomicLongFieldUpdater<TestClassWithVolatileFields> lUpdater =
        AtomicLongFieldUpdater.newUpdater(TestClassWithVolatileFields.class, "longField");
    lUpdater.set(instance, 8L);

    AtomicReferenceFieldUpdater<TestClassWithVolatileFields, Object> oUpdater =
        AtomicReferenceFieldUpdater.newUpdater(
            TestClassWithVolatileFields.class, Object.class, "objField");
    oUpdater.set(instance, null);
  }
}
