// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

/**
 * The context used for the {@link ParameterUsages} lattice.
 *
 * <p>By only using {@link DefaultAnalysisContext} a context-insensitive result is achieved.
 * Context-sensitive results can be achieved by forking different analysis contexts during the class
 * inliner constraint analysis.
 */
public abstract class AnalysisContext {

  public static DefaultAnalysisContext getDefaultContext() {
    return DefaultAnalysisContext.getInstance();
  }
}
