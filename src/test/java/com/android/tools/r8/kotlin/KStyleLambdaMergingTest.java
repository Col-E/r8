// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.optimize.lambda.CaptureSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

public class KStyleLambdaMergingTest extends AbstractR8KotlinTestBase {
  private static final String KOTLIN_FUNCTION_IFACE = "Lkotlin/jvm/functions/Function";
  private static final String KEEP_INNER_AND_ENCLOSING =
      "-keepattributes InnerClasses,EnclosingMethod\n";
  private static final String KEEP_SIGNATURE_INNER_ENCLOSING =
      "-keepattributes Signature,InnerClasses,EnclosingMethod\n";

  abstract static class LambdaOrGroup {
    abstract boolean match(DexClass clazz);
  }

  static class Group extends LambdaOrGroup {
    final String pkg;
    final String capture;
    final int arity;

    private Group(String pkg, String capture, int arity) {
      this.pkg = pkg;
      this.capture = fixCapture(capture);
      this.arity = arity;
    }

    private String fixCapture(String capture) {
      capture += "I";
      char[] chars = capture.toCharArray();
      Arrays.sort(chars);
      return new String(chars);
    }

    @Override
    public String toString() {
      return "group class " +
          (pkg.length() == 0 ? "" : pkg + "/") +
          "-$$LambdaGroup$XXXX (arity: " + arity + ", capture: " + capture + ")";
    }

    @Override
    boolean match(DexClass clazz) {
      return clazz.type.getPackageDescriptor().equals(pkg) &&
          getLambdaGroupCapture(clazz).equals(capture) &&
          getLambdaArity(clazz) == arity;
    }
  }

  static class Lambda extends LambdaOrGroup {
    final String pkg;
    final String name;
    final int arity;

    private Lambda(String pkg, String name, int arity) {
      this.pkg = pkg;
      this.name = name;
      this.arity = arity;
    }

    @Override
    public String toString() {
      return "lambda class " +
          (pkg.length() == 0 ? "" : pkg + "/") +
          name + " (arity: " + arity + ")";
    }

    @Override
    boolean match(DexClass clazz) {
      return clazz.type.getPackageDescriptor().equals(pkg) &&
          clazz.type.getName().equals(name) &&
          getLambdaArity(clazz) == arity;
    }
  }

  static class Verifier {
    final DexInspector dexInspector;
    final List<DexClass> lambdas = new ArrayList<>();
    final List<DexClass> groups = new ArrayList<>();

    Verifier(AndroidApp app) throws IOException, ExecutionException {
      this.dexInspector = new DexInspector(app);
      dexInspector.forAllClasses(clazz -> {
        DexClass dexClass = clazz.getDexClass();
        if (extendsLambdaBase(dexClass)) {
          if (isLambdaGroupClass(dexClass)) {
            groups.add(dexClass);
          } else {
            lambdas.add(dexClass);
          }
        }
      });
    }

    void assertLambdaGroups(Group... groups) {
      assertLambdasOrGroups("Lambda group", this.groups, groups);
    }

    void assertLambdas(Lambda... lambdas) {
      assertLambdasOrGroups("Lambda", this.lambdas, lambdas);
    }

    @SafeVarargs
    private static <T extends LambdaOrGroup>
    void assertLambdasOrGroups(String what, List<DexClass> objects, T... checks) {
      ArrayList<DexClass> list = Lists.newArrayList(objects);
      for (int i = 0; i < checks.length; i++) {
        T check = checks[i];
        for (DexClass clazz : list) {
          if (check.match(clazz)) {
            list.remove(clazz);
            checks[i] = null;
            break;
          }
        }
      }

      int notFound = 0;
      for (T check : checks) {
        if (check != null) {
          System.err.println(what + " not found: " + check);
          notFound++;
        }
      }

      for (DexClass dexClass : list) {
        System.err.println(what + " unexpected: " +
            dexClass.type.descriptor.toString() +
            ", arity: " + getLambdaArity(dexClass) +
            ", capture: " + getLambdaGroupCapture(dexClass));
        notFound++;
      }

      assertTrue(what + "s match failed", 0 == notFound && 0 == list.size());
    }
  }

  private static int getLambdaArity(DexClass clazz) {
    for (DexType iface : clazz.interfaces.values) {
      String descr = iface.descriptor.toString();
      if (descr.startsWith(KOTLIN_FUNCTION_IFACE)) {
        return Integer.parseInt(
            descr.substring(KOTLIN_FUNCTION_IFACE.length(), descr.length() - 1));
      }
    }
    fail("Type " + clazz.type.descriptor.toString() +
        " does not implement functional interface.");
    throw new AssertionError();
  }

  private static boolean extendsLambdaBase(DexClass clazz) {
    return clazz.superType.descriptor.toString().equals("Lkotlin/jvm/internal/Lambda;");
  }

  private static boolean isLambdaGroupClass(DexClass clazz) {
    return clazz.type.getName().startsWith("-$$LambdaGroup$");
  }

  private static String getLambdaGroupCapture(DexClass clazz) {
    return CaptureSignature.getCaptureSignature(clazz.instanceFields());
  }

