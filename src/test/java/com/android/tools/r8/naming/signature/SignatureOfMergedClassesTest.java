// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.signature;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.signature.merging.I;
import com.android.tools.r8.naming.signature.merging.ImplI;
import com.android.tools.r8.naming.signature.merging.ImplK;
import com.android.tools.r8.naming.signature.merging.ImplL;
import com.android.tools.r8.naming.signature.merging.InterfaceToKeep;
import com.android.tools.r8.naming.signature.merging.J;
import com.android.tools.r8.naming.signature.merging.K;
import com.android.tools.r8.naming.signature.merging.L;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SignatureOfMergedClassesTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SignatureOfMergedClassesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRemovalOfMergedInterfaceOnSameClass()
      throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(
            ImplI.class,
            ImplK.class,
            I.class,
            J.class,
            InterfaceToKeep.class,
            K.class,
            Main.class,
            L.class,
            ImplL.class)
        .addKeepMainRule(Main.class)
        .addKeepClassRules(InterfaceToKeep.class, ImplI.class, K.class)
        .addKeepAttributes("Signature, InnerClasses, EnclosingMethod, *Annotation*")
        .setMinApi(parameters)
        .addDontObfuscate()
        .addOptionsModification(
            internalOptions -> {
              internalOptions.enableUnusedInterfaceRemoval = false;
            })
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "ImplI.foo",
            "ImplI: com.android.tools.r8.naming.signature.merging.InterfaceToKeep<java.lang.Void>",
            "K: com.android.tools.r8.naming.signature.merging.InterfaceToKeep<java.lang.Void>",
            "ImplK.foo",
            "ImplK.bar",
            "ImplK: interface com.android.tools.r8.naming.signature.merging.K",
            "ImplL.print")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(I.class), not(isPresent()));
              assertThat(codeInspector.clazz(J.class), not(isPresent()));
            });
  }

  @Test
  public void testKeepingOneSelfOnInterface()
      throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClasses(Foo.class, InterfaceToKeep.class)
        .addKeepMainRule(Foo.class)
        .addKeepClassRules(InterfaceToKeep.class)
        .addKeepAttributes("Signature, InnerClasses, EnclosingMethod, *Annotation*")
        .setMinApi(parameters)
        .addDontObfuscate()
        .addOptionsModification(
            internalOptions -> {
              internalOptions.enableUnusedInterfaceRemoval = false;
            })
        .run(parameters.getRuntime(), Foo.class)
        .assertSuccessWithOutputLines(
            "com.android.tools.r8.naming.signature.merging.InterfaceToKeep"
                + "<com.android.tools.r8.naming.signature.SignatureOfMergedClassesTest$Foo>");
  }

  public static class Foo implements InterfaceToKeep<Foo> {

    public static void main(String[] args) {
      for (Type genericInterface : Foo.class.getGenericInterfaces()) {
        System.out.println(genericInterface);
      }
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new ImplI().foo();
      for (Type genericInterface : ImplI.class.getGenericInterfaces()) {
        System.out.println("ImplI: " + genericInterface);
      }
      for (Type genericInterface : K.class.getGenericInterfaces()) {
        System.out.println("K: " + genericInterface);
      }
      K k = new ImplK();
      k.foo();
      k.bar();
      for (Type genericInterface : ImplK.class.getGenericInterfaces()) {
        System.out.println("ImplK: " + genericInterface);
      }
      L<ImplL> l = new ImplL();
      l.print((ImplL) l);
      for (Type genericInterface : ImplL.class.getGenericInterfaces()) {
        System.out.println("ImplL: " + genericInterface);
      }
    }
  }
}
