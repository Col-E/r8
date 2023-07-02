// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.shaking.ProguardConfigurationParser.FLATTEN_PACKAGE_HIERARCHY;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateKeptMethodAllowRenamingOnReachableClassDirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateKeptMethodAllowRenamingOnReachableClassIndirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateKeptMethodOnReachableClassDirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateKeptMethodOnReachableClassIndirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateMethodOnKeptClassAllowRenamingDirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateMethodOnKeptClassAllowRenamingIndirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateMethodOnKeptClassDirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateMethodOnKeptClassIndirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateMethodOnReachableClassDirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPackagePrivateMethodOnReachableClassIndirect;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPublicKeptMethodAllowRenamingOnReachableClass;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPublicKeptMethodOnReachableClass;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPublicMethodOnKeptClass;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPublicMethodOnKeptClassAllowRenaming;
import com.android.tools.r8.repackage.testclasses.repackagetest.AccessPublicMethodOnReachableClass;
import com.android.tools.r8.repackage.testclasses.repackagetest.KeptClass;
import com.android.tools.r8.repackage.testclasses.repackagetest.KeptClassAllowRenaming;
import com.android.tools.r8.repackage.testclasses.repackagetest.ReachableClassWithKeptMethod;
import com.android.tools.r8.repackage.testclasses.repackagetest.ReachableClassWithKeptMethodAllowRenaming;
import com.android.tools.r8.repackage.testclasses.repackagetest.TestClass;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RepackageTest extends RepackageTestBase {

  private static final List<String> EXPECTED =
      ImmutableList.of(
          "KeptClass.publicMethod()",
          "KeptClass.packagePrivateMethod()",
          "KeptClass.packagePrivateMethod()",
          "KeptClassAllowRenaming.publicMethod()",
          "KeptClassAllowRenaming.packagePrivateMethod()",
          "KeptClassAllowRenaming.packagePrivateMethod()",
          "ReachableClassWithKeptMethod.publicMethod()",
          "ReachableClassWithKeptMethod.packagePrivateMethod()",
          "ReachableClassWithKeptMethod.packagePrivateMethod()",
          "ReachableClassWithKeptMethodAllowRenaming.publicMethod()",
          "ReachableClassWithKeptMethodAllowRenaming.packagePrivateMethod()",
          "ReachableClassWithKeptMethodAllowRenaming.packagePrivateMethod()",
          "ReachableClass.publicMethod()",
          "ReachableClass.packagePrivateMethod()",
          "ReachableClass.packagePrivateMethod()");

  private final boolean allowAccessModification;

  @Parameters(name = "{2}, allow access modification: {0}, kind: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public RepackageTest(
      boolean allowAccessModification,
      String flattenPackageHierarchyOrRepackageClasses,
      TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
    this.allowAccessModification = allowAccessModification;
  }

  @Test
  public void testJvm() throws Exception {
    assumeFalse(allowAccessModification);
    assumeTrue(isFlattenPackageHierarchy());
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(ToolHelper.getClassFilesForTestPackage(TestClass.class.getPackage()))
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-keep class " + KeptClass.class.getTypeName(),
            "-keep,allowobfuscation class " + KeptClassAllowRenaming.class.getTypeName(),
            "-keepclassmembers class " + ReachableClassWithKeptMethod.class.getTypeName() + " {",
            "  <methods>;",
            "}",
            "-keepclassmembers,allowobfuscation class "
                + ReachableClassWithKeptMethodAllowRenaming.class.getTypeName()
                + " {",
            "  <methods>;",
            "}")
        .allowAccessModification(allowAccessModification)
        .applyIf(
            allowAccessModification,
            testBuilder -> testBuilder.addNoAccessModificationAnnotation(),
            testBuilder -> testBuilder.enableNoAccessModificationAnnotationsForMembers())
        .apply(this::configureRepackaging)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    forEachClass(
        (clazz, eligibleForRepackaging) ->
            assertThat(clazz, isRepackagedIf(inspector, eligibleForRepackaging)));
  }

  /**
   * For each test class, calls {@param consumer} with a boolean that indicates if the class is
   * eligible for repackaging (or it needs to stay in its original package).
   */
  private void forEachClass(BiConsumer<Class<?>, Boolean> consumer) {
    Consumer<Class<?>> markShouldAlwaysBeEligible = clazz -> consumer.accept(clazz, true);
    Consumer<Class<?>> markEligibleWithAllowAccessModification =
        clazz -> consumer.accept(clazz, allowAccessModification);

    // 1) -keep class KeptClass

    // 1.A) Accessing a public method on a kept class is OK.
    markShouldAlwaysBeEligible.accept(AccessPublicMethodOnKeptClass.class);

    // 1.B) Accessing a package-private method on a kept class requires -allowaccessmodification.
    markEligibleWithAllowAccessModification.accept(
        AccessPackagePrivateMethodOnKeptClassDirect.class);

    // 1.C) Accessing a package-private method that accesses a package-private method on a kept
    //      class requires -allowaccessmodification.
    markEligibleWithAllowAccessModification.accept(
        AccessPackagePrivateMethodOnKeptClassIndirect.class);

    // 2) -keep,allowobfuscation class KeptClass

    // 2.A, 2.B, 2.C) Accessing a method on a kept class that is allowed to be renamed is OK.
    markShouldAlwaysBeEligible.accept(AccessPublicMethodOnKeptClassAllowRenaming.class);
    markShouldAlwaysBeEligible.accept(
        AccessPackagePrivateMethodOnKeptClassAllowRenamingDirect.class);
    markShouldAlwaysBeEligible.accept(
        AccessPackagePrivateMethodOnKeptClassAllowRenamingIndirect.class);

    // 3) -keepclassmembers class ReachableClassWithKeptMethod { <methods>; }

    // 3.A, 3.B, 3.C) Accessing a kept method is OK.
    markShouldAlwaysBeEligible.accept(AccessPublicKeptMethodOnReachableClass.class);
    markShouldAlwaysBeEligible.accept(AccessPackagePrivateKeptMethodOnReachableClassDirect.class);
    markShouldAlwaysBeEligible.accept(AccessPackagePrivateKeptMethodOnReachableClassIndirect.class);

    // 4) -keepclassmembers,allowobfuscation class ReachableClassWithKeptMethod { <methods>; }

    // 4.A, 4.B, 4.C) Accessing a kept method is OK.
    markShouldAlwaysBeEligible.accept(AccessPublicKeptMethodAllowRenamingOnReachableClass.class);
    markShouldAlwaysBeEligible.accept(
        AccessPackagePrivateKeptMethodAllowRenamingOnReachableClassDirect.class);
    markShouldAlwaysBeEligible.accept(
        AccessPackagePrivateKeptMethodAllowRenamingOnReachableClassIndirect.class);

    // 5) No keep rule.

    // 5.A, 5.B, 5.C) Accessing a non-kept method is OK.
    markShouldAlwaysBeEligible.accept(AccessPublicMethodOnReachableClass.class);
    markShouldAlwaysBeEligible.accept(AccessPackagePrivateMethodOnReachableClassDirect.class);
    markShouldAlwaysBeEligible.accept(AccessPackagePrivateMethodOnReachableClassIndirect.class);
  }
}
