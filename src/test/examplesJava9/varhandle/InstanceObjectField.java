// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.invoke.WrongMethodTypeException;

public class InstanceObjectField {

  private Object field;

  public static class A {

    private final int i;

    public A(int i) {
      this.i = i;
    }

    public String toString() {
      return "A(" + i + ")";
    }
  }

  public static void testSetGet(VarHandle varHandle) {
    System.out.println("testSetGet");

    InstanceObjectField instance = new InstanceObjectField();

    System.out.println(varHandle.get(instance));
    A a1 = new A(1);
    varHandle.set(instance, a1);
    System.out.println(varHandle.get(instance));
    System.out.println(varHandle.get(instance) == a1);
    A a2 = new A(2);
    varHandle.set(instance, a2);
    System.out.println(varHandle.get(instance));
    System.out.println(varHandle.get(instance) == a2);

    Object o;
    {
      int i;
      varHandle.set(instance, 1);
      System.out.println(varHandle.get(instance));
      System.out.println((int) varHandle.get(instance));
      o = varHandle.get(instance);
      System.out.println(o instanceof Integer);
      i = (int) varHandle.get(instance);
      System.out.println(i == 1);
      varHandle.set(instance, Integer.valueOf(2));
      System.out.println(varHandle.get(instance));
      System.out.println((int) varHandle.get(instance));
      o = varHandle.get(instance);
      System.out.println(o instanceof Integer);
      i = (int) varHandle.get(instance);
      System.out.println(i == 2);
    }
    {
      long l;
      varHandle.set(instance, 3L);
      System.out.println(varHandle.get(instance));
      System.out.println((long) varHandle.get(instance));
      o = varHandle.get(instance);
      System.out.println(o instanceof Long);
      l = (long) varHandle.get(instance);
      System.out.println(l == 3L);
      varHandle.set(instance, Long.valueOf(4L));
      System.out.println(varHandle.get(instance));
      System.out.println((long) varHandle.get(instance));
      o = varHandle.get(instance);
      System.out.println(o instanceof Long);
      l = (long) varHandle.get(instance);
      System.out.println(l == 4L);
    }
    {
      byte b;
      varHandle.set(instance, (byte) 5);
      System.out.println(varHandle.get(instance));
      System.out.println((byte) varHandle.get(instance));
      o = varHandle.get(instance);
      System.out.println(o instanceof Byte);
      b = (byte) varHandle.get(instance);
      System.out.println(b == (byte) 5);
      varHandle.set(instance, Byte.valueOf((byte) 6));
      System.out.println(varHandle.get(instance));
      System.out.println((byte) varHandle.get(instance));
      o = varHandle.get(instance);
      System.out.println(o instanceof Byte);
      b = (byte) varHandle.get(instance);
      System.out.println(b == 6);
    }
    {
      short s;
      varHandle.set(instance, (short) 7);
      System.out.println(varHandle.get(instance));
      System.out.println((short) varHandle.get(instance));
      o = varHandle.get(instance);
      System.out.println(o instanceof Short);
      s = (short) varHandle.get(instance);
      System.out.println(s == (short) 7);
      varHandle.set(instance, Short.valueOf((short) 8));
      System.out.println(varHandle.get(instance));
      System.out.println((short) varHandle.get(instance));
      o = varHandle.get(instance);
      System.out.println(o instanceof Short);
      s = (short) varHandle.get(instance);
      System.out.println(s == 8);
    }
    {
      float f;
      varHandle.set(instance, (float) 9.0f);
      System.out.println(varHandle.get(instance));
      System.out.println((float) varHandle.get(instance));
      o = varHandle.get(instance);
      System.out.println(o instanceof Float);
      f = (float) varHandle.get(instance);
      System.out.println(f == (float) 9.0f);
      varHandle.set(instance, Float.valueOf(10.0f));
      System.out.println(varHandle.get(instance));
      System.out.println((float) varHandle.get(instance));
      o = varHandle.get(instance);
      System.out.println(o instanceof Float);
      f = (float) varHandle.get(instance);
      System.out.println(f == 10.0f);
    }
    {
      double d;
      varHandle.set(instance, (double) 11.0);
      System.out.println(varHandle.get(instance));
      System.out.println((double) varHandle.get(instance));
      o = varHandle.get(instance);
      System.out.println(o instanceof Double);
      d = (double) varHandle.get(instance);
      System.out.println(d == (double) 11.0);
      varHandle.set(instance, Double.valueOf(12.0));
      System.out.println(varHandle.get(instance));
      System.out.println((double) varHandle.get(instance));
      o = varHandle.get(instance);
      System.out.println(o instanceof Double);
      d = (double) varHandle.get(instance);
      System.out.println(d == 12.0);
    }
    {
      char c;
      varHandle.set(instance, 'A');
      System.out.println(varHandle.get(instance));
      System.out.println((char) varHandle.get(instance));
      o = varHandle.get(instance);
      System.out.println(o instanceof Character);
      c = (char) varHandle.get(instance);
      System.out.println(c == 'A');
      varHandle.set(instance, Character.valueOf('B'));
      System.out.println(varHandle.get(instance));
      System.out.println((char) varHandle.get(instance));
      o = varHandle.get(instance);
      System.out.println(o instanceof Character);
      c = (char) varHandle.get(instance);
      System.out.println(c == 'B');
    }
  }

