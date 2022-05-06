// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.CustomLibrarySpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ChannelSetTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

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

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        // Skip Android 4.4.4 due to missing libjavacrypto.
        getTestParameters()
            .withDexRuntime(Version.V4_0_4)
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build(),
        ImmutableList.of(JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public ChannelSetTest(
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
        .addProgramClasses(TestClass.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLib.class, AndroidApiLevel.B))
        .addKeepMainRule(TestClass.class)
        .compile()
        .withArt6Plus64BitsLib()
        .run(
            parameters.getRuntime(),
            TestClass.class,
            Integer.toString(parameters.getApiLevel().getLevel()))
        .assertSuccessWithOutput(getExpectedResult());
  }

  private String getExpectedResult() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.O)
        ? EXPECTED_RESULT_26
        : EXPECTED_RESULT;
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
