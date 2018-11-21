// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.uninstantiatedtypes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VoidReturnTypeRewritingTest extends TestBase {

  private final Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public VoidReturnTypeRewritingTest(Backend backend) {
    this.backend = backend;
  }

  @Ignore("b/110806787")
  @Test
  public void test() throws Exception {
    String expected =
        StringUtils.lines(
            "Factory.createStatic() -> null",
            "Factory.createVirtual() -> null",
            "SubFactory.createVirtual() -> null",
            "SubSubFactory.createVirtual() -> null");

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expected);

    CodeInspector inspector =
        testForR8(backend)
            .addInnerClasses(VoidReturnTypeRewritingTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .enableMergeAnnotations()
            .addKeepRules("-dontobfuscate")
            .addOptionsModification(options -> options.enableClassInlining = false)
            .run(TestClass.class)
            .assertSuccessWithOutput(expected)
            .inspector();

    ClassSubject factoryClassSubject = inspector.clazz(Factory.class);
    MethodSubject createStaticMethodSubject =
        factoryClassSubject.uniqueMethodWithName("createStatic");
    assertThat(createStaticMethodSubject, isPresent());
    assertTrue(createStaticMethodSubject.getMethod().method.proto.returnType.isVoidType());
    MethodSubject createVirtualMethodSubject =
        factoryClassSubject.uniqueMethodWithName("createVirtual");
    assertThat(createVirtualMethodSubject, isPresent());
    assertTrue(createVirtualMethodSubject.getMethod().method.proto.returnType.isVoidType());

    createVirtualMethodSubject =
        inspector.clazz(SubFactory.class).uniqueMethodWithName("createVirtual");
    assertThat(createVirtualMethodSubject, isPresent());
    assertTrue(createVirtualMethodSubject.getMethod().method.proto.returnType.isVoidType());

    ClassSubject subSubFactoryClassSubject = inspector.clazz(SubSubFactory.class);
    assertThat(subSubFactoryClassSubject.method("void", "createVirtual"), isPresent());
    assertThat(
        subSubFactoryClassSubject.method(SubUninstantiated.class.getTypeName(), "createVirtual"),
        isPresent());

    // TODO(b/110806787): Uninstantiated is kept because SubUninstantiated inherits from it.
    // We should consider rewriting SubUninstantiated such that it no longer inherits from
    // Uninstantiated.
    assertThat(inspector.clazz(Uninstantiated.class), isPresent());
    assertThat(inspector.clazz(SubUninstantiated.class), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      Uninstantiated obj1 = Factory.createStatic();
      System.out.println(" -> " + obj1);

      Uninstantiated obj2 = new Factory().createVirtual();
      System.out.println(" -> " + obj2);

      Uninstantiated obj3 = new SubFactory().createVirtual();
      System.out.println(" -> " + obj3);

      Uninstantiated obj4 = new SubSubFactory().createVirtual();
      System.out.println(" -> " + obj4);
    }
  }

  @NeverMerge
  static class Uninstantiated {}

  @NeverMerge
  static class SubUninstantiated extends Uninstantiated {}

  @NeverMerge
  static class Factory {

    @NeverInline
    public static Uninstantiated createStatic() {
      System.out.print("Factory.createStatic()");
      return null;
    }

    @NeverInline
    public Uninstantiated createVirtual() {
      System.out.print("Factory.createVirtual()");
      return null;
    }
  }

  @NeverMerge
  static class SubFactory extends Factory {

    @Override
    @NeverInline
    public Uninstantiated createVirtual() {
      System.out.print("SubFactory.createVirtual()");
      return null;
    }
  }

  @NeverMerge
  static class SubSubFactory extends SubFactory {

    @Override
    @NeverInline
    public SubUninstantiated createVirtual() {
      System.out.print("SubSubFactory.createVirtual()");
      return null;
    }
  }
}
