// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import static com.android.tools.r8.desugar.desugaredlibrary.jdk11.FilesMoveCopyTest.TestClass.CopyOrMove.COPY;
import static com.android.tools.r8.desugar.desugaredlibrary.jdk11.FilesMoveCopyTest.TestClass.CopyOrMove.MOVE;
import static com.android.tools.r8.desugar.desugaredlibrary.jdk11.FilesMoveCopyTest.TestClass.NewFile.EXISTING;
import static com.android.tools.r8.desugar.desugaredlibrary.jdk11.FilesMoveCopyTest.TestClass.NewFile.NEW;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests Files#copy and Files#move methods. Known limitations/details below 26: - ATOMIC MOVE is a
 * no-op, moves are effectively file renaming which seem to be atomic on Android. - COPY_ATTRIBUTES
 * is a no-op, except if unsupported in which case it throws appropriately, now it seems only basic
 * file attributes are supported below 26 anyway and such attributes are not meaningfully copied
 * around anyway. Creation/last modification/last access times are set to the time of the move/copy,
 * and the other ones such as "isDirectory" cannot be easily abused.
 */
@RunWith(Parameterized.class)
public class FilesMoveCopyTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "Testing MOVE NEW NONE",
          "MOVE_NONE",
          "Testing COPY NEW NONE",
          "COPY_NONE",
          "Testing MOVE EXISTING NONE",
          "Failure: class java.nio.file.FileAlreadyExistsException :: dest",
          "Testing COPY EXISTING NONE",
          "Failure: class java.nio.file.FileAlreadyExistsException :: dest",
          "Testing MOVE NEW ATOMIC_MOVE",
          "MOVE_ATOMIC_MOVE",
          "Testing COPY NEW ATOMIC_MOVE",
          "Failure: class java.lang.UnsupportedOperationException :: Unsupported copy option",
          "Testing MOVE NEW COPY_ATTRIBUTES",
          "Failure: class java.lang.UnsupportedOperationException :: Unsupported copy option",
          "Testing COPY NEW COPY_ATTRIBUTES",
          "COPY_COPY_ATTRIBUTES",
          "Testing MOVE NEW REPLACE_EXISTING",
          "MOVE_REPLACE_EXISTING",
          "Testing COPY NEW REPLACE_EXISTING",
          "COPY_REPLACE_EXISTING",
          "Testing MOVE EXISTING REPLACE_EXISTING",
          "MOVE_REPLACE_EXISTING",
          "Testing COPY EXISTING REPLACE_EXISTING",
          "COPY_REPLACE_EXISTING",
          "Testing MOVE NEW NOFOLLOW_LINKS",
          "MOVE_NOFOLLOW_LINKS",
          "Testing COPY NEW NOFOLLOW_LINKS",
          "COPY_NOFOLLOW_LINKS");

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

  public FilesMoveCopyTest(
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
      Assume.assumeTrue(parameters.isCfRuntime(CfVm.JDK11));
      testForJvm()
          .addInnerClasses(getClass())
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED_RESULT);
      return;
    }
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .compile()
        .withArt6Plus64BitsLib()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  public static class TestClass {

    enum CopyOrMove {
      COPY,
      MOVE
    }

    enum NewFile {
      EXISTING,
      NEW
    }

    public static void main(String[] args) throws Throwable {
      for (CopyOption copyOption : allOptions()) {
        test(MOVE, NEW, copyOption);
        test(COPY, NEW, copyOption);
        if (copyOption == null || copyOption == StandardCopyOption.REPLACE_EXISTING) {
          test(MOVE, EXISTING, copyOption);
          test(COPY, EXISTING, copyOption);
        }
      }
    }

    private static CopyOption[] allOptions() {
      return new CopyOption[] {
        null,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.COPY_ATTRIBUTES,
        StandardCopyOption.REPLACE_EXISTING,
        LinkOption.NOFOLLOW_LINKS
      };
    }

    private static void clearTmp(Path src, Path dest) throws IOException {
      if (Files.exists(src)) {
        Files.delete(src);
      }
      if (Files.exists(dest)) {
        Files.delete(dest);
      }
    }

    private static void test(CopyOrMove copyOrMove, NewFile newFile, CopyOption copyOption)
        throws IOException {
      String copyOptionString = copyOption == null ? "NONE" : copyOption.toString();
      CopyOption[] opts = copyOption == null ? new CopyOption[0] : new CopyOption[] {copyOption};
      System.out.println("Testing " + copyOrMove + " " + newFile + " " + copyOptionString);
      Path src = Files.createTempFile("src_", ".txt");
      Path dest =
          newFile == NEW
              ? src.getParent().resolve("dest_" + System.currentTimeMillis() + ".txt")
              : Files.createTempFile("dest_", ".txt");
      String toWrite = copyOrMove + "_" + copyOptionString;
      Files.write(src, toWrite.getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE);
      try {
        if (copyOrMove == COPY) {
          Files.copy(src, dest, opts);
        } else {
          Files.move(src, dest, opts);
        }
        System.out.println(Files.readAllLines(dest).get(0));
      } catch (Throwable t) {
        String subMessage;
        if (t instanceof FileAlreadyExistsException && t.getMessage() != null) {
          // The message is the file path, however we cannot rely on temp path, so we check
          // only against the known part of the path.
          String[] split = t.getMessage().split("/");
          subMessage = split[split.length - 1].substring(0, 4);
        } else {
          subMessage = t.getMessage();
        }
        System.out.println("Failure: " + t.getClass() + " :: " + subMessage);
      }
      clearTmp(src, dest);
    }
  }
}
