// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * Information about the contexts that references an item that was not part of the compilation unit.
 */
@Keep
public interface MissingDefinitionInfo {

  /**
   * Provides the missing definition to {@param classReferenceConsumer} if the missing definition is
   * a class, to {@param fieldReferenceConsumer} if the missing definition is a field, and to
   * {@param methodReferenceConsumer} if the missing definition is a method..
   */
  void getMissingDefinition(
      Consumer<ClassReference> classReferenceConsumer,
      Consumer<FieldReference> fieldReferenceConsumer,
      Consumer<MethodReference> methodReferenceConsumer);

  /** The contexts from which this missing definition was referenced. */
  Collection<MissingDefinitionContext> getReferencedFromContexts();
}
