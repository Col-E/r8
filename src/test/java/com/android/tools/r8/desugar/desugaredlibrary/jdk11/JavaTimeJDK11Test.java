// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class JavaTimeJDK11Test extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final String EXPECTED_RESULT =
      StringUtils.lines("0", "1", "2", "3", "4", "5", "6", "7", "8", "00:00", "0");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        ImmutableList.of(JDK11),
        DEFAULT_SPECIFICATIONS);
  }

  public JavaTimeJDK11Test(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private Collection<byte[]> getProgramClassFileData() throws IOException {
    Set<String> methodsToRewriteToDurationVirtualInvoke =
        ImmutableSet.of(
            "toDaysPart",
            "toHoursPart",
            "toMillisPart",
            "toMinutesPart",
            "toNanosPart",
            "toSeconds",
            "toSecondsPart",
            "dividedBy",
            "truncatedTo");
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
                    if (opcode == Opcodes.INVOKESTATIC
                        && methodsToRewriteToDurationVirtualInvoke.contains(name)) {
                      super.visitMethodInsn(
                          Opcodes.INVOKEVIRTUAL,
                          "java/time/Duration",
                          name,
                          withoutFirstObjectArg(descriptor),
                          isInterface);
                      return;
                    }
                    if (opcode == Opcodes.INVOKESTATIC && name.equals("ofInstant")) {
                      super.visitMethodInsn(
                          opcode, "java/time/LocalTime", name, descriptor, isInterface);
                      return;
                    }
                    if (opcode == Opcodes.INVOKESTATIC && name.equals("toEpochSecond")) {
                      super.visitMethodInsn(
                          Opcodes.INVOKEVIRTUAL,
                          "java/time/LocalTime",
                          name,
                          withoutFirstObjectArg(descriptor),
                          isInterface);
                      return;
                    }
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                  }
                })
            .transform());
  }

  private String withoutFirstObjectArg(String descriptor) {
    int i = descriptor.indexOf(";");
    return "(" + descriptor.substring(i + 1);
  }

  public static class TestClass {

    public static void main(String[] args) {
      // Test all java.time.Duration methods added in Android S (where added in JDK 9).
      System.out.println(toSecondsPart(truncatedTo(Duration.ofSeconds(62), ChronoUnit.MINUTES)));
      System.out.println(toDaysPart(Duration.ofHours(31)));
      System.out.println(toHoursPart(Duration.ofHours(26)));
      System.out.println(toSeconds(Duration.ofSeconds(3)));
      System.out.println(toSecondsPart(Duration.ofSeconds(64)));
      System.out.println(toMinutesPart(Duration.ofSeconds(301)));
      System.out.println(toMillisPart(Duration.ofNanos(6000002)));
      System.out.println(toNanosPart(Duration.ofNanos(1000000007)));
      System.out.println(dividedBy(Duration.ofHours(4), Duration.ofMinutes(30)));

      // Test all java.time.LocalTime methods added in Android S (where added in JDK 9).
      System.out.println(ofInstant(Instant.ofEpochSecond(0), ZoneId.of("UTC")));
      System.out.println(
          toEpochSecond(LocalTime.of(0, 0), LocalDate.ofEpochDay(0), ZoneOffset.UTC));
    }

    // Replaced in the transformer by JDK 11 virtual Duration#toDaysPart().
    private static long toDaysPart(Duration receiver) {
      return -1;
    }

    // Replaced in the transformer by JDK 11 virtual Duration#toHoursPart().
    private static int toHoursPart(Duration receiver) {
      return -1;
    }

    // Replaced in the transformer by JDK 11 virtual Duration#toMillisPart().
    private static int toMillisPart(Duration receiver) {
      return -1;
    }

    // Replaced in the transformer by JDK 11 virtual Duration#toMinutesPart().
    private static int toMinutesPart(Duration receiver) {
      return -1;
    }

    // Replaced in the transformer by JDK 11 virtual Duration#toNanosPart().
    private static int toNanosPart(Duration receiver) {
      return -1;
    }

    // Replaced in the transformer by JDK 11 virtual Duration#toSeconds().
    private static long toSeconds(Duration receiver) {
      return -1;
    }

    // Replaced in the transformer by JDK 11 virtual Duration#toSecondsPart().
    private static int toSecondsPart(Duration receiver) {
      return -1;
    }

    // Replaced in the transformer by JDK 11 virtual Duration#dividedBy(Duration).
    private static long dividedBy(Duration receiver, Duration divisor) {
      return -1;
    }

    // Replaced in the transformer by JDK 11 virtual Duration#truncatedTo(TemporalUnit).
    private static Duration truncatedTo(Duration receiver, TemporalUnit unit) {
      return null;
    }

    // Replaced in the transformer by JDK 11 static LocalTime#ofInstant.
    private static LocalTime ofInstant(Instant instant, ZoneId zone) {
      return null;
    }

    // Replaced in the transformer by JDK 11 virtual LocalTime#toEpochSecond.
    private static long toEpochSecond(LocalTime receiver, LocalDate date, ZoneOffset offset) {
      return -1;
    }
  }
}
