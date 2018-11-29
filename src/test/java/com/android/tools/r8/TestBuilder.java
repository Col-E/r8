// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;

import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.utils.ListUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class TestBuilder<RR extends TestRunResult, T extends TestBuilder<RR, T>> {

  private final TestState state;

  public TestBuilder(TestState state) {
    this.state = state;
  }

  public TestState getState() {
    return state;
  }

  abstract T self();

  public abstract RR run(String mainClass)
      throws IOException, CompilationFailedException;

  public RR run(Class mainClass) throws IOException, CompilationFailedException {
    return run(mainClass.getTypeName());
  }

  public abstract DebugTestConfig debugConfig();

  public abstract T addProgramFiles(Collection<Path> files);

  public abstract T addProgramClassFileData(Collection<byte[]> classes);

  public T addProgramClassFileData(byte[]... classes) {
    return addProgramClassFileData(Arrays.asList(classes));
  }

  public T addProgramClasses(Class<?>... classes) {
    return addProgramClasses(Arrays.asList(classes));
  }

  public T addProgramClasses(Collection<Class<?>> classes) {
    return addProgramFiles(getFilesForClasses(classes));
  }

  public T addProgramFiles(Path... files) {
    return addProgramFiles(Arrays.asList(files));
  }

  public T addProgramClassesAndInnerClasses(Class<?>... classes) throws IOException {
    return addProgramClassesAndInnerClasses(Arrays.asList(classes));
  }

  public T addProgramClassesAndInnerClasses(Collection<Class<?>> classes) throws IOException {
    return addProgramClasses(classes).addInnerClasses(classes);
  }

  public T addInnerClasses(Class<?>... classes) throws IOException {
    return addInnerClasses(Arrays.asList(classes));
  }

  public T addInnerClasses(Collection<Class<?>> classes) throws IOException {
    return addProgramFiles(getFilesForInnerClasses(classes));
  }

  public abstract T addLibraryFiles(Collection<Path> files);

  public T addLibraryClasses(Class<?>... classes) {
    return addLibraryClasses(Arrays.asList(classes));
  }

  public T addLibraryClasses(Collection<Class<?>> classes) {
    return addLibraryFiles(getFilesForClasses(classes));
  }

  public T addLibraryFiles(Path... files) {
    return addLibraryFiles(Arrays.asList(files));
  }

  static Collection<Path> getFilesForClasses(Collection<Class<?>> classes) {
    return ListUtils.map(classes, ToolHelper::getClassFileForTestClass);
  }

  static Collection<Path> getFilesForInnerClasses(Collection<Class<?>> classes) throws IOException {
    Set<Path> paths = new HashSet<>();
    for (Class clazz : classes) {
      Path path = ToolHelper.getClassFileForTestClass(clazz);
      String prefix = path.toString().replace(CLASS_EXTENSION, "$");
      paths.addAll(
          ToolHelper.getClassFilesForTestDirectory(
              path.getParent(), p -> p.toString().startsWith(prefix)));
    }
    return paths;
  }
}
