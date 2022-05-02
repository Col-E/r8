// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderMismatchException;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ChannelSetTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "bytes written: 11",
          "String written: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "unsupported");
  private static final String EXPECTED_RESULT_26 =
      StringUtils.lines(
          "bytes written: 11",
          "String written: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "wrapper start",
          "bytes read: 11",
          "String read: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "bytes read: 11",
          "String read: Hello World");
  private static Path CUSTOM_LIB;

  @BeforeClass
  public static void compileCustomLib() throws Exception {
    CUSTOM_LIB =
        testForD8(getStaticTemp())
            .addProgramClasses(CustomLib.class)
            .setMinApi(AndroidApiLevel.B)
            .compile()
            .writeToZip();
  }

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    // Skip Android 4.4.4 due to missing libjavacrypto.
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters()
            .withDexRuntime(Version.V4_0_4)
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build());
  }

  public ChannelSetTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  private String getExpectedResult() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.O)
        ? EXPECTED_RESULT_26
        : EXPECTED_RESULT;
  }

  private LibraryDesugaringTestConfiguration pathConfiguration() {
    return LibraryDesugaringTestConfiguration.builder()
        .setMinApi(parameters.getApiLevel())
        .addDesugaredLibraryConfiguration(
            StringResource.fromFile(ToolHelper.getDesugarLibJsonForTestingWithPath()))
        .setMode(shrinkDesugaredLibrary ? CompilationMode.RELEASE : CompilationMode.DEBUG)
        .withKeepRuleConsumer()
        .build();
  }

  @Test
  public void testD8() throws Exception {
    Assume.assumeTrue(isJDK11DesugaredLibrary());
    testForD8(parameters.getBackend())
        .addLibraryFiles(getLibraryFile())
        .addProgramClasses(TestClass.class)
        .addLibraryClasses(CustomLib.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(pathConfiguration())
        .compile()
        .withArt6Plus64BitsLib()
        .withArtFrameworks()
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(
            parameters.getRuntime(),
            TestClass.class,
            Integer.toString(parameters.getApiLevel().getLevel()))
        .assertSuccessWithOutput(getExpectedResult());
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue(isJDK11DesugaredLibrary());
    testForR8(Backend.DEX)
        .addLibraryFiles(getLibraryFile())
        .addProgramClasses(TestClass.class)
        .addLibraryClasses(CustomLib.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(TestClass.class)
        .enableCoreLibraryDesugaring(pathConfiguration())
        .compile()
        .withArt6Plus64BitsLib()
        .withArtFrameworks()
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(
            parameters.getRuntime(),
            TestClass.class,
            Integer.toString(parameters.getApiLevel().getLevel()))
        .assertSuccessWithOutput(getExpectedResult());
  }

  public static class CustomLib {

    // Answers effectively a Path wrapper.
    public static Path get(String path) {
      return Paths.get(path);
    }
  }

  public static class TestClass {

    public static void main(String[] args) throws Throwable {
      Path path = Files.createTempFile("example", ".txt");
      String hello = "Hello World";
      Set<OpenOption> openOptions = new HashSet<>();
      openOptions.add(LinkOption.NOFOLLOW_LINKS);
      writeHelloWorldIntoFile(path, hello);
      readHelloWithPathApis(path, hello, openOptions);
      // The rest of tests are testing API conversion, which is possible only on + devices.
      if (Integer.parseInt(args[0]) < 26) {
        return;
      }
      System.out.println("wrapper start");
      Path pathWrapper = CustomLib.get(path.toString());
      readHelloWithPathWrapperApis(pathWrapper, hello, openOptions);
      try {
        try (SeekableByteChannel channel =
            pathWrapper.getFileSystem().provider().newByteChannel(path, openOptions)) {
          readFromChannel(channel, hello.length());
        }
      } catch (ProviderMismatchException e) {
        System.out.println("provider missmatch");
      }
    }

    private static void readHelloWithPathWrapperApis(
        Path pathWrapper, String hello, Set<OpenOption> openOptions)
        throws IOException, InterruptedException, ExecutionException {
      try (SeekableByteChannel channel =
          pathWrapper.getFileSystem().provider().newByteChannel(pathWrapper, openOptions)) {
        readFromChannel(channel, hello.length());
      }
      try (FileChannel channel =
          pathWrapper.getFileSystem().provider().newFileChannel(pathWrapper, openOptions)) {
        readFromChannel(channel, hello.length());
      }
      try {
        try (AsynchronousFileChannel channel =
            pathWrapper
                .getFileSystem()
                .provider()
                .newAsynchronousFileChannel(pathWrapper, openOptions, ForkJoinPool.commonPool())) {
          ByteBuffer byteBuffer = ByteBuffer.allocate(hello.length());
          Future<Integer> readFuture = channel.read(byteBuffer, 0);
          // We force the future to await here with get().
          int read = readFuture.get();
          System.out.println("bytes read: " + read);
          System.out.println("String read: " + new String(byteBuffer.array()));
        }
      } catch (UnsupportedOperationException e) {
        System.out.println("unsupported");
      }
    }

    private static void readHelloWithPathApis(Path path, String hello, Set<OpenOption> openOptions)
        throws IOException, InterruptedException, ExecutionException {
      try (SeekableByteChannel channel =
          path.getFileSystem().provider().newByteChannel(path, openOptions)) {
        readFromChannel(channel, hello.length());
      }
      try (FileChannel channel =
          path.getFileSystem().provider().newFileChannel(path, openOptions)) {
        readFromChannel(channel, hello.length());
      }
      try {
        try (AsynchronousFileChannel channel =
            path.getFileSystem()
                .provider()
                .newAsynchronousFileChannel(path, openOptions, ForkJoinPool.commonPool())) {
          ByteBuffer byteBuffer = ByteBuffer.allocate(hello.length());
          Future<Integer> readFuture = channel.read(byteBuffer, 0);
          int read = readFuture.get();
          System.out.println("bytes read: " + read);
          System.out.println("String read: " + new String(byteBuffer.array()));
        }
      } catch (NoClassDefFoundError | UnsupportedOperationException e) {
        // ForkJoinPool is missing (officially below Android 21, in practice somewhere below).
        // The method newAsynchronousFileChannel is unsupported on DesugarFileSystems.
        System.out.println("unsupported");
      }
    }

    private static void writeHelloWorldIntoFile(Path path, String hello) throws IOException {
      try (SeekableByteChannel channel =
          Files.newByteChannel(path, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(hello.getBytes());
        int write = channel.write(byteBuffer);
        System.out.println("bytes written: " + write);
        System.out.println("String written: " + hello);
      }
    }

    private static void readFromChannel(SeekableByteChannel channel, int helloWorldSize)
        throws IOException {
      ByteBuffer byteBuffer = ByteBuffer.allocate(helloWorldSize);
      int read = channel.read(byteBuffer);
      System.out.println("bytes read: " + read);
      System.out.println("String read: " + new String(byteBuffer.array()));
    }
  }
}
