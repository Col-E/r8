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

    public boolean compareAndSwapInt(Object obj, long offset, int expectedValue, int newValue) {
      throw new RuntimeException("Stub called.");
    }

    public boolean compareAndSwapLong(Object obj, long offset, long expectedValue, long newValue) {
      throw new RuntimeException("Stub called.");
    }

    public boolean compareAndSwapObject(
        Object receiver, long offset, Object expect, Object update) {
      throw new RuntimeException("Stub called.");
    }

    public int getInt(Object obj, long offset) {
      throw new RuntimeException("Stub called.");
    }

    public void putInt(Object obj, long offset, int newValue) {
      throw new RuntimeException("Stub called.");
    }

    public long getLong(Object obj, long offset) {
      throw new RuntimeException("Stub called.");
    }

    public void putLong(Object obj, long offset, long newValue) {
      throw new RuntimeException("Stub called.");
    }

    public Object getObject(Object receiver, long offset) {
      throw new RuntimeException("Stub called.");
    }

    public void putObject(Object obj, long offset, Object newValue) {
      throw new RuntimeException("Stub called.");
    }

    public int getIntVolatile(Object obj, long offset) {
      throw new RuntimeException("Stub called.");
    }

    public int arrayBaseOffset(Class<?> clazz) {
      throw new RuntimeException("Stub called.");
    }

    public int arrayIndexScale(Class<?> clazz) {
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

  DesugarVarHandle(Class<?> arrayType) throws Exception {
    Field theUnsafe = UnsafeStub.class.getDeclaredField("theUnsafe");
    theUnsafe.setAccessible(true);
    U = (UnsafeStub) theUnsafe.get(null);
    this.recv = arrayType;
    this.type = arrayType.getComponentType();
    this.offset = U.arrayBaseOffset(recv);
  }

  // Helpers.
  RuntimeException desugarWrongMethodTypeException() {
    return new RuntimeException("java.lang.invoke.WrongMethodTypeException");
  }

  int toIntIfPossible(Object value) {
    if (value instanceof Integer) {
      return (Integer) value;
    }
    if (value instanceof Byte) {
      return (Byte) value;
    }
    if (value instanceof Character) {
      return (Character) value;
    }
    if (value instanceof Short) {
      return (Short) value;
    }
    throw desugarWrongMethodTypeException();
  }

  long toLongIfPossible(Object value) {
    if (value instanceof Long) {
      return (Long) value;
    }
    return toIntIfPossible(value);
  }

  // get variants.
  Object get(Object ct1) {
    if (type == int.class) {
      return U.getInt(ct1, offset);
    }
    if (type == long.class) {
      return U.getLong(ct1, offset);
    }
    return U.getObject(ct1, offset);
  }

  int getInt(Object ct1) {
    return U.getInt(ct1, offset);
  }

  long getLong(Object ct1) {
    // TODO(b/247076137): Implement.
    return -1L;
  }

  // set variants.
  void set(Object ct1, Object newValue) {
    if (type == int.class) {
      setInt(ct1, toIntIfPossible(newValue));
    } else {
      U.putObject(ct1, offset, newValue);
    }
  }

  void setInt(Object ct1, int newValue) {
    if (type == int.class) {
      U.putInt(ct1, offset, newValue);
    } else if (type == long.class) {
      U.putLong(ct1, offset, newValue);
    } else {
      set(ct1, newValue);
    }
  }

  void setLong(Object ct1, long newValue) {
    if (type == long.class) {
      U.putLong(ct1, offset, newValue);
    } else {
      throw desugarWrongMethodTypeException();
    }
  }

  boolean compareAndSet(Object ct1, Object expetedValue, Object newValue) {
    if (type == int.class) {
      return U.compareAndSwapInt(
          ct1, offset, toIntIfPossible(expetedValue), toIntIfPossible((newValue)));
    }
    if (type == long.class) {
      return U.compareAndSwapLong(
          ct1, offset, toLongIfPossible(expetedValue), toLongIfPossible((newValue)));
    }
    return U.compareAndSwapObject(ct1, offset, expetedValue, newValue);
  }

  boolean compareAndSetInt(Object ct1, int expetedValue, int newValue) {
    if (type == int.class) {
      return U.compareAndSwapInt(ct1, offset, expetedValue, newValue);
    } else if (type == long.class) {
      return U.compareAndSwapLong(ct1, offset, expetedValue, newValue);
    } else {
      return compareAndSet(ct1, expetedValue, newValue);
    }
  }

  boolean compareAndSetLong(Object ct1, long expetedValue, long newValue) {
    if (type == long.class) {
      return U.compareAndSwapLong(ct1, offset, expetedValue, newValue);
    }
    return compareAndSet(ct1, expetedValue, newValue);
  }
}
