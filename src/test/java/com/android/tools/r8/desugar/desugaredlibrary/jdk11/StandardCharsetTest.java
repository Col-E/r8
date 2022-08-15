// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class StandardCharsetTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final String EXPECTED_RESULT =
      StringUtils.lines("%E3%81%8B", "%82%A0%82%A9%97%43%24%E3%81%8B", "true", "true", "written");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        ImmutableList.of(JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public StandardCharsetTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class, temp.newFile().toString())
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private Collection<byte[]> getProgramClassFileData() throws IOException {
    return ImmutableList.of(
        transformer(TestClass.class)
            .addMethodTransformer(
                new MethodTransformer() {
                  @Override
                  public void visitMethodInsn(
                      int opcode,
                      String owner,
                      String name,
                      String descriptor,
                      boolean isInterface) {
                    if (opcode == Opcodes.INVOKESTATIC && name.equals("encode")) {
                      super.visitMethodInsn(
                          opcode, "java/net/URLEncoder", name, descriptor, isInterface);
                      return;
                    }
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                  }
                })
            .transform());
  }

  public static class TestClass {

    public static void main(String[] args) {
      System.out.println(encode("か", StandardCharsets.UTF_8));
      System.out.println(
          encode("あ", "Shift_JIS")
              + encode("か", "Shift_JIS")
              + encode("佑", "Shift_JIS")
              + encode("$", "Shift_JIS")
              + encode("か", StandardCharsets.UTF_8));
      System.out.println(Character.isBmpCodePoint('か'));

      System.out.println(Charset.defaultCharset() == StandardCharsets.UTF_8);

      // The following Files methods internally uses UTF_8 references.
      String path = args[0];
      try {
        Files.write(Paths.get(path), Collections.singleton("written"));
        System.out.println(Files.readAllLines(Paths.get(path)).get(0));
      } catch (IOException e) {
        System.out.println("IOException");
      }
    }

    // Replaced in the transformer by JDK 11 URLEncoder#encode.
    public static String encode(String s, Charset set) {
      return null;
    }

    // Replaced in the transformer by JDK 11 URLEncoder#encode.
    public static String encode(String s, String set) {
      return null;
    }
  }
}
