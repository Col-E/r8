// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.D8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// The type arguments D8Command, Builder is not relevant for running DX.
public class DXTestBuilder
    extends TestCompilerBuilder<
        D8Command, Builder, DXTestCompileResult, DXTestRunResult, DXTestBuilder> {

  // Ordered list of injar entries.
  private List<Path> injars = new ArrayList<>();

  private DXTestBuilder(TestState state) {
    super(state, D8Command.builder(), Backend.DEX);
  }

  public static DXTestBuilder create(TestState state) {
    return new DXTestBuilder(state);
  }

  @Override
  DXTestBuilder self() {
    return this;
  }

  @Override
  DXTestCompileResult internalCompile(
      Builder builder, Consumer<InternalOptions> optionsConsumer, Supplier<AndroidApp> app)
      throws CompilationFailedException {
    try {
      Path dxOutputFolder = getState().getNewTempFolder();
      Path outJar = dxOutputFolder.resolve("output.jar");

      List<String> args = new ArrayList<>();
      args.add("--min-sdk-version=" + minApiLevel);
      args.add("--output=" + outJar.toString());
      args.addAll(injars.stream().map(Path::toString).collect(Collectors.toList()));
      ProcessResult result =
          ToolHelper.runProcess(
              ToolHelper.createProcessBuilderForRunningDx(args.toArray(StringUtils.EMPTY_ARRAY)),
              getStdoutForTesting());
      if (result.exitCode != 0) {
        throw new CompilationFailedException(result.toString());
      }
      return new DXTestCompileResult(
          getState(), AndroidApp.builder().addProgramFile(outJar).build(), minApiLevel);
    } catch (IOException e) {
      throw new CompilationFailedException(e);
    }
  }

  @Override
  public DXTestBuilder addClasspathClasses(Collection<Class<?>> classes) {
    throw new Unimplemented("No support for adding classpath data directly");
  }

  @Override
  public DXTestBuilder addClasspathFiles(Collection<Path> files) {
    throw new Unimplemented("No support for adding classpath data directly");
  }

  @Override
  public DXTestBuilder addProgramClasses(Collection<Class<?>> classes) {
    return addProgramClassFileData(
        ListUtils.map(
            classes,
            c -> {
              try {
                return ToolHelper.getClassAsBytes(c);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            }));
  }

  @Override
  public DXTestBuilder addProgramClassFileData(Collection<byte[]> classes) {
    try {
      Path out = getState().getNewTempFolder().resolve("out.jar");
      ArchiveConsumer consumer = new ArchiveConsumer(out);
      for (byte[] clazz : classes) {
        String descriptor = TestBase.extractClassDescriptor(clazz);
        consumer.accept(ByteDataView.of(clazz), descriptor, null);
      }
      consumer.finished(null);
      addProgramFiles(out);
      return self();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public DXTestBuilder addProgramDexFileData(Collection<byte[]> data) {
    throw new Unimplemented("No support for adding dex file data directly");
  }

  @Override
  public DXTestBuilder addProgramFiles(Collection<Path> files) {
    injars.addAll(files);
    return self();
  }
}
