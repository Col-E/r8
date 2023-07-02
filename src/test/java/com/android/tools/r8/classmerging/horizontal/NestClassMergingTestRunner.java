// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.classmerging.horizontal.NestClassMergingTestRunner.HorizontalClassMergingTestSources.jar;
import static com.android.tools.r8.classmerging.horizontal.NestClassMergingTestRunner.HorizontalClassMergingTestSources.nestClassMergingTest;
import static com.android.tools.r8.classmerging.horizontal.NestClassMergingTestRunner.HorizontalClassMergingTestSources.nestHostA;
import static com.android.tools.r8.classmerging.horizontal.NestClassMergingTestRunner.HorizontalClassMergingTestSources.nestHostA$NestMemberA;
import static com.android.tools.r8.classmerging.horizontal.NestClassMergingTestRunner.HorizontalClassMergingTestSources.nestHostA$NestMemberB;
import static com.android.tools.r8.classmerging.horizontal.NestClassMergingTestRunner.HorizontalClassMergingTestSources.nestHostB;
import static com.android.tools.r8.classmerging.horizontal.NestClassMergingTestRunner.HorizontalClassMergingTestSources.nestHostB$NestMemberA;
import static com.android.tools.r8.classmerging.horizontal.NestClassMergingTestRunner.HorizontalClassMergingTestSources.nestHostB$NestMemberB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.examples.JavaExampleClassProxy;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.references.ClassReference;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runners.Parameterized;

public class NestClassMergingTestRunner extends HorizontalClassMergingTestBase {

  public static class HorizontalClassMergingTestSources {

    private static final String EXAMPLE_FILE = "examplesJava11";

    public static final JavaExampleClassProxy nestClassMergingTest =
        new JavaExampleClassProxy(EXAMPLE_FILE, "horizontalclassmerging/NestClassMergingTest");
    public static final JavaExampleClassProxy nestHostA =
        new JavaExampleClassProxy(EXAMPLE_FILE, "horizontalclassmerging/NestHostA");
    public static final JavaExampleClassProxy nestHostA$NestMemberA =
        new JavaExampleClassProxy(EXAMPLE_FILE, "horizontalclassmerging/NestHostA$NestMemberA");
    public static final JavaExampleClassProxy nestHostA$NestMemberB =
        new JavaExampleClassProxy(EXAMPLE_FILE, "horizontalclassmerging/NestHostA$NestMemberB");
    public static final JavaExampleClassProxy nestHostB =
        new JavaExampleClassProxy(EXAMPLE_FILE, "horizontalclassmerging/NestHostB");
    public static final JavaExampleClassProxy nestHostB$NestMemberA =
        new JavaExampleClassProxy(EXAMPLE_FILE, "horizontalclassmerging/NestHostB$NestMemberA");
    public static final JavaExampleClassProxy nestHostB$NestMemberB =
        new JavaExampleClassProxy(EXAMPLE_FILE, "horizontalclassmerging/NestHostB$NestMemberB");

    public static Path jar() {
      return JavaExampleClassProxy.examplesJar(EXAMPLE_FILE + "/horizontalclassmerging");
    }
  }

