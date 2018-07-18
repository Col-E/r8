// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.optimize.lambda.CaptureSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.junit.Test;

public class KotlinLambdaMergingTest extends AbstractR8KotlinTestBase {
  private static final String KOTLIN_FUNCTION_IFACE = "Lkotlin/jvm/functions/Function";
  private static final String KOTLIN_FUNCTION_IFACE_STR = "kotlin.jvm.functions.Function";
  private static final String KEEP_INNER_AND_ENCLOSING =
      "-keepattributes InnerClasses,EnclosingMethod\n";
  private static final String KEEP_SIGNATURE_INNER_ENCLOSING =
      "-keepattributes Signature,InnerClasses,EnclosingMethod\n";
  private Consumer<InternalOptions> optionsModifier =
      opts -> {
        opts.enableClassInlining = false;
        // Ensure that enclosing method and inner class attributes are kept even on classes that are
        // not explicitly mentioned by a keep rule.
        opts.forceProguardCompatibility = true;
      };

  abstract static class LambdaOrGroup {
    abstract boolean match(DexClass clazz);
  }

  static class Group extends LambdaOrGroup {
    final String pkg;
    final String capture;
    final int arity;
    final String sam;
    final int singletons;

    private Group(String pkg, String capture, int arity, String sam, int singletons) {
      this.pkg = pkg;
      this.capture = fixCapture(capture);
      this.arity = arity;
      this.sam = sam;
      this.singletons = singletons;
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
          "-$$LambdaGroup$XXXX (arity: " + arity +
          ", capture: " + capture + ", iface: " + sam + ", sing: " + singletons + ")";
    }

    @Override
    boolean match(DexClass clazz) {
      return clazz.type.getPackageDescriptor().equals(pkg) &&
          getLambdaOrGroupCapture(clazz).equals(capture) &&
          getLambdaSam(clazz).equals(sam) &&
          getLambdaSingletons(clazz) == singletons &&
          getLambdaOrGroupArity(clazz) == arity;
    }
  }

  private static Group kstyleImpl(String pkg, String capture, int arity, int singletons) {
    assertEquals(capture.isEmpty(), singletons != 0);
    return new Group(pkg, capture, arity, KOTLIN_FUNCTION_IFACE_STR + arity, singletons);
  }

  static Group kstyle(String pkg, int arity, int singletons) {
    assertTrue(singletons != 0);
    return kstyleImpl(pkg, "", arity, singletons);
  }

  private static Group kstyle(String pkg, String capture, int arity) {
    assertFalse(capture.isEmpty());
    return kstyleImpl(pkg, capture, arity, 0);
  }

  private static Group jstyleImpl(
      String pkg, String capture, int arity, String sam, int singletons) {
    assertTrue(capture.isEmpty() || singletons == 0);
    return new Group(pkg, capture, arity, sam, singletons);
  }

  private static Group jstyle(String pkg, String capture, int arity, String sam) {
    return jstyleImpl(pkg, capture, arity, sam, 0);
  }

  private static Group jstyle(String pkg, int arity, String sam, int singletons) {
    return jstyleImpl(pkg, "", arity, sam, singletons);
  }

  static class Lambda extends LambdaOrGroup {
    final String pkg;
    final String name;
    final int arity;

    Lambda(String pkg, String name, int arity) {
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
          getLambdaOrGroupArity(clazz) == arity;
    }
  }

  static class Verifier {
    final CodeInspector codeInspector;
    final List<DexClass> lambdas = new ArrayList<>();
    final List<DexClass> groups = new ArrayList<>();

    Verifier(AndroidApp app) throws IOException, ExecutionException {
      this(new CodeInspector(app));
    }

    Verifier(CodeInspector codeInspector) {
      this.codeInspector = codeInspector;
      initGroupsAndLambdas();
    }

    private void initGroupsAndLambdas() {
      codeInspector.forAllClasses(clazz -> {
        DexClass dexClass = clazz.getDexClass();
        if (isLambdaOrGroup(dexClass)) {
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
            // Validate static initializer.
            if (check instanceof Group) {
              assertEquals(clazz.directMethods().length, ((Group) check).singletons == 0 ? 1 : 2);
            }

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
            ", arity: " + getLambdaOrGroupArity(dexClass) +
            ", capture: " + getLambdaOrGroupCapture(dexClass) +
            ", sam: " + getLambdaSam(dexClass) +
            ", sing: " + getLambdaSingletons(dexClass));
        notFound++;
      }

      assertTrue(what + "s match failed", 0 == notFound && 0 == list.size());
    }
  }

