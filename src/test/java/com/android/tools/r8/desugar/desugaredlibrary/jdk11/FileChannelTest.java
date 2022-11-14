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
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FileChannelTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "Hello World! ",
          "Hello World! ",
          "Bye bye. ",
          "Hello World! ",
          "Bye bye. ",
          "Hello World! ",
          "The monkey eats...",
          "Bananas!",
          "Bananas!",
          "Bananas!");

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

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

  public FileChannelTest(
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
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .compile()
        .withArt6Plus64BitsLib()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  public static class TestClass {

    public static void main(String[] args) throws IOException {
      fisTest();
      fosTest();
      fileChannelOpen();
    }

    private static void fosTest() throws IOException {
      String toWrite = "The monkey eats...";
      Path tmp = Files.createTempFile("fos", ".txt");

      ByteBuffer byteBuffer = ByteBuffer.wrap(toWrite.getBytes(StandardCharsets.UTF_8));
      FileOutputStream fos = new FileOutputStream(tmp.toFile());
      FileChannel channel = fos.getChannel();
      channel.write(byteBuffer);

      List<String> lines = Files.readAllLines(tmp);
      System.out.println(lines.get(0));
    }

    private static void fileChannelOpen() throws IOException {
      fileChannelOpenTest();
      fileChannelOpenSetTest();
      fileChannelOpenLockTest();
    }

    private static void fileChannelOpenLockTest() throws IOException {
      Path tmp = Files.createTempFile("lock", ".txt");
      String contents = "Bananas!";
      Files.write(tmp, contents.getBytes(StandardCharsets.UTF_8));
      FileChannel fc = FileChannel.open(tmp, StandardOpenOption.READ);
      ByteBuffer byteBuffer = ByteBuffer.allocate(contents.length());
      fc.read(byteBuffer);
      System.out.println(new String(byteBuffer.array()));
      fc.close();
    }

    private static void fileChannelOpenTest() throws IOException {
      Path tmp = Files.createTempFile("a", ".txt");
      String contents = "Bananas!";
      Files.write(tmp, contents.getBytes(StandardCharsets.UTF_8));
      FileChannel fc = FileChannel.open(tmp, StandardOpenOption.READ, StandardOpenOption.WRITE);
      ByteBuffer byteBuffer = ByteBuffer.allocate(contents.length());
      // Extra indirection through the lock.
      fc.lock().channel().read(byteBuffer);
      System.out.println(new String(byteBuffer.array()));
      fc.close();
    }

    private static void fileChannelOpenSetTest() throws IOException {
      Path tmp = Files.createTempFile("b", ".txt");
      String contents = "Bananas!";
      Files.write(tmp, contents.getBytes(StandardCharsets.UTF_8));
      Set<OpenOption> options = new HashSet<>();
      options.add(StandardOpenOption.READ);
      FileChannel fc = FileChannel.open(tmp, options);
      ByteBuffer byteBuffer = ByteBuffer.allocate(contents.length());
      fc.read(byteBuffer);
      System.out.println(new String(byteBuffer.array()));
      fc.close();
    }

    private static void fisTest() throws IOException {
      fisOwner();
      fisNotOwner(true);
      fisNotOwner(false);
      fisOwnerTryResources();
    }

    private static void fisNotOwner(boolean closeFirst) throws IOException {
      String toWrite = "Hello World! ";
      String toWriteFIS = "Bye bye. ";
      Path tmp = Files.createTempFile("tmp", ".txt");
      Files.write(tmp, (toWrite + toWriteFIS).getBytes(StandardCharsets.UTF_8));

      ByteBuffer byteBuffer = ByteBuffer.allocate(toWrite.length());
      ByteBuffer byteBufferFIS = ByteBuffer.allocate(toWriteFIS.length());
      FileInputStream fileInputStream = new FileInputStream(tmp.toFile());
      FileDescriptor fd = fileInputStream.getFD();
      FileInputStream fis2 = new FileInputStream(fd);
      fileInputStream.getChannel().read(byteBuffer);
      fis2.getChannel().read(byteBufferFIS);

      if (closeFirst) {
        fileInputStream.close();
        fis2.close();
      } else {
        fis2.close();
        fileInputStream.close();
      }

      System.out.println(new String(byteBuffer.array()));
      System.out.println(new String(byteBufferFIS.array()));
    }

    private static void fisOwner() throws IOException {
      String toWrite = "Hello World! ";
      Path tmp = Files.createTempFile("tmp", ".txt");
      Files.write(tmp, toWrite.getBytes(StandardCharsets.UTF_8));

      ByteBuffer byteBuffer = ByteBuffer.allocate(toWrite.length());
      FileInputStream fileInputStream = new FileInputStream(tmp.toFile());
      fileInputStream.getChannel().read(byteBuffer);
      fileInputStream.close();

      System.out.println(new String(byteBuffer.array()));
    }

    private static void fisOwnerTryResources() throws IOException {
      String toWrite = "Hello World! ";
      Path tmp = Files.createTempFile("tmp", ".txt");
      Files.write(tmp, toWrite.getBytes(StandardCharsets.UTF_8));

      ByteBuffer byteBuffer = ByteBuffer.allocate(toWrite.length());
      try (FileInputStream fileInputStream = new FileInputStream(tmp.toFile())) {
        fileInputStream.getChannel().read(byteBuffer);
      }

      System.out.println(new String(byteBuffer.array()));
    }
  }
}
