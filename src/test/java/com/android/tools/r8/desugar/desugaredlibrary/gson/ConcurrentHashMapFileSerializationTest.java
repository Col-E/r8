// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.gson;

import static com.android.tools.r8.desugar.desugaredlibrary.gson.GsonDesugaredLibraryTestUtils.uniqueName;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConcurrentHashMapFileSerializationTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final String EXPECTED_RESULT = StringUtils.lines("2", "2", "v1", "v1", "v2", "v2");

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

  public ConcurrentHashMapFileSerializationTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testMap() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(ConcurrentHashMapFileSerializationTest.class)
        .addKeepMainRule(Executor.class)
        .noMinification()
        .compile()
        .inspectL8(this::assertVersionUID)
        .withArt6Plus64BitsLib()
        .run(
            parameters.getRuntime(),
            Executor.class,
            uniqueName(temp, libraryDesugaringSpecification, compilationSpecification, parameters))
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void assertVersionUID(CodeInspector inspector) {
    ClassSubject mapClass = inspector.clazz("j$.util.concurrent.ConcurrentHashMap");
    if (parameters.getApiLevel().isLessThan(AndroidApiLevel.N)) {
      assertTrue(mapClass.isPresent());
      FieldSubject serialVersionUID = mapClass.uniqueFieldWithOriginalName("serialVersionUID");
      assertTrue(serialVersionUID.isPresent());
    } else {
      assertFalse(mapClass.isPresent());
    }
  }

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  static class Executor {
    public static void main(String[] args) throws Exception {
      chmTest(args[0]);
    }

    @SuppressWarnings("unchecked")
    private static void chmTest(String uniqueName) throws IOException, ClassNotFoundException {
      ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
      map.put("k1", "v1");
      map.put("k2", "v2");
      File file = new File(uniqueName);

      FileOutputStream fos = new FileOutputStream(file);
      ObjectOutputStream oos = new ObjectOutputStream(fos);
      oos.writeObject(map);
      oos.close();
      fos.close();

      FileInputStream fis = new FileInputStream(file);
      ObjectInputStream ois = new ObjectInputStream(fis);
      ConcurrentHashMap<String, String> newMap =
          (ConcurrentHashMap<String, String>) ois.readObject();
      fis.close();
      ois.close();

      System.out.println(map.size());
      System.out.println(newMap.size());
      System.out.println(map.get("k1"));
      System.out.println(newMap.get("k1"));
      System.out.println(map.get("k2"));
      System.out.println(newMap.get("k2"));
    }
  }
}
