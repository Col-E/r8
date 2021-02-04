// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import java.util.function.Function;

public interface Definition {

  <T> T apply(
      Function<ProgramDefinition, T> programFunction,
      Function<ClasspathDefinition, T> classpathFunction,
      Function<LibraryDefinition, T> libraryFunction);

  ProgramDerivedContext asProgramDerivedContext(ProgramDerivedContext witness);

  DexType getContextType();

  DexReference getReference();
}
