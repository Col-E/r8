// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class ArrayOfObject {

  public static class A {

    private final int i;

    public A(int i) {
      this.i = i;
    }

    public String toString() {
      return "A(" + i + ")";
    }
  }

  private static void checkJavaLangInvokeWrongMethodTypeException(RuntimeException e) {
    if (e.getClass().getCanonicalName().equals("java.lang.invoke.WrongMethodTypeException")
        || e.getMessage().equals("java.lang.invoke.WrongMethodTypeException")) {
      return;
    }
    throw e;
  }

  public static void unsupportedGetConversion(VarHandle arrayVarHandle, Object[] array) {
    try {
      System.out.println((boolean) arrayVarHandle.get(array, 0));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((boolean) arrayVarHandle.get(array, 1));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((byte) arrayVarHandle.get(array, 0));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((byte) arrayVarHandle.get(array, 1));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((short) arrayVarHandle.get(array, 0));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((short) arrayVarHandle.get(array, 1));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((char) arrayVarHandle.get(array, 0));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((char) arrayVarHandle.get(array, 1));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((String) arrayVarHandle.get(array, 0));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((String) arrayVarHandle.get(array, 1));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
  }

  public static void testGet() {
    System.out.println("testGet");
    VarHandle arrayVarHandle = MethodHandles.arrayElementVarHandle(Object[].class);
    Object[] array = new Object[2];

    arrayVarHandle.set(array, 0, 1);
    arrayVarHandle.set(array, 1, 2);

    System.out.println(arrayVarHandle.get(array, 0));
    System.out.println(arrayVarHandle.get(array, 1));
    System.out.println((Object) arrayVarHandle.get(array, 0));
    System.out.println((Object) arrayVarHandle.get(array, 1));
    System.out.println((int) arrayVarHandle.get(array, 0));
    System.out.println((int) arrayVarHandle.get(array, 1));
    System.out.println((long) arrayVarHandle.get(array, 0));
    System.out.println((long) arrayVarHandle.get(array, 1));
    System.out.println((float) arrayVarHandle.get(array, 0));
    System.out.println((float) arrayVarHandle.get(array, 1));
    System.out.println((double) arrayVarHandle.get(array, 0));
    System.out.println((double) arrayVarHandle.get(array, 1));
    unsupportedGetConversion(arrayVarHandle, array);

    arrayVarHandle.set(array, 0, 3L);
    arrayVarHandle.set(array, 1, 4L);

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
    unsupportedGetConversion(arrayVarHandle, array);
    try {
      System.out.println((int) arrayVarHandle.get(array, 0));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((int) arrayVarHandle.get(array, 1));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
  }

  public static void unsupportedGetVolatileConversion(VarHandle arrayVarHandle, Object[] array) {
    try {
      System.out.println((boolean) arrayVarHandle.getVolatile(array, 0));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((boolean) arrayVarHandle.getVolatile(array, 1));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((byte) arrayVarHandle.getVolatile(array, 0));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((byte) arrayVarHandle.getVolatile(array, 1));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((short) arrayVarHandle.getVolatile(array, 0));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((short) arrayVarHandle.getVolatile(array, 1));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((char) arrayVarHandle.getVolatile(array, 0));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((char) arrayVarHandle.getVolatile(array, 1));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((String) arrayVarHandle.getVolatile(array, 0));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((String) arrayVarHandle.getVolatile(array, 1));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
  }

  public static void testGetVolatile() {
    System.out.println("testGetVolatile");
    VarHandle arrayVarHandle = MethodHandles.arrayElementVarHandle(Object[].class);
    Object[] array = new Object[2];

    arrayVarHandle.set(array, 0, 1);
    arrayVarHandle.set(array, 1, 2);

    System.out.println(arrayVarHandle.getVolatile(array, 0));
    System.out.println(arrayVarHandle.getVolatile(array, 1));
    System.out.println((Object) arrayVarHandle.getVolatile(array, 0));
    System.out.println((Object) arrayVarHandle.getVolatile(array, 1));
    System.out.println((int) arrayVarHandle.getVolatile(array, 0));
    System.out.println((int) arrayVarHandle.getVolatile(array, 1));
    System.out.println((long) arrayVarHandle.getVolatile(array, 0));
    System.out.println((long) arrayVarHandle.getVolatile(array, 1));
    System.out.println((float) arrayVarHandle.getVolatile(array, 0));
    System.out.println((float) arrayVarHandle.getVolatile(array, 1));
    System.out.println((double) arrayVarHandle.getVolatile(array, 0));
    System.out.println((double) arrayVarHandle.getVolatile(array, 1));
    unsupportedGetVolatileConversion(arrayVarHandle, array);

    arrayVarHandle.set(array, 0, 3L);
    arrayVarHandle.set(array, 1, 4L);

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
    unsupportedGetVolatileConversion(arrayVarHandle, array);
    try {
      System.out.println((int) arrayVarHandle.getVolatile(array, 0));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((int) arrayVarHandle.getVolatile(array, 1));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
      // The reference implementation throws ClassCastException.
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
  }

  public static void testSet() {
    System.out.println("testSet");

    VarHandle arrayVarHandle = MethodHandles.arrayElementVarHandle(Object[].class);
    Object[] array = new Object[2];

    Object o;
    {
      int index = 0;
      byte b;
      arrayVarHandle.set(array, index, (byte) 5);
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((byte) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Byte);
      b = (byte) arrayVarHandle.get(array, index);
      System.out.println(b == (byte) 5);
      arrayVarHandle.set(array, index, Byte.valueOf((byte) 6));
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((byte) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Byte);
      b = (byte) arrayVarHandle.get(array, index);
      System.out.println(b == 6);
    }
    {
      int index = 0;
      short s;
      arrayVarHandle.set(array, index, (short) 7);
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((short) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Short);
      s = (short) arrayVarHandle.get(array, index);
      System.out.println(s == (short) 7);
      arrayVarHandle.set(array, index, Short.valueOf((short) 8));
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((short) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Short);
      s = (short) arrayVarHandle.get(array, index);
      System.out.println(s == 8);
    }
    {
      int index = 0;
      float f;
      arrayVarHandle.set(array, index, (float) 9.0f);
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((float) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Float);
      f = (float) arrayVarHandle.get(array, index);
      System.out.println(f == (float) 9.0f);
      arrayVarHandle.set(array, index, Float.valueOf(10.0f));
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((float) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Float);
      f = (float) arrayVarHandle.get(array, index);
      System.out.println(f == 10.0f);
    }
    {
      int index = 0;
      double d;
      arrayVarHandle.set(array, index, (double) 11.0);
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((double) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Double);
      d = (double) arrayVarHandle.get(array, index);
      System.out.println(d == (double) 11.0);
      arrayVarHandle.set(array, index, Double.valueOf(12.0));
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((double) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Double);
      d = (double) arrayVarHandle.get(array, index);
      System.out.println(d == 12.0);
    }
    {
      int index = 0;
      char c;
      arrayVarHandle.set(array, index, 'A');
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((char) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Character);
      c = (char) arrayVarHandle.get(array, index);
      System.out.println(c == 'A');
      arrayVarHandle.set(array, index, Character.valueOf('B'));
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((char) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Character);
      c = (char) arrayVarHandle.get(array, index);
      System.out.println(c == 'B');
    }
  }

  public static void testSetVolatile() {
    System.out.println("testSetVolatile");

    VarHandle arrayVarHandle = MethodHandles.arrayElementVarHandle(Object[].class);
    Object[] array = new Object[2];

    Object o;
    {
      int index = 0;
      byte b;
      arrayVarHandle.setVolatile(array, index, (byte) 5);
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((byte) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Byte);
      b = (byte) arrayVarHandle.get(array, index);
      System.out.println(b == (byte) 5);
      arrayVarHandle.setVolatile(array, index, Byte.valueOf((byte) 6));
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((byte) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Byte);
      b = (byte) arrayVarHandle.get(array, index);
      System.out.println(b == 6);
    }
    {
      int index = 0;
      short s;
      arrayVarHandle.setVolatile(array, index, (short) 7);
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((short) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Short);
      s = (short) arrayVarHandle.get(array, index);
      System.out.println(s == (short) 7);
      arrayVarHandle.setVolatile(array, index, Short.valueOf((short) 8));
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((short) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Short);
      s = (short) arrayVarHandle.get(array, index);
      System.out.println(s == 8);
    }
    {
      int index = 0;
      float f;
      arrayVarHandle.setVolatile(array, index, (float) 9.0f);
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((float) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Float);
      f = (float) arrayVarHandle.get(array, index);
      System.out.println(f == (float) 9.0f);
      arrayVarHandle.setVolatile(array, index, Float.valueOf(10.0f));
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((float) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Float);
      f = (float) arrayVarHandle.get(array, index);
      System.out.println(f == 10.0f);
    }
    {
      int index = 0;
      double d;
      arrayVarHandle.setVolatile(array, index, (double) 11.0);
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((double) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Double);
      d = (double) arrayVarHandle.get(array, index);
      System.out.println(d == (double) 11.0);
      arrayVarHandle.setVolatile(array, index, Double.valueOf(12.0));
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((double) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Double);
      d = (double) arrayVarHandle.get(array, index);
      System.out.println(d == 12.0);
    }
    {
      int index = 0;
      char c;
      arrayVarHandle.setVolatile(array, index, 'A');
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((char) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Character);
      c = (char) arrayVarHandle.get(array, index);
      System.out.println(c == 'A');
      arrayVarHandle.setVolatile(array, index, Character.valueOf('B'));
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((char) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Character);
      c = (char) arrayVarHandle.get(array, index);
      System.out.println(c == 'B');
    }
  }

  public static void testSetRelease() {
    System.out.println("testSetRelease");

    VarHandle arrayVarHandle = MethodHandles.arrayElementVarHandle(Object[].class);
    Object[] array = new Object[2];

    Object o;
    {
      int index = 0;
      byte b;
      arrayVarHandle.setRelease(array, index, (byte) 5);
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((byte) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Byte);
      b = (byte) arrayVarHandle.get(array, index);
      System.out.println(b == (byte) 5);
      arrayVarHandle.setRelease(array, index, Byte.valueOf((byte) 6));
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((byte) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Byte);
      b = (byte) arrayVarHandle.get(array, index);
      System.out.println(b == 6);
    }
    {
      int index = 0;
      short s;
      arrayVarHandle.setRelease(array, index, (short) 7);
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((short) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Short);
      s = (short) arrayVarHandle.get(array, index);
      System.out.println(s == (short) 7);
      arrayVarHandle.setRelease(array, index, Short.valueOf((short) 8));
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((short) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Short);
      s = (short) arrayVarHandle.get(array, index);
      System.out.println(s == 8);
    }
    {
      int index = 0;
      float f;
      arrayVarHandle.setRelease(array, index, (float) 9.0f);
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((float) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Float);
      f = (float) arrayVarHandle.get(array, index);
      System.out.println(f == (float) 9.0f);
      arrayVarHandle.setRelease(array, index, Float.valueOf(10.0f));
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((float) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Float);
      f = (float) arrayVarHandle.get(array, index);
      System.out.println(f == 10.0f);
    }
    {
      int index = 0;
      double d;
      arrayVarHandle.setRelease(array, index, (double) 11.0);
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((double) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Double);
      d = (double) arrayVarHandle.get(array, index);
      System.out.println(d == (double) 11.0);
      arrayVarHandle.setRelease(array, index, Double.valueOf(12.0));
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((double) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Double);
      d = (double) arrayVarHandle.get(array, index);
      System.out.println(d == 12.0);
    }
    {
      int index = 0;
      char c;
      arrayVarHandle.setRelease(array, index, 'A');
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((char) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Character);
      c = (char) arrayVarHandle.get(array, index);
      System.out.println(c == 'A');
      arrayVarHandle.setRelease(array, index, Character.valueOf('B'));
      System.out.println(arrayVarHandle.get(array, index));
      System.out.println((char) arrayVarHandle.get(array, index));
      o = arrayVarHandle.get(array, index);
      System.out.println(o instanceof Character);
      c = (char) arrayVarHandle.get(array, index);
      System.out.println(c == 'B');
    }
  }

  public static void testCompareAndSet() {
    System.out.println("testCompareAndSet");

    VarHandle arrayVarHandle = MethodHandles.arrayElementVarHandle(Object[].class);
    Object[] array = new Object[2];

    int index = 0;
    A a1 = new A(1);
    arrayVarHandle.compareAndSet(array, index, 0, a1);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, null, a1);
    System.out.println(arrayVarHandle.get(array, index));
    System.out.println(arrayVarHandle.get(array, index) == a1);
    A a2 = new A(2);
    arrayVarHandle.compareAndSet(array, index, a1, a2);
    System.out.println(arrayVarHandle.get(array, index));
    System.out.println(arrayVarHandle.get(array, index) == a2);

    arrayVarHandle.compareAndSet(array, index, a2, 1);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, Integer.valueOf(1), 2);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, Integer.valueOf(2), Integer.valueOf(3));
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, Integer.valueOf(3), 4);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, 4L, 5);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, (byte) 4, 5);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, (short) 4, 5);
    System.out.println(arrayVarHandle.get(array, index));

    arrayVarHandle.compareAndSet(array, index, 4, 5L);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, Long.valueOf(5), 6L);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, Long.valueOf(6), Long.valueOf(7));
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, Long.valueOf(7), 8L);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, 8, 9L);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, (byte) 8, 9);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, (short) 8, 9);
    System.out.println(arrayVarHandle.get(array, index));
    System.out.println(arrayVarHandle.get(array, index) == a1);
    arrayVarHandle.compareAndSet(array, index, a1, a2);
    System.out.println(arrayVarHandle.get(array, index));
    System.out.println(arrayVarHandle.get(array, index) == a2);

    arrayVarHandle.compareAndSet(array, index, a2, 1);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, Integer.valueOf(1), 2);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, Integer.valueOf(2), Integer.valueOf(3));
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, Integer.valueOf(3), 4);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, 4L, 5);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, (byte) 4, 5);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, (short) 4, 5);
    System.out.println(arrayVarHandle.get(array, index));

    arrayVarHandle.compareAndSet(array, index, 4, 5L);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, Long.valueOf(5), 6L);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, Long.valueOf(6), Long.valueOf(7));
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, Long.valueOf(7), 8L);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, 8, 9L);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, (byte) 8, 9);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.compareAndSet(array, index, (short) 8, 9);
    System.out.println(arrayVarHandle.get(array, index));
  }

  // This test is not testing weakCompareAndSet behaviour, but assuming it behaves like
  // compareAndSet, that is without any spurious failures. This is the desugaring behaviour, as
  // as there is no weakCompareAndSet primitive in sun.misc.Unsafe, only compareAndSwapXXX.
  public static void testWeakCompareAndSet() {
    System.out.println("testWeakCompareAndSet");

    VarHandle arrayVarHandle = MethodHandles.arrayElementVarHandle(Object[].class);
    Object[] array = new Object[2];

    int index = 0;
    A a1 = new A(1);
    arrayVarHandle.weakCompareAndSet(array, index, 0, a1);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, null, a1);
    System.out.println(arrayVarHandle.get(array, index));
    System.out.println(arrayVarHandle.get(array, index) == a1);
    A a2 = new A(2);
    arrayVarHandle.weakCompareAndSet(array, index, a1, a2);
    System.out.println(arrayVarHandle.get(array, index));
    System.out.println(arrayVarHandle.get(array, index) == a2);

    arrayVarHandle.weakCompareAndSet(array, index, a2, 1);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, Integer.valueOf(1), 2);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, Integer.valueOf(2), Integer.valueOf(3));
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, Integer.valueOf(3), 4);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, 4L, 5);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, (byte) 4, 5);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, (short) 4, 5);
    System.out.println(arrayVarHandle.get(array, index));

    arrayVarHandle.weakCompareAndSet(array, index, 4, 5L);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, Long.valueOf(5), 6L);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, Long.valueOf(6), Long.valueOf(7));
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, Long.valueOf(7), 8L);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, 8, 9L);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, (byte) 8, 9);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, (short) 8, 9);
    System.out.println(arrayVarHandle.get(array, index));
    System.out.println(arrayVarHandle.get(array, index) == a1);
    arrayVarHandle.weakCompareAndSet(array, index, a1, a2);
    System.out.println(arrayVarHandle.get(array, index));
    System.out.println(arrayVarHandle.get(array, index) == a2);

    arrayVarHandle.weakCompareAndSet(array, index, a2, 1);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, Integer.valueOf(1), 2);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, Integer.valueOf(2), Integer.valueOf(3));
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, Integer.valueOf(3), 4);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, 4L, 5);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, (byte) 4, 5);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, (short) 4, 5);
    System.out.println(arrayVarHandle.get(array, index));

    arrayVarHandle.weakCompareAndSet(array, index, 4, 5L);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, Long.valueOf(5), 6L);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, Long.valueOf(6), Long.valueOf(7));
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, Long.valueOf(7), 8L);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, 8, 9L);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, (byte) 8, 9);
    System.out.println(arrayVarHandle.get(array, index));
    arrayVarHandle.weakCompareAndSet(array, index, (short) 8, 9);
    System.out.println(arrayVarHandle.get(array, index));
  }

  public static void testArrayVarHandleForNonSingleDimension() {
    System.out.println("testArrayVarHandleForNonSingleDimension");
    try {
      MethodHandles.arrayElementVarHandle(Object.class);
      System.out.println("Unexpected success");
    } catch (IllegalArgumentException e) {
      System.out.println("IllegalArgumentException");
    }
    try {
      MethodHandles.arrayElementVarHandle(Object[][].class);
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
    testSetRelease();
    testCompareAndSet();
    testWeakCompareAndSet();
    testArrayVarHandleForNonSingleDimension();
  }
}
