// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.transformers.ClassFileTransformer.FieldPredicate;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnknownClassInSignatureTest extends TestBase {

  private final TestParameters parameters;
  private final String NEW_FIELD_SIGNATURE = "LUnknownClass4<LUnknownClass4;>;";
  private final String NEW_METHOD_SIGNATURE = "()LUnkownClass5<LunknownPackage/UnknownClass6;>;";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public UnknownClassInSignatureTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(Main.class)
                .removeInnerClasses()
                .setGenericSignature("<T:LUnknownClass1;>LUnknownClass2<LUnknownClass3;>;")
                .setGenericSignature(FieldPredicate.onName("field"), NEW_FIELD_SIGNATURE)
                .setGenericSignature(MethodPredicate.onName("main"), NEW_METHOD_SIGNATURE)
                .transform())
        .addKeepAllClassesRule()
        .addKeepAttributes(
            ProguardKeepAttributes.SIGNATURE,
            ProguardKeepAttributes.ENCLOSING_METHOD,
            ProguardKeepAttributes.INNER_CLASSES)
        .setMinApi(parameters)
        .allowDiagnosticInfoMessages()
        .compile()
        .apply(TestBase::verifyExpectedInfoFromGenericSignatureSuperTypeValidation)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!")
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(Main.class);
              assertThat(clazz, isPresent());
              assertNull(clazz.getFinalSignatureAttribute());
              FieldSubject field = clazz.uniqueFieldWithFinalName("field");
              assertThat(field, isPresent());
              assertEquals(NEW_FIELD_SIGNATURE, field.getFinalSignatureAttribute());
              MethodSubject method = clazz.uniqueMethodWithFinalName("main");
              assertThat(method, isPresent());
              assertEquals(NEW_METHOD_SIGNATURE, method.getFinalSignatureAttribute());
            });
  }

  public static class Main {

    private List<Main> field;

    public static void main(String[] args) {
      System.out.println("Hello World!");
    }
  }
}
