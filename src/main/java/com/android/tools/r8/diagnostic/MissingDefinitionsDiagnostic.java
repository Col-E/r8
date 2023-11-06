// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.util.Collection;

/**
 * Information about items that are not part of the compilation unit, but which are referenced from
 * a reachable program location.
 */
@KeepForApi
public interface MissingDefinitionsDiagnostic extends Diagnostic {

  /**
   * Returns a collection containing information about each of the missing definitions, along with
   * contextual information describing where these missing definitions are referenced from.
   */
  Collection<MissingDefinitionInfo> getMissingDefinitions();
}
