// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FilesTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT_FORMAT =
      StringUtils.lines(
          "bytes written: 11",
          "String written: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "bytes read: 11",
          "String read: Hello World",
          "tmp",
          "/",
          "true",
          "tmpFile",
          "DirectoryStream created: class"
              + " java.nio.file.DirectoryIteratorException::java.io.IOException: Here",
          "%s",
          "This",
          "is",
          "fun!",
          "%s",
          "%s",
          "%s",
          "%s");
  private static final List<String> EXPECTED_RESULT_POSIX =
      ImmutableList.of(
          "Succeeded with POSIX RO:false",
          "Successfully set RO with POSIX",
          "Succeeded with POSIX RO:true");
  private static final List<String> EXPECTED_RESULT_DESUGARING_NON_POSIX =
      ImmutableList.of(
          "Fail to understand if the file is read-only: class"
              + " java.lang.UnsupportedOperationException",
          "Fail to set file as read-only: class java.lang.UnsupportedOperationException",
          "NotSet");

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
    List<String> strings = new ArrayList<>();
    strings.add(
        libraryDesugaringSpecification.usesPlatformFileSystem(parameters)
                && libraryDesugaringSpecification.hasNioFileDesugaring(parameters)
            ? "fail"
            : "npe caught");
    strings.addAll(
        libraryDesugaringSpecification.usesPlatformFileSystem(parameters)
            ? EXPECTED_RESULT_POSIX
            : EXPECTED_RESULT_DESUGARING_NON_POSIX);
    strings.add(
        libraryDesugaringSpecification.hasNioFileDesugaring(parameters)
            ? "j$.nio.file.attribute"
            : "java.nio.file.attribute");
    return String.format(EXPECTED_RESULT_FORMAT, strings.toArray());
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
      Files.setAttribute(path, "basic:lastModifiedTime", FileTime.from(Instant.EPOCH));
      pathGeneric();
      lines(path);
      readOnlyTest(path);
      fspMethodsWithGeneric(path);
    }

    private static void readOnlyTest(Path path) {
      isReadOnly(path);
      if (setReadOnly(path)) {
        isReadOnly(path);
      } else {
        System.out.println("NotSet");
      }
    }

    private static boolean isReadOnly(Path path) {
      try {
        // DOS attempt.
        try {
          DosFileAttributeView dosFileAttributeView =
              Files.getFileAttributeView(path, DosFileAttributeView.class);
          if (dosFileAttributeView != null && dosFileAttributeView.readAttributes() != null) {
            boolean readOnly = dosFileAttributeView.readAttributes().isReadOnly();
            System.out.println("Succeeded with DOS RO:" + readOnly);
            return readOnly;
          }
        } catch (IOException ignored) {
        }
        // Posix attempt.
        Set<PosixFilePermission> posixFilePermissions = Files.getPosixFilePermissions(path);
        boolean readOnly =
            posixFilePermissions.contains(OWNER_READ)
                && !posixFilePermissions.contains(OWNER_WRITE);
        System.out.println("Succeeded with POSIX RO:" + readOnly);
        return readOnly;

      } catch (Throwable t) {
        System.out.println("Fail to understand if the file is read-only: " + t.getClass());
        return false;
      }
    }

    /** Common pattern to set a file as read-only: Try on Dos, on failure, retry on Posix. */
    private static boolean setReadOnly(Path path) {
      try {

        // DOS attempt.
        try {
          DosFileAttributeView dosFileAttributeView =
              Files.getFileAttributeView(path, DosFileAttributeView.class);
          if (dosFileAttributeView != null) {
            dosFileAttributeView.setReadOnly(true);
            System.out.println("Successfully set RO with DOS");
            return true;
          }
        } catch (IOException ignored) {
        }

        // Posix attempt.
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
        List<PosixFilePermission> readPermissions = Arrays.asList(OWNER_READ);
        List<PosixFilePermission> writePermissions = Arrays.asList(PosixFilePermission.OWNER_WRITE);
        permissions.addAll(readPermissions);
        permissions.removeAll(writePermissions);
        Files.setPosixFilePermissions(path, permissions);
        System.out.println("Successfully set RO with POSIX");
        return true;

      } catch (Throwable t) {
        System.out.println("Fail to set file as read-only: " + t.getClass());
        return false;
      }
    }

    private static void pathGeneric() throws IOException {
      Path tmpDict = Files.createTempDirectory("tmpDict");
      Path tmpFile = Files.createFile(tmpDict.resolve("tmpFile"));
      Iterator<Path> iterator = tmpDict.iterator();
      System.out.println(iterator.next());
      Iterable<Path> rootDirectories = tmpFile.getFileSystem().getRootDirectories();
      System.out.println(rootDirectories.iterator().next());
      DirectoryStream<Path> paths = Files.newDirectoryStream(tmpDict);
      Iterator<Path> theIterator = paths.iterator();
      System.out.println(theIterator.hasNext());
      System.out.println(theIterator.next().getFileName());
      try {
        // TODO(b/262190079): In desugared lib the filter is resolved eagerly which leads to the
        // exception being thrown here and not later.
        DirectoryStream<Path> pathExceptions =
            Files.newDirectoryStream(
                tmpDict,
                x -> {
                  throw new IOException("Here");
                });
        System.out.print("DirectoryStream created: ");
        pathExceptions.iterator().next();
        System.out.println("fail");
      } catch (Throwable t) {
        System.out.println(t.getClass() + "::" + t.getMessage());
      }
      try {
        DirectoryStream<Path> thrown =
            Files.newDirectoryStream(tmpDict, (Filter<? super Path>) null);
        System.out.println("fail");
      } catch (NullPointerException npe) {
        System.out.println("npe caught");
      }
    }

    private static void fspMethodsWithGeneric(Path path) throws IOException {
      Map<String, Object> mapping = Files.readAttributes(path, "lastModifiedTime");
      System.out.println(mapping.values().iterator().next().getClass().getPackage().getName());
    }

    private static void lines(Path path) throws IOException {
      Files.write(path, "This\nis\nfun!".getBytes(StandardCharsets.UTF_8));
      Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8);
      lines.forEach(System.out::println);
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
