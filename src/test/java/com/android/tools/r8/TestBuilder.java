// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class TestBuilder<RR extends TestRunResult<RR>, T extends TestBuilder<RR, T>> {

  private final TestState state;

  public TestBuilder(TestState state) {
    this.state = state;
  }

  public TestState getState() {
    return state;
  }

  abstract T self();

  public <S, E extends Throwable> S map(ThrowingFunction<T, S, E> fn) {
    return fn.applyWithRuntimeException(self());
  }

  public T apply(ThrowableConsumer<T> fn) {
    if (fn != null) {
      fn.acceptWithRuntimeException(self());
    }
    return self();
  }

  public T applyIf(boolean value, ThrowableConsumer<T> consumer) {
    T self = self();
    if (value) {
      consumer.acceptWithRuntimeException(self);
    }
    return self;
  }

  public T applyIf(
      boolean value, ThrowableConsumer<T> trueConsumer, ThrowableConsumer<T> falseConsumer) {
    T self = self();
    if (value) {
      trueConsumer.acceptWithRuntimeException(self);
    } else {
      falseConsumer.acceptWithRuntimeException(self);
    }
    return self;
  }

  public T applyIf(
      boolean value,
      ThrowableConsumer<T> trueConsumer,
      boolean value2,
      ThrowableConsumer<T> trueConsumer2,
      ThrowableConsumer<T> falseConsumer) {
    T self = self();
    if (value) {
      trueConsumer.acceptWithRuntimeException(self);
    } else if (value2) {
      trueConsumer2.acceptWithRuntimeException(self);
    } else {
      falseConsumer.acceptWithRuntimeException(self);
    }
    return self;
  }

  @Deprecated
  public RR run(String mainClass)
      throws CompilationFailedException, ExecutionException, IOException {
    throw new Unimplemented("Deprecated");
  }

  public abstract RR run(TestRuntime runtime, String mainClass, String... args)
      throws CompilationFailedException, ExecutionException, IOException;

  @Deprecated
  public RR run(Class<?> mainClass)
      throws CompilationFailedException, ExecutionException, IOException {
    return run(mainClass.getTypeName());
  }

  public RR run(TestRuntime runtime, Class<?> mainClass, String... args)
      throws CompilationFailedException, ExecutionException, IOException {
    return run(runtime, mainClass.getTypeName(), args);
  }

  public abstract T addProgramFiles(Collection<Path> files);

  public abstract T addProgramClassFileData(Collection<byte[]> classes);

  public T addProgramClassFileData(byte[]... classes) {
    return addProgramClassFileData(Arrays.asList(classes));
  }

  public abstract T addProgramDexFileData(Collection<byte[]> data);

  public T addProgramDexFileData(byte[]... data) {
    return addProgramDexFileData(Arrays.asList(data));
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

  public T addAndroidResources(AndroidTestResource testResource) throws IOException {
    return addProgramClassFileData(testResource.getRClass().getClassFileData());
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

  public abstract T addLibraryClasses(Collection<Class<?>> classes);

  public T addLibraryFiles(Path... files) {
    return addLibraryFiles(Arrays.asList(files));
  }

  public T addLibraryClassFileData(byte[]... classes) {
    return addLibraryClassFileData(Arrays.asList(classes));
  }

  public T addLibraryClassFileData(Collection<byte[]> classes) {
    return addByteCollectionToJar("library.jar", classes, this::addLibraryFiles);
  }

  public T addDefaultRuntimeLibrary(TestParameters parameters) {
    if (parameters.getBackend() == Backend.DEX) {
      addLibraryFiles(ToolHelper.getFirstSupportedAndroidJar(parameters.getApiLevel()));
    } else {
      assert parameters.getBackend() == Backend.CF;
      addLibraryFiles(ToolHelper.getJava8RuntimeJar());
    }
    return self();
  }

  public T addDefaultRuntimeLibraryWithReflectiveOperationException(TestParameters parameters) {
    if (parameters.getBackend() == Backend.DEX) {
      addLibraryFiles(
          ToolHelper.getFirstSupportedAndroidJar(
              Ordered.max(parameters.getApiLevel(), AndroidApiLevel.K)));
    } else {
      addDefaultRuntimeLibrary(parameters);
    }
    return self();
  }

  public T addClasspathClasses(Class<?>... classes) {
    return addClasspathClasses(Arrays.asList(classes));
  }

  public abstract T addClasspathClasses(Collection<Class<?>> classes);

  public T addClasspathFiles(Path... files) {
    return addClasspathFiles(Arrays.asList(files));
  }

  public abstract T addClasspathFiles(Collection<Path> files);

  public T addClasspathClassFileData(byte[]... classes) {
    return addClasspathClassFileData(Arrays.asList(classes));
  }

  public T addClasspathClassFileData(Collection<byte[]> classes) {
    return addByteCollectionToJar("cp.jar", classes, this::addClasspathFiles);
  }

  private T addByteCollectionToJar(
      String name, Collection<byte[]> classes, Function<Path, T> outputConsumer) {
    Path out;
    try {
      out = getState().getNewTempFolder().resolve(name);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    ArchiveConsumer consumer = new ArchiveConsumer(out);
    for (byte[] bytes : classes) {
      consumer.accept(ByteDataView.of(bytes), TestBase.extractClassDescriptor(bytes), null);
    }
    consumer.finished(null);
    return outputConsumer.apply(out);
  }

  public final T addTestingAnnotationsAsProgramClasses() {
    return addProgramClasses(getTestingAnnotations());
  }

  public final T addTestingAnnotationsAsLibraryClasses() {
    return addLibraryClasses(getTestingAnnotations());
  }

  public static List<Class<?>> getTestingAnnotations() {
    return ImmutableList.of(
        AlwaysInline.class,
        AssumeMayHaveSideEffects.class,
        KeepConstantArguments.class,
        KeepUnusedArguments.class,
        NeverClassInline.class,
        NeverInline.class,
        NoVerticalClassMerging.class,
        NoHorizontalClassMerging.class,
        NeverPropagateValue.class);
  }

  static Collection<Path> getFilesForClasses(Collection<Class<?>> classes) {
    return ListUtils.map(classes, ToolHelper::getClassFileForTestClass);
  }

  static Collection<Path> getFilesForInnerClasses(Collection<Class<?>> classes) throws IOException {
    return ToolHelper.getClassFilesForInnerClasses(classes);
  }

  public abstract T addRunClasspathFiles(Collection<Path> files);

  public T addRunClasspathFiles(Path... files) {
    return addRunClasspathFiles(Arrays.asList(files));
  }

  public T setDiagnosticsLevelModifier(
      BiFunction<DiagnosticsLevel, Diagnostic, DiagnosticsLevel> modifier) {
    getState().setDiagnosticsLevelModifier(modifier);
    return self();
  }

  public T allowStdoutMessages() {
    // Default ignored.
    return self();
  }

  public DebugTestConfig debugConfig(TestRuntime runtime) throws Exception {
    throw new Unimplemented("Not implemented for this test builder");
  }
}
