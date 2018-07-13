// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.includedescriptorclasses;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class IncludeDescriptorClassesTest extends TestBase {
  private class Result {
    final DexInspector inspector;
    final DexInspector proguardedInspector;

    Result(DexInspector inspector, DexInspector proguardedInspector) {
      this.inspector = inspector;
      this.proguardedInspector = proguardedInspector;
    }

    void assertKept(Class clazz) {
      assertTrue(inspector.clazz(clazz.getCanonicalName()).isPresent());
      assertFalse(inspector.clazz(clazz.getCanonicalName()).isRenamed());
      if (proguardedInspector != null) {
        assertTrue(proguardedInspector.clazz(clazz).isPresent());
      }
    }

    // NOTE: 'synchronized' is supposed to disable inlining of this method.
    synchronized void assertRemoved(Class clazz) {
      assertFalse(inspector.clazz(clazz.getCanonicalName()).isPresent());
      if (proguardedInspector != null) {
        assertFalse(proguardedInspector.clazz(clazz).isPresent());
      }
    }

    void assertRenamed(Class clazz) {
      assertTrue(inspector.clazz(clazz.getCanonicalName()).isPresent());
      assertTrue(inspector.clazz(clazz.getCanonicalName()).isRenamed());
      if (proguardedInspector != null) {
        assertTrue(proguardedInspector.clazz(clazz).isPresent());
        assertTrue(proguardedInspector.clazz(clazz).isRenamed());
      }
    }
  }

  private List<Class> applicationClasses = ImmutableList.of(
      ClassWithNativeMethods.class, NativeArgumentType.class, NativeReturnType.class,
      StaticFieldType.class, InstanceFieldType.class);
  private List<Class> mainClasses = ImmutableList.of(
      MainCallMethod1.class, MainCallMethod2.class, MainCallMethod3.class);

  Result runTest(Class mainClass, Path proguardConfig) throws Exception {
    List<Class> classes = new ArrayList<>(applicationClasses);
    classes.add(mainClass);

    DexInspector inspector = new DexInspector(compileWithR8(classes, proguardConfig));

    DexInspector proguardedInspector = null;
    // Actually running Proguard should only be during development.
    if (isRunProguard()) {
      Path proguardedJar = temp.newFolder().toPath().resolve("proguarded.jar");
      Path proguardedMap = temp.newFolder().toPath().resolve("proguarded.map");
      ToolHelper.runProguard(jarTestClasses(classes), proguardedJar, proguardConfig, proguardedMap);
      proguardedInspector = new DexInspector(readJar(proguardedJar), proguardedMap);
    }

    return new Result(inspector, proguardedInspector);
  }

  @Test
  public void testNoIncludesDescriptorClasses() throws Exception {
    for (Class mainClass : mainClasses) {
      List<Class> allClasses = new ArrayList<>(applicationClasses);
      allClasses.add(mainClass);

      Path proguardConfig = writeTextToTempFile(
          keepMainProguardConfiguration(mainClass),
          "-keepclasseswithmembers class * {   ",
          "  <fields>;                         ",
          "  native <methods>;                 ",
          "}                                   ",
          "-allowaccessmodification            ",
          "-printmapping                       "
      );

      Result result = runTest(mainClass, proguardConfig);

      // Without includedescriptorclasses return type and argument type are removed.
      result.assertKept(ClassWithNativeMethods.class);
      result.assertRemoved(NativeArgumentType.class);
      result.assertRemoved(NativeReturnType.class);
      // Field type is not removed due to the concern about the broken type hierarchy.
      result.assertRenamed(InstanceFieldType.class);
      result.assertRenamed(StaticFieldType.class);
    }
  }

  @Test
  public void testKeepClassesWithMembers() throws Exception {
    for (Class mainClass : mainClasses) {
      Path proguardConfig = writeTextToTempFile(
          keepMainProguardConfiguration(mainClass),
          "-keepclasseswithmembers,includedescriptorclasses class * {  ",
          "  <fields>;                                                 ",
          "  native <methods>;                                         ",
          "}                                                           ",
          "-allowaccessmodification                                    ",
          "-printmapping                                               "
      );

      Result result = runTest(mainClass, proguardConfig);

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
      Path proguardConfig = writeTextToTempFile(
          keepMainProguardConfiguration(mainClass),
          "-keepclassmembers,includedescriptorclasses class * {  ",
          "  <fields>;                                           ",
          "  native <methods>;                                   ",
          "}                                                     ",
          "-allowaccessmodification                              ",
          "-printmapping                                         "
      );

      Result result = runTest(mainClass, proguardConfig);

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
      for (Class mainClass : mainClasses) {
        Path proguardConfig = writeTextToTempFile(
            keepMainProguardConfiguration(mainClass),
            // same as -keepclassmembers,allowshrinking,includedescriptorclasses
            "-keepclassmembernames,includedescriptorclasses class * {  ",
            "  <fields>;                                               ",
            "  native <methods>;                                       ",
            "}                                                         ",
            "-allowaccessmodification                                  ",
            "-printmapping                                             "
        );

        Result result = runTest(mainClass, proguardConfig);

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
