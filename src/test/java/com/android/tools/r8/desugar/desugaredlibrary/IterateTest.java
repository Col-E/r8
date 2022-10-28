// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.DoublePredicate;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.LongPredicate;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class IterateTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("1", "2", "1.0", "3", "1", "2", "1.0", "3");

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withCfRuntime(CfVm.JDK11)
            .withDexRuntimes()
            .withAllApiLevelsAlsoForCf()
            .build(),
        ImmutableList.of(JDK11, JDK11_PATH),
        SPECIFICATIONS_WITH_CF2CF);
  }

  public IterateTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testIterable() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClassFileData(getProgramClassFileData())
        .addProgramFiles(getOtherProgramClasses())
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private Collection<Path> getOtherProgramClasses() throws IOException {
    Collection<Path> files = ToolHelper.getClassFilesForInnerClasses(getClass());
    files.removeIf(p -> p.toString().endsWith("Main.class"));
    return files;
  }

  private Collection<byte[]> getProgramClassFileData() throws IOException {
    ImmutableMap<String, String> mapping =
        ImmutableMap.of(
            "com/android/tools/r8/desugar/desugaredlibrary/IterateTest$IntStreamStub",
            "java/util/stream/IntStream",
            "com/android/tools/r8/desugar/desugaredlibrary/IterateTest$StreamStub",
            "java/util/stream/Stream",
            "com/android/tools/r8/desugar/desugaredlibrary/IterateTest$LongStreamStub",
            "java/util/stream/LongStream",
            "com/android/tools/r8/desugar/desugaredlibrary/IterateTest$DoubleStreamStub",
            "java/util/stream/DoubleStream");
    return ImmutableList.of(
        transformer(Main.class)
            .addMethodTransformer(
                new MethodTransformer() {
                  @Override
                  public void visitMethodInsn(
                      int opcode,
                      String owner,
                      String name,
                      String descriptor,
                      boolean isInterface) {
                    if (opcode == Opcodes.INVOKESTATIC && mapping.containsKey(owner)) {
                      super.visitMethodInsn(
                          opcode, mapping.get(owner), name, descriptor, isInterface);
                      return;
                    }
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                  }
                })
            .transform());
  }

  static class Main {

    public static void main(String[] args) {
      noPredicate();
      predicate();
    }

    private static void noPredicate() {
      IntStream iterateInt = IntStream.iterate(1, x -> x + 3);
      System.out.println(iterateInt.findFirst().getAsInt());
      LongStream iterateLong = LongStream.iterate(2L, x -> x + 3);
      System.out.println(iterateLong.findFirst().getAsLong());
      DoubleStream iterateDouble = DoubleStream.iterate(1.0, x -> x + 3);
      System.out.println(iterateDouble.findFirst().getAsDouble());
      Stream<Integer> iterateObject = Stream.iterate(3, x -> x + 3);
      System.out.println(iterateObject.findFirst().get());
    }

    private static void predicate() {
      IntStream iterateInt = IntStreamStub.iterate(1, x -> x != 0, x -> x + 3);
      System.out.println(iterateInt.findFirst().getAsInt());
      LongStream iterateLong = LongStreamStub.iterate(2L, x -> x != 0, x -> x + 3);
      System.out.println(iterateLong.findFirst().getAsLong());
      DoubleStream iterateDouble = DoubleStreamStub.iterate(1.0, x -> x != 0, x -> x + 3);
      System.out.println(iterateDouble.findFirst().getAsDouble());
      Stream<Integer> iterateObject = StreamStub.iterate(3, x -> x != 0, x -> x + 3);
      System.out.println(iterateObject.findFirst().get());
    }
  }

  private interface IntStreamStub {

    static IntStream iterate(int i, IntPredicate predicate, IntUnaryOperator operator) {
      return null;
    }
  }

  private interface LongStreamStub {

    static LongStream iterate(long i, LongPredicate predicate, LongUnaryOperator operator) {
      return null;
    }
  }

  private interface DoubleStreamStub {

    static DoubleStream iterate(double i, DoublePredicate predicate, DoubleUnaryOperator operator) {
      return null;
    }
  }

  private interface StreamStub<T> {

    static <T> Stream<T> iterate(T o, Predicate<T> predicate, UnaryOperator<T> operator) {
      return null;
    }
  }
}
