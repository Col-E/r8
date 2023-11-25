// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/**
 * Base interface for any element in a retrace result.
 *
 * <p>The element represents an unambiguous retracing.
 */
@KeepForApi
public interface RetraceElement<R extends RetraceResult<?>> {

  R getParentResult();

  boolean isCompilerSynthesized();
}
