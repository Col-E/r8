// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** This test reproduces b/132593471 on d8-1.4. */
@RunWith(Parameterized.class)
public class ApplyMappingSignatureTest extends TestBase {

  public interface A {}

  public static class SignatureTest {
    Set<A> field;

    public static void main(String[] args) {
      System.out.print("HELLO");
    }
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private TestParameters parameters;

  public ApplyMappingSignatureTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testApplyMappingWithSignatureRenaming()
      throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, SignatureTest.class)
        .addKeepClassRulesWithAllowObfuscation(A.class)
        .addKeepClassAndMembersRules(SignatureTest.class)
        .addKeepAttributes(
            ProguardKeepAttributes.SIGNATURE,
            ProguardKeepAttributes.INNER_CLASSES,
            ProguardKeepAttributes.ENCLOSING_METHOD)
        .addApplyMapping(A.class.getTypeName() + " -> foo:")
        .setMinApi(parameters)
        .run(parameters.getRuntime(), SignatureTest.class)
        .assertSuccessWithOutput("HELLO")
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(SignatureTest.class);
              assertThat(clazz, isPresent());
              FieldSubject field = clazz.uniqueFieldWithOriginalName("field");
              assertThat(field, isPresent());
              assertThat(field.getFinalSignatureAttribute(), containsString("foo"));
            });
  }
}
