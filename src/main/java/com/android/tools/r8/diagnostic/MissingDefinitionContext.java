// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic;

import com.android.tools.r8.Keep;
import com.android.tools.r8.origin.Origin;

/** A context that references a missing definition in the program, classpath, or library. */
@Keep
public interface MissingDefinitionContext {

  /** The origin of the context. */
  Origin getOrigin();

  /** Predicate that is true iff this is an instance of {@link MissingDefinitionClassContext}. */
  default boolean isClassContext() {
    return false;
  }

  /** Predicate that is true iff this is an instance of {@link MissingDefinitionFieldContext}. */
  default boolean isFieldContext() {
    return false;
  }

  /** Predicate that is true iff this is an instance of {@link MissingDefinitionMethodContext}. */
  default boolean isMethodContext() {
    return false;
  }

  /**
   * Return a non-null {@link MissingDefinitionClassContext} if this type is {@link
   * MissingDefinitionClassContext}.
   *
   * @return this with static type of {@link MissingDefinitionClassContext}.
   */
  default MissingDefinitionClassContext asClassContext() {
    return null;
  }

  /**
   * Return a non-null {@link MissingDefinitionFieldContext} if this type is {@link
   * MissingDefinitionFieldContext}.
   *
   * @return this with static type of {@link MissingDefinitionFieldContext}.
   */
  default MissingDefinitionFieldContext asFieldContext() {
    return null;
  }

  /**
   * Return a non-null {@link MissingDefinitionMethodContext} if this type is {@link
   * MissingDefinitionMethodContext}.
   *
   * @return this with static type of {@link MissingDefinitionMethodContext}.
   */
  default MissingDefinitionMethodContext asMethodContext() {
    return null;
  }
}
