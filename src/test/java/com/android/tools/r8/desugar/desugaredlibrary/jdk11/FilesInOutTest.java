// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.function.Supplier;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FilesInOutTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "PRESENT FILE",
          "buffRead:cHello!",
          "inStream[null]:class java.lang.NullPointerException :: npe",
          "outStream[null]:class java.lang.NullPointerException :: npe",
          "buffWrite[null]:class java.lang.NullPointerException :: npe",
          "newByte[null]:class java.lang.NullPointerException :: npe",
          "inStream[READ]:cHello",
          "outStream[READ]:class java.lang.IllegalArgumentException :: READ not allowed",
          "buffWrite[READ]:class java.lang.IllegalArgumentException :: READ not allowed",
          "newByte[READ]:c6",
          "inStream[WRITE]:class java.lang.UnsupportedOperationException :: 'WRITE' not allowed",
          "outStream[WRITE]:cwGame over!",
          "buffWrite[WRITE]:cwHello!",
          "newByte[WRITE]:c6",
          "inStream[APPEND]:class java.lang.UnsupportedOperationException :: 'APPEND' not allowed",
          "outStream[APPEND]:cwHello!Game over!",
          "buffWrite[APPEND]:cwHello!",
          "newByte[APPEND]:c6",
          "inStream[TRUNCATE_EXISTING]:cHello",
          "outStream[TRUNCATE_EXISTING]:cwGame over!",
          "buffWrite[TRUNCATE_EXISTING]:cwclass java.lang.IndexOutOfBoundsException :: index 0,"
              + " size 0",
          "newByte[TRUNCATE_EXISTING]:c6",
          "inStream[CREATE]:cHello",
          "outStream[CREATE]:cwGame over!",
          "buffWrite[CREATE]:cwHello!",
          "newByte[CREATE]:c6",
          "inStream[CREATE_NEW]:cHello",
          "outStream[CREATE_NEW]:class java.nio.file.FileAlreadyExistsException :: example",
          "buffWrite[CREATE_NEW]:class java.nio.file.FileAlreadyExistsException :: example",
          "newByte[CREATE_NEW]:c6",
          "inStream[DELETE_ON_CLOSE]:cHello",
          "outStream[DELETE_ON_CLOSE]:%s",
          "buffWrite[DELETE_ON_CLOSE]:%s",
          "newByte[DELETE_ON_CLOSE]:c6",
          "inStream[SPARSE]:cHello",
          "outStream[SPARSE]:cwGame over!",
          "buffWrite[SPARSE]:cwHello!",
          "newByte[SPARSE]:c6",
          "inStream[SYNC]:cHello",
          "outStream[SYNC]:cwGame over!",
          "buffWrite[SYNC]:cwHello!",
          "newByte[SYNC]:c6",
          "inStream[DSYNC]:cHello",
          "outStream[DSYNC]:cwGame over!",
          "buffWrite[DSYNC]:cwHello!",
          "newByte[DSYNC]:c6",
          "ABSENT FILE",
          "buffRead:class java.nio.file.NoSuchFileException :: notExisting",
          "inStream[null]:class java.lang.NullPointerException :: npe",
          "outStream[null]:class java.lang.NullPointerException :: npe",
          "buffWrite[null]:class java.lang.NullPointerException :: npe",
          "newByte[null]:class java.lang.NullPointerException :: npe",
          "inStream[READ]:class java.nio.file.NoSuchFileException :: notExisting",
          "outStream[READ]:class java.lang.IllegalArgumentException :: READ not allowed",
          "buffWrite[READ]:class java.lang.IllegalArgumentException :: READ not allowed",
          "newByte[READ]:class java.nio.file.NoSuchFileException :: notExisting",
          "inStream[WRITE]:class java.lang.UnsupportedOperationException :: 'WRITE' not allowed",
          "outStream[WRITE]:class java.nio.file.NoSuchFileException :: notExisting",
          "buffWrite[WRITE]:class java.nio.file.NoSuchFileException :: notExisting",
          "newByte[WRITE]:class java.nio.file.NoSuchFileException :: notExisting",
          "inStream[APPEND]:class java.lang.UnsupportedOperationException :: 'APPEND' not allowed",
          "outStream[APPEND]:class java.nio.file.NoSuchFileException :: notExisting",
          "buffWrite[APPEND]:class java.nio.file.NoSuchFileException :: notExisting",
          "newByte[APPEND]:class java.nio.file.NoSuchFileException :: notExisting",
          "inStream[TRUNCATE_EXISTING]:class java.nio.file.NoSuchFileException :: notExisting",
          "outStream[TRUNCATE_EXISTING]:class java.nio.file.NoSuchFileException :: notExisting",
          "buffWrite[TRUNCATE_EXISTING]:class java.nio.file.NoSuchFileException :: notExisting",
          "newByte[TRUNCATE_EXISTING]:class java.nio.file.NoSuchFileException :: notExisting",
          "inStream[CREATE]:%s",
          "outStream[CREATE]:cwGame over!",
          "buffWrite[CREATE]:cwclass java.lang.IndexOutOfBoundsException :: index 0, size 0",
          "newByte[CREATE]:%s",
          "inStream[CREATE_NEW]:%s",
          "outStream[CREATE_NEW]:cwGame over!",
          "buffWrite[CREATE_NEW]:cwclass java.lang.IndexOutOfBoundsException :: index 0, size 0",
          "newByte[CREATE_NEW]:%s",
          "inStream[DELETE_ON_CLOSE]:class java.nio.file.NoSuchFileException :: notExisting",
          "outStream[DELETE_ON_CLOSE]:class java.nio.file.NoSuchFileException :: notExisting",
          "buffWrite[DELETE_ON_CLOSE]:class java.nio.file.NoSuchFileException :: notExisting",
          "newByte[DELETE_ON_CLOSE]:class java.nio.file.NoSuchFileException :: notExisting",
          "inStream[SPARSE]:class java.nio.file.NoSuchFileException :: notExisting",
          "outStream[SPARSE]:class java.nio.file.NoSuchFileException :: notExisting",
          "buffWrite[SPARSE]:class java.nio.file.NoSuchFileException :: notExisting",
          "newByte[SPARSE]:class java.nio.file.NoSuchFileException :: notExisting",
          "inStream[SYNC]:class java.nio.file.NoSuchFileException :: notExisting",
          "outStream[SYNC]:class java.nio.file.NoSuchFileException :: notExisting",
          "buffWrite[SYNC]:class java.nio.file.NoSuchFileException :: notExisting",
          "newByte[SYNC]:class java.nio.file.NoSuchFileException :: notExisting",
          "inStream[DSYNC]:class java.nio.file.NoSuchFileException :: notExisting",
          "outStream[DSYNC]:class java.nio.file.NoSuchFileException :: notExisting",
          "buffWrite[DSYNC]:class java.nio.file.NoSuchFileException :: notExisting",
          "newByte[DSYNC]:class java.nio.file.NoSuchFileException :: notExisting");

  private static final String[] EXPECTED_RESULT_NO_DESUGARING =
      new String[] {
        "cwclass java.nio.file.NoSuchFileException :: example",
        "cwclass java.nio.file.NoSuchFileException :: example",
        "class java.nio.file.NoSuchFileException :: notExisting",
        "class java.nio.file.NoSuchFileException :: notExisting",
        "class java.nio.file.NoSuchFileException :: notExisting",
        "class java.nio.file.NoSuchFileException :: notExisting"
      };

  private static final String[] EXPECTED_RESULT_DESUGARING =
      new String[] {
        "cwGame over!",
        "cwHello!",
        // In some cases the desugaring version raises FileNotFoundException instead of
        // NoSuchFileException on the same file.
        "class java.io.FileNotFoundException :: notExisting",
        "class java.io.FileNotFoundException :: notExisting",
        "class java.io.FileNotFoundException :: notExisting",
        "class java.io.FileNotFoundException :: notExisting"
      };

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        // Skip Android 4.4.4 due to missing libjavacrypto.
        getTestParameters()
            .withCfRuntime(CfVm.JDK11)
            .withDexRuntime(Version.V4_0_4)
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build(),
        ImmutableList.of(JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public FilesInOutTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  private String getExpectedResult() {
    if (parameters.isCfRuntime()
        || libraryDesugaringSpecification.usesPlatformFileSystem(parameters)) {
      return String.format(EXPECTED_RESULT, EXPECTED_RESULT_NO_DESUGARING);
    }
    return String.format(EXPECTED_RESULT, EXPECTED_RESULT_DESUGARING);
  }

  @Test
  public void test() throws Throwable {
    if (parameters.isCfRuntime()) {
      // Reference runtime, we use Jdk 11 since this is Jdk 11 desugared library, not that Jdk 8
      // behaves differently on this test.
      Assume.assumeTrue(parameters.isCfRuntime(CfVm.JDK11) && !ToolHelper.isWindows());
      testForJvm(parameters)
          .addInnerClasses(getClass())
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(getExpectedResult());
      return;
    }
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .compile()
        .withArt6Plus64BitsLib()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(getExpectedResult());
  }

  public static class TestClass {

    public static void main(String[] args) throws Throwable {
      System.out.println("PRESENT FILE");
      Supplier<Path> presentFileSupplier =
          () -> {
            try {
              Path path = Files.createTempFile("example_", ".txt");
              Files.write(path, "Hello!".getBytes(StandardCharsets.UTF_8));
              return path;
            } catch (IOException t) {
              // For the test it should never happen so this is enough.
              throw new RuntimeException(t);
            }
          };
      testProperties(presentFileSupplier);
      System.out.println("ABSENT FILE");
      testProperties(() -> Paths.get("notExisting_XXX.txt"));
    }

    private static void printError(Throwable t) {
      String[] split =
          t.getMessage() == null ? new String[] {"no-message_XXX"} : t.getMessage().split("/");
      split = split[split.length - 1].split("_");
      if (t instanceof IndexOutOfBoundsException) {
        // IndexOutOfBoundsException are printed slightly differently across platform and it's not
        // really relevant for the test.
        split = new String[] {"index 0, size 0"};
      }
      if (t instanceof NullPointerException) {
        // NullPointerException are printed slightly differently across platform and it's not
        // really relevant for the test.
        split = new String[] {"npe"};
      }
      System.out.println(t.getClass() + " :: " + split[0]);
    }

    private static void testProperties(Supplier<Path> pathSupplier) throws IOException {
      Path path = pathSupplier.get();
      System.out.print("buffRead:");
      try (BufferedReader bufferedReader = Files.newBufferedReader(path)) {
        System.out.print("c");
        System.out.println(bufferedReader.readLine());
      } catch (Throwable t) {
        printError(t);
      }
      Files.deleteIfExists(path);
      testWithOpenOption(pathSupplier, null);
      for (StandardOpenOption openOption : StandardOpenOption.values()) {
        testWithOpenOption(pathSupplier, openOption);
      }
    }

    private static void testWithOpenOption(
        Supplier<Path> pathSupplier, StandardOpenOption openOption) throws IOException {
      Path path;
      path = pathSupplier.get();
      System.out.print("inStream[" + openOption + "]:");
      try (InputStream inputStream = Files.newInputStream(path, openOption)) {
        System.out.print("c");
        byte[] read = new byte[5];
        inputStream.read(read);
        System.out.println(new String(read));
      } catch (Throwable t) {
        printError(t);
      }
      Files.deleteIfExists(path);
      path = pathSupplier.get();
      System.out.print("outStream[" + openOption + "]:");
      try (OutputStream outputStream = Files.newOutputStream(path, openOption)) {
        System.out.print("c");
        outputStream.write("Game over!".getBytes(StandardCharsets.UTF_8));
        System.out.print("w");
        System.out.println(Files.readAllLines(path).get(0));
      } catch (Throwable t) {
        printError(t);
      }
      Files.deleteIfExists(path);
      path = pathSupplier.get();
      System.out.print("buffWrite[" + openOption + "]:");
      try (BufferedWriter bufferedWriter = Files.newBufferedWriter(path, openOption)) {
        System.out.print("c");
        bufferedWriter.write("Game over!");
        System.out.print("w");
        System.out.println(Files.readAllLines(path).get(0));
      } catch (Throwable t) {
        printError(t);
      }
      Files.deleteIfExists(path);
      path = pathSupplier.get();
      System.out.print("newByte[" + openOption + "]:");
      try (SeekableByteChannel seekableByteChannel = Files.newByteChannel(path, openOption)) {
        System.out.print("c");
        System.out.println(seekableByteChannel.size());
      } catch (Throwable t) {
        printError(t);
      }
      Files.deleteIfExists(path);
    }
  }
}
