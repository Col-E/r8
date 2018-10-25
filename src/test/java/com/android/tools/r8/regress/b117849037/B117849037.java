// Copyright (c) 2018 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b117849037;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

interface MyConsumer<T> {
  void accept(T element);
}

class TestClass {
  public static void doStuff(MyConsumer<String> consumer) {
    consumer.accept("a");
    consumer.accept("b");
    consumer.accept("c");
  }

  public static void add1(String s) {
    System.out.println(s + 1);
  }

  public static void add2(String s) {
    System.out.println(s + 2);
  }

  public static void add3(String s) {
    System.out.println(s + 3);
  }

  public static void add4(String s) {
    System.out.println(s + 4);
  }

  public static void add5(String s) {
    System.out.println(s + 5);
  }

  public static void add6(String s) {
    System.out.println(s + 6);
  }

  public static void add7(String s) {
    System.out.println(s + 7);
  }

  public static void add8(String s) {
    System.out.println(s + 8);
  }

  public static void add9(String s) {
    System.out.println(s + 9);
  }

  public static void add10(String s) {
    System.out.println(s + 10);
  }

  public static void main() {
    doStuff(Object::hashCode);
    doStuff(Object::toString);
    doStuff(String::length);
    doStuff(String::trim);
    doStuff(TestClass::add1);
    doStuff(TestClass::add2);
    doStuff(TestClass::add3);
    doStuff(TestClass::add4);
    doStuff(TestClass::add5);
    doStuff(TestClass::add6);
    doStuff(TestClass::add7);
    doStuff(TestClass::add8);
    doStuff(TestClass::add9);
    doStuff(TestClass::add10);
  }
}

public class B117849037 extends TestBase {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  public void compile(Path output) throws CompilationFailedException, IOException {
    testForD8()
        .addProgramClasses(TestClass.class, MyConsumer.class)
        .setIntermediate(true)
        .compile()
        .writeToZip(output);
  }

  @Test
  public void testConsistentSynthesizedMapOutput() throws IOException, CompilationFailedException {
    Path file1 = folder.getRoot().toPath().resolve("classes1.jar");
    Path file2 = folder.getRoot().toPath().resolve("classes2.jar");
    compile(file1);
    compile(file2);
    assertTrue(Arrays.equals(Files.readAllBytes(file1), Files.readAllBytes(file2)));
  }
}
