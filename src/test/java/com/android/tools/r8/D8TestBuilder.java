// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.D8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

public class D8TestBuilder extends TestCompilerBuilder<D8Command, Builder, D8TestBuilder> {

  private final D8Command.Builder builder;

  private D8TestBuilder(TestState state, D8Command.Builder builder) {
    super(state, builder, Backend.DEX);
    this.builder = builder;
  }

  public static D8TestBuilder create(TestState state) {
    return new D8TestBuilder(state, D8Command.builder());
  }

  @Override
  D8TestBuilder self() {
    return this;
  }

  @Override
  void internalCompile(Builder builder) throws CompilationFailedException {
    D8.run(builder.build());
  }

  public D8TestBuilder addClasspathClasses(Class<?>... classes) {
    return addClasspathClasses(Arrays.asList(classes));
  }

  public D8TestBuilder addClasspathClasses(Collection<Class<?>> classes) {
    return addClasspathFiles(getFilesForClasses(classes));
  }

  public D8TestBuilder addClasspathFiles(Path... files) {
    return addClasspathFiles(Arrays.asList(files));
  }

  public D8TestBuilder addClasspathFiles(Collection<Path> files) {
    builder.addClasspathFiles(files);
    return self();
  }
}
