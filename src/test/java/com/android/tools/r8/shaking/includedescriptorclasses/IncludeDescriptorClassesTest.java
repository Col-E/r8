// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.includedescriptorclasses;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IncludeDescriptorClassesTest extends TestBase {
  private final TestParameters testParameters;

  public IncludeDescriptorClassesTest(TestParameters parameters) {
    this.testParameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}, horizontalClassMerging:{1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  private class Result {
    final CodeInspector inspector;
    final CodeInspector proguardedInspector;

    Result(CodeInspector inspector, CodeInspector proguardedInspector) {
      this.inspector = inspector;
      this.proguardedInspector = proguardedInspector;
    }

    void assertKept(Class<?> clazz) {
      assertTrue(inspector.clazz(clazz.getCanonicalName()).isPresent());
      assertFalse(inspector.clazz(clazz.getCanonicalName()).isRenamed());
      if (proguardedInspector != null) {
        assertTrue(proguardedInspector.clazz(clazz).isPresent());
      }
    }

    // NOTE: 'synchronized' is supposed to disable inlining of this method.
    synchronized void assertRemoved(Class<?> clazz) {
      assertFalse(inspector.clazz(clazz.getCanonicalName()).isPresent());
      if (proguardedInspector != null) {
        assertFalse(proguardedInspector.clazz(clazz).isPresent());
      }
    }

    void assertRenamed(Class<?> clazz) {
      assertTrue(inspector.clazz(clazz.getCanonicalName()).isPresent());
      assertTrue(inspector.clazz(clazz.getCanonicalName()).isRenamed());
      if (proguardedInspector != null) {
        assertTrue(proguardedInspector.clazz(clazz).isPresent());
        assertTrue(proguardedInspector.clazz(clazz).isRenamed());
      }
    }
  }

  private List<Class<?>> applicationClasses =
      ImmutableList.of(
          ClassWithNativeMethods.class,
          NativeArgumentType.class,
          NativeReturnType.class,
          StaticFieldType.class,
          InstanceFieldType.class);
  private List<Class<?>> mainClasses =
      ImmutableList.of(MainCallMethod1.class, MainCallMethod2.class, MainCallMethod3.class);

  Result runTest(ThrowableConsumer<TestShrinkerBuilder<?, ?, ?, ?, ?>> configure) throws Exception {
    return runTest(configure, ignore -> {});
  }

  Result runTest(
      ThrowableConsumer<TestShrinkerBuilder<?, ?, ?, ?, ?>> configure,
      ThrowableConsumer<R8FullTestBuilder> configureR8)
      throws Exception {
    CodeInspector inspector =
        testForR8(testParameters.getBackend())
            .setMinApi(testParameters.getApiLevel())
            .addProgramClasses(applicationClasses)
            .apply(configure::accept)
            .apply(configureR8)
            .compile()
            .inspector();

    CodeInspector proguardedInspector = null;
    // Actually running Proguard should only be during development.
    if (isRunProguard()) {
      proguardedInspector =
          testForProguard()
              .setMinApi(testParameters.getApiLevel())
              .addProgramClasses(applicationClasses)
              .apply(configure::accept)
              .compile()
              .inspector();
    }

    return new Result(inspector, proguardedInspector);
  }

  @Test
  public void testNoIncludesDescriptorClasses() throws Exception {
    for (Class<?> mainClass : mainClasses) {
      List<String> proguardConfig =
          ImmutableList.of(
              "-keepclasseswithmembers class * {   ",
              "  <fields>;                         ",
              "  native <methods>;                 ",
              "}                                   ",
              "-allowaccessmodification            ");

      Result result =
          runTest(
              builder ->
                  builder
                      .addProgramClasses(mainClass)
                      .addKeepMainRule(mainClass)
                      .addKeepRules(proguardConfig));

      result.assertKept(ClassWithNativeMethods.class);
      // Return types are not removed as they can be needed for verification.
      // See b/112517039.
      result.assertRenamed(NativeReturnType.class);
      // Argument type is not removed due to the concern about the broken type hierarchy.
      result.assertRenamed(NativeArgumentType.class);
      // Field type is not removed due to the concern about the broken type hierarchy.
      result.assertRenamed(InstanceFieldType.class);
      result.assertRenamed(StaticFieldType.class);
    }
  }

  @Test
  public void testKeepClassesWithMembers() throws Exception {
    for (Class mainClass : mainClasses) {
      List<String> proguardConfig =
          ImmutableList.of(
              "-keepclasseswithmembers,includedescriptorclasses class * {  ",
              "  <fields>;                                                 ",
              "  native <methods>;                                         ",
              "}                                                           ",
              "-allowaccessmodification                                    ");
      Result result =
          runTest(
              builder ->
                  builder
                      .addProgramClasses(mainClass)
                      .addKeepMainRule(mainClass)
                      .addKeepRules(proguardConfig));

      // With includedescriptorclasses return type, argument type ad field type are not renamed.
      result.assertKept(ClassWithNativeMethods.class);
      result.assertKept(NativeArgumentType.class);
      result.assertKept(NativeReturnType.class);
      result.assertKept(InstanceFieldType.class);
      result.assertKept(StaticFieldType.class);
    }
  }

  @Test
  public void testKeepClassMembers() throws Exception {
    for (Class mainClass : mainClasses) {
      List<String> proguardConfig =
          ImmutableList.of(
              "-keepclassmembers,includedescriptorclasses class * {  ",
              "  <fields>;                                           ",
              "  native <methods>;                                   ",
              "}                                                     ",
              "-allowaccessmodification                              ");

      Result result =
          runTest(
              builder ->
                  builder
                      .addProgramClasses(mainClass)
                      .addKeepMainRule(mainClass)
                      .addKeepRules(proguardConfig));

      // With includedescriptorclasses return type and argument type are not renamed.
      result.assertRenamed(ClassWithNativeMethods.class);
      result.assertKept(NativeArgumentType.class);
      result.assertKept(NativeReturnType.class);
      result.assertKept(InstanceFieldType.class);
      result.assertKept(StaticFieldType.class);
    }
  }

    @Test
    public void testKeepClassMemberNames() throws Exception {
    for (Class<?> mainClass : mainClasses) {
      List<String> proguardConfig =
          ImmutableList.of(
              // same as -keepclassmembers,allowshrinking,includedescriptorclasses
              "-keepclassmembernames,includedescriptorclasses class * {  ",
              "  <fields>;                                               ",
              "  native <methods>;                                       ",
              "}                                                         ",
              "-allowaccessmodification                                  ");

      Result result =
          runTest(
              builder ->
                  builder
                      .addProgramClasses(mainClass)
                      .addKeepMainRule(mainClass)
                      .addKeepRules(proguardConfig));

        boolean useNativeArgumentType =
            mainClass == MainCallMethod1.class || mainClass == MainCallMethod3.class;
        boolean useNativeReturnType =
            mainClass == MainCallMethod2.class || mainClass == MainCallMethod3.class;

        result.assertRenamed(ClassWithNativeMethods.class);
        if (useNativeArgumentType) {
          result.assertKept(NativeArgumentType.class);
        } else {
          result.assertRemoved(NativeArgumentType.class);
        }

        if (useNativeReturnType) {
          result.assertKept(NativeReturnType.class);
        } else {
          result.assertRemoved(NativeReturnType.class);
        }

        result.assertRemoved(InstanceFieldType.class);
        result.assertRemoved(StaticFieldType.class);
      }
    }
}
