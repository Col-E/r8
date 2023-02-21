// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.classmerging.horizontal.NestClassMergingTestRunner.R.horizontalclassmerging.NestClassMergingTest;
import com.android.tools.r8.classmerging.horizontal.NestClassMergingTestRunner.R.horizontalclassmerging.NestHostA;
import com.android.tools.r8.classmerging.horizontal.NestClassMergingTestRunner.R.horizontalclassmerging.NestHostB;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.ReflectiveBuildPathUtils.ExamplesClass;
import com.android.tools.r8.utils.ReflectiveBuildPathUtils.ExamplesJava11RootPackage;
import com.android.tools.r8.utils.ReflectiveBuildPathUtils.ExamplesPackage;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runners.Parameterized;

public class NestClassMergingTestRunner extends HorizontalClassMergingTestBase {

  public static class R extends ExamplesJava11RootPackage {
    public static class horizontalclassmerging extends ExamplesPackage {
      public static class NestClassMergingTest extends ExamplesClass {}

      public static class NestHostA extends ExamplesClass {
        public static class NestMemberA extends ExamplesClass {}

        public static class NestMemberB extends ExamplesClass {}
      }

      public static class NestHostB extends ExamplesClass {
        public static class NestMemberA extends ExamplesClass {}

        public static class NestMemberB extends ExamplesClass {}
      }
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
                            classRef(NestHostA.class),
                            classRef(NestHostA.NestMemberA.class),
                            classRef(NestHostA.NestMemberB.class))
                        .assertIsCompleteMergeGroup(
                            classRef(NestHostB.class),
                            classRef(NestHostB.NestMemberA.class),
                            classRef(NestHostB.NestMemberB.class));
                  } else {
                    inspector.assertIsCompleteMergeGroup(
                        classRef(NestHostA.class),
                        classRef(NestHostA.NestMemberA.class),
                        classRef(NestHostA.NestMemberB.class),
                        classRef(NestHostB.class),
                        classRef(NestHostB.NestMemberA.class),
                        classRef(NestHostB.NestMemberB.class));
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
                                classRef(NestHostA.class), classRef(NestHostA.NestMemberA.class))
                            .assertIsCompleteMergeGroup(
                                classRef(NestHostB.class), classRef(NestHostB.NestMemberA.class))
                            .assertClassReferencesNotMerged(
                                classRef(NestHostA.NestMemberB.class),
                                classRef(NestHostB.NestMemberB.class)))
                .addNoHorizontalClassMergingRule(
                    examplesTypeName(NestHostA.NestMemberB.class),
                    examplesTypeName(NestHostB.NestMemberB.class))
                .addOptionsModification(
                    options -> {
                      options.testing.horizontalClassMergingTarget =
                          (appView, canditates, target) -> {
                            Set<ClassReference> candidateClassReferences =
                                Streams.stream(canditates)
                                    .map(DexClass::getClassReference)
                                    .collect(Collectors.toSet());
                            if (candidateClassReferences.contains(classRef(NestHostA.class))) {
                              assertEquals(
                                  ImmutableSet.of(
                                      classRef(NestHostA.class),
                                      classRef(NestHostA.NestMemberA.class)),
                                  candidateClassReferences);
                            } else {
                              assertEquals(
                                  ImmutableSet.of(
                                      classRef(NestHostB.class),
                                      classRef(NestHostB.NestMemberA.class)),
                                  candidateClassReferences);
                            }
                            return Iterables.find(
                                canditates,
                                candidate -> {
                                  ClassReference classReference = candidate.getClassReference();
                                  return classReference.equals(
                                          classRef(NestHostA.NestMemberA.class))
                                      || classReference.equals(
                                          classRef(NestHostB.NestMemberA.class));
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
                                classRef(NestHostA.class), classRef(NestHostA.NestMemberB.class))
                            .assertIsCompleteMergeGroup(
                                classRef(NestHostB.class), classRef(NestHostB.NestMemberB.class))
                            .assertClassReferencesNotMerged(
                                classRef(NestHostA.NestMemberA.class),
                                classRef(NestHostB.NestMemberA.class)))
                .addNoHorizontalClassMergingRule(
                    examplesTypeName(NestHostA.NestMemberA.class),
                    examplesTypeName(NestHostB.NestMemberA.class))
                .addOptionsModification(
                    options -> {
                      options.testing.horizontalClassMergingTarget =
                          (appView, canditates, target) -> {
                            Set<ClassReference> candidateClassReferences =
                                Streams.stream(canditates)
                                    .map(DexClass::getClassReference)
                                    .collect(Collectors.toSet());
                            if (candidateClassReferences.contains(classRef(NestHostA.class))) {
                              assertEquals(
                                  ImmutableSet.of(
                                      classRef(NestHostA.class),
                                      classRef(NestHostA.NestMemberB.class)),
                                  candidateClassReferences);
                            } else {
                              assertEquals(
                                  ImmutableSet.of(
                                      classRef(NestHostB.class),
                                      classRef(NestHostB.NestMemberB.class)),
                                  candidateClassReferences);
                            }
                            return Iterables.find(
                                canditates,
                                candidate -> {
                                  ClassReference classReference = candidate.getClassReference();
                                  return classReference.equals(
                                          classRef(NestHostA.NestMemberB.class))
                                      || classReference.equals(
                                          classRef(NestHostB.NestMemberB.class));
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
                                classRef(NestHostA.class), classRef(NestHostA.NestMemberA.class))
                            .assertIsCompleteMergeGroup(
                                classRef(NestHostB.class), classRef(NestHostB.NestMemberA.class))
                            .assertClassReferencesNotMerged(
                                classRef(NestHostA.NestMemberB.class),
                                classRef(NestHostB.NestMemberB.class)))
                .addNoHorizontalClassMergingRule(
                    examplesTypeName(NestHostA.NestMemberB.class),
                    examplesTypeName(NestHostB.NestMemberB.class))
                .addOptionsModification(
                    options -> {
                      options.testing.horizontalClassMergingTarget =
                          (appView, canditates, target) -> {
                            Set<ClassReference> candidateClassReferences =
                                Streams.stream(canditates)
                                    .map(DexClass::getClassReference)
                                    .collect(Collectors.toSet());
                            if (candidateClassReferences.contains(classRef(NestHostA.class))) {
                              assertEquals(
                                  ImmutableSet.of(
                                      classRef(NestHostA.class),
                                      classRef(NestHostA.NestMemberA.class)),
                                  candidateClassReferences);
                            } else {
                              assertEquals(
                                  ImmutableSet.of(
                                      classRef(NestHostB.class),
                                      classRef(NestHostB.NestMemberA.class)),
                                  candidateClassReferences);
                            }
                            return Iterables.find(
                                canditates,
                                candidate -> {
                                  ClassReference classReference = candidate.getClassReference();
                                  return classReference.equals(classRef(NestHostA.class))
                                      || classReference.equals(classRef(NestHostB.class));
                                });
                          };
                    }));
  }

  private void runTest(ThrowableConsumer<R8FullTestBuilder> configuration) throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(examplesTypeName(NestClassMergingTest.class))
        .addExamplesProgramFiles(R.class)
        .apply(configuration)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), examplesTypeName(NestClassMergingTest.class))
        .assertSuccessWithOutputLines(
            "NestHostA$NestMemberA",
            "NestHostA$NestMemberB",
            "NestHostB$NestMemberA",
            "NestHostB$NestMemberB");
  }

  private static ClassReference classRef(Class<? extends ExamplesClass> clazz) {
    return examplesClassReference(clazz);
  }
}
