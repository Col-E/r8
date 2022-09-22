// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.test;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;

public class CustomLibrarySpecification {

  private final Collection<Path> jars;
  private final Collection<Class<?>> classes;
  private final AndroidApiLevel minApi;

  public CustomLibrarySpecification(Path jar, AndroidApiLevel minApi) {
    this(ImmutableList.of(jar), ImmutableList.of(), minApi);
  }

  public CustomLibrarySpecification(Class<?> clazz, AndroidApiLevel minApi) {
    this(ImmutableList.of(), ImmutableList.of(clazz), minApi);
  }

  public CustomLibrarySpecification(
      Collection<Path> jars, Collection<Class<?>> classes, AndroidApiLevel minApi) {
    this.jars = jars;
    this.classes = classes;
    this.minApi = minApi;
  }

  public D8TestCompileResult compileCustomLibrary(D8TestBuilder builder)
      throws CompilationFailedException {
    return builder.addProgramClasses(classes).addProgramFiles(jars).setMinApi(minApi).compile();
  }

  public void addLibraryClasses(
      TestCompilerBuilder<?, ?, ?, ? extends SingleTestRunResult<?>, ?> builder) {
    builder.addLibraryClasses(classes).addLibraryFiles(jars);
  }
}
