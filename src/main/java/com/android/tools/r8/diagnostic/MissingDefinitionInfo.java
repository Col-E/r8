// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic;

import com.android.tools.r8.Keep;
import java.util.Collection;

/**
 * Information about the contexts that references an item that was not part of the compilation unit.
 */
@Keep
public interface MissingDefinitionInfo {

  /** The contexts from which this missing definition was referenced. */
  Collection<MissingDefinitionContext> getReferencedFromContexts();
}
