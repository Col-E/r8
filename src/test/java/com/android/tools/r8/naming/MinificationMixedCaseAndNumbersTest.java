// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.SymbolGenerationUtils;
import com.android.tools.r8.utils.SymbolGenerationUtils.MixedCasing;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MinificationMixedCaseAndNumbersTest extends TestBase {

  private static final int NUMBER_OF_MINIFIED_CLASSES = 60;

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MinificationMixedCaseAndNumbersTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNaming() throws ExecutionException, CompilationFailedException, IOException {
    Set<String> allowedNames = new HashSet<>();
    allowedNames.add(Main.class.getTypeName());
    for (int i = 1; i < NUMBER_OF_MINIFIED_CLASSES; i++) {
      String newString =
          SymbolGenerationUtils.numberToIdentifier(i, MixedCasing.DONT_USE_MIXED_CASE);
      assertFalse(Character.isDigit(newString.charAt(0)));
      allowedNames.add("com.android.tools.r8.naming." + newString);
    }
    testForR8(parameters.getBackend())
        .addInnerClasses(MinificationMixedCaseAndNumbersTest.class)
        .addKeepMainRule(Main.class)
        .noTreeShaking()
        .addKeepRules(
            "-dontusemixedcaseclassnames", "-keeppackagenames com.android.tools.r8.naming")
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .inspect(
            inspector -> {
              List<FoundClassSubject> foundClassSubjects = new ArrayList<>(inspector.allClasses());
              foundClassSubjects.forEach(
                  foundClazz -> {
                    assertTrue(allowedNames.contains(foundClazz.getFinalName()));
                    allowedNames.remove(foundClazz.getFinalName());
                  });
            });
    // The first identifier to use a number is 27 for MixedCasing.DONT_USE_MIXED_CASE.
    assertTrue(allowedNames.isEmpty());
    assertEquals(
        "a0", SymbolGenerationUtils.numberToIdentifier(27, MixedCasing.DONT_USE_MIXED_CASE));
    assertEquals(
        "zz", SymbolGenerationUtils.numberToIdentifier(962, MixedCasing.DONT_USE_MIXED_CASE));
    assertEquals(
        "a00", SymbolGenerationUtils.numberToIdentifier(963, MixedCasing.DONT_USE_MIXED_CASE));
  }

  public static class A {}

  public static class B {}

  public static class C {}

  public static class D {}

  public static class E {}

  public static class F {}

  public static class G {}

  public static class H {}

  public static class I {}

  public static class J {}

  public static class K {}

  public static class L {}

  public static class M {}

  public static class N {}

  public static class O {}

  public static class P {}

  public static class Q {}

  public static class R {}

  public static class S {}

  public static class T {}

  public static class U {}

  public static class V {}

  public static class W {}

  public static class X {}

  public static class Y {}

  public static class Z {}

  public static class AA {}

  public static class AB {}

  public static class AC {}

  public static class AD {}

  public static class AE {}

  public static class AF {}

  public static class AG {}

  public static class AH {}

  public static class AI {}

  public static class AJ {}

  public static class AK {}

  public static class AL {}

  public static class AM {}

  public static class AN {}

  public static class AO {}

  public static class AP {}

  public static class AQ {}

  public static class AR {}

  public static class AS {}

  public static class AT {}

  public static class AU {}

  public static class AV {}

  public static class AW {}

  public static class AX {}

  public static class AY {}

  public static class AZ {}

  public static class AAA {}

  public static class AAB {}

  public static class AAC {}

  public static class AAD {}

  public static class AAE {}

  public static class AAF {}

  public static class AAG {}

  public static class Main {

    public static void main(String[] args) {
      System.out.println("HELLO WORLD");
    }
  }
}
