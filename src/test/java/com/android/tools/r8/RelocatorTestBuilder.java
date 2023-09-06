// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.TestBase.extractClassDescriptor;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.PackageReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.relocator.Relocator;
import com.android.tools.r8.relocator.RelocatorCommand;
import com.android.tools.r8.relocator.RelocatorCommandLine;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class RelocatorTestBuilder
    extends TestBuilder<RelocatorTestCompileResult, RelocatorTestBuilder> {

  private final boolean isExternal;
  private final List<String> commandLineArgs = new ArrayList<>();
  private final RelocatorCommand.Builder commandBuilder = RelocatorCommand.builder();
  private Path output = null;

  private RelocatorTestBuilder(TestState state, boolean isExternal) {
    super(state);
    this.isExternal = isExternal;
  }

  public static RelocatorTestBuilder create(TestState state, boolean external) {
    return new RelocatorTestBuilder(state, external);
  }

  @Override
  RelocatorTestBuilder self() {
    return this;
  }

  public RelocatorTestCompileResult run() throws Exception {
    if (output == null) {
      setOutputPath(getState().getNewTempFolder().resolve("output.jar"));
    }
    if (isExternal) {
      RelocatorCommandLine.run(commandLineArgs.toArray(new String[0]));
    } else {
      Relocator.run(commandBuilder.build());
    }
    return new RelocatorTestCompileResult(output);
  }

  public RelocatorTestBuilder setOutputPath(Path path) {
    assert output == null;
    output = path;
    if (isExternal) {
      commandLineArgs.add("--output");
      commandLineArgs.add(path.toString());
    } else {
      commandBuilder.setOutputPath(path);
    }
    return self();
  }

  @Override
  public RelocatorTestCompileResult run(TestRuntime runtime, String mainClass, String... args)
      throws CompilationFailedException, ExecutionException, IOException {
    throw new Unreachable("Not implemented - use run()");
  }

  @Override
  public RelocatorTestBuilder addProgramFiles(Collection<Path> files) {
    if (isExternal) {
      files.forEach(
          file -> {
            commandLineArgs.add("--input");
            commandLineArgs.add(file.toString());
          });
    } else {
      commandBuilder.addProgramFiles(files);
    }
    return self();
  }

  @Override
  public RelocatorTestBuilder addProgramClassFileData(Collection<byte[]> classes) {
    try {
      Path resolve = getState().getNewTempFolder().resolve("input.jar");
      ClassFileConsumer inputConsumer = new ClassFileConsumer.ArchiveConsumer(resolve);
      for (byte[] clazz : classes) {
        inputConsumer.accept(ByteDataView.of(clazz), extractClassDescriptor(clazz), null);
      }
      inputConsumer.finished(null);
      addProgramFiles(resolve);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return self();
  }

  @Override
  public RelocatorTestBuilder addProgramDexFileData(Collection<byte[]> data) {
    throw new Unimplemented("No support for adding dex file data directly");
  }

  @Override
  public RelocatorTestBuilder addLibraryFiles(Collection<Path> files) {
    throw new Unimplemented("No support for adding library");
  }

  @Override
  public RelocatorTestBuilder addLibraryClasses(Collection<Class<?>> classes) {
    throw new Unimplemented("No support for adding library");
  }

  @Override
  public RelocatorTestBuilder addClasspathClasses(Collection<Class<?>> classes) {
    throw new Unimplemented("No support for adding classpath");
  }

  @Override
  public RelocatorTestBuilder addClasspathFiles(Collection<Path> files) {
    throw new Unimplemented("No support for adding classpath");
  }

  @Override
  public RelocatorTestBuilder addRunClasspathFiles(Collection<Path> files) {
    throw new Unimplemented("No support for adding run classpath");
  }

  public RelocatorTestBuilder addPackageMapping(PackageReference from, PackageReference to) {
    if (isExternal) {
      commandLineArgs.add("--map");
      commandLineArgs.add(from.getPackageName() + ".*->" + to.getPackageName());
    } else {
      commandBuilder.addPackageMapping(from, to);
    }
    return self();
  }

  public RelocatorTestBuilder addPackageAndAllSubPackagesMapping(
      PackageReference from, PackageReference to) {
    if (isExternal) {
      commandLineArgs.add("--map");
      commandLineArgs.add(from.getPackageName() + ".**->" + to.getPackageName());
    } else {
      commandBuilder.addSubPackageMapping(from, to);
    }
    return self();
  }

  public RelocatorTestBuilder addClassMapping(ClassReference from, ClassReference to) {
    if (isExternal) {
      commandLineArgs.add("--map");
      commandLineArgs.add(from.getTypeName() + "->" + to.getTypeName());
    } else {
      commandBuilder.addClassMapping(from, to);
    }
    return self();
  }

  public RelocatorTestBuilder addPackageMapping(String from, String to) {
    return addPackageMapping(Reference.packageFromString(from), Reference.packageFromString(to));
  }

  public RelocatorTestBuilder addPackageAndAllSubPackagesMapping(String from, String to) {
    return addPackageAndAllSubPackagesMapping(
        Reference.packageFromString(from), Reference.packageFromString(to));
  }
}
