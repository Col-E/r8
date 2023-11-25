// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/**
 * Interface for providing a custom source file to the compiler.
 *
 * <p>The source file will become the inserted source file attribute in all classes in the residual
 * output program. The source file attribute is present in the stacktraces computed by the JVM and
 * DEX runtimes, thus it can be used to identify builds.
 */
@KeepForApi
@FunctionalInterface
public interface SourceFileProvider {

  /**
   * Return the source file content.
   *
   * @param environment An environment of values available for use when defining the source file.
   */
  String get(SourceFileEnvironment environment);

  /**
   * Allow producing outputs that might not always include the source file in stack traces.
   *
   * <p>Note that this does not affect the ability to retrace a stack trace. It is indented to
   * ensure that the non-original/residual source file attribute will be printed in all stack traces
   * on the supported VMs.
   *
   * @return True if the compiler may discard source file information (default false).
   */
  default boolean allowDiscardingSourceFile() {
    return false;
  }
}
