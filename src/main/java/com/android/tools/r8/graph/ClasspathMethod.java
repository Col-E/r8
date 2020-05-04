// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.logging.Log;

/** Type representing a method definition on the classpath and its holder. */
public final class ClasspathMethod extends DexClassAndMethod {

  public ClasspathMethod(DexClasspathClass holder, DexEncodedMethod method) {
    super(holder, method);
  }

  public void registerCodeReferencesForDesugaring(UseRegistry registry) {
    Code code = getDefinition().getCode();
    if (code != null) {
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Registering definitions reachable from `%s`.", this);
      }
      code.registerCodeReferencesForDesugaring(this, registry);
    }
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
