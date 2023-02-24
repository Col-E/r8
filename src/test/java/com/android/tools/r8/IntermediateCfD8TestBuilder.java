// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class IntermediateCfD8TestBuilder
    extends TestBuilder<D8TestRunResult, IntermediateCfD8TestBuilder> {

  public static IntermediateCfD8TestBuilder create(TestState state, AndroidApiLevel apiLevel) {
    assert state != null;
    assert apiLevel != null;
    return new IntermediateCfD8TestBuilder(state, apiLevel);
  }

  private final D8TestBuilder cf2cf;
  private final D8TestBuilder cf2dex;

  private IntermediateCfD8TestBuilder(TestState state, AndroidApiLevel apiLevel) {
    super(state);
    cf2cf = D8TestBuilder.create(state, Backend.CF).setMinApi(apiLevel);
    cf2dex = D8TestBuilder.create(state, Backend.DEX).disableDesugaring().setMinApi(apiLevel);
  }

  public IntermediateCfD8TestBuilder addOptionsModification(Consumer<InternalOptions> fn) {
    cf2cf.addOptionsModification(fn);
    cf2dex.addOptionsModification(fn);
    return self();
  }

  @Override
  IntermediateCfD8TestBuilder self() {
    return this;
  }

  @Override
  public D8TestRunResult run(TestRuntime runtime, String mainClass, String... args)
      throws CompilationFailedException, ExecutionException, IOException {
    return cf2dex.addProgramFiles(cf2cf.compile().writeToZip()).run(runtime, mainClass, args);
  }

  @Override
  public IntermediateCfD8TestBuilder addProgramFiles(Collection<Path> files) {
    cf2cf.addProgramFiles(files);
    return self();
  }

  @Override
  public IntermediateCfD8TestBuilder addProgramClassFileData(Collection<byte[]> classes) {
    cf2cf.addProgramClassFileData(classes);
    return self();
  }

  @Override
  public IntermediateCfD8TestBuilder addProgramDexFileData(Collection<byte[]> data) {
    cf2cf.addProgramDexFileData(data);
    return self();
  }

  @Override
  public IntermediateCfD8TestBuilder addLibraryFiles(Collection<Path> files) {
    cf2cf.addLibraryFiles(files);
    cf2dex.addLibraryFiles(files);
    return self();
  }

  @Override
  public IntermediateCfD8TestBuilder addLibraryClasses(Collection<Class<?>> classes) {
    cf2cf.addLibraryClasses(classes);
    cf2dex.addLibraryClasses(classes);
    return self();
  }

  @Override
  public IntermediateCfD8TestBuilder addClasspathClasses(Collection<Class<?>> classes) {
    cf2cf.addClasspathClasses(classes);
    cf2dex.addClasspathClasses(classes);
    return self();
  }

  @Override
  public IntermediateCfD8TestBuilder addClasspathFiles(Collection<Path> files) {
    cf2cf.addClasspathFiles(files);
    cf2dex.addClasspathFiles(files);
    return self();
  }

  @Override
  public IntermediateCfD8TestBuilder addRunClasspathFiles(Collection<Path> files) {
    cf2dex.addRunClasspathFiles(files);
    return self();
  }
}
