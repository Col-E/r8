// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.Keep;
import com.android.tools.r8.KeepForSubclassing;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.PackageReference;

/** Consumer interface for recording references */
@KeepForSubclassing
public interface TraceReferencesConsumer {

  /**
   * Interface for asking for the access flags for a traced reference when the definition is present
   */
  @Keep
  interface AccessFlags {
    boolean isStatic();

    boolean isPublic();

    boolean isProtected();

    boolean isPrivate();
  }

  /**
   * Interface for asking for additional class information for a traced class when the definition is
   * found.
   */
  @Keep
  interface ClassAccessFlags extends AccessFlags {
    boolean isInterface();

    boolean isEnum();
  }

  @Keep
  interface FieldAccessFlags extends AccessFlags {}

  @Keep
  interface MethodAccessFlags extends AccessFlags {}

  /** Interface implemented by all references reported */
  @Keep
  interface TracedReference<T, F> {
    /** Returns if the reference does not have a definition in the program traced. */
    boolean isMissingDefinition();

    /** Returns the reference traced. */
    T getReference();

    /**
     * Returns the access flags for the reference traced. If the definition is not found (<code>
     * isMissingDefinition()</code> returns <code>true</code>) the access flags are not known and
     * this returns <code>null</code>.
     */
    F getAccessFlags();
  }

  @Keep
  interface TracedClass extends TracedReference<ClassReference, ClassAccessFlags> {}

  @Keep
  interface TracedField extends TracedReference<FieldReference, FieldAccessFlags> {}

  @Keep
  interface TracedMethod extends TracedReference<MethodReference, MethodAccessFlags> {}

  /** Class has been traced. */
  void acceptType(TracedClass tracedClass);

  /** Field has been traced. */
  void acceptField(TracedField tracedField);

  /** Method has been traced. */
  void acceptMethod(TracedMethod tracedMethod);

  /** Package which is required for package private access has been traced. */
  default void acceptPackage(PackageReference pkg) {}

  /**
   * Tracing has finished. There will be no more calls to any of the <code>acceptXXX</code> methods.
   */
  default void finished() {}
}
