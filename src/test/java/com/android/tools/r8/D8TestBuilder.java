// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.D8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class D8TestBuilder
    extends TestCompilerBuilder<
        D8Command, Builder, D8TestCompileResult, D8TestRunResult, D8TestBuilder> {

  // Consider an in-order collection of both class and files on the classpath.
  private List<Class<?>> classpathClasses = new ArrayList<>();

  private D8TestBuilder(TestState state, Builder builder) {
    super(state, builder, Backend.DEX);
  }

  public static D8TestBuilder create(TestState state) {
    return new D8TestBuilder(state, D8Command.builder(state.getDiagnosticsHandler()));
  }

  @Override
  D8TestBuilder self() {
    return this;
  }

  @Override
  D8TestCompileResult internalCompile(
      Builder builder, Consumer<InternalOptions> optionsConsumer, Supplier<AndroidApp> app)
      throws CompilationFailedException {
    if (!classpathClasses.isEmpty()) {
      Path cp;
      try {
        cp = getState().getNewTempFolder().resolve("cp.jar");
      } catch (IOException e) {
        throw builder.getReporter().fatalError("Failed to create temp file for classpath archive");
      }
      ArchiveConsumer archiveConsumer = new ArchiveConsumer(cp);
      for (Class<?> classpathClass : classpathClasses) {
        try {
          archiveConsumer.accept(
              ByteDataView.of(ToolHelper.getClassAsBytes(classpathClass)),
              DescriptorUtils.javaTypeToDescriptor(classpathClass.getTypeName()),
              builder.getReporter());
        } catch (IOException e) {
          builder
              .getReporter()
              .error("Failed to read bytes for classpath class: " + classpathClass.getTypeName());
        }
      }
      archiveConsumer.finished(builder.getReporter());
      builder.addClasspathFiles(cp);
    }
    ToolHelper.runD8(builder, optionsConsumer);
    return new D8TestCompileResult(getState(), app.get());
  }

  public D8TestBuilder addClasspathClasses(Class<?>... classes) {
    return addClasspathClasses(Arrays.asList(classes));
  }

  public D8TestBuilder addClasspathClasses(Collection<Class<?>> classes) {
    classpathClasses.addAll(classes);
    return self();
  }

  public D8TestBuilder addClasspathFiles(Path... files) {
    return addClasspathFiles(Arrays.asList(files));
  }

  public D8TestBuilder addClasspathFiles(Collection<Path> files) {
    builder.addClasspathFiles(files);
    return self();
  }

  public D8TestBuilder setIntermediate(boolean b) {
    builder.setIntermediate(true);
    return self();
  }
}
