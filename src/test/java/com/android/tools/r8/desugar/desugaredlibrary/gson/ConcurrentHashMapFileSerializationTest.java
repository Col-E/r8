// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.gson;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
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

  private static final String EXPECTED_RESULT = StringUtils.lines("2", "2", "v1", "v1", "v2", "v2");

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    // TODO(b/134732760): Skip Android 4.4.4 due to missing libjavacrypto.
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters()
            .withDexRuntime(Version.V4_0_4)
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build());
  }

  public ConcurrentHashMapFileSerializationTest(
      boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testMapSerializationD8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addInnerClasses(ConcurrentHashMapFileSerializationTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .withArt6Plus64BitsLib()
        .withArtFrameworks()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testMapSerializationR8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(Backend.DEX)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addInnerClasses(ConcurrentHashMapFileSerializationTest.class)
        .addKeepMainRule(Executor.class)
        .noMinification()
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .withArt6Plus64BitsLib()
        .withArtFrameworks()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  static class Executor {
    public static void main(String[] args) throws Exception {
      chmTest();
    }

    @SuppressWarnings("unchecked")
    private static void chmTest() throws IOException, ClassNotFoundException {
      ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
      map.put("k1", "v1");
      map.put("k2", "v2");
      File file = new File("testTemp");

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
