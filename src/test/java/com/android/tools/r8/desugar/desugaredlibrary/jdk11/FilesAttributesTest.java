// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;

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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FilesAttributesTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT_JVM_LINUX =
      StringUtils.lines(
          "PRESENT FILE",
          "basic:true",
          "posix:true",
          "dos:false",
          "acl:null",
          "fileOwner:true",
          "userDefined:user",
          "basic:true",
          "posix:true",
          "dos:false",
          "ABSENT FILE",
          "basic:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "posix:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "dos:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "acl:null",
          "fileOwner:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "userDefined:user",
          "basic:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "posix:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "dos:class java.nio.file.NoSuchFileException :: notExisting.txt");
  private static final String EXPECTED_RESULT_ANDROID =
      StringUtils.lines(
          "PRESENT FILE",
          "basic:true",
          "posix:true",
          "dos:null",
          "acl:null",
          "fileOwner:true",
          "userDefined:null",
          "basic:true",
          "posix:true",
          "dos:class java.lang.UnsupportedOperationException :: no-message",
          "ABSENT FILE",
          "basic:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "posix:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "dos:null",
          "acl:null",
          "fileOwner:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "userDefined:null",
          "basic:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "posix:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "dos:class java.lang.UnsupportedOperationException :: no-message");
  private static final String EXPECTED_RESULT_ANDROID_DESUGARING =
      StringUtils.lines(
          "PRESENT FILE",
          "basic:true",
          "posix:null",
          "dos:null",
          "acl:null",
          "fileOwner:null",
          "userDefined:null",
          "basic:true",
          "posix:class java.lang.UnsupportedOperationException :: no-message",
          "dos:class java.lang.UnsupportedOperationException :: no-message",
          "ABSENT FILE",
          "basic:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "posix:null",
          "dos:null",
          "acl:null",
          "fileOwner:null",
          "userDefined:null",
          "basic:class java.nio.file.NoSuchFileException :: notExisting.txt",
          "posix:class java.lang.UnsupportedOperationException :: no-message",
          "dos:class java.lang.UnsupportedOperationException :: no-message");

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

  public FilesAttributesTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  private String getExpectedResult() {
    if (parameters.isCfRuntime()) {
      return EXPECTED_RESULT_JVM_LINUX;
    }
    if (libraryDesugaringSpecification.usesPlatformFileSystem(parameters)) {
      return EXPECTED_RESULT_ANDROID;
    }
    return EXPECTED_RESULT_ANDROID_DESUGARING;
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
      Path path = Files.createTempFile("example", ".txt");
      attributeViewAccess(path);
      attributeAccess(path);
      System.out.println("ABSENT FILE");
      Path notExisting = Paths.get("notExisting.txt");
      Files.deleteIfExists(notExisting);
      attributeViewAccess(notExisting);
      attributeAccess(notExisting);
    }

    private static void printError(Throwable t) {
      String[] split =
          t.getMessage() == null ? new String[] {"no-message"} : t.getMessage().split("/");
      System.out.println(t.getClass() + " :: " + split[split.length - 1]);
    }

    private static void attributeViewAccess(Path path) {
      try {
        System.out.print("basic:");
        BasicFileAttributeView basicView =
            Files.getFileAttributeView(path, BasicFileAttributeView.class);
        if (basicView != null) {
          System.out.println(basicView.readAttributes().isRegularFile());
        } else {
          System.out.println("null");
        }
      } catch (Throwable t) {
        printError(t);
      }

      try {
        System.out.print("posix:");
        PosixFileAttributeView posixView =
            Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posixView != null) {
          System.out.println(posixView.readAttributes().permissions().contains(OWNER_READ));
        } else {
          System.out.println("null");
        }
      } catch (Throwable t) {
        printError(t);
      }

      try {
        System.out.print("dos:");
        DosFileAttributeView dosView = Files.getFileAttributeView(path, DosFileAttributeView.class);
        if (dosView != null) {
          System.out.println(dosView.readAttributes().isReadOnly());
        } else {
          System.out.println("null");
        }
      } catch (Throwable t) {
        printError(t);
      }

      try {
        System.out.print("acl:");
        AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (aclView != null) {
          System.out.println(aclView.getAcl().isEmpty());
        } else {
          System.out.println("null");
        }
      } catch (Throwable t) {
        printError(t);
      }
      try {
        System.out.print("fileOwner:");
        FileOwnerAttributeView foView =
            Files.getFileAttributeView(path, FileOwnerAttributeView.class);
        if (foView != null) {
          System.out.println(foView.getOwner() != null);
        } else {
          System.out.println("null");
        }
      } catch (Throwable t) {
        printError(t);
      }

      try {
        System.out.print("userDefined:");
        UserDefinedFileAttributeView udView =
            Files.getFileAttributeView(path, UserDefinedFileAttributeView.class);
        if (udView != null) {
          System.out.println(udView.name());
        } else {
          System.out.println("null");
        }
      } catch (Throwable t) {
        printError(t);
      }
    }

    private static void attributeAccess(Path path) {
      try {
        System.out.print("basic:");
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        if (attributes != null) {
          System.out.println(attributes.isRegularFile());
        } else {
          System.out.println("null");
        }
      } catch (Throwable t) {
        printError(t);
      }

      try {
        System.out.print("posix:");
        PosixFileAttributes posixAttributes = Files.readAttributes(path, PosixFileAttributes.class);
        if (posixAttributes != null) {
          System.out.println(posixAttributes.permissions().contains(OWNER_READ));
        } else {
          System.out.println("null");
        }
      } catch (Throwable t) {
        printError(t);
      }

      try {
        System.out.print("dos:");
        DosFileAttributes dosFileAttributes = Files.readAttributes(path, DosFileAttributes.class);
        if (dosFileAttributes != null) {
          System.out.println(dosFileAttributes.isReadOnly());
        } else {
          System.out.println("null");
        }
      } catch (Throwable t) {
        printError(t);
      }
    }
  }
}
