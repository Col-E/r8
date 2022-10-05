// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.UnsupportedMainDexListUsageDiagnostic;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MainDexListFromGenerateMainDexInliningSpuriousRootTest extends TestBase {

  private static List<ClassReference> mainDexList;
  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsEndingAtExcluding(apiLevelWithNativeMultiDexSupport())
        .build();
  }

  @BeforeClass
  public static void setup() throws Exception {
    mainDexList =
        testForMainDexListGenerator(getStaticTemp())
            .addInnerClasses(MainDexListFromGenerateMainDexInliningSpuriousRootTest.class)
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .addMainDexRules(
                "-keep class " + Main.class.getTypeName() + " {",
                "  public static void main(java.lang.String[]);",
                "}")
            .run()
            .getMainDexList();
  }

  public MainDexListFromGenerateMainDexInliningSpuriousRootTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  // TODO(b/181858113): This test is likely obsolete once main-dex-list support is removed.
  @Test
  public void test() throws Exception {
    // The generated main dex list should contain Main (which is a root) and A (which is a direct
    // dependency of Main).
    assertEquals(2, mainDexList.size());
    assertEquals(A.class.getTypeName(), mainDexList.get(0).getTypeName());
    assertEquals(Main.class.getTypeName(), mainDexList.get(1).getTypeName());
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addInliningAnnotations()
            .addKeepClassAndMembersRules(Main.class)
            .addKeepMainRule(Main2.class)
            .addMainDexListClassReferences(mainDexList)
            .addMainDexRules(
                "-keep class " + Main2.class.getTypeName() + " {",
                "  public static void main(java.lang.String[]);",
                "}")
            .collectMainDexClasses()
            .enableInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .setMinApi(parameters.getApiLevel())
            .addDontObfuscate()
            .allowDiagnosticMessages()
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    diagnostics
                        .assertOnlyWarnings()
                        .assertWarningsMatch(
                            diagnosticType(UnsupportedMainDexListUsageDiagnostic.class)));
    CodeInspector inspector = compileResult.inspector();
    ClassSubject mainClassSubject = inspector.clazz(Main.class);
    assertThat(mainClassSubject, isPresent());
    MethodSubject fooMethodSubject = mainClassSubject.uniqueMethodWithOriginalName("foo");
    assertThat(fooMethodSubject, isPresent());
    ClassSubject main2ClassSubject = inspector.clazz(Main2.class);
    assertThat(main2ClassSubject, isPresent());
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());
    MethodSubject barMethodSubject = aClassSubject.uniqueMethodWithOriginalName("bar");
    assertThat(barMethodSubject, isPresent());
    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());
    MethodSubject bazMethodSubject = bClassSubject.uniqueMethodWithOriginalName("baz");
    assertThat(bazMethodSubject, isPresent());
    assertThat(fooMethodSubject, invokesMethod(barMethodSubject));
    assertThat(barMethodSubject, invokesMethod(bazMethodSubject));
    assertEquals(
        ImmutableSet.of(
            mainClassSubject.getFinalName(),
            main2ClassSubject.getFinalName(),
            aClassSubject.getFinalName()),
        compileResult.getMainDexClasses());
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println("Main.main()");
    }

    static void foo() {
      A.bar();
    }
  }

  static class Main2 {

    public static void main(String[] args) {
      if (getFalse()) {
        A.bar();
      }
    }

    static boolean getFalse() {
      return false;
    }
  }

  @NoHorizontalClassMerging
  static class A {
    // Must not be inlined into Main.foo(), since that would cause B to become direct dependence of
    // Main without ending up in the main dex.
    static void bar() {
      B.baz();
    }
  }

  @NoHorizontalClassMerging
  static class B {

    @NeverInline
    static void baz() {
      System.out.println("B.baz");
    }
  }
}
