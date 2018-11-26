// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.debug.CfDebugTestConfig;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ListUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class JvmTestBuilder extends TestBuilder<JvmTestBuilder> {

  private static class ClassFileResource implements ProgramResource {

    private final Path file;
    private final String descriptor;
    private final Origin origin;

    ClassFileResource(Class<?> clazz) {
      this(
          ToolHelper.getClassFileForTestClass(clazz),
          DescriptorUtils.javaTypeToDescriptor(clazz.getTypeName()));
    }

    ClassFileResource(Path file, String descriptor) {
      this.file = file;
      this.descriptor = descriptor;
      origin = new PathOrigin(file);
    }

    @Override
    public Kind getKind() {
      return Kind.CF;
    }

    @Override
    public InputStream getByteStream() throws ResourceException {
      try {
        return Files.newInputStream(file);
      } catch (IOException e) {
        throw new ResourceException(getOrigin(), e);
      }
    }

    @Override
    public Set<String> getClassDescriptors() {
      return Collections.singleton(descriptor);
    }

    @Override
    public Origin getOrigin() {
      return origin;
    }
  }

  private static class ClassFileResourceProvider implements ProgramResourceProvider {

    private final List<ProgramResource> resources;

    public ClassFileResourceProvider(List<ProgramResource> resources) {
      this.resources = resources;
    }

    @Override
    public Collection<ProgramResource> getProgramResources() throws ResourceException {
      return resources;
    }

    @Override
    public DataResourceProvider getDataResourceProvider() {
      return null;
    }
  }

  // Ordered list of classpath entries.
  private List<Path> classpath = new ArrayList<>();

  private AndroidApp.Builder builder = AndroidApp.builder();

  private JvmTestBuilder(TestState state) {
    super(state);
  }

  public static JvmTestBuilder create(TestState state) {
    return new JvmTestBuilder(state);
  }

  @Override
  JvmTestBuilder self() {
    return this;
  }

  @Override
  public TestRunResult run(String mainClass) throws IOException {
    ProcessResult result = ToolHelper.runJava(classpath, mainClass);
    return new TestRunResult(builder.build(), result);
  }

  @Override
  public DebugTestConfig debugConfig() {
    return new CfDebugTestConfig().addPaths(classpath);
  }

  @Override
  public JvmTestBuilder addLibraryFiles(Collection<Path> files) {
    throw new Unimplemented("No support for changing the Java runtime library.");
  }

  @Override
  public JvmTestBuilder addProgramClasses(Collection<Class<?>> classes) {
    // Adding a collection of classes will build a jar of exactly those classes so that no other
    // classes are made available via a too broad classpath directory.
    List<ProgramResource> resources = ListUtils.map(classes, ClassFileResource::new);
    AndroidApp build = AndroidApp.builder()
        .addProgramResourceProvider(new ClassFileResourceProvider(resources)).build();
    Path out;
    try {
      out = getState().getNewTempFolder().resolve("out.zip");
      build.writeToZip(out, OutputMode.ClassFile);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    classpath.add(out);
    builder.addProgramFiles(out);
    return self();
  }

  @Override
  public JvmTestBuilder addProgramFiles(Collection<Path> files) {
    throw new Unimplemented(
        "No support for adding paths directly (we need to compute the descriptor)");
  }

  @Override
  public JvmTestBuilder addProgramClassFileData(Collection<byte[]> files) {
    throw new Unimplemented(
        "No support for adding classfile data directly (we need to compute the descriptor)");
  }

  public JvmTestBuilder addClasspath(Path... paths) {
    return addClasspath(Arrays.asList(paths));
  }

  public JvmTestBuilder addClasspath(List<Path> paths) {
    for (Path path : paths) {
      assert Files.isDirectory(path) || FileUtils.isArchive(path);
      classpath.add(path);
    }
    return self();
  }

  public JvmTestBuilder addTestClasspath() {
    return addClasspath(ToolHelper.getClassPathForTests());
  }
}
