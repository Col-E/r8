// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package invokecustom;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

class ArgumentType {
}

class ReturnType {
}

interface I {
  default ReturnType targetMethodTest4(ArgumentType arg) {
    System.out.println("I.targetMethodTest4");
    return new ReturnType();
  }
}

class Middle implements I {
}

class Sub extends Middle {
}

interface I2 {
  default ReturnType targetMethodTest5(ArgumentType arg) {
    System.out.println("I2.targetMethodTest5");
    return new ReturnType();
  }
}

// TODO(116283747): Add the same test where the interface method is not overriden but inherited
// from the interface. Currently, that works on the reference implementation but fails on Art.
class Impl implements I2 {
  @Override
  public ReturnType targetMethodTest5(ArgumentType arg) {
    System.out.println("Impl.targetMethodTest5");
    return new ReturnType();
  }
}

public class InvokeCustom {

  private static String staticField1 = "StaticField1";

  private static void targetMethodTest1() {
    System.out.println("Hello World!");
  }

  private static void targetMethodTest2(MethodHandle mhInvokeStatic, MethodHandle mhGetStatic)
      throws Throwable {
    mhInvokeStatic.invokeExact();
    System.out.println(mhGetStatic.invoke());
  }

  private static void targetMethodTest3(MethodType mt)
      throws Throwable {
    System.out.println("MethodType: " + mt.toString());
  }

  public static CallSite bsmLookupStatic(MethodHandles.Lookup caller, String name, MethodType type)
      throws NoSuchMethodException, IllegalAccessException {
    final MethodHandles.Lookup lookup = MethodHandles.lookup();
    final MethodHandle targetMH = lookup.findStatic(lookup.lookupClass(), name, type);
    return new ConstantCallSite(targetMH.asType(type));
  }

  public static void doInvokeSubWithArg(MethodHandle handle) throws Throwable {
    handle.invoke(new Sub(), new ArgumentType());
  }

  public static void doInvokeExactImplWithArg(MethodHandle handle) throws Throwable {
    ReturnType result = (ReturnType) handle.invokeExact(new Impl(), new ArgumentType());
  }
}
