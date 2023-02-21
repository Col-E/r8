// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.genericsignature.GenericSignatureKeepAttributesTest.Outer.Middle;
import com.android.tools.r8.graph.genericsignature.GenericSignatureKeepAttributesTest.Outer.Middle.Inner;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignatureKeepAttributesTest extends TestBase {

  private final TestParameters parameters;
  private final boolean isCompat;

  private final String[] EXPECTED_JVM =
      new String[] {
        "Outer.Middle.Inner::test",
        "public class com.android.tools.r8.graph.genericsignature"
            + ".GenericSignatureKeepAttributesTest$Outer$Middle$Inner<I>"
      };

  private final String[] EXPECTED_DEX =
      new String[] {
        "Outer.Middle.Inner::test",
        "class com.android.tools.r8.graph.genericsignature"
            + ".GenericSignatureKeepAttributesTest$Outer$Middle$Inner"
      };

  @Parameters(name = "{0}, isCompat: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public GenericSignatureKeepAttributesTest(TestParameters parameters, boolean isCompat) {
    this.parameters = parameters;
    this.isCompat = isCompat;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Supplier.class, Predicate.class, Outer.class, Middle.class, Main.class)
        .addProgramClassFileData(getClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(parameters.isCfRuntime() ? EXPECTED_JVM : EXPECTED_DEX);
  }

  @Test
  public void testR8() throws Exception {
    (isCompat ? testForR8Compat(parameters.getBackend()) : testForR8(parameters.getBackend()))
        .addProgramClasses(Supplier.class, Predicate.class, Outer.class, Middle.class, Main.class)
        .addProgramClassFileData(getClassFileData())
        .setMinApi(parameters)
        .addKeepAttributeSignature()
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(Outer.Middle.Inner.class, Supplier.class, Predicate.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(parameters.isCfRuntime() ? EXPECTED_JVM : EXPECTED_DEX)
        .inspect(this::inspectSignatures);
  }

  private byte[] getClassFileData() throws Exception {
    return transformer(Inner.class)
        .transformMethodInsnInMethod(
            "test",
            ((opcode, owner, name, descriptor, isInterface, continuation) -> {
              if (parameters.isCfRuntime() && name.equals("toString")) {
                name = "toGenericString";
              }
              continuation.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }))
        .transform();
  }

  private void inspectSignatures(CodeInspector inspector) {
    ClassSubject outerClass = inspector.clazz(Outer.class);
    assertThat(outerClass, isPresent());
    assertEquals(
        isCompat ? "<O::L" + binaryName(Supplier.class) + "<*>;>Ljava/lang/Object;" : null,
        outerClass.getFinalSignatureAttribute());

    ClassSubject middleClass = inspector.clazz(Middle.class);
    assertThat(middleClass, isPresent());
    assertEquals(
        isCompat ? "<M::L" + binaryName(Predicate.class) + "<TO;>;>Ljava/lang/Object;" : null,
        middleClass.getFinalSignatureAttribute());

    ClassSubject innerClass = inspector.clazz(Inner.class);
    assertThat(innerClass, isPresent());
    MethodSubject testMethod = innerClass.uniqueMethodWithOriginalName("test");
    assertThat(testMethod, isPresent());
    if (isCompat) {
      assertEquals("(TO;TM;)TI;", testMethod.getFinalSignatureAttribute());
    } else {
      assertEquals(
          "(L"
              + binaryName(Supplier.class)
              + "<*>;L"
              + binaryName(Predicate.class)
              + "<Ljava/lang/Object;>;)TI;",
          testMethod.getFinalSignatureAttribute());
    }
  }

  public interface Supplier<T> {}

  public interface Predicate<T> {}

  public static class Outer<O extends Supplier<?>> {

    public class Middle<M extends Predicate<O>> {

      public class Inner<I> {

        public I test(O o, M m) {
          System.out.println("Outer.Middle.Inner::test");
          System.out.println(this.getClass().toString()); // .toGenericString() for JVMs
          return null;
        }
      }

      private Outer<O>.Middle<M>.Inner<Object> createInner() {
        return new Inner<>();
      }
    }

    private Outer<O>.Middle<?> createMiddle() {
      return new Outer<O>.Middle<>();
    }

    @NeverInline
    public static Outer<?>.Middle<?>.Inner<Object> create() {
      return new Outer<>().createMiddle().createInner();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Outer.create().test(null, null);
    }
  }
}
