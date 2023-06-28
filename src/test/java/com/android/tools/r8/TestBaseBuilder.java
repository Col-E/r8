// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public abstract class TestBaseBuilder<
        C extends BaseCommand,
        B extends BaseCommand.Builder<C, B>,
        CR extends TestBaseResult<CR, RR>,
        RR extends TestRunResult<RR>,
        T extends TestBaseBuilder<C, B, CR, RR, T>>
    extends TestBuilder<RR, T> {

  final B builder;

  TestBaseBuilder(TestState state, B builder) {
    super(state);
    this.builder = builder;
  }

  @Override
  public T addProgramClassFileData(Collection<byte[]> classes) {
    for (byte[] clazz : classes) {
      builder.addClassProgramData(clazz, Origin.unknown());
    }
    return self();
  }

  @Override
  public T addProgramDexFileData(Collection<byte[]> data) {
    for (byte[] dex : data) {
      builder.addDexProgramData(dex, Origin.unknown());
    }
    return self();
  }

  @Override
  public T addProgramFiles(Collection<Path> files) {
    builder.addProgramFiles(files);
    return self();
  }

  public T addLibraryProvider(ClassFileResourceProvider provider) {
    builder.addLibraryResourceProvider(provider);
    return self();
  }

  @Override
  public T addLibraryFiles(Collection<Path> files) {
    builder.addLibraryFiles(files);
    return self();
  }

  @Override
  public T addLibraryClasses(Collection<Class<?>> classes) {
    builder.addLibraryResourceProvider(ClassFileResourceProviderFromClasses(classes));
    return self();
  }

  public T addMainDexListClassReferences(ClassReference... classes) {
    return addMainDexListClassReferences(Arrays.asList(classes));
  }

  public T addMainDexListClassReferences(Collection<ClassReference> classes) {
    classes.forEach(c -> builder.addMainDexClasses(c.getTypeName()));
    return self();
  }

  public T addMainDexListClasses(Class<?>... classes) {
    return addMainDexListClasses(Arrays.asList(classes));
  }

  public T addMainDexListClasses(Collection<Class<?>> classes) {
    return addMainDexListClassReferences(ListUtils.map(classes, Reference::classFromClass));
  }

  public T addMainDexListFiles(Path... files) {
    builder.addMainDexListFiles(files);
    return self();
  }

  public T addMainDexListFiles(Collection<Path> files) {
    builder.addMainDexListFiles(files);
    return self();
  }

  public static ClassFileResourceProvider ClassFileResourceProviderFromClasses(
      Collection<Class<?>> classes) {
    return new ClassFileResourceProvider() {
      final Map<String, ProgramResource> resources;

      {
        ImmutableMap.Builder<String, ProgramResource> builder = ImmutableMap.builder();
        classes.forEach(
            c ->
                builder.put(
                    DescriptorUtils.javaTypeToDescriptor(c.getTypeName()),
                    ProgramResource.fromFile(Kind.CF, ToolHelper.getClassFileForTestClass(c))));
        resources = builder.build();
      }

      @Override
      public Set<String> getClassDescriptors() {
        return resources.keySet();
      }

      @Override
      public ProgramResource getProgramResource(String descriptor) {
        return resources.get(descriptor);
      }
    };
  }
}
