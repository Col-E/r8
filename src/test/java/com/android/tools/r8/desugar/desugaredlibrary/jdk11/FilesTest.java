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
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FilesTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "bytes written: 11",
          "String written: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "null",
          "true",
          "unsupported");
  private static final String EXPECTED_RESULT_24_26 =
      StringUtils.lines(
          "bytes written: 11",
          "String written: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "unsupported",
          "unsupported",
          "null",
          "true",
          "unsupported");
  private static final String EXPECTED_RESULT_26 =
      StringUtils.lines(
          "bytes written: 11",
          "String written: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "true",
          "true",
          "true");

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

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

  private String getExpectedResult() {
    if (parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.O)) {
      return EXPECTED_RESULT_26;
    }
    return parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N)
        ? EXPECTED_RESULT_24_26
        : EXPECTED_RESULT;
  }

  public FilesTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
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
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(pathConfiguration())
        .compile()
        .withArt6Plus64BitsLib()
        .withArtFrameworks()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(getExpectedResult());
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue(isJDK11DesugaredLibrary());
    testForR8(Backend.DEX)
        .addLibraryFiles(getLibraryFile())
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(TestClass.class)
        .enableCoreLibraryDesugaring(pathConfiguration())
        .compile()
        .withArt6Plus64BitsLib()
        .withArtFrameworks()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(getExpectedResult());
  }

  public static class TestClass {

    public static void main(String[] args) throws Throwable {
      Path path = Files.createTempFile("example", ".txt");
      readWriteThroughFilesAPI(path);
      readThroughFileChannelAPI(path);
      attributeAccess(path);
    }

    private static void attributeAccess(Path path) throws IOException {
      PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
      if (view != null) {
        System.out.println(
            view.readAttributes().permissions().contains(PosixFilePermission.OWNER_READ));
      } else {
        System.out.println("null");
      }

      BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
      if (attributes != null) {
        System.out.println(attributes.isRegularFile());
      } else {
        System.out.println("null");
      }

      try {
        PosixFileAttributes posixAttributes = Files.readAttributes(path, PosixFileAttributes.class);
        if (attributes != null) {
          System.out.println(
              posixAttributes.permissions().contains(PosixFilePermission.OWNER_READ));
        } else {
          System.out.println("null");
        }
      } catch (UnsupportedOperationException e) {
        System.out.println("unsupported");
      }
    }

    private static void readWriteThroughFilesAPI(Path path) throws IOException {
      try (SeekableByteChannel channel =
          Files.newByteChannel(path, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
        String toWrite = "Hello World";

        // Write the String toWrite into the channel.
        ByteBuffer byteBuffer = ByteBuffer.wrap(toWrite.getBytes());
        int write = channel.write(byteBuffer);
        System.out.println("bytes written: " + write);
        System.out.println("String written: " + toWrite);

        // Read the String toWrite from the channel.
        channel.position(0);
        ByteBuffer byteBuffer2 = ByteBuffer.allocate(write);
        int read = channel.read(byteBuffer2);
        System.out.println("bytes read: " + read);
        System.out.println("String read: " + new String(byteBuffer2.array()));
      }
    }

    private static void readThroughFileChannelAPI(Path path) throws IOException {
      try {
        Set<OpenOption> openOptions = new HashSet<>();
        openOptions.add(LinkOption.NOFOLLOW_LINKS);
        try (FileChannel channel = FileChannel.open(path, openOptions)) {
          String toWrite = "Hello World";

          // Read the String toWrite from the channel.
          channel.position(0);
          ByteBuffer byteBuffer2 = ByteBuffer.allocate(toWrite.length());
          int read = channel.read(byteBuffer2);
          System.out.println("bytes read: " + read);
          System.out.println("String read: " + new String(byteBuffer2.array()));
        }
      } catch (NoClassDefFoundError err) {
        // TODO(b/222647019): FileChannel#open is not supported in between 24 and 26.
        System.out.println("unsupported");
        System.out.println("unsupported");
      }
    }
  }
}
