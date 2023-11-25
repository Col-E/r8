// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;

/** A context that references a missing definition in the program, classpath, or library. */
@KeepForApi
public interface DefinitionContext {

  /** The origin of the context. */
  Origin getOrigin();

  /** Predicate that is true iff this is an instance of {@link DefinitionClassContext}. */
  default boolean isClassContext() {
    return false;
  }

  /** Predicate that is true iff this is an instance of {@link DefinitionFieldContext}. */
  default boolean isFieldContext() {
    return false;
  }

  /** Predicate that is true iff this is an instance of {@link DefinitionMethodContext}. */
  default boolean isMethodContext() {
    return false;
  }

  /**
   * Return a non-null {@link DefinitionClassContext} if this type is {@link
   * DefinitionClassContext}.
   *
   * @return this with static type of {@link DefinitionClassContext}.
   */
  default DefinitionClassContext asClassContext() {
    return null;
  }

  /**
   * Return a non-null {@link DefinitionFieldContext} if this type is {@link
   * DefinitionFieldContext}.
   *
   * @return this with static type of {@link DefinitionFieldContext}.
   */
  default DefinitionFieldContext asFieldContext() {
    return null;
  }

  /**
   * Return a non-null {@link DefinitionMethodContext} if this type is {@link
   * DefinitionMethodContext}.
   *
   * @return this with static type of {@link DefinitionMethodContext}.
   */
  default DefinitionMethodContext asMethodContext() {
    return null;
  }
}
