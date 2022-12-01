// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.varhandle;

import java.lang.reflect.Field;

// Template class for desugaring VarHandle into com.android.tools.r8.DesugarVarHandle.
public final class DesugarVarHandle {

  // This only have methods found in libcore/libart/src/main/java/sun/misc/Unsafe.java for Lollipop.
  private static class UnsafeStub {

    public long objectFieldOffset(Field f) {
      throw new RuntimeException("Stub called.");
    }
  }

  private final UnsafeStub U;
  private final Class<?> recv;
  private final Class<?> type;
  private final long offset;

  DesugarVarHandle(Class<?> recv, String name, Class<?> type)
      throws NoSuchFieldException, IllegalAccessException {
    Field theUnsafe = UnsafeStub.class.getDeclaredField("theUnsafe");
    theUnsafe.setAccessible(true);
    U = (UnsafeStub) theUnsafe.get(null);
    this.recv = recv;
    Field field = recv.getDeclaredField(name);
    this.type = field.getType();
    this.offset = U.objectFieldOffset(recv.getDeclaredField(name));
  }

  // get variants.
  Object get(Object ct1) {
    // TODO(b/247076137): Implement.
    return null;
  }

  int getInt(Object ct1) {
    // TODO(b/247076137): Implement.
    return -1;
  }

  long getLong(Object ct1) {
    // TODO(b/247076137): Implement.
    return -1L;
  }

  // set variants.
  void set(Object ct1, Object newValue) {
    // TODO(b/247076137): Implement.
  }

  void setInt(Object ct1, int newValue) {
    // TODO(b/247076137): Implement.
  }

  void setLong(Object ct1, long newValue) {
    // TODO(b/247076137): Implement.
  }

  boolean compareAndSet(Object ct1, Object expectedValue, Object newValue) {
    // TODO(b/247076137): Implement.
    return false;
  }

  boolean compareAndSetInt(Object ct1, int expectedValue, int newValue) {
    // TODO(b/247076137): Implement.
    return false;
  }

  boolean compareAndSetLong(Object ct1, long expectedValue, long newValue) {
    // TODO(b/247076137): Implement.
    return false;
  }
}
