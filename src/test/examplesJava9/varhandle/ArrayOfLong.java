// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class ArrayOfLong {

  private static void checkJavaLangInvokeWrongMethodTypeException(RuntimeException e) {
    if (e.getClass().getCanonicalName().equals("java.lang.invoke.WrongMethodTypeException")
        || e.getMessage().equals("java.lang.invoke.WrongMethodTypeException")) {
      return;
    }
    throw e;
  }

  public static void testGet() {
    System.out.println("testGet");
    VarHandle arrayVarHandle = MethodHandles.arrayElementVarHandle(long[].class);
    long[] array = new long[2];

    arrayVarHandle.set(array, 0, 1L);
    arrayVarHandle.set(array, 1, 2L);

    System.out.println(arrayVarHandle.get(array, 0));
    System.out.println(arrayVarHandle.get(array, 1));
    System.out.println((Object) arrayVarHandle.get(array, 0));
    System.out.println((Object) arrayVarHandle.get(array, 1));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    System.out.println((float) arrayVarHandle.get(array, 0));
    System.out.println((float) arrayVarHandle.get(array, 1));
    System.out.println((double) arrayVarHandle.get(array, 0));
    System.out.println((double) arrayVarHandle.get(array, 1));
    try {
      System.out.println((boolean) arrayVarHandle.get(array, 0));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((boolean) arrayVarHandle.get(array, 1));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((byte) arrayVarHandle.get(array, 0));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((byte) arrayVarHandle.get(array, 1));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((short) arrayVarHandle.get(array, 0));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((short) arrayVarHandle.get(array, 1));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((char) arrayVarHandle.get(array, 0));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((char) arrayVarHandle.get(array, 1));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((int) arrayVarHandle.get(array, 0));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((int) arrayVarHandle.get(array, 1));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((String) arrayVarHandle.get(array, 0));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((String) arrayVarHandle.get(array, 1));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
  }

  public static void testGetVolatile() {
    System.out.println("testGetVolatile");
    VarHandle arrayVarHandle = MethodHandles.arrayElementVarHandle(long[].class);
    long[] array = new long[2];

    arrayVarHandle.set(array, 0, 1L);
    arrayVarHandle.set(array, 1, 2L);

    System.out.println(arrayVarHandle.getVolatile(array, 0));
    System.out.println(arrayVarHandle.getVolatile(array, 1));
    System.out.println((Object) arrayVarHandle.getVolatile(array, 0));
    System.out.println((Object) arrayVarHandle.getVolatile(array, 1));
    System.out.println((long) arrayVarHandle.getVolatile(array, 0));
    System.out.println((long) arrayVarHandle.getVolatile(array, 1));
    System.out.println((float) arrayVarHandle.getVolatile(array, 0));
    System.out.println((float) arrayVarHandle.getVolatile(array, 1));
    System.out.println((double) arrayVarHandle.getVolatile(array, 0));
    System.out.println((double) arrayVarHandle.getVolatile(array, 1));
    try {
      System.out.println((boolean) arrayVarHandle.getVolatile(array, 0));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((boolean) arrayVarHandle.getVolatile(array, 1));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((byte) arrayVarHandle.getVolatile(array, 0));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((byte) arrayVarHandle.getVolatile(array, 1));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((short) arrayVarHandle.getVolatile(array, 0));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((short) arrayVarHandle.getVolatile(array, 1));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((char) arrayVarHandle.getVolatile(array, 0));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((char) arrayVarHandle.getVolatile(array, 1));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((int) arrayVarHandle.getVolatile(array, 0));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((int) arrayVarHandle.getVolatile(array, 1));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((String) arrayVarHandle.getVolatile(array, 0));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((String) arrayVarHandle.getVolatile(array, 1));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
  }

  public static void testSet() {
    System.out.println("testSet");

    VarHandle arrayVarHandle = MethodHandles.arrayElementVarHandle(long[].class);
    long[] array = new long[2];

    // long and Long values.
    arrayVarHandle.set(array, 0, 1L);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 1, 2L);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 0, Long.valueOf(3L));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 1, Long.valueOf(4L));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));

    // long and Long compatible values.
    arrayVarHandle.set(array, 0, (byte) 5);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 1, (byte) 6);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 0, Byte.valueOf((byte) 7));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 1, Byte.valueOf((byte) 8));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 0, '0');
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 1, '1');
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 0, Character.valueOf('2'));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 1, Character.valueOf('3'));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 0, (short) 9);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 1, (short) 10);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 0, Short.valueOf((short) 11));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 1, Short.valueOf((short) 12));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 0, 13);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 1, 14);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 0, Integer.valueOf(15));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.set(array, 1, Integer.valueOf(16));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));

    // long and Long non-compatible values.
    try {
      arrayVarHandle.set(array, 0, true);
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println((long) arrayVarHandle.get(array, 0));
      System.out.println((long) arrayVarHandle.get(array, 1));
    }
    try {
      arrayVarHandle.set(array, 0, 1.3f);
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println((long) arrayVarHandle.get(array, 0));
      System.out.println((long) arrayVarHandle.get(array, 1));
    }
    try {
      arrayVarHandle.set(array, 0, Float.valueOf(1.3f));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println((long) arrayVarHandle.get(array, 0));
      System.out.println((long) arrayVarHandle.get(array, 1));
    }
    try {
      arrayVarHandle.set(array, 0, 1.4);
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println((long) arrayVarHandle.get(array, 0));
      System.out.println((long) arrayVarHandle.get(array, 1));
    }
    try {
      arrayVarHandle.set(array, 0, Double.valueOf(1.4));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println((long) arrayVarHandle.get(array, 0));
      System.out.println((long) arrayVarHandle.get(array, 1));
    }
    try {
      arrayVarHandle.set(array, 0, new Object());
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
      System.out.println((long) arrayVarHandle.get(array, 0));
      System.out.println((long) arrayVarHandle.get(array, 1));
    } catch (RuntimeException e) {
      // The Art and desugaring throws WrongMethodTypeException.
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println((long) arrayVarHandle.get(array, 0));
      System.out.println((long) arrayVarHandle.get(array, 1));
    }
    try {
      arrayVarHandle.set(array, 0, "X");
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println((long) arrayVarHandle.get(array, 0));
      System.out.println((long) arrayVarHandle.get(array, 1));
    }
  }

  public static void testSetVolatile() {
    System.out.println("testSetVolatile");

    VarHandle arrayVarHandle = MethodHandles.arrayElementVarHandle(long[].class);
    long[] array = new long[2];

    // long and Long values.
    arrayVarHandle.setVolatile(array, 0, 1L);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 1, 2L);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 0, Long.valueOf(3L));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 1, Long.valueOf(4L));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));

    // long and Long compatible values.
    arrayVarHandle.setVolatile(array, 0, (byte) 5);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 1, (byte) 6);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 0, Byte.valueOf((byte) 7));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 1, Byte.valueOf((byte) 8));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 0, '0');
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 1, '1');
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 0, Character.valueOf('2'));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 1, Character.valueOf('3'));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 0, (short) 9);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 1, (short) 10);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 0, Short.valueOf((short) 11));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 1, Short.valueOf((short) 12));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 0, 13);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 1, 14);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 0, Integer.valueOf(15));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.setVolatile(array, 1, Integer.valueOf(16));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));

    // long and Long non-compatible values.
    try {
      arrayVarHandle.setVolatile(array, 0, true);
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println((long) arrayVarHandle.get(array, 0));
      System.out.println((long) arrayVarHandle.get(array, 1));
    }
    try {
      arrayVarHandle.setVolatile(array, 0, 1.3f);
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println((long) arrayVarHandle.get(array, 0));
      System.out.println((long) arrayVarHandle.get(array, 1));
    }
    try {
      arrayVarHandle.setVolatile(array, 0, Float.valueOf(1.3f));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println((long) arrayVarHandle.get(array, 0));
      System.out.println((long) arrayVarHandle.get(array, 1));
    }
    try {
      arrayVarHandle.setVolatile(array, 0, 1.4);
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println((long) arrayVarHandle.get(array, 0));
      System.out.println((long) arrayVarHandle.get(array, 1));
    }
    try {
      arrayVarHandle.setVolatile(array, 0, Double.valueOf(1.4));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println((long) arrayVarHandle.get(array, 0));
      System.out.println((long) arrayVarHandle.get(array, 1));
    }
    try {
      arrayVarHandle.setVolatile(array, 0, new Object());
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
      System.out.println((long) arrayVarHandle.get(array, 0));
      System.out.println((long) arrayVarHandle.get(array, 1));
    } catch (RuntimeException e) {
      // The Art and desugaring throws WrongMethodTypeException.
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println((long) arrayVarHandle.get(array, 0));
      System.out.println((long) arrayVarHandle.get(array, 1));
    }
    try {
      arrayVarHandle.setVolatile(array, 0, "X");
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println((long) arrayVarHandle.get(array, 0));
      System.out.println((long) arrayVarHandle.get(array, 1));
    }
  }

  public static void testCompareAndSet() {
    System.out.println("testCompareAndSet");

    VarHandle arrayVarHandle = MethodHandles.arrayElementVarHandle(long[].class);
    long[] array = new long[2];
    arrayVarHandle.set(array, 0, 1L);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.compareAndSet(array, 1, 1L, 3L);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.compareAndSet(array, 1, 0, 2);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    // TODO(b/247076137): Handle boxed.
    // arrayVarHandle.compareAndSet(array, 1, 2, box(3));
    arrayVarHandle.compareAndSet(array, 1, 2, 3);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
  }

  // This test is not testing weakCompareAndSet behaviour, but assuming it behaves like
  // compareAndSet, that is without any spurious failures. This is the desugaring behaviour, as
  // as there is no weakCompareAndSet primitive in sun.misc.Unsafe, only compareAndSwapXXX.
  public static void testWeakCompareAndSet() {
    System.out.println("testWeakCompareAndSet");

    VarHandle arrayVarHandle = MethodHandles.arrayElementVarHandle(long[].class);
    long[] array = new long[2];
    arrayVarHandle.set(array, 0, 1L);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.weakCompareAndSet(array, 1, 1L, 3L);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    arrayVarHandle.weakCompareAndSet(array, 1, 0, 2);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    // TODO(b/247076137): Handle boxed.
    // arrayVarHandle.weakCompareAndSet(array, 1, 2, box(3));
    arrayVarHandle.weakCompareAndSet(array, 1, 2, 3);
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
  }

  public static void testArrayVarHandleForNonSingleDimension() {
    System.out.println("testArrayVarHandleForNonSingleDimension");
    try {
      MethodHandles.arrayElementVarHandle(long.class);
      System.out.println("Unexpected success");
    } catch (IllegalArgumentException e) {
      System.out.println("IllegalArgumentException");
    }
    try {
      MethodHandles.arrayElementVarHandle(long[][].class);
      System.out.println("Got array element VarHandle");
    } catch (UnsupportedOperationException e) {
      System.out.println("UnsupportedOperationException");
    }
  }

  public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
    testGet();
    testGetVolatile();
    testSet();
    testSetVolatile();
    testCompareAndSet();
    testWeakCompareAndSet();
    testArrayVarHandleForNonSingleDimension();
  }
}