  public static void testSetVolatileGetVolatile(VarHandle varHandle) {
    System.out.println("testSetVolatileGetVolatile");

    InstanceObjectField instance = new InstanceObjectField();

    System.out.println(varHandle.getVolatile(instance));
    A a1 = new A(1);
    varHandle.setVolatile(instance, a1);
    System.out.println(varHandle.getVolatile(instance));
    System.out.println(varHandle.getVolatile(instance) == a1);
    A a2 = new A(2);
    varHandle.setVolatile(instance, a2);
    System.out.println(varHandle.getVolatile(instance));
    System.out.println(varHandle.getVolatile(instance) == a2);

    Object o;
    {
      int i;
      varHandle.setVolatile(instance, 1);
      System.out.println(varHandle.getVolatile(instance));
      System.out.println((int) varHandle.getVolatile(instance));
      o = varHandle.getVolatile(instance);
      System.out.println(o instanceof Integer);
      i = (int) varHandle.getVolatile(instance);
      System.out.println(i == 1);
      varHandle.setVolatile(instance, Integer.valueOf(2));
      System.out.println(varHandle.getVolatile(instance));
      System.out.println((int) varHandle.getVolatile(instance));
      o = varHandle.getVolatile(instance);
      System.out.println(o instanceof Integer);
      i = (int) varHandle.getVolatile(instance);
      System.out.println(i == 2);
    }
    {
      long l;
      varHandle.setVolatile(instance, 3L);
      System.out.println(varHandle.getVolatile(instance));
      System.out.println((long) varHandle.getVolatile(instance));
      o = varHandle.getVolatile(instance);
      System.out.println(o instanceof Long);
      l = (long) varHandle.getVolatile(instance);
      System.out.println(l == 3L);
      varHandle.setVolatile(instance, Long.valueOf(4L));
      System.out.println(varHandle.getVolatile(instance));
      System.out.println((long) varHandle.getVolatile(instance));
      o = varHandle.getVolatile(instance);
      System.out.println(o instanceof Long);
      l = (long) varHandle.getVolatile(instance);
      System.out.println(l == 4L);
    }
    {
      byte b;
      varHandle.setVolatile(instance, (byte) 5);
      System.out.println(varHandle.getVolatile(instance));
      System.out.println((byte) varHandle.getVolatile(instance));
      o = varHandle.getVolatile(instance);
      System.out.println(o instanceof Byte);
      b = (byte) varHandle.getVolatile(instance);
      System.out.println(b == (byte) 5);
      varHandle.setVolatile(instance, Byte.valueOf((byte) 6));
      System.out.println(varHandle.getVolatile(instance));
      System.out.println((byte) varHandle.getVolatile(instance));
      o = varHandle.getVolatile(instance);
      System.out.println(o instanceof Byte);
      b = (byte) varHandle.getVolatile(instance);
      System.out.println(b == 6);
    }
    {
      short s;
      varHandle.setVolatile(instance, (short) 7);
      System.out.println(varHandle.getVolatile(instance));
      System.out.println((short) varHandle.getVolatile(instance));
      o = varHandle.getVolatile(instance);
      System.out.println(o instanceof Short);
      s = (short) varHandle.getVolatile(instance);
      System.out.println(s == (short) 7);
      varHandle.setVolatile(instance, Short.valueOf((short) 8));
      System.out.println(varHandle.getVolatile(instance));
      System.out.println((short) varHandle.getVolatile(instance));
      o = varHandle.getVolatile(instance);
      System.out.println(o instanceof Short);
      s = (short) varHandle.getVolatile(instance);
      System.out.println(s == 8);
    }
    {
      float f;
      varHandle.setVolatile(instance, (float) 9.0f);
      System.out.println(varHandle.getVolatile(instance));
      System.out.println((float) varHandle.getVolatile(instance));
      o = varHandle.getVolatile(instance);
      System.out.println(o instanceof Float);
      f = (float) varHandle.getVolatile(instance);
      System.out.println(f == (float) 9.0f);
      varHandle.setVolatile(instance, Float.valueOf(10.0f));
      System.out.println(varHandle.getVolatile(instance));
      System.out.println((float) varHandle.getVolatile(instance));
      o = varHandle.getVolatile(instance);
      System.out.println(o instanceof Float);
      f = (float) varHandle.getVolatile(instance);
      System.out.println(f == 10.0f);
    }
    {
      double d;
      varHandle.setVolatile(instance, (double) 11.0);
      System.out.println(varHandle.getVolatile(instance));
      System.out.println((double) varHandle.getVolatile(instance));
      o = varHandle.getVolatile(instance);
      System.out.println(o instanceof Double);
      d = (double) varHandle.getVolatile(instance);
      System.out.println(d == (double) 11.0);
      varHandle.setVolatile(instance, Double.valueOf(12.0));
      System.out.println(varHandle.getVolatile(instance));
      System.out.println((double) varHandle.getVolatile(instance));
      o = varHandle.getVolatile(instance);
      System.out.println(o instanceof Double);
      d = (double) varHandle.getVolatile(instance);
      System.out.println(d == 12.0);
    }
    {
      char c;
      varHandle.setVolatile(instance, 'A');
      System.out.println(varHandle.getVolatile(instance));
      System.out.println((char) varHandle.getVolatile(instance));
      o = varHandle.getVolatile(instance);
      System.out.println(o instanceof Character);
      c = (char) varHandle.getVolatile(instance);
      System.out.println(c == 'A');
      varHandle.setVolatile(instance, Character.valueOf('B'));
      System.out.println(varHandle.getVolatile(instance));
      System.out.println((char) varHandle.getVolatile(instance));
      o = varHandle.getVolatile(instance);
      System.out.println(o instanceof Character);
      c = (char) varHandle.getVolatile(instance);
      System.out.println(c == 'B');
    }
  }

