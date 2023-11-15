// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

public class DefaultUseRegistryWithResult<R, T extends Definition>
    extends UseRegistryWithResult<R, T> {

  public DefaultUseRegistryWithResult(AppView<?> appView, T context) {
    super(appView, context);
  }

  public DefaultUseRegistryWithResult(AppView<?> appView, T context, R defaultResult) {
    super(appView, context, defaultResult);
  }

  @Override
  public void registerInitClass(DexType type) {
    // Intentionally empty.
  }

  @Override
  public void registerInstanceFieldRead(DexField field) {
    // Intentionally empty.
  }

  @Override
  public void registerInstanceFieldWrite(DexField field) {
    // Intentionally empty.
  }

  @Override
  public void registerInvokeDirect(DexMethod method) {
    // Intentionally empty.
  }

  @Override
  public void registerInvokeInterface(DexMethod method) {
    // Intentionally empty.
  }

  @Override
  public void registerInvokeStatic(DexMethod method) {
    // Intentionally empty.
  }

  @Override
  public void registerInvokeSuper(DexMethod method) {
    // Intentionally empty.
  }

  @Override
  public void registerInvokeVirtual(DexMethod method) {
    // Intentionally empty.
  }

  @Override
  public void registerStaticFieldRead(DexField field) {
    // Intentionally empty.
  }

  @Override
  public void registerStaticFieldWrite(DexField field) {
    // Intentionally empty.
  }

  @Override
  public void registerTypeReference(DexType type) {
    // Intentionally empty.
  }
}
