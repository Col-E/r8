// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

/**
 * Interface for providing a custom source file to the compiler.
 *
 * <p>The source file will become the inserted source file attribute in all classes in the residual
 * output program. The source file attribute is present in the stacktraces computed by the JVM and
 * DEX runtimes, thus it can be used to identify builds.
 */
@Keep
@FunctionalInterface
public interface SourceFileProvider {

  /**
   * Return the source file content.
   *
   * @param environment An environment of values available for use when defining the source file.
   */
  String get(SourceFileEnvironment environment);
}
