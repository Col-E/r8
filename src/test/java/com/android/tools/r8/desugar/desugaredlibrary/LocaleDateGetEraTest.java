// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
//  for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.LocalDate;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.Era;
import java.time.chrono.IsoEra;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class LocaleDateGetEraTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT = StringUtils.lines("CE");

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final Class<?> eraClass;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withAllRuntimes()
            .withAllApiLevelsAlsoForCf()
            .withApiLevel(AndroidApiLevel.N)
            .build(),
        getJdk8Jdk11(),
        SPECIFICATIONS_WITH_CF2CF,
        ImmutableList.of(IsoEra.class, Era.class));
  }

  public LocaleDateGetEraTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification,
      Class<?> eraClass) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.eraClass = eraClass;
  }

  @Test
  public void testLocaleDate() throws Throwable {
    SingleTestRunResult<?> run =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addProgramClassFileData(getProgramClassFileData())
            .addKeepMainRule(Executor.class)
            .run(parameters.getRuntime(), Executor.class);
    if (parameters.getRuntime().isCf()
        && parameters.getRuntime().asCf().isOlderThan(CfVm.JDK9)
        && eraClass == IsoEra.class
        // We desugar up to 30 at this point...
        && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.S)) {
      // The method with the covariant return type is present only from JDK9.
      run.assertFailureWithErrorThatMatches(
          containsString(
              "java.lang.NoSuchMethodError:"
                  + " java.time.LocalDate.getEra()Ljava/time/chrono/IsoEra;"));
    } else if (parameters.getRuntime().isDex()
        && eraClass == IsoEra.class
        && parameters.getApiLevel().betweenBothIncluded(AndroidApiLevel.O, AndroidApiLevel.S)
        && libraryDesugaringSpecification == JDK8) {
      // No support for this desugaring in JDK8 desugared library.
      run.assertFailureWithErrorThatMatches(containsString("java.lang.NoSuchMethodError"));
    } else {
      run.assertSuccessWithOutput(EXPECTED_RESULT);
    }
  }

  private Collection<byte[]> getProgramClassFileData() throws IOException {
    return ImmutableList.of(
        transformer(Executor.class)
            .addMethodTransformer(
                new MethodTransformer() {
                  @Override
                  public void visitMethodInsn(
                      int opcode,
                      String owner,
                      String name,
                      String descriptor,
                      boolean isInterface) {
                    if (opcode == Opcodes.INVOKEINTERFACE && name.equals("getEra")) {
                      super.visitMethodInsn(
                          Opcodes.INVOKEVIRTUAL,
                          "java/time/LocalDate",
                          name,
                          "()" + DescriptorUtils.javaTypeToDescriptor(eraClass.getTypeName()),
                          false);
                      return;
                    }
                    if (opcode == Opcodes.CHECKCAST) {
                      return;
                    }
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                  }
                })
            .transform());
  }

  static class Executor {

    public static void main(String[] args) {
      System.out.println(((ChronoLocalDate) LocalDate.ofEpochDay(123456789L)).getEra());
    }
  }
}
