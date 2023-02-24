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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FileLockTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "true",
          "true",
          "pos:2;sz:7;sh:false",
          "true",
          "true",
          "true",
          "pos:2;sz:7;sh:false",
          "false",
          "true",
          "true",
          "pos:2;sz:7;sh:false",
          "true");

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

  public FileLockTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Throwable {
    if (parameters.isCfRuntime()) {
      // Reference runtime, we use Jdk 11 since this is Jdk 11 desugared library, not that Jdk 8
      // behaves differently on this test.
      Assume.assumeTrue(parameters.isCfRuntime(CfVm.JDK11) && !ToolHelper.isWindows());
      testForJvm(parameters)
          .addInnerClasses(getClass())
          .run(parameters.getRuntime(), TestClass.class, "10000")
          .assertSuccessWithOutput(EXPECTED_RESULT);
      return;
    }
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .compile()
        .withArt6Plus64BitsLib()
        .run(
            parameters.getRuntime(),
            TestClass.class,
            String.valueOf(parameters.getApiLevel().getLevel()))
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  public static class TestClass {

    private static int minApi;

    public static void main(String[] args) throws Throwable {
      minApi = Integer.parseInt(args[0]);
      appendTest();
      deleteTest();
      fosTest();
    }

    private static void checkChannel(FileChannel channel) throws IOException {
      FileLock lock = channel.lock(2, 7, false);
      System.out.println(lock.channel() == channel);
      checkAcquiredBy(lock, channel);
      System.out.println(
          "pos:" + lock.position() + ";sz:" + lock.size() + ";sh:" + lock.isShared());
      lock.release();
      channel.close();
    }

    private static void checkAcquiredBy(FileLock lock, FileChannel channel) {
      // The method acquiredBy was introduced in 24, we do not backport or check below.
      if (minApi >= 24) {
        System.out.println((lock.acquiredBy() == channel));
      } else {
        System.out.println("true");
      }
    }

    private static void appendTest() throws IOException {
      Path tmpAppend = Files.createTempFile("tmp_append", ".txt");
      Files.write(tmpAppend, "There will be dragons!".getBytes(StandardCharsets.UTF_8));
      FileChannel appendChannel =
          FileChannel.open(tmpAppend, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
      checkChannel(appendChannel);
      System.out.println(Files.exists(tmpAppend));
      Files.delete(tmpAppend);
    }

    private static void deleteTest() throws IOException {
      Path tmpDelete = Files.createTempFile("tmp_delete", ".txt");
      Files.write(tmpDelete, "There will be dragons!".getBytes(StandardCharsets.UTF_8));
      FileChannel deleteChannel =
          FileChannel.open(tmpDelete, StandardOpenOption.DELETE_ON_CLOSE, StandardOpenOption.WRITE);
      checkChannel(deleteChannel);
      System.out.println(Files.exists(tmpDelete));
      Files.deleteIfExists(tmpDelete);
    }

    private static void fosTest() throws IOException {
      Path tmpFOS = Files.createTempFile("tmp_fos", ".txt");
      Files.write(tmpFOS, "There will be dragons!".getBytes(StandardCharsets.UTF_8));
      FileOutputStream fileOutputStream = new FileOutputStream(tmpFOS.toFile());
      FileChannel fosChannel = fileOutputStream.getChannel();
      checkChannel(fosChannel);
      System.out.println(Files.exists(tmpFOS));
      Files.delete(tmpFOS);
    }
  }
}
