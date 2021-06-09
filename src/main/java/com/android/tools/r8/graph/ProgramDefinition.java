// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.DexAnnotation.AnnotatedKind;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface ProgramDefinition extends Definition, ProgramDerivedContext {

  @Override
  default <T> T apply(
      Function<ProgramDefinition, T> programFunction,
      Function<ClasspathDefinition, T> classpathFunction,
      Function<LibraryDefinition, T> libraryFunction) {
    return programFunction.apply(this);
  }

  @Override
  default ProgramDerivedContext asProgramDerivedContext(ProgramDerivedContext witness) {
    return this;
  }

  default void clearAllAnnotations() {
    getDefinition().clearAllAnnotations();
  }

  default void rewriteAllAnnotations(
      BiFunction<DexAnnotation, AnnotatedKind, DexAnnotation> rewriter) {
    getDefinition().rewriteAllAnnotations(rewriter);
  }

  DexProgramClass getContextClass();

  AccessFlags<?> getAccessFlags();

  DexDefinition getDefinition();

  default boolean isProgramClass() {
    return false;
  }

  default DexProgramClass asProgramClass() {
    return null;
  }

  @Override
  default boolean isProgramDefinition() {
    return true;
  }

  @Override
  default ProgramDefinition asProgramDefinition() {
    return this;
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
}