  private static int getLambdaOrGroupArity(DexClass clazz) {
    if (isKStyleLambdaOrGroup(clazz)) {
      for (DexType iface : clazz.interfaces.values) {
        String descr = iface.descriptor.toString();
        if (descr.startsWith(KOTLIN_FUNCTION_IFACE)) {
          return Integer.parseInt(
              descr.substring(KOTLIN_FUNCTION_IFACE.length(), descr.length() - 1));
        }
      }

    } else {
      assertTrue(isJStyleLambdaOrGroup(clazz));
      // Taking the number of any virtual method parameters seems to be good enough.
      assertTrue(clazz.virtualMethods().length > 0);
      return clazz.virtualMethods()[0].method.proto.parameters.size();
    }
    fail("Failed to get arity for " + clazz.type.descriptor.toString());
    throw new AssertionError();
  }

  private static String getLambdaSam(DexClass clazz) {
    assertEquals(1, clazz.interfaces.size());
    return clazz.interfaces.values[0].toSourceString();
  }

  private static int getLambdaSingletons(DexClass clazz) {
    assertEquals(1, clazz.interfaces.size());
    return clazz.staticFields().length;
  }

  private static boolean isLambdaOrGroup(DexClass clazz) {
    return !clazz.type.getPackageDescriptor().startsWith("kotlin") &&
        (isKStyleLambdaOrGroup(clazz) || isJStyleLambdaOrGroup(clazz));
  }

  private static boolean isKStyleLambdaOrGroup(DexClass clazz) {
    return clazz.superType.descriptor.toString().equals("Lkotlin/jvm/internal/Lambda;");
  }

  private static boolean isJStyleLambdaOrGroup(DexClass clazz) {
    return clazz.superType.descriptor.toString().equals("Ljava/lang/Object;") &&
        clazz.interfaces.size() == 1;
  }

  private static boolean isLambdaGroupClass(DexClass clazz) {
    return clazz.type.getName().startsWith("-$$LambdaGroup$");
  }

  private static String getLambdaOrGroupCapture(DexClass clazz) {
    return CaptureSignature.getCaptureSignature(clazz.instanceFields());
  }

