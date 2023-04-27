// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.naming.ClassNameMinifier.getParentPackagePrefix;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PackageNamingTest extends TestBase {

  private final Path input;
  private final Path keepRulesFile;
  private final ThrowingConsumer<CodeInspector, RuntimeException> inspection;

  @Parameters(name = "test: {0} keep: {1}")
  public static Collection<Object[]> data() {
    List<String> tests = Arrays.asList("naming044", "naming101");
    Map<String, ThrowingConsumer<CodeInspector, RuntimeException>> inspections = new HashMap<>();
    inspections.put("naming044:keep-rules-001.txt", PackageNamingTest::test044_rule001);
    inspections.put("naming044:keep-rules-002.txt", PackageNamingTest::test044_rule002);
    inspections.put("naming044:keep-rules-003.txt", PackageNamingTest::test044_rule003);
    inspections.put("naming044:keep-rules-004.txt", PackageNamingTest::test044_rule004);
    inspections.put("naming044:keep-rules-005.txt", PackageNamingTest::test044_rule005);
    inspections.put("naming101:keep-rules-001.txt", PackageNamingTest::test101_rule001);
    inspections.put("naming101:keep-rules-002.txt", PackageNamingTest::test101_rule002);
    inspections.put("naming101:keep-rules-003.txt", PackageNamingTest::test101_rule003);
    inspections.put("naming101:keep-rules-004.txt", PackageNamingTest::test101_rule004);
    inspections.put("naming101:keep-rules-005.txt", PackageNamingTest::test101_rule005);
    inspections.put("naming101:keep-rules-102.txt", PackageNamingTest::test101_rule102);
    inspections.put("naming101:keep-rules-104.txt", PackageNamingTest::test101_rule104);
    inspections.put("naming101:keep-rules-106.txt", PackageNamingTest::test101_rule106);
    return createTests(tests, inspections);
  }

  public PackageNamingTest(
      String test,
      Path keepRulesFile,
      ThrowingConsumer<CodeInspector, RuntimeException> inspection) {
    this.input = Paths.get(ToolHelper.EXAMPLES_BUILD_DIR + test + ".jar");
    this.keepRulesFile = keepRulesFile;
    this.inspection = inspection;
  }

  @Test
  public void packageNamingTest() throws Exception {
    testForR8(Backend.DEX)
        .addProgramFiles(input)
        .addKeepRuleFiles(keepRulesFile)
        .setMinApi(21)
        .compile()
        .inspect(inspection);
  }

  private static final List<String> CLASSES_IN_NAMING044 =
      ImmutableList.of("naming044.A", "naming044.B", "naming044.sub.SubA", "naming044.sub.SubB");

  private static final List<String> CLASSES_IN_NAMING101 =
      ImmutableList.of(
          "naming101.c",
          "naming101.d",
          "naming101.a.a",
          "naming101.a.c",
          "naming101.a.b.c",
          "naming101.b.a'",
          "naming101.b.b");

  private static List<Object[]> createTests(
      List<String> tests,
      Map<String, ThrowingConsumer<CodeInspector, RuntimeException>> inspections) {
    List<Object[]> testCases = new ArrayList<>();
    Set<String> usedInspections = new HashSet<>();
    for (String test : tests) {
      File[] keepFiles =
          new File(ToolHelper.EXAMPLES_DIR + test)
              .listFiles(file -> file.isFile() && file.getName().endsWith(".txt"));
      for (File keepFile : keepFiles) {
        String keepName = keepFile.getName();
        ThrowingConsumer<CodeInspector, RuntimeException> inspection =
            getTestOptionalParameter(inspections, usedInspections, test, keepName);
        if (inspection != null) {
          testCases.add(new Object[] {test, keepFile.toPath(), inspection});
        }
      }
    }
    assert usedInspections.size() == inspections.size();
    return testCases;
  }

  private static <T> T getTestOptionalParameter(
      Map<String, T> specifications, Set<String> usedSpecifications, String test, String keepName) {
    T parameter = specifications.get(test);
    if (parameter == null) {
      parameter = specifications.get(test + ":" + keepName);
      if (parameter != null) {
        usedSpecifications.add(test + ":" + keepName);
      }
    } else {
      usedSpecifications.add(test);
    }
    return parameter;
  }

  private static int countPackageDepth(ClassSubject subject) {
    return CharMatcher.is('.').countIn(subject.getFinalName());
  }

  private static void verifyUniqueNaming(CodeInspector inspector, List<String> klasses) {
    Set<String> renamedNames = Sets.newHashSet();
    for (String klass : klasses) {
      String finalName = inspector.getObfuscatedTypeName(klass);
      assertFalse(renamedNames.contains(finalName));
      renamedNames.add(finalName);
    }
  }

  // repackageclasses ''
  private static void test044_rule001(CodeInspector inspector) {
    verifyUniqueNaming(inspector, CLASSES_IN_NAMING044);

    // All classes are moved to the top-level package, hence no package separator.
    ClassSubject bClassSubject = inspector.clazz("naming044.B");
    assertFalse(bClassSubject.getFinalName().contains("."));

    // Even classes in a sub-package are moved to the same top-level package.
    assertFalse(inspector.clazz("naming044.sub.SubB").getFinalName().contains("."));

    // Method naming044.B.m would be renamed.
    MethodSubject mMethodSubject = bClassSubject.uniqueMethodWithOriginalName("m");
    assertNotEquals("m", mMethodSubject.getMethod().getName().toSourceString());
  }

  // repackageclasses 'p44.x'
  private static void test044_rule002(CodeInspector inspector) {
    verifyUniqueNaming(inspector, CLASSES_IN_NAMING044);

    // All classes are moved to a single package, so they all have the same package prefix.
    assertThat(inspector.clazz("naming044.A").getFinalName(), startsWith("p44.x."));

    ClassSubject bClassSubject = inspector.clazz("naming044.B");
    assertThat(bClassSubject.getFinalName(), startsWith("p44.x."));

    ClassSubject subBClassSubject = inspector.clazz("naming044.sub.SubB");
    assertThat(subBClassSubject.getFinalName(), startsWith("p44.x."));

    // Even classes in a sub-package are moved to the same package.
    assertEquals(
        subBClassSubject.getDexProgramClass().getType().getPackageName(),
        bClassSubject.getDexProgramClass().getType().getPackageName());
  }

  // flattenpackagehierarchy ''
  private static void test044_rule003(CodeInspector inspector) {
    verifyUniqueNaming(inspector, CLASSES_IN_NAMING044);

    // All packages are moved to the top-level package, hence only one package separator.
    ClassSubject b = inspector.clazz("naming044.B");
    ClassSubject bClassSubject = inspector.clazz("naming044.B");
    assertEquals(1, countPackageDepth(bClassSubject));

    // Classes in a sub-package are moved to the top-level as well, but in a different one.
    ClassSubject sub = inspector.clazz("naming044.sub.SubB");
    assertEquals(1, countPackageDepth(sub));
    assertNotEquals(
        sub.getDexProgramClass().getType().getPackageName(),
        b.getDexProgramClass().getType().getPackageName());

    // method naming044.B.m would be renamed.
    MethodSubject mMethodSubject = b.uniqueMethodWithOriginalName("m");
    assertNotEquals("m", mMethodSubject.getMethod().getName().toSourceString());
  }

  // flattenpackagehierarchy 'p44.y'
  private static void test044_rule004(CodeInspector inspector) {
    // All packages are moved to a single package.
    ClassSubject a = inspector.clazz("naming044.A");
    assertTrue(a.getFinalName().startsWith("p44.y."));
    // naming004.A -> Lp44/y/a/a;
    assertEquals(3, countPackageDepth(a));

    ClassSubject b = inspector.clazz("naming044.B");
    assertTrue(b.getFinalName().startsWith("p44.y."));
    // naming004.B -> Lp44/y/a/b;
    assertEquals(3, countPackageDepth(b));

    ClassSubject sub = inspector.clazz("naming044.sub.SubB");
    assertTrue(sub.getFinalName().startsWith("p44.y."));
    // naming004.sub.SubB -> Lp44/y/b/b;
    assertEquals(3, countPackageDepth(sub));

    // Classes in a sub-package should be in a different package.
    assertNotEquals(
        sub.getDexProgramClass().getType().getPackageName(),
        b.getDexProgramClass().getType().getPackageName());
  }

  private static void test044_rule005(CodeInspector inspector) {
    verifyUniqueNaming(inspector, CLASSES_IN_NAMING044);

    // All packages are renamed somehow. Need to check package hierarchy is consistent.
    ClassSubject a = inspector.clazz("naming044.A");
    assertEquals(1, countPackageDepth(a));
    ClassSubject b = inspector.clazz("naming044.B");
    assertEquals(1, countPackageDepth(b));
    assertEquals(
        a.getDexProgramClass().getType().getPackageName(),
        b.getDexProgramClass().getType().getPackageName());

    ClassSubject sub_a = inspector.clazz("naming044.sub.SubA");
    assertEquals(1, countPackageDepth(sub_a));
    ClassSubject sub_b = inspector.clazz("naming044.sub.SubB");
    assertEquals(1, countPackageDepth(sub_b));
    assertEquals(
        sub_a.getDexProgramClass().getType().getPackageName(),
        sub_b.getDexProgramClass().getType().getPackageName());

    // Lnaming044.B -> La/c --prefix--> La
    // Lnaming044.sub.SubB -> La/b/b --prefix--> La/b --prefix--> La
    assertEquals(
        getParentPackagePrefix(b.getFinalName()),
        getParentPackagePrefix(getParentPackagePrefix(sub_b.getFinalName())));
  }

  private static void test101_rule001(CodeInspector inspector) {
    verifyUniqueNaming(inspector, CLASSES_IN_NAMING101);

    // All classes are moved to the top-level package, hence no package separator.
    ClassSubject c = inspector.clazz("naming101.c");
    assertFalse(c.getFinalName().contains("."));

    ClassSubject abc = inspector.clazz("naming101.a.b.c");
    assertFalse(abc.getFinalName().contains("."));
    assertNotEquals(abc.getFinalName(), c.getFinalName());
  }

  private static void test101_rule002(CodeInspector inspector) {
    verifyUniqueNaming(inspector, CLASSES_IN_NAMING101);

    // Check naming101.*.a is kept due to **.a
    ClassSubject aa = inspector.clazz("naming101.a.a");
    assertEquals("naming101.a.a", aa.getFinalName());
    ClassSubject ba = inspector.clazz("naming101.b.a");
    assertEquals("naming101.b.a", ba.getFinalName());

    // Repackaged to naming101.a, but naming101.a.a exists to make a name conflict.
    // Thus, everything else should not be renamed to 'a',
    // except for naming101.b.a, which is also kept due to **.a
    List<String> klasses =
        ImmutableList.of(
            "naming101.c", "naming101.d", "naming101.a.c", "naming101.a.b.c", "naming101.b.b");
    for (String klass : klasses) {
      ClassSubject k = inspector.clazz(klass);
      assertEquals("naming101.a", k.getDexProgramClass().getType().getPackageName());
      assertNotEquals("naming101.a.a", k.getFinalName());
    }
  }

  private static void test101_rule003(CodeInspector inspector) {
    verifyUniqueNaming(inspector, CLASSES_IN_NAMING101);

    // All packages are moved to the top-level package, hence only one package separator.
    ClassSubject aa = inspector.clazz("naming101.a.a");
    assertEquals(1, countPackageDepth(aa));

    ClassSubject ba = inspector.clazz("naming101.b.a");
    assertEquals(1, countPackageDepth(ba));

    assertNotEquals(
        aa.getDexProgramClass().getType().getPackageName(),
        ba.getDexProgramClass().getType().getPackageName());
  }

  private static void test101_rule004(CodeInspector inspector) {
    verifyUniqueNaming(inspector, CLASSES_IN_NAMING101);

    // Check naming101.*.a is kept due to **.a
    ClassSubject aa = inspector.clazz("naming101.a.a");
    assertEquals("naming101.a.a", aa.getFinalName());
    ClassSubject ba = inspector.clazz("naming101.b.a");
    assertEquals("naming101.b.a", ba.getFinalName());

    // Flattened to naming101, hence all other classes will be in naming101.* package.
    // Due to naming101.a.a, prefix naming101.a is already used. So, any other classes,
    // except for naming101.a.c, should not have naming101.a as package.
    List<String> klasses =
        ImmutableList.of(
            "naming101.c", "naming101.d", "naming101.a.b.c", "naming101.b.a", "naming101.b.b");
    for (String klass : klasses) {
      ClassSubject k = inspector.clazz(klass);
      assertNotEquals("naming101.a", k.getDexProgramClass().getType().getPackageName());
    }
  }

  private static void test101_rule005(CodeInspector inspector) {
    verifyUniqueNaming(inspector, CLASSES_IN_NAMING101);

    // All packages are renamed somehow. Need to check package hierarchy is consistent.
    ClassSubject aa = inspector.clazz("naming101.a.a");
    assertEquals(1, countPackageDepth(aa));
    ClassSubject abc = inspector.clazz("naming101.a.b.c");
    assertEquals(1, countPackageDepth(abc));

    // naming101.a/a; -> La/a/a; --prefix--> La/a
    // naming101.a/b/c; -> La/a/a/a; --prefix--> La/a/a --prefix--> La/a
    assertEquals(
        getParentPackagePrefix(aa.getFinalName()),
        getParentPackagePrefix(getParentPackagePrefix(abc.getFinalName())));
  }

  private static void test101_rule102(CodeInspector inspector) {
    verifyUniqueNaming(inspector, CLASSES_IN_NAMING101);

    // Check naming101.*.a is kept due to **.a
    ClassSubject aa = inspector.clazz("naming101.a.a");
    assertEquals("naming101.a.a", aa.getFinalName());
    ClassSubject ba = inspector.clazz("naming101.b.a");
    assertEquals("naming101.b.a", ba.getFinalName());
    // Due to package-private access, classes in the same package should remain in the same package.
    ClassSubject ac = inspector.clazz("naming101.a.c");
    assertEquals(
        aa.getDexProgramClass().getType().getPackageName(),
        ac.getDexProgramClass().getType().getPackageName());
    ClassSubject bb = inspector.clazz("naming101.b.b");
    assertEquals(
        ba.getDexProgramClass().getType().getPackageName(),
        bb.getDexProgramClass().getType().getPackageName());

    // We can repackage c and d since these have no external package-private references.
    List<String> klasses = ImmutableList.of("naming101.c", "naming101.d");
    for (String klass : klasses) {
      ClassSubject k = inspector.clazz(klass);
      assertEquals("naming101.a", k.getDexProgramClass().getType().getPackageName());
    }

    // All other classes can be repackaged to naming101.a, but naming101.a.a exists to make a name
    // conflict. Thus, those should not be renamed to 'a'.
    ClassSubject namingAbc = inspector.clazz("naming101.a.b.c");
    assertEquals("naming101.a", namingAbc.getDexProgramClass().getType().getPackageName());
    assertNotEquals("naming101.a.a", namingAbc.getFinalName());
  }

  private static void test101_rule104(CodeInspector inspector) {
    verifyUniqueNaming(inspector, CLASSES_IN_NAMING101);

    // Check naming101.*.a is kept due to **.a
    ClassSubject aa = inspector.clazz("naming101.a.a");
    assertEquals("naming101.a.a", aa.getFinalName());
    ClassSubject ba = inspector.clazz("naming101.b.a");
    assertEquals("naming101.b.a", ba.getFinalName());
    // Due to package-private access, classes in the same package should remain in the same package.
    ClassSubject ac = inspector.clazz("naming101.a.c");
    assertEquals(
        aa.getDexProgramClass().getType().getPackageName(),
        ac.getDexProgramClass().getType().getPackageName());
    ClassSubject bb = inspector.clazz("naming101.b.b");
    assertEquals(
        ba.getDexProgramClass().getType().getPackageName(),
        bb.getDexProgramClass().getType().getPackageName());

    // All other packages are flattened to naming101, hence all other classes will be in
    // naming101.* package. Due to naming101.a.a, prefix naming101.a is already used. So,
    // remaining classes should not have naming101.a as package.
    List<String> klasses = ImmutableList.of("naming101.c", "naming101.d", "naming101.a.b.c");
    for (String klass : klasses) {
      ClassSubject k = inspector.clazz(klass);
      assertNotEquals("naming101.a", k.getDexProgramClass().getType().getPackageName());
    }
  }

  private static void test101_rule106(CodeInspector inspector) {
    // Classes that end with either c or d will be kept and renamed.
    List<String> klasses =
        CLASSES_IN_NAMING101.stream()
            .filter(className -> className.endsWith("c") || className.endsWith("d"))
            .collect(Collectors.toList());
    verifyUniqueNaming(inspector, klasses);

    // Check naming101.c is kept as-is.
    ClassSubject topC = inspector.clazz("naming101.c");
    assertEquals("naming101.c", topC.getFinalName());

    // naming101.d accesses to a package-private, static field in naming101.c.
    // Therefore, it should remain in the same package.
    ClassSubject topD = inspector.clazz("naming101.d");
    assertEquals("naming101", topD.getDexProgramClass().getType().getPackageName());

    // The remaining classes are in subpackages of naming101 and will therefore not have
    // package-private access to namin101.c
    ClassSubject subAC = inspector.clazz("naming101.a.c");
    assertEquals(1, countPackageDepth(subAC));

    ClassSubject subABC = inspector.clazz("naming101.a.b.c");
    assertEquals(1, countPackageDepth(subABC));
  }
}
