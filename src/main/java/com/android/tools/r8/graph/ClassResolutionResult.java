// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.ClassResolutionResult.NoResolutionResult.noResult;

import java.util.function.Consumer;

public interface ClassResolutionResult {

  boolean hasClassResolutionResult();

  DexClass toSingleClassWithProgramOverLibrary();

  void forEachClassResolutionResult(Consumer<DexClass> consumer);

  static Builder builder() {
    return new Builder();
  }

  class Builder {

    private ProgramOrClasspathClass programOrClasspathClass;
    private DexLibraryClass libraryClass;

    public Builder add(DexProgramClass programClass) {
      assert this.programOrClasspathClass == null;
      this.programOrClasspathClass = programClass;
      return this;
    }

    public Builder add(DexClasspathClass classpathClass) {
      assert this.programOrClasspathClass == null;
      this.programOrClasspathClass = classpathClass;
      return this;
    }

    public Builder add(DexLibraryClass libraryClass) {
      assert this.libraryClass == null;
      this.libraryClass = libraryClass;
      return this;
    }

    public ClassResolutionResult build() {
      if (programOrClasspathClass == null && libraryClass == null) {
        return noResult();
      } else if (programOrClasspathClass == null) {
        return libraryClass;
      } else if (libraryClass == null) {
        return programOrClasspathClass;
      } else if (programOrClasspathClass.isProgramClass()) {
        return new ProgramAndLibraryClassResolutionResult(
            programOrClasspathClass.asProgramClass(), libraryClass);
      } else {
        assert programOrClasspathClass.isClasspathClass();
        return new ClasspathAndLibraryClassResolutionResult(
            programOrClasspathClass.asClasspathClass(), libraryClass);
      }
    }
  }

  class NoResolutionResult implements ClassResolutionResult {

    private static final NoResolutionResult NO_RESULT = new NoResolutionResult();

    static ClassResolutionResult noResult() {
      return NO_RESULT;
    }

    @Override
    public boolean hasClassResolutionResult() {
      return false;
    }

    @Override
    public DexClass toSingleClassWithProgramOverLibrary() {
      return null;
    }

    @Override
    public void forEachClassResolutionResult(Consumer<DexClass> consumer) {
      // Intentionally empty
    }
  }

  abstract class MultipleClassResolutionResult<T extends DexClass>
      implements ClassResolutionResult {

    protected final T programOrClasspathClass;
    protected final DexLibraryClass libraryClass;

    public MultipleClassResolutionResult(T programOrClasspathClass, DexLibraryClass libraryClass) {
      this.programOrClasspathClass = programOrClasspathClass;
      this.libraryClass = libraryClass;
    }

    @Override
    public boolean hasClassResolutionResult() {
      return true;
    }

    @Override
    public void forEachClassResolutionResult(Consumer<DexClass> consumer) {
      consumer.accept(programOrClasspathClass);
      consumer.accept(libraryClass);
    }
  }

  class ProgramAndLibraryClassResolutionResult
      extends MultipleClassResolutionResult<DexProgramClass> {

    public ProgramAndLibraryClassResolutionResult(
        DexProgramClass programClass, DexLibraryClass libraryClass) {
      super(programClass, libraryClass);
    }

    @Override
    public DexClass toSingleClassWithProgramOverLibrary() {
      return programOrClasspathClass;
    }
  }

  class ClasspathAndLibraryClassResolutionResult
      extends MultipleClassResolutionResult<DexClasspathClass> {

    public ClasspathAndLibraryClassResolutionResult(
        DexClasspathClass classpathClass, DexLibraryClass libraryClass) {
      super(classpathClass, libraryClass);
    }

    @Override
    public DexClass toSingleClassWithProgramOverLibrary() {
      return libraryClass;
    }
  }
}
