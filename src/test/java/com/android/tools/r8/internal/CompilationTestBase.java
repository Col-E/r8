// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.ArtErrorParser;
import com.android.tools.r8.utils.ArtErrorParser.ArtErrorInfo;
import com.android.tools.r8.utils.ArtErrorParser.ArtErrorParserException;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundFieldSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Assert;
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
      List<String> pgConfs,
      String input)
      throws ExecutionException, IOException, CompilationFailedException {
    return runAndCheckVerification(
        compiler, mode, referenceApk, pgConfs, null, Collections.singletonList(input));
  }

  public AndroidApp runAndCheckVerification(D8Command.Builder builder, String referenceApk)
      throws IOException, ExecutionException, CompilationFailedException {
    AndroidAppConsumers appSink = new AndroidAppConsumers(builder);
    D8.run(builder.build());
    AndroidApp result = appSink.build();
    checkVerification(result, referenceApk);
    return result;
  }

  public void assertIdenticalZipFiles(File file1, File file2) throws IOException {
    try (ZipFile zipFile1 = new ZipFile(file1); ZipFile zipFile2 = new ZipFile(file2)) {
      final Enumeration<? extends ZipEntry> entries1 = zipFile1.entries();
      final Enumeration<? extends ZipEntry> entries2 = zipFile2.entries();

      while (entries1.hasMoreElements()) {
        Assert.assertTrue(entries2.hasMoreElements());
        ZipEntry entry1 = entries1.nextElement();
        ZipEntry entry2 = entries2.nextElement();
        Assert.assertEquals(entry1.getName(), entry2.getName());
        Assert.assertEquals(entry1.getCrc(), entry2.getCrc());
        Assert.assertEquals(entry1.getSize(), entry2.getSize());
      }
    }
  }

  public AndroidApp runAndCheckVerification(
      CompilerUnderTest compiler,
      CompilationMode mode,
      String referenceApk,
      List<String> pgConfs,
      Consumer<InternalOptions> optionsConsumer,
      List<String> inputs)
      throws ExecutionException, IOException, CompilationFailedException {
    return runAndCheckVerification(compiler, mode, referenceApk, pgConfs, optionsConsumer,
        DexIndexedConsumer::emptyConsumer, inputs);
  }

  public AndroidApp runAndCheckVerification(
      CompilerUnderTest compiler,
      CompilationMode mode,
      String referenceApk,
      List<String> pgConfs,
      Consumer<InternalOptions> optionsConsumer,
      Supplier<DexIndexedConsumer> dexIndexedConsumerSupplier,
      List<String> inputs)
      throws ExecutionException, IOException, CompilationFailedException {
    assertTrue(referenceApk == null || new File(referenceApk).exists());
    AndroidAppConsumers outputApp;
    if (compiler == CompilerUnderTest.R8) {
      R8Command.Builder builder = R8Command.builder();
      builder.addProgramFiles(ListUtils.map(inputs, Paths::get));
      if (pgConfs != null) {
        builder.addProguardConfigurationFiles(
            pgConfs.stream().map(Paths::get).collect(Collectors.toList()));
      }
      builder.setMode(mode);
      builder.setProgramConsumer(dexIndexedConsumerSupplier.get());
      builder.setMinApiLevel(AndroidApiLevel.L.getLevel());
      ToolHelper.allowPartiallyImplementedProguardOptions(builder);
      ToolHelper.addProguardConfigurationConsumer(
          builder,
          pgConfig -> {
            pgConfig.setPrintSeeds(false);
            pgConfig.setIgnoreWarnings(true);
          });
      outputApp = new AndroidAppConsumers(builder);
      ToolHelper.runR8(builder.build(), optionsConsumer);
    } else {
      assert compiler == CompilerUnderTest.D8;
      D8Command.Builder builder =
          D8Command.builder()
              .addProgramFiles(ListUtils.map(inputs, Paths::get))
              .setMode(mode)
              .setMinApiLevel(AndroidApiLevel.L.getLevel());
      outputApp = new AndroidAppConsumers(builder);
      D8.run(builder.build());
    }
    return checkVerification(outputApp.build(), referenceApk);
  }


  public AndroidApp checkVerification(AndroidApp outputApp, String referenceApk)
      throws IOException, ExecutionException {
    Path out = temp.getRoot().toPath().resolve("all.zip");
    Path oatFile = temp.getRoot().toPath().resolve("all.oat");
    outputApp.writeToZip(out, OutputMode.DexIndexed);
    try {
      ToolHelper.runDex2Oat(out, oatFile);
      return outputApp;
    } catch (AssertionError e) {
      if (referenceApk == null) {
        throw e;
      }
      CodeInspector theirs = new CodeInspector(Paths.get(referenceApk));
      CodeInspector ours = new CodeInspector(out);
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

  public int applicationSize(AndroidApp app) throws IOException, ResourceException {
    int bytes = 0;
    try (Closer closer = Closer.create()) {
      for (ProgramResource dex : app.getDexProgramResourcesForTesting()) {
        bytes += ByteStreams.toByteArray(closer.register(dex.getByteStream())).length;
      }
    }
    return bytes;
  }

  public void assertIdenticalApplications(AndroidApp app1, AndroidApp app2)
      throws IOException, ResourceException {
    assertIdenticalApplications(app1, app2, false);
  }

  public void assertIdenticalApplications(AndroidApp app1, AndroidApp app2, boolean write)
      throws IOException, ResourceException {
    try (Closer closer = Closer.create()) {
      if (write) {
        app1.writeToDirectory(temp.newFolder("app1").toPath(), OutputMode.DexIndexed);
        app2.writeToDirectory(temp.newFolder("app2").toPath(), OutputMode.DexIndexed);
      }
      List<ProgramResource> files1 = app1.getDexProgramResourcesForTesting();
      List<ProgramResource> files2 = app2.getDexProgramResourcesForTesting();
      assertEquals(files1.size(), files2.size());
      for (int index = 0; index < files1.size(); index++) {
        InputStream file1 = closer.register(files1.get(index).getByteStream());
        InputStream file2 = closer.register(files2.get(index).getByteStream());
        byte[] bytes1 = ByteStreams.toByteArray(file1);
        byte[] bytes2 = ByteStreams.toByteArray(file2);
        assertArrayEquals("File index " + index, bytes1, bytes2);
      }
    }
  }

  public void assertIdenticalApplicationsUpToCode(
      AndroidApp app1, AndroidApp app2, boolean allowNewClassesInApp2)
      throws IOException, ExecutionException {
    CodeInspector inspect1 = new CodeInspector(app1);
    CodeInspector inspect2 = new CodeInspector(app2);

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

    BiConsumer<CodeInspector, Boolean> collectClasses =
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
