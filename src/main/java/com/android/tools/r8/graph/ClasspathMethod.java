// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;



/** Type representing a method definition on the classpath and its holder. */
public final class ClasspathMethod extends DexClassAndMethod
    implements ClasspathMember<DexEncodedMethod, DexMethod> {

  public ClasspathMethod(DexClasspathClass holder, DexEncodedMethod method) {
    super(holder, method);
  }

  public void registerCodeReferencesForDesugaring(UseRegistry registry) {
    Code code = getDefinition().getCode();
    if (code != null) {
      code.registerCodeReferencesForDesugaring(this, registry);
    }
  }

  @Override
  public boolean isClasspathMember() {
    return true;
  }

  @Override
  public boolean isClasspathMethod() {
    return true;
  }

  @Override
  public ClasspathMethod asClasspathMethod() {
    return this;
  }

  @Override
  public DexClasspathClass getHolder() {
    DexClass holder = super.getHolder();
    assert holder.isClasspathClass();
    return holder.asClasspathClass();
  }
}