  public static void testCompareAndSet(VarHandle varHandle) {
    System.out.println("testCompareAndSet");

    InstanceObjectField instance = new InstanceObjectField();

    A a1 = new A(1);
    varHandle.compareAndSet(instance, 0, a1);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, null, a1);
    System.out.println(varHandle.get(instance));
    System.out.println(varHandle.get(instance) == a1);
    A a2 = new A(2);
    varHandle.compareAndSet(instance, a1, a2);
    System.out.println(varHandle.get(instance));
    System.out.println(varHandle.get(instance) == a2);

    varHandle.compareAndSet(instance, a2, 1);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Integer.valueOf(1), 2);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Integer.valueOf(2), Integer.valueOf(3));
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Integer.valueOf(3), 4);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, 4L, 5);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (byte) 4, 5);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (short) 4, 5);
    System.out.println(varHandle.get(instance));

    varHandle.compareAndSet(instance, 4, 5L);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Long.valueOf(5), 6L);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Long.valueOf(6), Long.valueOf(7));
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Long.valueOf(7), 8L);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, 8, 9L);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (byte) 8, 9);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (short) 8, 9);
    System.out.println(varHandle.get(instance));
    System.out.println(varHandle.get(instance) == a1);
    varHandle.compareAndSet(instance, a1, a2);
    System.out.println(varHandle.get(instance));
    System.out.println(varHandle.get(instance) == a2);

    varHandle.compareAndSet(instance, a2, 1);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Integer.valueOf(1), 2);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Integer.valueOf(2), Integer.valueOf(3));
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Integer.valueOf(3), 4);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, 4L, 5);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (byte) 4, 5);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (short) 4, 5);
    System.out.println(varHandle.get(instance));

    varHandle.compareAndSet(instance, 4, 5L);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Long.valueOf(5), 6L);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Long.valueOf(6), Long.valueOf(7));
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Long.valueOf(7), 8L);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, 8, 9L);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (byte) 8, 9);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (short) 8, 9);
    System.out.println(varHandle.get(instance));
  }

  // This test is not testing weakCompareAndSet behaviour, but assuming it behaves like
  // compareAndSet, that is without any spurious failures. This is the desugaring behaviour, as
  // as there is no weakCompareAndSet primitive in sun.misc.Unsafe, only compareAndSwapXXX.
  public static void testWeakCompareAndSet(VarHandle varHandle) {
    System.out.println("testWeakCompareAndSet");

    InstanceObjectField instance = new InstanceObjectField();

    A a1 = new A(1);
    varHandle.compareAndSet(instance, 0, a1);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, null, a1);
    System.out.println(varHandle.get(instance));
    System.out.println(varHandle.get(instance) == a1);
    A a2 = new A(2);
    varHandle.compareAndSet(instance, a1, a2);
    System.out.println(varHandle.get(instance));
    System.out.println(varHandle.get(instance) == a2);

    varHandle.compareAndSet(instance, a2, 1);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Integer.valueOf(1), 2);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Integer.valueOf(2), Integer.valueOf(3));
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Integer.valueOf(3), 4);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, 4L, 5);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (byte) 4, 5);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (short) 4, 5);
    System.out.println(varHandle.get(instance));

    varHandle.compareAndSet(instance, 4, 5L);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Long.valueOf(5), 6L);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Long.valueOf(6), Long.valueOf(7));
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Long.valueOf(7), 8L);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, 8, 9L);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (byte) 8, 9);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (short) 8, 9);
    System.out.println(varHandle.get(instance));
    System.out.println(varHandle.get(instance) == a1);
    varHandle.compareAndSet(instance, a1, a2);
    System.out.println(varHandle.get(instance));
    System.out.println(varHandle.get(instance) == a2);

    varHandle.compareAndSet(instance, a2, 1);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Integer.valueOf(1), 2);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Integer.valueOf(2), Integer.valueOf(3));
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Integer.valueOf(3), 4);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, 4L, 5);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (byte) 4, 5);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (short) 4, 5);
    System.out.println(varHandle.get(instance));

    varHandle.compareAndSet(instance, 4, 5L);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Long.valueOf(5), 6L);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Long.valueOf(6), Long.valueOf(7));
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Long.valueOf(7), 8L);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, 8, 9L);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (byte) 8, 9);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (short) 8, 9);
    System.out.println(varHandle.get(instance));
  }

  public static void testReturnValueClassCastException(VarHandle varHandle) {
    System.out.println("testReturnValueClassCastException");

    InstanceObjectField instance = new InstanceObjectField();
    A a = new A(1);

    varHandle.set(instance, a);
    try {
      System.out.println((int) varHandle.get(instance));
      System.out.println("Expected ClassCastException");
    } catch (ClassCastException e) {
      System.out.println("Reference implementation");
    } catch (WrongMethodTypeException e) {
      System.out.println("Art implementation");
    }
    varHandle.set(instance, a);
    try {
      System.out.println((int) (Integer) (int) varHandle.get(instance));
      System.out.println("Expected ClassCastException");
    } catch (ClassCastException e) {
      System.out.println("Reference implementation");
    } catch (WrongMethodTypeException e) {
      System.out.println("Art implementation");
    }
  }

  public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
    VarHandle varHandle =
        MethodHandles.lookup().findVarHandle(InstanceObjectField.class, "field", Object.class);
    testSetGet(varHandle);
    testSetVolatileGetVolatile(varHandle);
    testCompareAndSet(varHandle);
    testWeakCompareAndSet(varHandle);
    testReturnValueClassCastException(varHandle);
  }
}
