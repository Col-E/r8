// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8CompatTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/* This is a reproduction of b/196406764 where a collision will appear when repackaging */
@RunWith(Parameterized.class)
public class RepackageMissingTypeCollisionTest extends RepackageTestBase {

  public RepackageMissingTypeCollisionTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    String newMissingTypeName =
        getRepackagePackage() + (isFlattenPackageHierarchy() ? ".a.a" : ".a");
    String newMissingDescriptor = DescriptorUtils.javaTypeToDescriptor(newMissingTypeName);
    String newATypeName = A.class.getPackage().getName() + ".a";
    String newADescriptor = DescriptorUtils.javaTypeToDescriptor(newATypeName);
    testForJvm(parameters)
        .addProgramClassFileData(
            transformer(A.class).setClassDescriptor(newADescriptor).transform(),
            transformer(Anno.class)
                .replaceClassDescriptorInMembers(descriptor(Missing.class), newMissingDescriptor)
                .replaceClassDescriptorInAnnotationDefault(
                    descriptor(Missing.class), newMissingDescriptor)
                .transform(),
            transformer(Main.class)
                .replaceClassDescriptorInMethodInstructions(descriptor(A.class), newADescriptor)
                .replaceClassDescriptorInMethodInstructions(
                    descriptor(Missing.class), newMissingDescriptor)
                .transform())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NoClassDefFoundError.class);
  }

  @Test
  public void testRepackageMissingCollision() throws Exception {
    testMissingReference(true)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(getNewATypeName());
              assertThat(clazz, isPresentAndRenamed());
              assertEquals(
                  getRepackagePackage() + (isFlattenPackageHierarchy() ? ".a.b" : ".b"),
                  clazz.getFinalName());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NoClassDefFoundError.class);
  }

  @Test
  public void testNoRepackage() throws Exception {
    testMissingReference(false)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NoClassDefFoundError.class);
  }

  private String getNewMissingTypeName() {
    return getRepackagePackage() + (isFlattenPackageHierarchy() ? ".a.a" : ".a");
  }

  private String getNewATypeName() {
    return A.class.getPackage().getName() + ".a";
  }

  private R8CompatTestBuilder testMissingReference(boolean repackage) throws Exception {
    // The references to Missing will be rewritten to <repackage>.a but the definition will not be
    // present.
    String newMissingDescriptor = DescriptorUtils.javaTypeToDescriptor(getNewMissingTypeName());
    String newADescriptor = DescriptorUtils.javaTypeToDescriptor(getNewATypeName());
    return testForR8Compat(parameters.getBackend())
        .addProgramClassFileData(
            transformer(A.class).setClassDescriptor(newADescriptor).transform(),
            transformer(Anno.class)
                .replaceClassDescriptorInMembers(descriptor(Missing.class), newMissingDescriptor)
                .replaceClassDescriptorInAnnotationDefault(
                    descriptor(Missing.class), newMissingDescriptor)
                .transform(),
            transformer(Main.class)
                .replaceClassDescriptorInMethodInstructions(descriptor(A.class), newADescriptor)
                .replaceClassDescriptorInMethodInstructions(
                    descriptor(Missing.class), newMissingDescriptor)
                .transform())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(Anno.class)
        .addKeepRuntimeVisibleAnnotations()
        .applyIf(repackage, this::configureRepackaging)
        .setMinApi(parameters)
        .addDontWarn(getNewMissingTypeName())
        .addOptionsModification(internalOptions -> internalOptions.enableEnumUnboxing = false)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged);
  }

  /* Will be missing on input */
  public enum Missing {
    foo;
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface Anno {

    /* Renamed to <repackage>.a */ Missing missing() default Missing.foo;
  }

  @Anno
  public enum /* renamed to a */ A {
    foo;
  }

  public static class Main {

    public static void main(String[] args) {
      Anno annotation = A.class.getAnnotation(Anno.class);
      System.out.println(annotation.missing());
    }
  }
}
