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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FilesTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT_DESUGARING_FILE_SYSTEM =
      StringUtils.lines(
          "bytes written: 11",
          "String written: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "null",
          "true",
          "unsupported",
          "j$.nio.file.attribute",
          "tmp",
          "/",
          "true");
  private static final String EXPECTED_RESULT_DESUGARING_FILE_SYSTEM_PLATFORM_CHANNEL =
      StringUtils.lines(
          "bytes written: 11",
          "String written: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "unsupported",
          "unsupported",
          "null",
          "true",
          "unsupported",
          "j$.nio.file.attribute",
          "tmp",
          "/",
          "true");
  private static final String EXPECTED_RESULT_PLATFORM_FILE_SYSTEM_DESUGARING =
      StringUtils.lines(
          "bytes written: 11",
          "String written: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "true",
          "true",
          "true",
          "j$.nio.file.attribute",
          "tmp",
          "/",
          "true");
  private static final String EXPECTED_RESULT_PLATFORM_FILE_SYSTEM =
      StringUtils.lines(
          "bytes written: 11",
          "String written: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "true",
          "true",
          "true",
          "java.nio.file.attribute",
          "tmp",
          "/",
          "true");

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

  public FilesTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  private String getExpectedResult() {
    if (libraryDesugaringSpecification.usesPlatformFileSystem(parameters)) {
      return libraryDesugaringSpecification.hasNioFileDesugaring(parameters)
          ? EXPECTED_RESULT_PLATFORM_FILE_SYSTEM_DESUGARING
          : EXPECTED_RESULT_PLATFORM_FILE_SYSTEM;
    }
    return libraryDesugaringSpecification.hasNioChannelDesugaring(parameters)
        ? EXPECTED_RESULT_DESUGARING_FILE_SYSTEM
        : EXPECTED_RESULT_DESUGARING_FILE_SYSTEM_PLATFORM_CHANNEL;
  }

  @Test
  public void test() throws Throwable {
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
      Path path = Files.createTempFile("example", ".txt");
      readWriteThroughFilesAPI(path);
      readThroughFileChannelAPI(path);
      attributeAccess(path);
      Files.setAttribute(path, "basic:lastModifiedTime", FileTime.from(Instant.EPOCH));
      fspMethodsWithGeneric(path);
      pathGeneric();
    }

    private static void pathGeneric() throws IOException {
      Path tmpDict = Files.createTempDirectory("tmpDict");
      Path tmpFile = Files.createFile(tmpDict.resolve("tmpFile"));
      Iterator<Path> iterator = tmpDict.iterator();
      System.out.println(iterator.next());
      Iterable<Path> rootDirectories = tmpFile.getFileSystem().getRootDirectories();
      System.out.println(rootDirectories.iterator().next());
      DirectoryStream<Path> paths = Files.newDirectoryStream(tmpDict);
      System.out.println(paths.iterator().hasNext());
    }

    private static void fspMethodsWithGeneric(Path path) throws IOException {
      Map<String, Object> mapping = Files.readAttributes(path, "lastModifiedTime");
      System.out.println(mapping.values().iterator().next().getClass().getPackage().getName());
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
        if (posixAttributes != null) {
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
