// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Provides immutable access to {@link FieldAccessInfoImpl}. */
public interface FieldAccessInfo {

  FieldAccessInfoImpl asMutable();

  DexField getField();

  int getNumberOfReadContexts();

  int getNumberOfWriteContexts();

  AbstractAccessContexts getReadsWithContexts();

  AbstractAccessContexts getWritesWithContexts();

  ProgramMethod getUniqueReadContext();

  boolean hasKnownReadContexts();

  boolean hasKnownWriteContexts();

  void forEachIndirectAccess(Consumer<DexField> consumer);

  void forEachIndirectAccessWithContexts(BiConsumer<DexField, ProgramMethodSet> consumer);

  void forEachAccessContext(Consumer<ProgramMethod> consumer);

  void forEachReadContext(Consumer<ProgramMethod> consumer);

  void forEachWriteContext(Consumer<ProgramMethod> consumer);

  boolean hasReflectiveAccess();

  boolean hasReflectiveRead();

  boolean hasReflectiveWrite();

  default boolean isAccessedFromMethodHandle() {
    return isReadFromMethodHandle() || isWrittenFromMethodHandle();
  }

  boolean isRead();

  boolean isReadFromAnnotation();

  boolean isReadFromRecordInvokeDynamic();

  boolean isReadFromMethodHandle();

  boolean isReadOnlyInMethodSatisfying(Predicate<ProgramMethod> predicate);

  boolean isWritten();

  boolean isWrittenFromMethodHandle();

  boolean isWrittenInMethodSatisfying(Predicate<ProgramMethod> predicate);

  boolean isWrittenOnlyInMethodSatisfying(Predicate<ProgramMethod> predicate);

  boolean isWrittenOutside(DexEncodedMethod method);
}
