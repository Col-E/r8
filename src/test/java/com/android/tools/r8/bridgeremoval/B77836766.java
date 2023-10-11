// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bridgeremoval;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexInvokeVirtual;
import com.android.tools.r8.dex.code.DexReturnVoid;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B77836766 extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  /**
   * The below Jasmin code mimics the following Kotlin code:
   *
   * package java_pkg;
   *
   * public interface Itf1 {
   *   void foo(String arg);
   * }
   *
   * public interface Itf2 {
   *   void foo(Integer arg);
   * }
   *
   * package kt_pkg;
   *
   * internal abstract class AbsCls<T> {
   *   void foo(T obj) { ... }
   * }
   *
   * internal class Cls1() : AbsCls<String>(), Itf1
   *
   * internal class Cls2() : AbsCls<Integer>(), Itf2
   *
   *
   * kotlinc introduced bridge methods Cls?#foo to AbsCls#foo:
   *
   * class Cls1 extends AbsCls implements Itf1 {
   *   public bridge synthetic void foo(String arg) {
   *     invoke-virtual Cls1#foo(Object)V
   *   }
   * }
   *
   * Note that we can't write such code in Java because javac requires Itf?#foo, which are
   * technically abstract methods, to be explicitly overridden.
   */
  @Test
  public void test_bridgeTargetInBase_differentBridges() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();

    ClassBuilder absCls = jasminBuilder.addClass("AbsCls");
    absCls.setAccess("public abstract");
    absCls.addFinalMethod("foo", ImmutableList.of("Ljava/lang/Object;"), "V",
        ".limit stack 3",
        ".limit locals 2",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "aload_1",
        "invokevirtual java/io/PrintStream/print(Ljava/lang/Object;)V",
        "return");

    ClassBuilder itf1 = jasminBuilder.addInterface("Itf1");
    itf1.addAbstractMethod("foo", ImmutableList.of("Ljava/lang/String;"), "V");

    ClassBuilder cls1 = jasminBuilder.addClass("Cls1", absCls.name, itf1.name);
    // Mimic Kotlin's "internal" class
    cls1.setAccess("");
    cls1.addRuntimeInvisibleAnnotation(KeepConstantArguments.class.getTypeName());
    cls1.addBridgeMethod("foo", ImmutableList.of("Ljava/lang/String;"), "V",
        ".limit stack 2",
        ".limit locals 2",
        "aload_0",
        "aload_1",
        "invokevirtual " + cls1.name + "/foo(Ljava/lang/Object;)V",
        "return");

    ClassBuilder itf2 = jasminBuilder.addInterface("Itf2");
    itf2.addAbstractMethod("foo", ImmutableList.of("Ljava/lang/Integer;"), "V");

    ClassBuilder cls2Class = jasminBuilder.addClass("Cls2", absCls.name, itf2.name);
    // Mimic Kotlin's "internal" class
    cls2Class.setAccess("");
    cls2Class.addBridgeMethod(
        "foo",
        ImmutableList.of("Ljava/lang/Integer;"),
        "V",
        ".limit stack 2",
        ".limit locals 2",
        "aload_0",
        "aload_1",
        "invokevirtual " + cls2Class.name + "/foo(Ljava/lang/Object;)V",
        "return");

    ClassBuilder mainClass = jasminBuilder.addClass("Main");
    mainClass.addMainMethod(
        ".limit stack 5",
        ".limit locals 2",
        "new " + cls1.name,
        "dup",
        "invokespecial " + cls1.name + "/<init>()V",
        "ldc \"Hello\"",
        "invokevirtual " + cls1.name + "/foo(Ljava/lang/String;)V",
        "new " + cls2Class.name,
        "dup",
        "invokespecial " + cls2Class.name + "/<init>()V",
        "aload_0",
        "arraylength",
        "invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;",
        "invokevirtual " + cls2Class.name + "/foo(Ljava/lang/Integer;)V",
        "return");

    testForR8(parameters.getBackend())
        .addProgramClassFileData(jasminBuilder.buildClasses())
        .addKeepMainRule(mainClass.name)
        .addOptionsModification(this::configure)
        .enableConstantArgumentAnnotations()
        .noHorizontalClassMerging(cls2Class.name)
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject absSubject = inspector.clazz(absCls.name);
              assertThat(absSubject, isPresent());
              ClassSubject cls1Subject = inspector.clazz(cls1.name);
              assertThat(cls1Subject, isPresent());
              ClassSubject cls2Subject = inspector.clazz(cls2Class.name);
              assertThat(cls2Subject, isPresent());

              // Cls1#foo and Cls2#foo should not refer to each other.
              // They can invoke their own bridge method or AbsCls#foo (via member rebinding).

              // Cls2#foo has been moved to AbsCls#foo as a result of bridge hoisting.
              MethodSubject fooInCls2 = cls2Subject.method("void", "foo", "java.lang.Integer");
              assertThat(fooInCls2, not(isPresent()));

              MethodSubject fooFromCls2InAbsCls =
                  absSubject.method("void", "foo", "java.lang.Integer");
              assertThat(fooFromCls2InAbsCls, isPresent());

              // Cls1#foo has been moved to AbsCls#foo as a result of bridge hoisting.
              MethodSubject fooInCls1 = cls1Subject.method("void", "foo", "java.lang.String");
              assertThat(fooInCls1, not(isPresent()));

              MethodSubject fooFromCls1InAbsCls =
                  absSubject.method("void", "foo", "java.lang.String");
              assertThat(fooFromCls1InAbsCls, isPresent());

              if (parameters.isDexRuntime()) {
                DexCode code = fooFromCls2InAbsCls.getMethod().getCode().asDexCode();
                checkInstructions(
                    code, ImmutableList.of(DexInvokeVirtual.class, DexReturnVoid.class));
                DexInvokeVirtual invoke = (DexInvokeVirtual) code.instructions[0];
                assertEquals(absSubject.getDexProgramClass().type, invoke.getMethod().holder);

                code = fooFromCls1InAbsCls.getMethod().getCode().asDexCode();
                checkInstructions(
                    code, ImmutableList.of(DexInvokeVirtual.class, DexReturnVoid.class));
                invoke = (DexInvokeVirtual) code.instructions[0];
                assertEquals(absSubject.getDexProgramClass().type, invoke.getMethod().holder);
              }
            })
        .run(parameters.getRuntime(), mainClass.name)
        .assertSuccessWithOutput("Hello0");
  }

  /**
   * class Base {
   *   void foo(Object o) {...}
   * }
   * interface ItfInteger {
   *   void foo(Integer o);
   * }
   * class DerivedInteger extends Base implements ItfInteger {
   *   // Bridge method deferring to Base#foo(Object):
   *   public bridge synthetic void foo(Integer o) {
   *     foo((Object) o);
   *   } }
   * class DerivedString extends Base {
   *   // Regular non-bridge method calling Base#foo(Object):
   *   public void bar(String o) {
   *     foo(o);
   *   } }
   */
  @Test
  public void test_bridgeTargetInBase_bridgeAndNonBridge() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();

    ClassBuilder baseCls = jasminBuilder.addClass("Base");
    baseCls.addVirtualMethod("foo", ImmutableList.of("Ljava/lang/Object;"), "V",
        ".limit stack 3",
        ".limit locals 2",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "aload_1",
        "invokevirtual java/io/PrintStream/print(Ljava/lang/Object;)V",
        "return");

    ClassBuilder itf = jasminBuilder.addInterface("ItfInteger");
    itf.addAbstractMethod("foo", ImmutableList.of("Ljava/lang/Integer;"), "V");

    ClassBuilder derivedIntegerClass =
        jasminBuilder.addClass("DerivedInteger", baseCls.name, itf.name);
    derivedIntegerClass.addBridgeMethod(
        "foo",
        ImmutableList.of("Ljava/lang/Integer;"),
        "V",
        ".limit stack 2",
        ".limit locals 2",
        "aload_0",
        "aload_1",
        "invokevirtual " + derivedIntegerClass.name + "/foo(Ljava/lang/Object;)V",
        "return");

    ClassBuilder cls2 = jasminBuilder.addClass("DerivedString", baseCls.name);
    cls2.addRuntimeInvisibleAnnotation(KeepConstantArguments.class.getTypeName());
    cls2.addVirtualMethod("bar", ImmutableList.of("Ljava/lang/String;"), "V",
        ".limit stack 2",
        ".limit locals 2",
        "aload_0",
        "aload_1",
        "invokevirtual " + cls2.name + "/foo(Ljava/lang/Object;)V",
        "return");

    ClassBuilder mainClass = jasminBuilder.addClass("Main");
    mainClass.addMainMethod(
        ".limit stack 5",
        ".limit locals 2",
        "new " + derivedIntegerClass.name,
        "dup",
        "invokespecial " + derivedIntegerClass.name + "/<init>()V",
        "aload_0",
        "arraylength",
        "invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;",
        "invokevirtual " + derivedIntegerClass.name + "/foo(Ljava/lang/Integer;)V",
        "new " + cls2.name,
        "dup",
        "invokespecial " + cls2.name + "/<init>()V",
        "ldc \"Bar\"",
        "invokevirtual " + cls2.name + "/bar(Ljava/lang/String;)V",
        "return");

    testForR8(parameters.getBackend())
        .addProgramClassFileData(jasminBuilder.buildClasses())
        .addKeepMainRule(mainClass.name)
        .addOptionsModification(this::configure)
        .enableConstantArgumentAnnotations()
        .noHorizontalClassMerging(derivedIntegerClass.name)
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject baseSubject = inspector.clazz(baseCls.name);
              assertThat(baseSubject, isPresent());
              ClassSubject cls1Subject = inspector.clazz(derivedIntegerClass.name);
              assertThat(cls1Subject, isPresent());
              ClassSubject cls2Subject = inspector.clazz(cls2.name);
              assertThat(cls2Subject, isPresent());

              // Cls1#foo and Cls2#bar should refer to Base#foo.

              MethodSubject barInCls2 = baseSubject.method("void", "bar", "java.lang.String");
              assertThat(barInCls2, isPresent());

              // Cls1#foo has been moved to Base#foo as a result of bridge hoisting.
              MethodSubject fooInCls1 = cls1Subject.method("void", "foo", "java.lang.Integer");
              assertThat(fooInCls1, not(isPresent()));

              MethodSubject fooInBase = baseSubject.method("void", "foo", "java.lang.Integer");
              assertThat(fooInBase, isPresent());

              if (parameters.isDexRuntime()) {
                DexCode code = barInCls2.getMethod().getCode().asDexCode();
                checkInstructions(
                    code, ImmutableList.of(DexInvokeVirtual.class, DexReturnVoid.class));
                DexInvokeVirtual invoke = (DexInvokeVirtual) code.instructions[0];
                assertEquals(baseSubject.getDexProgramClass().type, invoke.getMethod().holder);

                code = fooInBase.getMethod().getCode().asDexCode();
                checkInstructions(
                    code, ImmutableList.of(DexInvokeVirtual.class, DexReturnVoid.class));
                invoke = (DexInvokeVirtual) code.instructions[0];
                assertEquals(baseSubject.getDexProgramClass().type, invoke.getMethod().holder);
              }
            })
        .run(parameters.getRuntime(), mainClass.name)
        .assertSuccessWithOutput("0Bar");
  }

  /**
   * class Base {
   *   protected void foo(Object o) { ... }
   *   // Bridge method deferring to Base#foo(Object):
   *   public bridge synthetic void foo(Integer o) {
   *     foo((Object) o);
   *   } }
   * class DerivedString extends Base {
   *   // Regular non-bridge method calling Base#foo(Object):
   *   public void bar(String o) {
   *     foo(o);
   *   } }
   */
  @Test
  public void test_nonBridgeInSubType() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();

    ClassBuilder baseCls = jasminBuilder.addClass("Base");
    baseCls.addVirtualMethod("foo", ImmutableList.of("Ljava/lang/Object;"), "V",
        ".limit stack 3",
        ".limit locals 2",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "aload_1",
        "invokevirtual java/io/PrintStream/print(Ljava/lang/Object;)V",
        "return");
    baseCls.addBridgeMethod("foo", ImmutableList.of("Ljava/lang/Integer;"), "V",
        ".limit stack 2",
        ".limit locals 2",
        "aload_0",
        "aload_1",
        "invokevirtual " + baseCls.name + "/foo(Ljava/lang/Object;)V",
        "return");

    ClassBuilder subCls = jasminBuilder.addClass("DerivedString", baseCls.name);
    subCls.addRuntimeInvisibleAnnotation(KeepConstantArguments.class.getTypeName());
    subCls.addVirtualMethod("bar", ImmutableList.of("Ljava/lang/String;"), "V",
        ".limit stack 2",
        ".limit locals 2",
        "aload_0",
        "aload_1",
        "invokevirtual " + subCls.name + "/foo(Ljava/lang/Object;)V",
        "return");

    ClassBuilder mainClass = jasminBuilder.addClass("Main");
    mainClass.addMainMethod(
        ".limit stack 5",
        ".limit locals 2",
        "new " + baseCls.name,
        "dup",
        "invokespecial " + baseCls.name + "/<init>()V",
        "aload_0",
        "arraylength",
        "invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;",
        "invokevirtual " + baseCls.name + "/foo(Ljava/lang/Integer;)V",
        "new " + subCls.name,
        "dup",
        "invokespecial " + subCls.name + "/<init>()V",
        "ldc \"Bar\"",
        "invokevirtual " + subCls.name + "/bar(Ljava/lang/String;)V",
        "return");

    testForR8(parameters.getBackend())
        .addProgramClassFileData(jasminBuilder.buildClasses())
        .addKeepMainRule(mainClass.name)
        .addOptionsModification(this::configure)
        .enableConstantArgumentAnnotations()
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject baseSubject = inspector.clazz(baseCls.name);
              assertThat(baseSubject, isPresent());
              ClassSubject subSubject = inspector.clazz(subCls.name);
              assertThat(subSubject, isPresent());

              // DerivedString2#bar should refer to Base#foo.

              MethodSubject barInSub = baseSubject.method("void", "bar", "java.lang.String");
              assertThat(barInSub, isPresent());

              if (parameters.isDexRuntime()) {
                DexCode code = barInSub.getMethod().getCode().asDexCode();
                checkInstructions(
                    code, ImmutableList.of(DexInvokeVirtual.class, DexReturnVoid.class));
                DexInvokeVirtual invoke = (DexInvokeVirtual) code.instructions[0];
                assertEquals(baseSubject.getDexProgramClass().type, invoke.getMethod().holder);
              }
            })
        .run(parameters.getRuntime(), mainClass.name)
        .assertSuccessWithOutput("0Bar");
  }

  /*
   * public class Base {
   *  public bridge void foo(Integer i) { foo((Object) i); }
   *  public foo(Object o) { print(o); }
   *  public bar(String s) { foo(s); }
   * }
   */
  @Test
  public void test_bridgeTargetInsideTheSameClass() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();

    ClassBuilder cls = jasminBuilder.addClass("Base");
    cls.addBridgeMethod("foo", ImmutableList.of("Ljava/lang/Integer;"), "V",
        ".limit stack 2",
        ".limit locals 2",
        "aload_0",
        "aload_1",
        "invokevirtual " + cls.name + "/foo(Ljava/lang/Object;)V",
        "return");
    cls.addVirtualMethod("foo", ImmutableList.of("Ljava/lang/Object;"), "V",
        ".limit stack 3",
        ".limit locals 2",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "aload_1",
        "invokevirtual java/io/PrintStream/print(Ljava/lang/Object;)V",
        "return");
    cls.addRuntimeInvisibleAnnotation(KeepConstantArguments.class.getTypeName());
    cls.addVirtualMethod("bar", ImmutableList.of("Ljava/lang/String;"), "V",
        ".limit stack 2",
        ".limit locals 2",
        "aload_0",
        "aload_1",
        "invokevirtual " + cls.name + "/foo(Ljava/lang/Object;)V",
        "return");

    ClassBuilder mainClass = jasminBuilder.addClass("Main");
    mainClass.addMainMethod(
        ".limit stack 5",
        ".limit locals 2",
        "new " + cls.name,
        "dup",
        "invokespecial " + cls.name + "/<init>()V",
        "aload_0",
        "arraylength",
        "invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;",
        "invokevirtual " + cls.name + "/foo(Ljava/lang/Integer;)V",
        "new " + cls.name,
        "dup",
        "invokespecial " + cls.name + "/<init>()V",
        "ldc \"Bar\"",
        "invokevirtual " + cls.name + "/bar(Ljava/lang/String;)V",
        "return");

    testForR8(parameters.getBackend())
        .addProgramClassFileData(jasminBuilder.buildClasses())
        .addKeepMainRule(mainClass.name)
        .addOptionsModification(this::configure)
        .enableConstantArgumentAnnotations()
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject baseSubject = inspector.clazz(cls.name);
              assertThat(baseSubject, isPresent());

              // Base#bar should remain as-is, i.e., refer to Base#foo(Object).

              MethodSubject barInSub = baseSubject.method("void", "bar", "java.lang.String");
              assertThat(barInSub, isPresent());

              if (parameters.isDexRuntime()) {
                DexCode code = barInSub.getMethod().getCode().asDexCode();
                checkInstructions(
                    code, ImmutableList.of(DexInvokeVirtual.class, DexReturnVoid.class));
                DexInvokeVirtual invoke = (DexInvokeVirtual) code.instructions[0];
                assertEquals(baseSubject.getDexProgramClass().type, invoke.getMethod().holder);
              }
            })
        .run(parameters.getRuntime(), mainClass.name)
        .assertSuccessWithOutput("0Bar");
  }

  private void configure(InternalOptions options) {
    options.callSiteOptimizationOptions().setEnableMethodStaticizing(false);
    // Callees are invoked with a simple constant, e.g., "Bar". Propagating it into the callees
    // bothers what the tests want to check, such as exact instructions in the body that include
    // invocation kinds, like virtual call to a bridge.
    // Disable inlining to avoid the (short) tested method from being inlined and then removed.
    options.inlinerOptions().enableInlining = false;
  }
}
