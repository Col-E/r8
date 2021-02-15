// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic;

import com.android.tools.r8.Keep;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import java.util.function.Consumer;

/** A context that references a missing definition in the program, classpath, or library. */
@Keep
public interface MissingDefinitionContext {

  /** The class context from which a missing definition is referenced. */
  ClassReference getClassReference();

  /** The origin of the context. */
  Origin getOrigin();

  /**
   * Provides the context in which the missing definition is referenced to {@param
   * classReferenceConsumer} if the context is a class, to {@param fieldReferenceConsumer} if the
   * context is a field, and to {@param methodReferenceConsumer} if the context is a method..
   */
  void getReference(
      Consumer<ClassReference> classReferenceConsumer,
      Consumer<FieldReference> fieldReferenceConsumer,
      Consumer<MethodReference> methodReferenceConsumer);
}
