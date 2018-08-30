// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jar.UnicodeSetRegression;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.ArtErrorParser;
import com.android.tools.r8.utils.ArtErrorParser.ArtErrorInfo;
import com.android.tools.r8.utils.ArtErrorParser.ArtErrorParserException;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.ComparisonFailure;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UnicodeSetRegressionTest {

  private static final String JAR_FILE =
      "src/test/java/com/android/tools/r8/jar/UnicodeSetRegression/UnicodeSet.jar";

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  private AndroidApp dexFromDX() throws IOException {
    return ToolHelper.runDexer(JAR_FILE, temp.newFolder("dx-dex").getPath());
  }

  @Test
  public void testUnicodeSetFromDex() throws Throwable {
    Path combinedInput = temp.getRoot().toPath().resolve("all.zip");
    Path oatFile = temp.getRoot().toPath().resolve("all.oat");
    AndroidApp output =
        ToolHelper.runR8(dexFromDX(), options -> options.ignoreMissingClasses = true);
    output.write(combinedInput, OutputMode.DexIndexed);
    ToolHelper.runDex2Oat(combinedInput, oatFile);
  }

  @Test
  public void testUnicodeSetFromJar() throws Throwable {
    Path combinedInput = temp.getRoot().toPath().resolve("all.zip");
    Path oatFile = temp.getRoot().toPath().resolve("all.oat");
    R8Command.Builder builder =
        R8Command.builder()
            .addProgramFiles(Paths.get(JAR_FILE))
            .setOutput(Paths.get(combinedInput.toString()), OutputMode.DexIndexed);
    AndroidAppConsumers compatSink = new AndroidAppConsumers(builder);
    // Ignore missing classes since we don't want to link to the IBM text library.
    ToolHelper.runR8(builder.build(), options -> options.ignoreMissingClasses = true);
    AndroidApp result = compatSink.build();
    try {
      ToolHelper.runDex2Oat(combinedInput, oatFile);
    } catch (AssertionError e) {
      AndroidApp fromDexApp =
          ToolHelper.runR8(dexFromDX(), options -> options.ignoreMissingClasses = true);
      CodeInspector fromDex = new CodeInspector(fromDexApp);
      CodeInspector fromJar = new CodeInspector(result);
      List<ArtErrorInfo> errors;
      try {
        errors = ArtErrorParser.parse(e.getMessage());
      } catch (ArtErrorParserException parserException) {
        System.err.println(parserException.toString());
        throw e;
      }
      if (errors.isEmpty()) {
        throw e;
      }
      for (ArtErrorInfo error : errors.subList(0, errors.size() - 1)) {
        System.err.println(new ComparisonFailure(error.getMessage(),
            "REFERENCE\n" + error.dump(fromDex, false) + "\nEND REFERENCE",
            "PROCESSED\n" + error.dump(fromJar, true) + "\nEND PROCESSED").toString());
      }
      ArtErrorInfo error = errors.get(errors.size() - 1);
      throw new ComparisonFailure(error.getMessage(),
          "REFERENCE\n" + error.dump(fromDex, false) + "\nEND REFERENCE",
          "PROCESSED\n" + error.dump(fromJar, true) + "\nEND PROCESSED");
    }
  }

  @Test
  public void testUnicodeSetFromJarToCF() throws Throwable {
    Path combinedInput = temp.getRoot().toPath().resolve("all.zip");
    Path oatFile = temp.getRoot().toPath().resolve("all.oat");
    R8Command.Builder builder =
        R8Command.builder()
            .addProgramFiles(Paths.get(JAR_FILE))
            .setOutput(Paths.get(combinedInput.toString()), OutputMode.ClassFile)
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar());
    AndroidAppConsumers compatSink = new AndroidAppConsumers(builder);
    // Ignore missing classes since we don't want to link to the IBM text library.
    ToolHelper.runR8(builder.build(), options -> options.ignoreMissingClasses = true);
    AndroidApp result = compatSink.build();
  }
}
