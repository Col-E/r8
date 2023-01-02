// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.varhandle;

public final class DesugarMethodHandlesLookup {
  public DesugarVarHandle findVarHandle(Class<?> recv, String name, Class<?> type)
      throws NoSuchFieldException, IllegalAccessException {
    return new DesugarVarHandle(recv, name, type);
  }

  // Emulation of MethodHandled.privateLookupIn
  public DesugarMethodHandlesLookup toPrivateLookupIn(Class<?> targetClass) {
    return this;
  }
  /*
   * Remaining methods on MethodHandles.Lookup.
   *
   * These could be implemented by forwarding to the runtime MethodHandles.Lookup if present at
   * runtime.
   *

  public Class<?>	accessClass(Class<?> targetClass) throws IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  public MethodHandle bind(Object receiver, String name, MethodType type) throws NoSuchMethodException,
      IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  public Class<?> defineClass(byte[] bytes) throws IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  public MethodHandles.Lookup dropLookupMode(int modeToDrop) {
    throw new RuntimeException("Unsupported");
  }

  public Class<?> findClass(String targetName) throws ClassNotFoundException, IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  public MethodHandle findConstructor(Class<?> refc, MethodType type) throws NoSuchMethodException, IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  public MethodHandle findGetter(Class<?> refc, String name, Class<?> type) throws NoSuchMethodException, IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  public MethodHandle findSetter(Class<?> refc, String name, Class<?> type) throws NoSuchFieldException, IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  public MethodHandle findSpecial(Class<?> refc, String name, MethodType type, Class<?> specialCaller) throws NoSuchFieldException, IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  public MethodHandle findStatic(Class<?> refc, String name, MethodType type) throws NoSuchMethodException, IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  public MethodHandle findStaticGetter(Class<?> refc, String name, Class<?> type) throws NoSuchMethodException, IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  public MethodHandle findStaticSetter(Class<?> refc, String name, Class<?> type) throws NoSuchMethodException, IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  public DesugarVarHandle findStaticVarHandle(Class<?> decl, String name, Class<?> type) throws Exception throws NoSuchFieldException, IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  public MethodHandle findVirtual(Class<?> refc, String name, MethodType type) throws NoSuchMethodException, IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  public boolean hasPrivateAccess() {
    throw new RuntimeException("Unsupported");
  }

  public MethodHandles.Lookup in(Class<?> requestedLookupClass) {
    throw new RuntimeException("Unsupported");
  }

  public Class<?> lookupClass() {
    throw new RuntimeException("Unsupported");
  }

  public int lookupModes() {
    throw new RuntimeException("Unsupported");
  }

  public MethodHandleInfo revealDirect(MethodHandle target) {
    throw new RuntimeException("Unsupported");
  }

  public String toString() {
    throw new RuntimeException("Unsupported");
  }

  public MethodHandle unreflect(Method m) throws IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  public MethodHandle unreflectConstructor(Constructor<?> c) throws IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  public MethodHandle unreflectGetter(Field f) throws IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  public MethodHandle unreflectSetter(Field f) throws IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  public MethodHandle unreflectSpecial(Method m, Class<?> specialCaller) {
    throw new RuntimeException("Unsupported");
  }

  DesugarVarHandle unreflectVarHandle(Field f) throws IllegalAccessException {
    throw new RuntimeException("Unsupported");
  }

  */
}