  @Test
  public void testTrivial() throws Exception {
    final String mainClassName = "lambdas.kstyle.trivial.MainKt";
    runTest("lambdas_kstyle_trivial", mainClassName, null, (app) -> {
      Verifier verifier = new Verifier(app);
      String pkg = "lambdas/kstyle/trivial";

      verifier.assertLambdaGroups(
          allowAccessModification ?
              new Group[]{
                  new Group("", "", 0),
                  new Group("", "", 1),
                  new Group("", "", 2), // -\
                  new Group("", "", 2), // - 3 groups different by main method
                  new Group("", "", 2), // -/
                  new Group("", "", 3),
                  new Group("", "", 22)} :
              new Group[]{
                  new Group(pkg, "", 0),
                  new Group(pkg, "", 1),
                  new Group(pkg, "", 2), // - 2 groups different by main method
                  new Group(pkg, "", 2), // -/
                  new Group(pkg, "", 3),
                  new Group(pkg, "", 22),
                  new Group(pkg + "/inner", "", 0),
                  new Group(pkg + "/inner", "", 1)}
      );

      verifier.assertLambdas(
          allowAccessModification ?
              new Lambda[]{
                  new Lambda(pkg, "MainKt$testStateless$6", 1) /* Banned for limited inlining */} :
              new Lambda[]{
                  new Lambda(pkg, "MainKt$testStateless$6", 1), /* Banned for limited inlining */
                  new Lambda(pkg, "MainKt$testStateless$8", 2),
                  new Lambda(pkg + "/inner", "InnerKt$testInnerStateless$7", 2)}

      );
    });
  }

  @Test
  public void testCaptures() throws Exception {
    final String mainClassName = "lambdas.kstyle.captures.MainKt";
    runTest("lambdas_kstyle_captures", mainClassName, null, (app) -> {
      Verifier verifier = new Verifier(app);
      String pkg = "lambdas/kstyle/captures";
      String grpPkg = allowAccessModification ? "" : pkg;

      verifier.assertLambdaGroups(
          new Group(grpPkg, "LLL", 0),
          new Group(grpPkg, "ILL", 0),
          new Group(grpPkg, "III", 0),
          new Group(grpPkg, "BCDFIJLLLLSZ", 0),
          new Group(grpPkg, "BCDFIJLLSZ", 0)
      );

      verifier.assertLambdas(
          new Lambda(pkg, "MainKt$test1$15", 0),
          new Lambda(pkg, "MainKt$test2$10", 0),
          new Lambda(pkg, "MainKt$test2$11", 0),
          new Lambda(pkg, "MainKt$test2$9", 0)
      );
    });
  }

  @Test
  public void testGenericsNoSignature() throws Exception {
    final String mainClassName = "lambdas.kstyle.generics.MainKt";
    runTest("lambdas_kstyle_generics", mainClassName, null, (app) -> {
      Verifier verifier = new Verifier(app);
      String pkg = "lambdas/kstyle/generics";
      String grpPkg = allowAccessModification ? "" : pkg;

      verifier.assertLambdaGroups(
          new Group(grpPkg, "", 1), // Group for Any
          new Group(grpPkg, "L", 1), // Group for Beta
          new Group(grpPkg, "LS", 1), // Group for Gamma
          new Group(grpPkg, "", 1)  // Group for int
      );

      verifier.assertLambdas(
          new Lambda(pkg, "MainKt$main$4", 1)
      );
    });
  }

  @Test
  public void testInnerClassesAndEnclosingMethods() throws Exception {
    final String mainClassName = "lambdas.kstyle.generics.MainKt";
    runTest("lambdas_kstyle_generics", mainClassName, KEEP_INNER_AND_ENCLOSING, (app) -> {
      Verifier verifier = new Verifier(app);
      String pkg = "lambdas/kstyle/generics";
      String grpPkg = allowAccessModification ? "" : pkg;

      verifier.assertLambdaGroups(
          new Group(grpPkg, "", 1), // Group for Any
          new Group(grpPkg, "L", 1), // Group for Beta   // First
          new Group(grpPkg, "L", 1), // Group for Beta   // Second
          new Group(grpPkg, "LS", 1), // Group for Gamma // First
          new Group(grpPkg, "LS", 1), // Group for Gamma // Second
          new Group(grpPkg, "", 1)  // Group for int
      );

      verifier.assertLambdas(
          new Lambda(pkg, "MainKt$main$4", 1)
      );
    });
  }

  @Test
  public void testGenericsSignatureInnerEnclosing() throws Exception {
    final String mainClassName = "lambdas.kstyle.generics.MainKt";
    runTest("lambdas_kstyle_generics", mainClassName, KEEP_SIGNATURE_INNER_ENCLOSING, (app) -> {
      Verifier verifier = new Verifier(app);
      String pkg = "lambdas/kstyle/generics";
      String grpPkg = allowAccessModification ? "" : pkg;

      verifier.assertLambdaGroups(
          new Group(grpPkg, "", 1), // Group for Any
          new Group(grpPkg, "L", 1), // Group for Beta in First
          new Group(grpPkg, "L", 1), // Group for Beta in Second
          new Group(grpPkg, "LS", 1), // Group for Gamma<String> in First
          new Group(grpPkg, "LS", 1), // Group for Gamma<Integer> in First
          new Group(grpPkg, "LS", 1), // Group for Gamma<String> in Second
          new Group(grpPkg, "LS", 1), // Group for Gamma<Integer> in Second
          new Group(grpPkg, "", 1)  // Group for int
      );

      verifier.assertLambdas(
          new Lambda(pkg, "MainKt$main$4", 1)
      );
    });
  }
}
