// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.gson;

import static com.android.tools.r8.desugar.desugaredlibrary.gson.GsonDesugaredLibraryTestUtils.uniqueName;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TimeSerializationTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "Z",
          "GMT",
          "2008-06-01T20:30:42.000000111Z[GMT]",
          "Z",
          "GMT",
          "2008-06-01T20:30:42.000000111Z[GMT]");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        // TODO(b/134732760): Skip Android 4.4.4 due to missing libjavacrypto.
        getTestParameters()
            .withDexRuntime(Version.V4_0_4)
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public TimeSerializationTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testZonedDateTimeSerialization() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(TimeSerializationTest.class)
        .addKeepMainRule(Executor.class)
        .compile()
        .withArt6Plus64BitsLib()
        .run(
            parameters.getRuntime(),
            Executor.class,
            uniqueName(temp, libraryDesugaringSpecification, compilationSpecification, parameters))
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  static class Executor {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
      ZoneOffset offset = ZoneOffset.UTC;
      System.out.println(offset);
      ZoneId gmt = ZoneId.of("GMT");
      System.out.println(gmt);
      ZonedDateTime dateTime = ZonedDateTime.of(2008, 6, 1, 20, 30, 42, 111, gmt);
      System.out.println(dateTime);
      File file = new File(args[0]);

      FileOutputStream fos = new FileOutputStream(file);
      ObjectOutputStream oos = new ObjectOutputStream(fos);
      oos.writeObject(offset);
      oos.writeObject(gmt);
      oos.writeObject(dateTime);
      oos.close();
      fos.close();

      FileInputStream fis = new FileInputStream(file);
      ObjectInputStream ois = new ObjectInputStream(fis);
      ZoneOffset newOffset = (ZoneOffset) ois.readObject();
      ZoneId newGmt = (ZoneId) ois.readObject();
      ZonedDateTime newDateTime = (ZonedDateTime) ois.readObject();
      fis.close();
      ois.close();

      System.out.println(newOffset);
      System.out.println(newGmt);
      System.out.println(newDateTime);
    }
  }
}
