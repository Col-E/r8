// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.varhandle;

import java.lang.reflect.Field;

// Template class for desugaring VarHandle into com.android.tools.r8.DesugarVarHandle.
public final class DesugarVarHandle {

  // This only have methods found in libcore/libart/src/main/java/sun/misc/Unsafe.java for Lollipop.
  public static class UnsafeStub {

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

    public long getLongVolatile(Object obj, long offset) {
      throw new RuntimeException("Stub called.");
    }

    public Object getObjectVolatile(Object obj, long offset) {
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
  private final long arrayIndexScale;

  DesugarVarHandle(Class<?> recv, String name, Class<?> type)
      throws NoSuchFieldException, IllegalAccessException {
    Field theUnsafe = UnsafeStub.class.getDeclaredField("theUnsafe");
    theUnsafe.setAccessible(true);
    U = (UnsafeStub) theUnsafe.get(null);
    this.recv = recv;
    Field field = recv.getDeclaredField(name);
    this.type = field.getType();
    if (type.isPrimitive() && type != int.class && type != long.class) {
      throw new UnsupportedOperationException(
          "Using a VarHandle for a field of type '"
              + type.getName()
              + "' requires native VarHandle support available from Android 13. "
              + "VarHandle desugaring only supports primitive types int and long and "
              + "reference types.");
    }
    this.offset = U.objectFieldOffset(recv.getDeclaredField(name));
    this.arrayIndexScale = 0;
  }

  DesugarVarHandle(Class<?> arrayType) throws Exception {
    Field theUnsafe = UnsafeStub.class.getDeclaredField("theUnsafe");
    theUnsafe.setAccessible(true);
    U = (UnsafeStub) theUnsafe.get(null);
    if (!arrayType.isArray()) {
      throw new IllegalArgumentException("not an array " + arrayType.getSimpleName());
    }
    Class<?> componentType = arrayType.getComponentType();
    if (componentType.isArray()) {
      throw new UnsupportedOperationException(
          "Using a VarHandle for a multidimensional array " + arrayRequiringNativeSupport());
    }
    if (componentType.isPrimitive() && componentType != int.class && componentType != long.class) {
      throw new UnsupportedOperationException(
          "Using a VarHandle for an array of type '"
              + componentType.getName()
              + "' "
              + arrayRequiringNativeSupport());
    }
    this.recv = arrayType;
    this.type = arrayType.getComponentType();
    this.offset = U.arrayBaseOffset(recv);
    this.arrayIndexScale = U.arrayIndexScale(recv);
  }

  // Helpers.
  String arrayRequiringNativeSupport() {
    return "requires native VarHandle support available from Android 13. "
        + "VarHandle desugaring only supports single dimensional arrays of primitive types"
        + "int and long and reference types.";
  }

  RuntimeException desugarWrongMethodTypeException() {
    return new RuntimeException("java.lang.invoke.WrongMethodTypeException");
  }

  int toIntIfPossible(Object value, boolean forReturnType) {
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
    if (forReturnType) {
      throw new ClassCastException();
    } else {
      throw desugarWrongMethodTypeException();
    }
  }

  long toLongIfPossible(Object value, boolean forReturnType) {
    if (value instanceof Long) {
      return (Long) value;
    }
    return toIntIfPossible(value, forReturnType);
  }

  Object boxIntIfPossible(int value, Class<?> expectedBox) {
    if (expectedBox == Long.class) {
      return Long.valueOf(value);
    }
    if (expectedBox == Float.class) {
      return Float.valueOf(value);
    }
    if (expectedBox == Double.class) {
      return Double.valueOf(value);
    }
    throw desugarWrongMethodTypeException();
  }

  Object boxLongIfPossible(long value, Class<?> expectedBox) {
    if (expectedBox == Float.class) {
      return Float.valueOf(value);
    }
    if (expectedBox == Double.class) {
      return Double.valueOf(value);
    }
    throw desugarWrongMethodTypeException();
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

  Object getInBox(Object ct1, Class<?> expectedBox) {
    if (type == int.class) {
      return boxIntIfPossible(U.getInt(ct1, offset), expectedBox);
    }
    if (type == long.class) {
      return boxLongIfPossible(U.getLong(ct1, offset), expectedBox);
    }
    return U.getObject(ct1, offset);
  }

  int getInt(Object ct1) {
    if (type == int.class) {
      return U.getInt(ct1, offset);
    } else if (type == long.class) {
      throw desugarWrongMethodTypeException();
    } else {
      return toIntIfPossible(U.getObject(ct1, offset), true);
    }
  }

  long getLong(Object ct1) {
    if (type == long.class) {
      return U.getLong(ct1, offset);
    } else if (type == int.class) {
      return U.getInt(ct1, offset);
    } else {
      return toLongIfPossible(U.getObject(ct1, offset), true);
    }
  }

  // set variants.
  void set(Object ct1, Object newValue) {
    if (type == int.class) {
      setInt(ct1, toIntIfPossible(newValue, false));
    } else if (type == long.class) {
      setLong(ct1, toLongIfPossible(newValue, false));
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
    } else if (type == int.class) {
      throw desugarWrongMethodTypeException();
    } else {
      U.putObject(ct1, offset, Long.valueOf(newValue));
    }
  }

  // getVolatile variants.
  Object getVolatile(Object ct1) {
    if (type == int.class) {
      return U.getIntVolatile(ct1, offset);
    }
    if (type == long.class) {
      return U.getLongVolatile(ct1, offset);
    }
    return U.getObjectVolatile(ct1, offset);
  }

  Object getVolatileInBox(Object ct1, Class<?> expectedBox) {
    if (type == int.class) {
      return boxIntIfPossible(U.getIntVolatile(ct1, offset), expectedBox);
    }
    if (type == long.class) {
      return boxLongIfPossible(U.getLongVolatile(ct1, offset), expectedBox);
    }
    return U.getObjectVolatile(ct1, offset);
  }

  int getVolatileInt(Object ct1) {
    if (type == int.class) {
      return U.getIntVolatile(ct1, offset);
    } else if (type == long.class) {
      throw desugarWrongMethodTypeException();
    } else {
      return toIntIfPossible(U.getObjectVolatile(ct1, offset), true);
    }
  }

  long getVolatileLong(Object ct1) {
    if (type == long.class) {
      return U.getLongVolatile(ct1, offset);
    } else if (type == int.class) {
      return U.getIntVolatile(ct1, offset);
    } else {
      return toLongIfPossible(U.getObjectVolatile(ct1, offset), true);
    }
  }

  boolean compareAndSet(Object ct1, Object expectedValue, Object newValue) {
    if (type == int.class) {
      return U.compareAndSwapInt(
          ct1, offset, toIntIfPossible(expectedValue, false), toIntIfPossible(newValue, false));
    }
    if (type == long.class) {
      return U.compareAndSwapLong(
          ct1, offset, toLongIfPossible(expectedValue, false), toLongIfPossible(newValue, false));
    }
    return U.compareAndSwapObject(ct1, offset, expectedValue, newValue);
  }

  boolean compareAndSetInt(Object ct1, int expectedValue, int newValue) {
    if (type == int.class) {
      return U.compareAndSwapInt(ct1, offset, expectedValue, newValue);
    } else if (type == long.class) {
      return U.compareAndSwapLong(ct1, offset, expectedValue, newValue);
    } else {
      return compareAndSet(ct1, expectedValue, newValue);
    }
  }

  boolean compareAndSetLong(Object ct1, long expectedValue, long newValue) {
    if (type == long.class) {
      return U.compareAndSwapLong(ct1, offset, expectedValue, newValue);
    }
    return compareAndSet(ct1, expectedValue, newValue);
  }

  // get array variants.
  Object getArray(Object ct1, int ct2) {
    if (!recv.isArray() || recv != ct1.getClass()) {
      throw new UnsupportedOperationException();
    }
    long elementOffset = offset + ((long) ct2) * arrayIndexScale;
    if (type == int.class) {
      return U.getInt(ct1, elementOffset);
    } else if (type == long.class) {
      return (int) U.getLong(ct1, elementOffset);
    } else {
      return U.getObject(ct1, elementOffset);
    }
  }

  Object getArrayInBox(Object ct1, int ct2, Class<?> expectedBox) {
    if (!recv.isArray() || recv != ct1.getClass()) {
      throw new UnsupportedOperationException();
    }
    long elementOffset = offset + ((long) ct2) * arrayIndexScale;
    if (type == int.class) {
      return boxIntIfPossible(U.getInt(ct1, elementOffset), expectedBox);
    } else if (type == long.class) {
      return boxLongIfPossible(U.getLong(ct1, elementOffset), expectedBox);
    } else {
      Object value = U.getObject(ct1, elementOffset);
      if (value instanceof Integer && expectedBox != Integer.class) {
        return boxIntIfPossible(((Integer) value).intValue(), expectedBox);
      }
      if (value instanceof Long && expectedBox != Long.class) {
        return boxLongIfPossible(((Long) value).longValue(), expectedBox);
      }
      return value;
    }
  }

  int getArrayInt(int[] ct1, int ct2) {
    if (recv != int[].class) {
      throw new UnsupportedOperationException();
    }
    long elementOffset = offset + ((long) ct2) * arrayIndexScale;
    return U.getInt(ct1, elementOffset);
  }

  long getArrayLong(long[] ct1, int ct2) {
    if (recv != long[].class) {
      throw new UnsupportedOperationException();
    }
    long elementOffset = offset + ((long) ct2) * arrayIndexScale;
    return U.getLong(ct1, elementOffset);
  }

  // getVolatile array variants.
  Object getVolatileArray(Object ct1, int ct2) {
    if (!recv.isArray() || recv != ct1.getClass()) {
      throw new UnsupportedOperationException();
    }
    long elementOffset = offset + ((long) ct2) * arrayIndexScale;
    if (type == int.class) {
      return U.getIntVolatile(ct1, elementOffset);
    } else if (type == long.class) {
      return (int) U.getLongVolatile(ct1, elementOffset);
    } else {
      return U.getObjectVolatile(ct1, elementOffset);
    }
  }

  Object getVolatileArrayInBox(Object ct1, int ct2, Class<?> expectedBox) {
    if (!recv.isArray() || recv != ct1.getClass()) {
      throw new UnsupportedOperationException();
    }
    long elementOffset = offset + ((long) ct2) * arrayIndexScale;
    if (type == int.class) {
      return boxIntIfPossible(U.getIntVolatile(ct1, elementOffset), expectedBox);
    } else if (type == long.class) {
      return boxLongIfPossible(U.getLongVolatile(ct1, elementOffset), expectedBox);
    } else {
      Object value = U.getObjectVolatile(ct1, elementOffset);
      if (value instanceof Integer && expectedBox != Integer.class) {
        return boxIntIfPossible(((Integer) value).intValue(), expectedBox);
      }
      if (value instanceof Long && expectedBox != Long.class) {
        return boxLongIfPossible(((Long) value).longValue(), expectedBox);
      }
      return value;
    }
  }

  int getVolatileArrayInt(int[] ct1, int ct2) {
    if (recv != int[].class) {
      throw new UnsupportedOperationException();
    }
    long elementOffset = offset + ((long) ct2) * arrayIndexScale;
    return U.getIntVolatile(ct1, elementOffset);
  }

  long getVolatileArrayLong(long[] ct1, int ct2) {
    if (recv != long[].class) {
      throw new UnsupportedOperationException();
    }
    long elementOffset = offset + ((long) ct2) * arrayIndexScale;
    return U.getLongVolatile(ct1, elementOffset);
  }

  // set array variants.
  void setArray(Object ct1, int ct2, Object newValue) {
    if (!recv.isArray() || recv != ct1.getClass()) {
      throw new UnsupportedOperationException();
    }
    long elementOffset = offset + ((long) ct2) * arrayIndexScale;
    if (recv == int[].class) {
      U.putInt(ct1, elementOffset, toIntIfPossible(newValue, false));
    } else if (recv == long[].class) {
      U.putLong(ct1, elementOffset, toLongIfPossible(newValue, false));
    } else {
      U.putObject(ct1, elementOffset, newValue);
    }
  }

  void setArrayInt(int[] ct1, int ct2, int newValue) {
    if (recv != int[].class) {
      throw new UnsupportedOperationException();
    }
    long elementOffset = offset + ((long) ct2) * arrayIndexScale;
    U.putInt(ct1, elementOffset, newValue);
  }

  void setArrayLong(long[] ct1, int ct2, long newValue) {
    if (recv != long[].class) {
      throw new UnsupportedOperationException();
    }
    long elementOffset = offset + ((long) ct2) * arrayIndexScale;
    U.putLong(ct1, elementOffset, newValue);
  }

  // compareAndSet array variants.
  boolean compareAndSetArray(Object ct1, int ct2, Object expetedValue, Object newValue) {
    if (!recv.isArray() || recv != ct1.getClass()) {
      throw new UnsupportedOperationException();
    }
    long elementOffset = offset + ((long) ct2) * arrayIndexScale;
    if (recv == int[].class) {
      return U.compareAndSwapInt(
          ct1,
          elementOffset,
          toIntIfPossible(expetedValue, false),
          toIntIfPossible(newValue, false));
    } else if (recv == long[].class) {
      return U.compareAndSwapLong(
          ct1,
          elementOffset,
          toLongIfPossible(expetedValue, false),
          toLongIfPossible(newValue, false));
    } else {
      return U.compareAndSwapObject(ct1, elementOffset, expetedValue, newValue);
    }
  }

  boolean compareAndSetArrayInt(int[] ct1, int ct2, int expetedValue, int newValue) {
    if (recv != int[].class) {
      throw new UnsupportedOperationException();
    }
    long elementOffset = offset + ((long) ct2) * arrayIndexScale;
    return U.compareAndSwapInt(ct1, elementOffset, expetedValue, newValue);
  }

  boolean compareAndSetArrayLong(long[] ct1, int ct2, long expetedValue, long newValue) {
    if (recv != long[].class) {
      throw new UnsupportedOperationException();
    }
    long elementOffset = offset + ((long) ct2) * arrayIndexScale;
    return U.compareAndSwapLong(ct1, elementOffset, expetedValue, newValue);
  }
}
