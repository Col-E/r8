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

  default ProgramOrClasspathClass asProgramOrClasspathClass() {
    return null;
  }

  default ProgramOrClasspathDefinition asProgramOrClasspathDefinition() {
    return null;
  }

  ProgramDerivedContext asProgramDerivedContext(ProgramDerivedContext witness);

  AccessFlags<?> getAccessFlags();

  DexClass getContextClass();

  DexType getContextType();

  DexDefinition getDefinition();

  Origin getOrigin();

  DexReference getReference();

  default boolean isClass() {
    return false;
  }

  default DexClass asClass() {
    return null;
  }

  default boolean isField() {
    return false;
  }

  default DexClassAndField asField() {
    return null;
  }

  default boolean isMember() {
    return !isClass();
  }

  default DexClassAndMember<?, ?> asMember() {
    return null;
  }

  default boolean isMethod() {
    return false;
  }

  default DexClassAndMethod asMethod() {
    return null;
  }

  default boolean isClasspathField() {
    return false;
  }

  default ClasspathField asClasspathField() {
    return null;
  }

  default boolean isClasspathMember() {
    return false;
  }

  default boolean isClasspathMethod() {
    return false;
  }

  default ClasspathMethod asClasspathMethod() {
    return null;
  }

  default boolean isLibraryField() {
    return false;
  }

  default LibraryField asLibraryField() {
    return null;
  }

  default boolean isLibraryMember() {
    return false;
  }

  default boolean isLibraryMethod() {
    return false;
  }

  default LibraryMethod asLibraryMethod() {
    return null;
  }

  default boolean isProgramClass() {
    return false;
  }

  default DexProgramClass asProgramClass() {
    return null;
  }

  default boolean isProgramDefinition() {
    return false;
  }

  default ProgramDefinition asProgramDefinition() {
    return null;
  }

  default boolean isProgramField() {
    return false;
  }

  default ProgramField asProgramField() {
    return null;
  }

  default boolean isProgramMember() {
    return false;
  }

  default ProgramMember<?, ?> asProgramMember() {
    return null;
  }

  default boolean isProgramMethod() {
    return false;
  }

  default ProgramMethod asProgramMethod() {
    return null;
  }

  default boolean isSamePackage(Definition definition) {
    return isSamePackage(definition.getReference());
  }

  default boolean isSamePackage(DexReference reference) {
    return getReference().isSamePackage(reference);
  }
}
