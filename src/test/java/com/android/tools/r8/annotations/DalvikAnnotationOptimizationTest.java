// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.annotations;

import static org.junit.Assert.assertEquals;
import static org.objectweb.asm.Opcodes.ASM7;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.AnnotationVisitor;

@RunWith(Parameterized.class)
public class DalvikAnnotationOptimizationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean optimizationPackage;

  @Parameter(2)
  public boolean addAnnotationsOnLibraryPath;

  @Parameters(name = "{0}, optimizationPackage = {1}, addAnnotationsOnLibraryPath = {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevels().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  private static final String dalvikOptimizationPrefix =
      DexItemFactory.dalvikAnnotationOptimizationPrefixString;
  private static final String dalvikCodegenPrefix = "Ldalvik/annotation/codegen/";
  private static final String ourClassName =
      DescriptorUtils.javaTypeToDescriptor(DalvikAnnotationOptimizationTest.class.getTypeName());
  private static final String innerClassPrefix =
      ourClassName.substring(0, ourClassName.length() - 1) + "$";

  private static String changePackage(boolean optimizationPackage, String descriptor) {
    return (optimizationPackage ? dalvikOptimizationPrefix : dalvikCodegenPrefix)
        + descriptor.substring(innerClassPrefix.length());
  }

  private void checkExpectedAnnotations(CodeInspector inspector) {
    Set<String> expected =
        optimizationPackage
            ? ImmutableSet.of(
                dalvikOptimizationPrefix + "CriticalNative;",
                dalvikOptimizationPrefix + "FastNative;",
                dalvikOptimizationPrefix + "NeverCompile;",
                dalvikOptimizationPrefix + "AndAnotherOne;")
            : ImmutableSet.of();
    assertEquals(
        expected,
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("main").annotations().stream()
            .map(s -> s.getAnnotation().type.getDescriptor().toSourceString())
            .collect(Collectors.toSet()));
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClassFileData(
            transformer(TestClass.class).addMethodTransformer(getMethodTransformer()).transform())
        .setMinApi(parameters)
        .applyIf(
            addAnnotationsOnLibraryPath,
            b -> {
              b.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST));
              b.addLibraryProvider(new ClassPathProviderForAnnotations(optimizationPackage));
            })
        .compile()
        .inspect(this::checkExpectedAnnotations);
  }

  private MethodTransformer getMethodTransformer() {
    return new MethodTransformer() {
      @Override
      public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
        assert descriptor.startsWith(innerClassPrefix);
        return new AnnotationVisitor(
            ASM7,
            super.visitAnnotation(changePackage(optimizationPackage, descriptor), visible)) {};
      }
    };
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(TestClass.class).addMethodTransformer(getMethodTransformer()).transform())
        .applyIf(
            addAnnotationsOnLibraryPath,
            b -> {
              b.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST));
              b.addLibraryProvider(new ClassPathProviderForAnnotations(optimizationPackage));
            },
            b ->
                b.addDontWarn(
                    optimizationPackage
                        ? "dalvik.annotation.optimization.*"
                        : "dalvik.annotation.codegen.*"))
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        .compile()
        .inspect(this::checkExpectedAnnotations);
  }

  static class ClassPathProviderForAnnotations implements ClassFileResourceProvider {
    private final boolean optimizationPackage;
    private final Map<String, byte[]> resources = new HashMap<>();

    ClassPathProviderForAnnotations(boolean optimizationPackage) throws IOException {
      this.optimizationPackage = optimizationPackage;
      for (Class<?> clazz :
          new Class<?>[] {
            CriticalNative.class, FastNative.class, NeverCompile.class, AndAnotherOne.class
          }) {
        resources.put(
            changePackage(
                optimizationPackage, DescriptorUtils.javaTypeToDescriptor(clazz.getTypeName())),
            transformPackageName(clazz));
      }
    }

    @Override
    public Set<String> getClassDescriptors() {
      return resources.keySet();
    }

    @Override
    public ProgramResource getProgramResource(String descriptor) {
      byte[] bytes = resources.get(descriptor);
      return bytes == null
          ? null
          : ProgramResource.fromBytes(
              Origin.unknown(), Kind.CF, bytes, Collections.singleton(descriptor));
    }

    private byte[] transformPackageName(Class<?> clazz) throws IOException {
      return transformer(clazz)
          .setClassDescriptor(
              changePackage(
                  optimizationPackage, DescriptorUtils.javaTypeToDescriptor(clazz.getTypeName())))
          .transform();
    }
  }

  // Keep all CLASS retention annotations in the dalvik.annotation.optimization package.
  // See b/209701182
  @Retention(RetentionPolicy.CLASS)
  @interface CriticalNative {}

  @Retention(RetentionPolicy.CLASS)
  @interface FastNative {}

  @Retention(RetentionPolicy.CLASS)
  @interface NeverCompile {}

  @Retention(RetentionPolicy.CLASS)
  @interface AndAnotherOne {}

  static class TestClass {
    @CriticalNative
    @FastNative
    @NeverCompile
    @AndAnotherOne
    public static void main(String[] args) {}
  }
}
