// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.attributes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MemberSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@SuppressWarnings("ALL")
@RunWith(Parameterized.class)
public class KeepSignatureTest extends TestBase {

  private final TestParameters parameters;
  private final String[] EXPECTED = new String[] {"Hello", "World", "Hello", "World"};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public KeepSignatureTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(transformer(KeptClass.class).removeInnerClasses().transform())
        .addProgramClassFileData(transformer(NotKeptClass.class).removeInnerClasses().transform())
        .run(parameters.getRuntime(), KeptClass.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testKeptClassFieldAndMethodFull() throws Exception {
    runTest(testForR8(parameters.getBackend()), false);
  }

  @Test
  public void testKeptClassFieldAndMethodCompat() throws Exception {
    runTest(testForR8Compat(parameters.getBackend()), true);
  }

  private void runTest(R8TestBuilder<?> testBuilder, boolean keptForNotKept) throws Exception {
    testBuilder
        .addProgramClassFileData(transformer(KeptClass.class).removeInnerClasses().transform())
        .addProgramClassFileData(transformer(NotKeptClass.class).removeInnerClasses().transform())
        .addKeepMainRule(KeptClass.class)
        .addKeepRules(
            StringUtils.lines(
                "-keep class " + KeptClass.class.getTypeName() + " {",
                "  *** keptMethod(...);",
                "  *** keptField;",
                "}"))
        .addKeepAttributes(
            ProguardKeepAttributes.SIGNATURE,
            ProguardKeepAttributes.INNER_CLASSES,
            ProguardKeepAttributes.ENCLOSING_METHOD)
        .setMinApi(parameters.getApiLevel())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), KeptClass.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(inspector -> inspect(inspector, keptForNotKept));
  }

  private void inspect(CodeInspector inspector, boolean keepForNotKept) {
    ClassSubject keptClass = inspector.clazz(KeptClass.class);
    assertThat(keptClass, isPresent());
    assertEquals(
        "<T:Ljava/lang/Object;>Ljava/lang/Object;", keptClass.getFinalSignatureAttribute());
    FieldSubject keptField = keptClass.uniqueFieldWithName("keptField");
    assertThat(keptField, isPresent());
    assertEquals("TT;", keptField.getFinalSignatureAttribute());
    MethodSubject keptMethod = keptClass.uniqueMethodWithName("keptMethod");
    assertThat(keptMethod, isPresent());
    assertEquals("<R:Ljava/lang/Object;>(TT;)TR;", keptMethod.getFinalSignatureAttribute());

    // For all remaining classes and members, we should only keep signatures if in compat mode.
    checkMemberSignature(keptClass.uniqueFieldWithName("notKeptField"), keepForNotKept, "TT;");
    checkMemberSignature(
        keptClass.uniqueMethodWithName("notKeptMethod"),
        keepForNotKept,
        "<R:Ljava/lang/Object;>(TT;)TR;");

    ClassSubject notKeptClass = inspector.clazz(NotKeptClass.class);
    assertThat(notKeptClass, isPresent());
    if (keepForNotKept) {
      assertEquals(
          "<P:Ljava/lang/Object;>Ljava/lang/Object;", notKeptClass.getFinalSignatureAttribute());
    } else {
      assertNull(notKeptClass.getFinalSignatureAttribute());
    }
    checkMemberSignature(
        notKeptClass.uniqueFieldWithName("notKeptField"), keepForNotKept, "Ljava/util/List<TP;>;");
    checkMemberSignature(
        notKeptClass.uniqueMethodWithName("notKeptMethod"),
        keepForNotKept,
        "(TP;TP;)Ljava/util/List<TP;>;");
  }

  private void checkMemberSignature(
      MemberSubject member, boolean keepForNotKept, String signature) {
    assertThat(member, isPresent());
    if (keepForNotKept) {
      assertEquals(signature, member.getFinalSignatureAttribute());
    } else {
      assertNull(member.getFinalSignatureAttribute());
    }
  }

  @NeverClassInline
  public static class NotKeptClass<P> {

    public List<P> notKeptField;

    @NeverInline
    public List<P> notKeptMethod(P p1, P p2) {
      if (notKeptField != null) {
        return notKeptField;
      }
      ArrayList<P> ps = new ArrayList<>();
      ps.add(p1);
      ps.add(p2);
      notKeptField = ps;
      return ps;
    }
  }

  public static class KeptClass<T> {

    private T keptField;
    private T notKeptField;

    public <R> R keptMethod(T t) {
      if (keptField == null) {
        keptField = t;
      }
      return (R) keptField;
    }

    @NeverInline
    public <R> R notKeptMethod(T t) {
      if (notKeptField == null) {
        notKeptField = t;
      }
      return (R) notKeptField;
    }

    public static void main(String[] args) {
      KeptClass<String> keptClass = new KeptClass<>();
      System.out.println(keptClass.<String>keptMethod("Hello"));
      System.out.println(keptClass.<String>notKeptMethod("World"));
      NotKeptClass<String> stringNotKeptClass = new NotKeptClass<>();
      List<String> strings = stringNotKeptClass.notKeptMethod("Hello", "World");
      System.out.println(strings.get(0));
      System.out.println(strings.get(1));
    }
  }
}
