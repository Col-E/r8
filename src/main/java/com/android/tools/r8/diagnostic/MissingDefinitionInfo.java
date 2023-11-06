// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.util.Collection;

/**
 * Information about the contexts that references an item that was not part of the compilation unit.
 */
@KeepForApi
public interface MissingDefinitionInfo {

  /**
   * Predicate that is true iff the MissingDefinitionInfo is an instance of {@link
   * MissingClassInfo}.
   */
  default boolean isMissingClass() {
    return false;
  }

  /**
   * Predicate that is true iff the MissingDefinitionInfo is an instance of {@link
   * MissingFieldInfo}.
   */
  default boolean isMissingField() {
    return false;
  }

  /**
   * Predicate that is true iff the MissingDefinitionInfo is an instance of {@link
   * MissingMethodInfo}.
   */
  default boolean isMissingMethod() {
    return false;
  }

  /**
   * Return a non-null {@link MissingClassInfo} if this type is {@link MissingClassInfo}.
   *
   * @return this with static type of {@link MissingClassInfo}.
   */
  default MissingClassInfo asMissingClass() {
    return null;
  }

  /**
   * Return a non-null {@link MissingFieldInfo} if this type is {@link MissingFieldInfo}.
   *
   * @return this with static type of {@link MissingFieldInfo}.
   */
  default MissingFieldInfo asMissingField() {
    return null;
  }

  /**
   * Return a non-null {@link MissingMethodInfo} if this type is {@link MissingMethodInfo}.
   *
   * @return this with static type of {@link MissingMethodInfo}.
   */
  default MissingMethodInfo asMissingMethod() {
    return null;
  }

  /** User friendly description of the missing definition. */
  String getDiagnosticMessage();

  /** The contexts from which this missing definition was referenced. */
  Collection<DefinitionContext> getReferencedFromContexts();
}
