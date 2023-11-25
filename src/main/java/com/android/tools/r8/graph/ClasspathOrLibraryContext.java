// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

/**
 * A classpath or library definition that is guaranteed to be derived from the tracing of a program
 * context.
 */
public class ClasspathOrLibraryContext implements ProgramDerivedContext {

  private final Definition context;

  @SuppressWarnings("UnusedVariable")
  private final ProgramDerivedContext programDerivedContext;

  private ClasspathOrLibraryContext(
      Definition context, ProgramDerivedContext programDerivedContext) {
    this.context = context;
    this.programDerivedContext = programDerivedContext;
  }

  public static ClasspathOrLibraryContext create(
      ClasspathDefinition context, ProgramDerivedContext programDerivedContext) {
    return new ClasspathOrLibraryContext(context, programDerivedContext);
  }

  public static ClasspathOrLibraryContext create(
      LibraryDefinition context, ProgramDerivedContext programDerivedContext) {
    return new ClasspathOrLibraryContext(context, programDerivedContext);
  }

  @Override
  public Definition getContext() {
    return context;
  }
}
