// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.origin.Origin;
import java.util.function.Function;

public interface Definition {

  <T> T apply(
      Function<ProgramDefinition, T> programFunction,
      Function<ClasspathDefinition, T> classpathFunction,
      Function<LibraryDefinition, T> libraryFunction);

  default ClasspathOrLibraryClass asClasspathOrLibraryClass() {
    return null;
  }

  default ClasspathOrLibraryDefinition asClasspathOrLibraryDefinition() {
    return null;
  }

  ProgramDerivedContext asProgramDerivedContext(ProgramDerivedContext witness);

  DexType getContextType();

  Origin getOrigin();

  DexReference getReference();

  default boolean isClass() {
    return false;
  }

  default ClassDefinition asClass() {
    return null;
  }

  default boolean isField() {
    return false;
  }

  default DexClassAndField asField() {
    return null;
  }

  default boolean isMethod() {
    return false;
  }

  default DexClassAndMethod asMethod() {
    return null;
  }

  default boolean isProgramDefinition() {
    return false;
  }

  default ProgramDefinition asProgramDefinition() {
    return null;
  }
}