  public NestClassMergingTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntimes()
        .withAllApiLevels()
        .build();
  }

  @Test
  public void test() throws Exception {
    runTest(
        builder ->
            builder.addHorizontallyMergedClassesInspector(
                inspector -> {
                  if (parameters.canUseNestBasedAccesses()) {
                    inspector
                        .assertIsCompleteMergeGroup(
                            nestHostA.getClassReference(),
                            nestHostA$NestMemberA.getClassReference(),
                            nestHostA$NestMemberB.getClassReference())
                        .assertIsCompleteMergeGroup(
                            nestHostB.getClassReference(),
                            nestHostB$NestMemberA.getClassReference(),
                            nestHostB$NestMemberB.getClassReference());
                  } else {
                    inspector.assertIsCompleteMergeGroup(
                        nestHostA.getClassReference(),
                        nestHostA$NestMemberA.getClassReference(),
                        nestHostA$NestMemberB.getClassReference(),
                        nestHostB.getClassReference(),
                        nestHostB$NestMemberA.getClassReference(),
                        nestHostB$NestMemberB.getClassReference());
                  }
                }));
  }

  @Test
  public void testMergeHostIntoNestMemberA() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    runTest(
        builder ->
            builder
                .addHorizontallyMergedClassesInspector(
                    inspector ->
                        inspector
                            .assertIsCompleteMergeGroup(
                                nestHostA.getClassReference(),
                                nestHostA$NestMemberA.getClassReference())
                            .assertIsCompleteMergeGroup(
                                nestHostB.getClassReference(),
                                nestHostB$NestMemberA.getClassReference())
                            .assertClassReferencesNotMerged(
                                nestHostA$NestMemberB.getClassReference(),
                                nestHostB$NestMemberB.getClassReference()))
                .addNoHorizontalClassMergingRule(
                    nestHostA$NestMemberB.getClassReference().getTypeName(),
                    nestHostB$NestMemberB.getClassReference().getTypeName())
                .addOptionsModification(
                    options -> {
                      options.testing.horizontalClassMergingTarget =
                          (appView, canditates, target) -> {
                            Set<ClassReference> candidateClassReferences =
                                Streams.stream(canditates)
                                    .map(DexClass::getClassReference)
                                    .collect(Collectors.toSet());
                            if (candidateClassReferences.contains(nestHostA.getClassReference())) {
                              assertEquals(
                                  ImmutableSet.of(
                                      nestHostA.getClassReference(),
                                      nestHostA$NestMemberA.getClassReference()),
                                  candidateClassReferences);
                            } else {
                              assertEquals(
                                  ImmutableSet.of(
                                      nestHostB.getClassReference(),
                                      nestHostB$NestMemberA.getClassReference()),
                                  candidateClassReferences);
                            }
                            return Iterables.find(
                                canditates,
                                candidate -> {
                                  ClassReference classReference = candidate.getClassReference();
                                  return classReference.equals(
                                          nestHostA$NestMemberA.getClassReference())
                                      || classReference.equals(
                                          nestHostB$NestMemberA.getClassReference());
                                });
                          };
                    }));
  }

  @Test
  public void testMergeHostIntoNestMemberB() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    runTest(
        builder ->
            builder
                .addHorizontallyMergedClassesInspector(
                    inspector ->
                        inspector
                            .assertIsCompleteMergeGroup(
                                nestHostA.getClassReference(),
                                nestHostA$NestMemberB.getClassReference())
                            .assertIsCompleteMergeGroup(
                                nestHostB.getClassReference(),
                                nestHostB$NestMemberB.getClassReference())
                            .assertClassReferencesNotMerged(
                                nestHostA$NestMemberA.getClassReference(),
                                nestHostB$NestMemberA.getClassReference()))
                .addNoHorizontalClassMergingRule(
                    nestHostA$NestMemberA.getClassReference().getTypeName(),
                    nestHostB$NestMemberA.getClassReference().getTypeName())
                .addOptionsModification(
                    options -> {
                      options.testing.horizontalClassMergingTarget =
                          (appView, canditates, target) -> {
                            Set<ClassReference> candidateClassReferences =
                                Streams.stream(canditates)
                                    .map(DexClass::getClassReference)
                                    .collect(Collectors.toSet());
                            if (candidateClassReferences.contains(nestHostA.getClassReference())) {
                              assertEquals(
                                  ImmutableSet.of(
                                      nestHostA.getClassReference(),
                                      nestHostA$NestMemberB.getClassReference()),
                                  candidateClassReferences);
                            } else {
                              assertEquals(
                                  ImmutableSet.of(
                                      nestHostB.getClassReference(),
                                      nestHostB$NestMemberB.getClassReference()),
                                  candidateClassReferences);
                            }
                            return Iterables.find(
                                canditates,
                                candidate -> {
                                  ClassReference classReference = candidate.getClassReference();
                                  return classReference.equals(
                                          nestHostA$NestMemberB.getClassReference())
                                      || classReference.equals(
                                          nestHostB$NestMemberB.getClassReference());
                                });
                          };
                    }));
  }

  @Test
  public void testMergeMemberAIntoNestHost() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    runTest(
        builder ->
            builder
                .addHorizontallyMergedClassesInspector(
                    inspector ->
                        inspector
                            .assertIsCompleteMergeGroup(
                                nestHostA.getClassReference(),
                                nestHostA$NestMemberA.getClassReference())
                            .assertIsCompleteMergeGroup(
                                nestHostB.getClassReference(),
                                nestHostB$NestMemberA.getClassReference())
                            .assertClassReferencesNotMerged(
                                nestHostA$NestMemberB.getClassReference(),
                                nestHostB$NestMemberB.getClassReference()))
                .addNoHorizontalClassMergingRule(
                    nestHostA$NestMemberB.getClassReference().getTypeName(),
                    nestHostB$NestMemberB.getClassReference().getTypeName())
                .addOptionsModification(
                    options -> {
                      options.testing.horizontalClassMergingTarget =
                          (appView, canditates, target) -> {
                            Set<ClassReference> candidateClassReferences =
                                Streams.stream(canditates)
                                    .map(DexClass::getClassReference)
                                    .collect(Collectors.toSet());
                            if (candidateClassReferences.contains(nestHostA.getClassReference())) {
                              assertEquals(
                                  ImmutableSet.of(
                                      nestHostA.getClassReference(),
                                      nestHostA$NestMemberA.getClassReference()),
                                  candidateClassReferences);
                            } else {
                              assertEquals(
                                  ImmutableSet.of(
                                      nestHostB.getClassReference(),
                                      nestHostB$NestMemberA.getClassReference()),
                                  candidateClassReferences);
                            }
                            return Iterables.find(
                                canditates,
                                candidate -> {
                                  ClassReference classReference = candidate.getClassReference();
                                  return classReference.equals(nestHostA.getClassReference())
                                      || classReference.equals(nestHostB.getClassReference());
                                });
                          };
                    }));
  }

  private void runTest(ThrowableConsumer<R8FullTestBuilder> configuration) throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(nestClassMergingTest.getClassReference())
        .addProgramFiles(jar())
        .apply(configuration)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), nestClassMergingTest.getClassReference().getTypeName())
        .assertSuccessWithOutputLines(
            "NestHostA$NestMemberA",
            "NestHostA$NestMemberB",
            "NestHostB$NestMemberA",
            "NestHostB$NestMemberB");
  }
}
