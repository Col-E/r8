// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.jumbostring;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class JumboStringTestRunner extends ExamplesTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().enableApiLevelsForCf().build();
  }

  public JumboStringTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return JumboString.class;
  }

  @Override
  public List<Class<?>> getTestClasses() {
    return ImmutableList.of(getMainClass(), StringPool0.class, StringPool1.class);
  }

  @Override
  public String getExpected() {
    return StringUtils.lines("zzzz - jumbo string");
  }

  @Test
  public void testDesugaring() throws Exception {
    runTestDesugaring();
  }

  @Test
  public void testR8() throws Exception {
    // Disable shrinking and obfuscation so that the fields and their names are retained.
    runTestR8(b -> b.addDontShrink().addDontObfuscate());
  }

  @Test
  public void testDebug() throws Exception {
    runTestDebugComparator();
  }

  // Code for generating the StringPoolX.java files.
  //
  // We only need to generate two files to get jumbo strings. Each file has 16k static final fields
  // with values, and both the field name and the value will be in the string pool.
  public static void generate() throws IOException {
    Path jumboExampleDir =
        ToolHelper.getSourceFileForTestClass(JumboStringTestRunner.class).getParent();
    int stringsPerFile = (1 << 14);
    for (int fileNumber = 0; fileNumber < 2; fileNumber++) {
      Path path = jumboExampleDir.resolve("StringPool" + fileNumber + ".java");
      PrintStream out =
          new PrintStream(
              Files.newOutputStream(
                  path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));

      out.println("// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file");
      out.println("// for details. All rights reserved. Use of this source code is governed by a");
      out.println("// BSD-style license that can be found in the LICENSE file.");
      out.println("package " + JumboStringTestRunner.class.getPackage().getName() + ";");
      out.println();
      out.println("// GENERATED FILE - DO NOT EDIT (See JumboStringTestRunner.generate)");
      out.println("class StringPool" + fileNumber + " {");

      int offset = fileNumber * stringsPerFile;
      for (int i = offset; i < offset + stringsPerFile; i++) {
        out.println("  public static final String s" + i + " = \"" + i + "\";");
      }
      out.println("}");
      out.close();
    }
  }
}
