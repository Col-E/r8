// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.Resource;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.shaking.ProguardRuleParserException;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ArtErrorParser;
import com.android.tools.r8.utils.ArtErrorParser.ArtErrorInfo;
import com.android.tools.r8.utils.ArtErrorParser.ArtErrorParserException;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.FoundClassSubject;
import com.android.tools.r8.utils.DexInspector.FoundFieldSubject;
import com.android.tools.r8.utils.DexInspector.FoundMethodSubject;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.OutputMode;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.ComparisonFailure;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public abstract class CompilationTestBase {

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  public AndroidApp runAndCheckVerification(
      CompilerUnderTest compiler,
      CompilationMode mode,
      String referenceApk,
      String pgConf,
      String input)
      throws ExecutionException, IOException, ProguardRuleParserException, CompilationException,
          CompilationFailedException {
    return runAndCheckVerification(
        compiler, mode, referenceApk, pgConf, null, Collections.singletonList(input));
  }

  public AndroidApp runAndCheckVerification(D8Command command, String referenceApk)
      throws IOException, ExecutionException, CompilationException {
    return checkVerification(ToolHelper.runD8(command), referenceApk);
  }

  public AndroidApp runAndCheckVerification(
      CompilerUnderTest compiler,
      CompilationMode mode,
      String referenceApk,
      String pgConf,
      Consumer<InternalOptions> optionsConsumer,
      List<String> inputs)
      throws ExecutionException, IOException, ProguardRuleParserException, CompilationException,
      CompilationFailedException {
    assertTrue(referenceApk == null || new File(referenceApk).exists());
    AndroidApp outputApp;
    if (compiler == CompilerUnderTest.R8) {
      R8Command.Builder builder = R8Command.builder();
      builder.addProgramFiles(ListUtils.map(inputs, Paths::get));
      if (pgConf != null) {
        builder.addProguardConfigurationFiles(Paths.get(pgConf));
      }
      builder.setMode(mode);
      builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
      builder.setMinApiLevel(AndroidApiLevel.L.getLevel());
      ToolHelper.addProguardConfigurationConsumer(
          builder,
          pgConfig -> {
            pgConfig.setPrintSeeds(false);
            pgConfig.setIgnoreWarnings(true);
          });
      outputApp = ToolHelper.runR8(builder.build(), optionsConsumer);
    } else {
      assert compiler == CompilerUnderTest.D8;
      outputApp =
          ToolHelper.runD8(
              D8Command.builder()
                  .addProgramFiles(ListUtils.map(inputs, Paths::get))
                  .setMode(mode)
                  .setMinApiLevel(AndroidApiLevel.L.getLevel())
                  .build());
    }
    return checkVerification(outputApp, referenceApk);
  }

  public AndroidApp checkVerification(AndroidApp outputApp, String referenceApk)
      throws IOException, ExecutionException {
    Path out = temp.getRoot().toPath().resolve("all.zip");
    Path oatFile = temp.getRoot().toPath().resolve("all.oat");
    outputApp.writeToZip(out, OutputMode.Indexed);
    try {
      ToolHelper.runDex2Oat(out, oatFile);
      return outputApp;
    } catch (AssertionError e) {
      if (referenceApk == null) {
        throw e;
      }
      DexInspector theirs = new DexInspector(Paths.get(referenceApk));
      DexInspector ours = new DexInspector(out);
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
            "REFERENCE\n" + error.dump(theirs, false) + "\nEND REFERENCE",
            "PROCESSED\n" + error.dump(ours, true) + "\nEND PROCESSED").toString());
      }
      ArtErrorInfo error = errors.get(errors.size() - 1);
      throw new ComparisonFailure(error.getMessage(),
          "REFERENCE\n" + error.dump(theirs, false) + "\nEND REFERENCE",
          "PROCESSED\n" + error.dump(ours, true) + "\nEND PROCESSED");
    }
  }

  public int applicationSize(AndroidApp app) throws IOException {
    int bytes = 0;
    try (Closer closer = Closer.create()) {
      for (Resource dex : app.getDexProgramResourcesForTesting()) {
        bytes += ByteStreams.toByteArray(closer.register(dex.getStream())).length;
      }
    }
    return bytes;
  }

  public void assertIdenticalApplications(AndroidApp app1, AndroidApp app2) throws IOException {
    assertIdenticalApplications(app1, app2, false);
  }

  public void assertIdenticalApplications(AndroidApp app1, AndroidApp app2, boolean write)
      throws IOException {
    try (Closer closer = Closer.create()) {
      if (write) {
        app1.writeToDirectory(temp.newFolder("app1").toPath(), OutputMode.Indexed);
        app2.writeToDirectory(temp.newFolder("app2").toPath(), OutputMode.Indexed);
      }
      List<ProgramResource> files1 = app1.getDexProgramResourcesForTesting();
      List<ProgramResource> files2 = app2.getDexProgramResourcesForTesting();
      assertEquals(files1.size(), files2.size());
      for (int index = 0; index < files1.size(); index++) {
        InputStream file1 = closer.register(files1.get(index).getStream());
        InputStream file2 = closer.register(files2.get(index).getStream());
        byte[] bytes1 = ByteStreams.toByteArray(file1);
        byte[] bytes2 = ByteStreams.toByteArray(file2);
        assertArrayEquals("File index " + index, bytes1, bytes2);
      }
    }
  }

  public void assertIdenticalApplicationsUpToCode(
      AndroidApp app1, AndroidApp app2, boolean allowNewClassesInApp2)
      throws IOException, ExecutionException {
    DexInspector inspect1 = new DexInspector(app1);
    DexInspector inspect2 = new DexInspector(app2);

    class Pair<T> {
      private T first;
      private T second;

      private void set(boolean selectFirst, T value) {
        if (selectFirst) {
          first = value;
        } else {
          second = value;
        }
      }
    }

    // Collect all classes from both inspectors, indexed by finalDescriptor.
    Map<String, Pair<FoundClassSubject>> allClasses = new HashMap<>();

    BiConsumer<DexInspector, Boolean> collectClasses =
        (inspector, selectFirst) -> {
          inspector.forAllClasses(
              clazz -> {
                String finalDescriptor = clazz.getFinalDescriptor();
                allClasses.compute(
                    finalDescriptor,
                    (k, v) -> {
                      if (v == null) {
                        v = new Pair<>();
                      }
                      v.set(selectFirst, clazz);
                      return v;
                    });
              });
        };

    collectClasses.accept(inspect1, true);
    collectClasses.accept(inspect2, false);

    for (Map.Entry<String, Pair<FoundClassSubject>> classEntry : allClasses.entrySet()) {
      String className = classEntry.getKey();
      FoundClassSubject class1 = classEntry.getValue().first;
      FoundClassSubject class2 = classEntry.getValue().second;

      assert class1 != null || class2 != null;

      if (!allowNewClassesInApp2) {
        assertNotNull(String.format("Class %s is missing from the first app.", className), class1);
      }
      assertNotNull(String.format("Class %s is missing from the second app.", className), class2);

      if (class1 == null) {
        continue;
      }

      // Collect all methods for this class from both apps.
      Map<MethodSignature, Pair<FoundMethodSubject>> allMethods = new HashMap<>();

      BiConsumer<FoundClassSubject, Boolean> collectMethods =
          (classSubject, selectFirst) -> {
            classSubject.forAllMethods(
                m -> {
                  MethodSignature fs = m.getFinalSignature();
                  allMethods.compute(
                      fs,
                      (k, v) -> {
                        if (v == null) {
                          v = new Pair<>();
                        }
                        v.set(selectFirst, m);
                        return v;
                      });
                });
          };

      collectMethods.accept(class1, true);
      collectMethods.accept(class2, false);

      for (Map.Entry<MethodSignature, Pair<FoundMethodSubject>> methodEntry :
          allMethods.entrySet()) {
        MethodSignature signature = methodEntry.getKey();
        FoundMethodSubject method1 = methodEntry.getValue().first;
        FoundMethodSubject method2 = methodEntry.getValue().second;
        assert method1 != null || method2 != null;

        assertNotNull(
            String.format(
                "Method %s of class %s is missing from the first app.", signature, className),
            method1);
        assertNotNull(
            String.format(
                "Method %s of class %s is missing from the second app.", signature, className),
            method2);
      }

      // Collect all fields for this class from both apps.
      Map<FieldSignature, Pair<FoundFieldSubject>> allFields = new HashMap<>();

      BiConsumer<FoundClassSubject, Boolean> collectFields =
          (classSubject, selectFirst) -> {
            classSubject.forAllFields(
                f -> {
                  FieldSignature fs = f.getFinalSignature();
                  allFields.compute(
                      fs,
                      (k, v) -> {
                        if (v == null) {
                          v = new Pair<>();
                        }
                        v.set(selectFirst, f);
                        return v;
                      });
                });
          };

      collectFields.accept(class1, true);
      collectFields.accept(class2, false);

      for (Map.Entry<FieldSignature, Pair<FoundFieldSubject>> fieldEntry : allFields.entrySet()) {
        FieldSignature signature = fieldEntry.getKey();
        FoundFieldSubject field1 = fieldEntry.getValue().first;
        FoundFieldSubject field2 = fieldEntry.getValue().second;
        assert field1 != null || field2 != null;

        assertNotNull(
            String.format(
                "Field %s of class %s is missing from the first app.", signature, className),
            field1);
        assertNotNull(
            String.format(
                "Field %s of class %s is missing from the second app.", signature, className),
            field2);
      }
    }
  }
}
