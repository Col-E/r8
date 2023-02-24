// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.utils.Pair;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/** Abstraction to allow setup and execution of multiple test builders. */
public abstract class TestBuilderCollection<
        C extends Enum<C>,
        RR extends TestRunResultCollection<C, RR>,
        T extends TestBuilderCollection<C, RR, T>>
    extends TestBuilder<RR, T> {

  final List<Pair<C, TestBuilder<? extends TestRunResult<?>, ?>>> builders;

  TestBuilderCollection(
      TestState state, List<Pair<C, TestBuilder<? extends TestRunResult<?>, ?>>> builders) {
    super(state);
    assert !builders.isEmpty();
    this.builders = builders;
  }

  private T forEach(Consumer<TestBuilder<? extends TestRunResult<?>, ?>> fn) {
    builders.forEach(b -> fn.accept(b.getSecond()));
    return self();
  }

  @Override
  public T addProgramFiles(Collection<Path> files) {
    return forEach(b -> b.addProgramFiles(files));
  }

  @Override
  public T addProgramClassFileData(Collection<byte[]> classes) {
    return forEach(b -> b.addProgramClassFileData(classes));
  }

  @Override
  public T addProgramDexFileData(Collection<byte[]> data) {
    return forEach(b -> b.addProgramDexFileData(data));
  }

  @Override
  public T addLibraryFiles(Collection<Path> files) {
    return forEach(b -> b.addLibraryFiles(files));
  }

  @Override
  public T addLibraryClasses(Collection<Class<?>> classes) {
    return forEach(b -> b.addLibraryClasses(classes));
  }

  @Override
  public T addClasspathClasses(Collection<Class<?>> classes) {
    return forEach(b -> b.addClasspathClasses(classes));
  }

  @Override
  public T addClasspathFiles(Collection<Path> files) {
    return forEach(b -> b.addClasspathFiles(files));
  }

  @Override
  public T addRunClasspathFiles(Collection<Path> files) {
    return forEach(b -> b.addRunClasspathFiles(files));
  }
}