  @Test
  public void testTrivialKs() throws Exception {
    final String mainClassName = "lambdas_kstyle_trivial.MainKt";
    runTest("lambdas_kstyle_trivial", mainClassName, optionsModifier, (app) -> {
      Verifier verifier = new Verifier(app);
      String pkg = "lambdas_kstyle_trivial";

      verifier.assertLambdaGroups(
          allowAccessModification ?
              new Group[]{
                  kstyle("", 0, 4),
                  kstyle("", 1, 8),
                  kstyle("", 2, 2), // -\
                  kstyle("", 2, 5), // - 3 groups different by main method
                  kstyle("", 2, 4), // -/
                  kstyle("", 3, 2),
                  kstyle("", 22, 2)} :
              new Group[]{
                  kstyle(pkg, 0, 2),
                  kstyle(pkg, 1, 4),
                  kstyle(pkg, 2, 5), // - 2 groups different by main method
                  kstyle(pkg, 2, 4), // -/
                  kstyle(pkg, 3, 2),
                  kstyle(pkg, 22, 2),
                  kstyle(pkg + "/inner", 0, 2),
                  kstyle(pkg + "/inner", 1, 4)}
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
  public void testCapturesKs() throws Exception {
    final String mainClassName = "lambdas_kstyle_captures.MainKt";
    runTest("lambdas_kstyle_captures", mainClassName, optionsModifier, (app) -> {
      Verifier verifier = new Verifier(app);
      String pkg = "lambdas_kstyle_captures";
      String grpPkg = allowAccessModification ? "" : pkg;

      verifier.assertLambdaGroups(
          kstyle(grpPkg, "LLL", 0),
          kstyle(grpPkg, "ILL", 0),
          kstyle(grpPkg, "III", 0),
          kstyle(grpPkg, "BCDFIJLLLLSZ", 0),
          kstyle(grpPkg, "BCDFIJLLSZ", 0)
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
  public void testGenericsNoSignatureKs() throws Exception {
    final String mainClassName = "lambdas_kstyle_generics.MainKt";
    runTest("lambdas_kstyle_generics", mainClassName, optionsModifier, (app) -> {
      Verifier verifier = new Verifier(app);
      String pkg = "lambdas_kstyle_generics";
      String grpPkg = allowAccessModification ? "" : pkg;

      verifier.assertLambdaGroups(
          kstyle(grpPkg, 1, 3), // Group for Any
          kstyle(grpPkg, "L", 1), // Group for Beta
          kstyle(grpPkg, "LS", 1), // Group for Gamma
          kstyle(grpPkg, 1, 2)  // Group for int
      );

      verifier.assertLambdas(
          new Lambda(pkg, "MainKt$main$4", 1)
      );
    });
  }

  @Test
  public void testInnerClassesAndEnclosingMethodsKs() throws Exception {
    final String mainClassName = "lambdas_kstyle_generics.MainKt";
    runTest("lambdas_kstyle_generics", mainClassName,
        KEEP_INNER_AND_ENCLOSING, optionsModifier, (app) -> {
          Verifier verifier = new Verifier(app);
          String pkg = "lambdas_kstyle_generics";
          String grpPkg = allowAccessModification ? "" : pkg;

          verifier.assertLambdaGroups(
              kstyle(grpPkg, 1, 3), // Group for Any
              kstyle(grpPkg, "L", 1), // Group for Beta   // First
              kstyle(grpPkg, "L", 1), // Group for Beta   // Second
              kstyle(grpPkg, "LS", 1), // Group for Gamma // First
              kstyle(grpPkg, "LS", 1), // Group for Gamma // Second
              kstyle(grpPkg, 1, 2)  // Group for int
          );

          verifier.assertLambdas(
              new Lambda(pkg, "MainKt$main$4", 1)
          );
        });
  }

  @Test
  public void testGenericsSignatureInnerEnclosingKs() throws Exception {
    final String mainClassName = "lambdas_kstyle_generics.MainKt";
    runTest("lambdas_kstyle_generics", mainClassName,
        KEEP_SIGNATURE_INNER_ENCLOSING, optionsModifier, (app) -> {
          Verifier verifier = new Verifier(app);
          String pkg = "lambdas_kstyle_generics";
          String grpPkg = allowAccessModification ? "" : pkg;

          verifier.assertLambdaGroups(
              kstyle(grpPkg, 1, 3), // Group for Any
              kstyle(grpPkg, "L", 1), // Group for Beta in First
              kstyle(grpPkg, "L", 1), // Group for Beta in Second
              kstyle(grpPkg, "LS", 1), // Group for Gamma<String> in First
              kstyle(grpPkg, "LS", 1), // Group for Gamma<Integer> in First
              kstyle(grpPkg, "LS", 1), // Group for Gamma<String> in Second
              kstyle(grpPkg, "LS", 1), // Group for Gamma<Integer> in Second
              kstyle(grpPkg, 1, 2)  // Group for int
          );

          verifier.assertLambdas(
              new Lambda(pkg, "MainKt$main$4", 1)
          );
        });
  }

  @Test
  public void testTrivialJs() throws Exception {
    final String mainClassName = "lambdas_jstyle_trivial.MainKt";
    runTest("lambdas_jstyle_trivial", mainClassName, optionsModifier, (app) -> {
      Verifier verifier = new Verifier(app);
      String pkg = "lambdas_jstyle_trivial";
      String grp = allowAccessModification ? "" : pkg;

      String supplier = "lambdas_jstyle_trivial.Lambdas$Supplier";
      String intSupplier = "lambdas_jstyle_trivial.Lambdas$IntSupplier";
      String consumer = "lambdas_jstyle_trivial.Lambdas$Consumer";
      String intConsumer = "lambdas_jstyle_trivial.Lambdas$IntConsumer";
      String multiFunction = "lambdas_jstyle_trivial.Lambdas$MultiFunction";

      verifier.assertLambdaGroups(
          jstyle(grp, 0, intSupplier, 2),
          jstyle(grp, "L", 0, supplier),
          jstyle(grp, "LL", 0, supplier),
          jstyle(grp, "LLL", 0, supplier),
          jstyle(grp, 1, intConsumer, allowAccessModification ? 3 : 2),
          jstyle(grp, "I", 1, consumer),
          jstyle(grp, "II", 1, consumer),
          jstyle(grp, "III", 1, consumer),
          jstyle(grp, "IIII", 1, consumer),
          jstyle(grp, 3, multiFunction, 2),
          jstyle(grp, 3, multiFunction, 2),
          jstyle(grp, 3, multiFunction, 4),
          jstyle(grp, 3, multiFunction, 6)
      );

      verifier.assertLambdas(
          allowAccessModification ?
              new Lambda[]{
                  new Lambda(pkg + "/inner", "InnerKt$testInner1$4", 1),
                  new Lambda(pkg + "/inner", "InnerKt$testInner1$5", 1)
              } :
              new Lambda[]{
                  new Lambda(pkg + "/inner", "InnerKt$testInner1$1", 1),
                  new Lambda(pkg + "/inner", "InnerKt$testInner1$2", 1),
                  new Lambda(pkg + "/inner", "InnerKt$testInner1$3", 1),
                  new Lambda(pkg + "/inner", "InnerKt$testInner1$4", 1),
                  new Lambda(pkg + "/inner", "InnerKt$testInner1$5", 1)
              }

      );
    });
  }

  @Test
  public void testSingleton() throws Exception {
    final String mainClassName = "lambdas_singleton.MainKt";
    runTest("lambdas_singleton", mainClassName, optionsModifier, (app) -> {
      Verifier verifier = new Verifier(app);
      String pkg = "lambdas_singleton";
      String grp = allowAccessModification ? "" : pkg;

      verifier.assertLambdaGroups(
          kstyle(grp, 1, 1 /* 1 out of 5 lambdas in the group */),
          jstyle(grp, 2, "java.util.Comparator", 0 /* 0 out of 2 lambdas in the group */)
      );

      verifier.assertLambdas(/* None */);
    });
  }
}
