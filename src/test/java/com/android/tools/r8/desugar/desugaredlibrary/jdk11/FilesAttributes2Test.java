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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FilesAttributes2Test extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "PRESENT FILE",
          "fileStore:%s",
          "lastModifiedTime:true",
          "lastModifiedTime:true",
          "owner:true",
          "owner:true",
          "posix:true",
          "posix:true",
          "dir:false",
          "dir:false",
          "hidden:false",
          "readable:true",
          "writable:true",
          "executable:false",
          "regular:true",
          "regular:true",
          "same:true",
          "same:false",
          "symlink:false",
          "PRESENT DIR",
          "fileStore:%s",
          "lastModifiedTime:true",
          "lastModifiedTime:true",
          "owner:true",
          "owner:true",
          "posix:true",
          "posix:true",
          "dir:true",
          "dir:true",
          "hidden:false",
          "readable:true",
          "writable:true",
          "executable:true",
          "regular:false",
          "regular:false",
          "same:true",
          "same:false",
          "symlink:false",
          "ABSENT FILE",
          "fileStore:%s",
          "lastModifiedTime:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "lastModifiedTime:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "owner:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "owner:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "posix:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "posix:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "dir:false",
          "dir:false",
          "hidden:false",
          "readable:false",
          "writable:false",
          "executable:false",
          "regular:false",
          "regular:false",
          "same:true",
          "same:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "symlink:false");
  private static final String EXPECTED_RESULT_ANDROID_DESUGARING =
      StringUtils.lines(
          "PRESENT FILE",
          "fileStore:class java.lang.SecurityException :: getFileStore",
          "lastModifiedTime:true",
          "lastModifiedTime:true",
          "owner:class java.lang.UnsupportedOperationException :: no-message",
          "owner:class java.lang.UnsupportedOperationException :: no-message",
          "posix:class java.lang.UnsupportedOperationException :: no-message",
          "posix:class java.lang.UnsupportedOperationException :: no-message",
          "dir:false",
          "dir:false",
          "hidden:false",
          "readable:true",
          "writable:true",
          "executable:false",
          "regular:true",
          "regular:true",
          "same:true",
          "same:false",
          "symlink:%s",
          "PRESENT DIR",
          "fileStore:class java.lang.SecurityException :: getFileStore",
          "lastModifiedTime:true",
          "lastModifiedTime:true",
          "owner:class java.lang.UnsupportedOperationException :: no-message",
          "owner:class java.lang.UnsupportedOperationException :: no-message",
          "posix:class java.lang.UnsupportedOperationException :: no-message",
          "posix:class java.lang.UnsupportedOperationException :: no-message",
          "dir:true",
          "dir:true",
          "hidden:false",
          "readable:true",
          "writable:true",
          "executable:true",
          "regular:false",
          "regular:false",
          "same:true",
          "same:false",
          "symlink:%s",
          "ABSENT FILE",
          "fileStore:class java.lang.SecurityException :: getFileStore",
          "lastModifiedTime:false",
          "lastModifiedTime:false",
          "owner:class java.lang.UnsupportedOperationException :: no-message",
          "owner:class java.lang.UnsupportedOperationException :: no-message",
          "posix:class java.lang.UnsupportedOperationException :: no-message",
          "posix:class java.lang.UnsupportedOperationException :: no-message",
          "dir:false",
          "dir:false",
          "hidden:false",
          "readable:false",
          "writable:false",
          "executable:false",
          "regular:false",
          "regular:false",
          "same:true",
          "same:false",
          "symlink:%s");

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

  public FilesAttributes2Test(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  private String getExpectedResult() {
    if (parameters.isCfRuntime()) {
      return String.format(
          EXPECTED_RESULT,
          "true",
          "true",
          "class java.nio.file.NoSuchFileException :: notExisting.txt");
    }
    if (libraryDesugaringSpecification.usesPlatformFileSystem(parameters)) {
      return String.format(
          EXPECTED_RESULT,
          "class java.lang.SecurityException :: getFileStore",
          "class java.lang.SecurityException :: getFileStore",
          "class java.lang.SecurityException :: getFileStore");
    }
    if (parameters.getDexRuntimeVersion().isEqualTo(Version.V7_0_0)) {
      // Everything is a symlink on 24.
      return String.format(EXPECTED_RESULT_ANDROID_DESUGARING, true, true, true);
    }
    return String.format(EXPECTED_RESULT_ANDROID_DESUGARING, false, false, false);
  }

  @Test
  public void test() throws Throwable {
    if (parameters.isCfRuntime()) {
      // Reference runtime, we use Jdk 11 since this is Jdk 11 desugared library, not that Jdk 8
      // behaves differently on this test.
      Assume.assumeTrue(parameters.isCfRuntime(CfVm.JDK11) && !ToolHelper.isWindows());
      testForJvm()
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
      Path path = Files.createTempFile("example", ".txt");
      testProperties(path);
      System.out.println("PRESENT DIR");
      Path dir = Files.createTempDirectory("dir");
      testProperties(dir);
      System.out.println("ABSENT FILE");
      Path notExisting = Paths.get("notExisting.txt");
      testProperties(notExisting);
    }

    private static void printError(Throwable t) {
      String[] split =
          t.getMessage() == null ? new String[] {"no-message"} : t.getMessage().split("/");
      System.out.println(t.getClass() + " :: " + split[split.length - 1]);
    }

    private static void testProperties(Path path) {
      try {
        System.out.print("fileStore:");
        System.out.println(Files.getFileStore(path) != null);
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("lastModifiedTime:");
        System.out.println(Files.getLastModifiedTime(path).toMillis() > 0);
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("lastModifiedTime:");
        System.out.println(
            Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis() > 0);
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("owner:");
        System.out.println(Files.getOwner(path) != null);
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("owner:");
        System.out.println(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS) != null);
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("posix:");
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
        System.out.println(perms == null ? "null" : perms.contains(PosixFilePermission.OWNER_READ));
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("posix:");
        Set<PosixFilePermission> perms =
            Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
        System.out.println(perms == null ? "null" : perms.contains(PosixFilePermission.OWNER_READ));
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("dir:");
        System.out.println(Files.isDirectory(path));
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("dir:");
        System.out.println(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS));
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("hidden:");
        System.out.println(Files.isHidden(path));
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("readable:");
        System.out.println(Files.isReadable(path));
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("writable:");
        System.out.println(Files.isWritable(path));
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("executable:");
        System.out.println(Files.isExecutable(path));
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("regular:");
        System.out.println(Files.isRegularFile(path));
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("regular:");
        System.out.println(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS));
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("same:");
        System.out.println(Files.isSameFile(path, path));
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("same:");
        System.out.println(Files.isSameFile(path, Paths.get("/")));
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("symlink:");
        System.out.println(Files.isSymbolicLink(path));
      } catch (Throwable t) {
        printError(t);
      }
    }
  }
}
