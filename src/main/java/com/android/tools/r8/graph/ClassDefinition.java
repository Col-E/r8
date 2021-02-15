// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import java.util.function.Consumer;

public interface ClassDefinition extends Definition {

  Iterable<DexType> allImmediateSupertypes();

  void forEachClassField(Consumer<? super DexClassAndField> consumer);

  void forEachClassMethod(Consumer<? super DexClassAndMethod> consumer);

  MethodCollection getMethodCollection();

  DexType getType();

  boolean isClasspathClass();

  DexClasspathClass asClasspathClass();

  boolean isLibraryClass();

  DexLibraryClass asLibraryClass();

  boolean isProgramClass();

  DexProgramClass asProgramClass();
}
