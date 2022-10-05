// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FieldNamingObfuscationDictionaryTest extends TestBase {

  public static class A {

    public String f1;

    public A(String a) {
      this.f1 = a;
    }
  }

  @NeverClassInline
  public static class B extends A {

    public int f0;
    public String f2;

    public B(int f0, String a, String b) {
      super(a);
      this.f0 = f0;
      this.f2 = b;
    }

    @NeverInline
    public void print() {
      System.out.println(f0 + f1 + " " + f2);
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class C extends A {

    public int f0;
    public String f3;

    public C(int f0, String a, String c) {
      super(a);
      this.f0 = f0;
      this.f3 = c;
    }

    @NeverInline
    public void print() {
      System.out.println(f0 + f1 + " " + f3);
    }
  }

  public static class Runner {

    public static void main(String[] args) {
      new B(args.length, args[0], args[1]).print();
      new C(args.length, args[0], args[1]).print();
    }
  }

  private TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public FieldNamingObfuscationDictionaryTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testInheritedNamingState()
      throws IOException, CompilationFailedException, ExecutionException {
    Path dictionary = temp.getRoot().toPath().resolve("dictionary.txt");
    FileUtils.writeTextFile(dictionary, "a", "b", "c");

    testForR8(parameters.getBackend())
        .addInnerClasses(FieldNamingObfuscationDictionaryTest.class)
        .addKeepRules("-overloadaggressively", "-obfuscationdictionary " + dictionary.toString())
        .addKeepMainRule(Runner.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Runner.class, "HELLO", "WORLD")
        .assertSuccessWithOutputLines("2HELLO WORLD", "2HELLO WORLD")
        .inspect(
            inspector -> {
              assertEquals(
                  "a", inspector.clazz(A.class).uniqueFieldWithOriginalName("f1").getFinalName());
              assertEquals(
                  "a", inspector.clazz(B.class).uniqueFieldWithOriginalName("f0").getFinalName());
              assertEquals(
                  "b", inspector.clazz(B.class).uniqueFieldWithOriginalName("f2").getFinalName());
              assertEquals(
                  "a", inspector.clazz(C.class).uniqueFieldWithOriginalName("f0").getFinalName());
              assertEquals(
                  "b", inspector.clazz(C.class).uniqueFieldWithOriginalName("f3").getFinalName());
            });
  }
}
