// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.ClassResolutionResult;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FieldResolutionWithMultipleResultsTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private final List<Class<?>> libraryClasses = ImmutableList.of(A.class, B.class);

  @Test
  public void testMultipleResultsWithProgramAndLibrary() throws Exception {
    List<Class<?>> programClasses = new ArrayList<>(libraryClasses);
    programClasses.add(C.class);
    testMultipleResolutionsMatchesFoundClasses(
        readClasses(programClasses, libraryClasses),
        fieldResult -> assertTrue(fieldResult.hasProgramResult()));
  }

  @Test
  public void testMultipleResultsWithClasspathAndLibrary() throws Exception {
    List<Class<?>> classPath = new ArrayList<>(libraryClasses);
    classPath.add(C.class);
    testMultipleResolutionsMatchesFoundClasses(
        readClasses(Collections.emptyList(), classPath, libraryClasses),
        fieldResult -> assertTrue(fieldResult.hasClasspathResult()));
  }

  @Test
  public void testProgramOverClasspath() throws Exception {
    List<Class<?>> programAndClasspathClasses = new ArrayList<>(libraryClasses);
    programAndClasspathClasses.add(C.class);
    testMultipleResolutionsMatchesFoundClasses(
        readClasses(programAndClasspathClasses, programAndClasspathClasses, libraryClasses),
        fieldResult -> assertTrue(fieldResult.hasProgramResult()));
  }

  private void testMultipleResolutionsMatchesFoundClasses(
      AndroidApp androidApp, Consumer<FieldResolutionResult> resultConsumer) throws Exception {
    AppInfoWithClassHierarchy appInfoWithClassHierarchy =
        computeAppInfoWithClassHierarchy(androidApp);
    DexItemFactory factory = appInfoWithClassHierarchy.dexItemFactory();
    DexField field =
        factory.createField(
            Reference.field(
                Reference.classFromClass(C.class), "foo", Reference.primitiveFromDescriptor("I")));
    FieldResolutionResult fieldResolutionResult = appInfoWithClassHierarchy.resolveField(field);
    assertTrue(fieldResolutionResult.isMultiFieldResolutionResult());
    resultConsumer.accept(fieldResolutionResult);
    assertTrue(fieldResolutionResult.hasProgramOrClasspathResult());
    assertFalse(fieldResolutionResult.isPossiblyFailedOrUnknownResolution());
    Set<DexClass> resolvedHolders = new HashSet<>();
    fieldResolutionResult.forEachFieldResolutionResult(
        resolutionResult -> {
          assertTrue(resolutionResult.isSingleFieldResolutionResult());
          boolean existing =
              resolvedHolders.add(
                  resolutionResult.asSingleFieldResolutionResult().getResolvedHolder());
          assertTrue(existing);
        });
    assertEquals(2, resolvedHolders.size());
    ClassResolutionResult classResolutionResult =
        appInfoWithClassHierarchy.contextIndependentDefinitionForWithResolutionResult(
            factory.createType(Reference.classFromClass(A.class)));
    assertTrue(classResolutionResult.hasClassResolutionResult());
    Set<DexClass> foundClasses = new HashSet<>();
    classResolutionResult.forEachClassResolutionResult(foundClasses::add);
    assertEquals(foundClasses, resolvedHolders);
  }

  public static class A {

    public int foo;
  }

  public static class B extends A {}

  public static class C extends B {}
}
