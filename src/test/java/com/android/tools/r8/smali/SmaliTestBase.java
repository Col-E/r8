// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.smali;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.origin.EmbeddedOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.antlr.runtime.RecognitionException;

public class SmaliTestBase extends TestBase {

  public static final String DEFAULT_CLASS_NAME = "Test";
  public static final String DEFAULT_MAIN_CLASS_NAME = DEFAULT_CLASS_NAME;
  public static final String DEFAULT_METHOD_NAME = "method";

  protected AndroidApp buildApplication(SmaliBuilder builder) {
    try {
      return AndroidApp.builder().addDexProgramData(builder.compile(), Origin.unknown()).build();
    } catch (IOException | RecognitionException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  protected AndroidApp buildApplicationWithAndroidJar(SmaliBuilder builder) {
    try {
      return AndroidApp.builder()
          .addDexProgramData(builder.compile(), EmbeddedOrigin.INSTANCE)
          .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
          .build();
    } catch (IOException | RecognitionException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  protected DexApplication buildApplication(AndroidApp input, InternalOptions options) {
    try {
      options.itemFactory.resetSortedIndices();
      return new ApplicationReader(input, options, new Timing("SmaliTest")).read();
    } catch (IOException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  protected AndroidApp processApplication(AndroidApp application) {
    return processApplication(application, null);
  }

  protected AndroidApp processApplication(AndroidApp application,
      Consumer<InternalOptions> optionsConsumer) {
    try {
      return ToolHelper.runR8(application, optionsConsumer);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected Path runR8(SmaliBuilder builder, List<String> proguardConfigurations) {
    return runR8(builder, proguardConfigurations, pg -> {}, o -> {});
  }

  protected Path runR8(
      SmaliBuilder builder,
      List<String> proguardConfigurations,
      Consumer<ProguardConfiguration.Builder> pgConsumer,
      Consumer<InternalOptions> optionsConsumer) {
    try {
      Path dexOutputDir = temp.newFolder().toPath();
      R8Command.Builder command =
          ToolHelper.addProguardConfigurationConsumer(R8Command.builder(), pgConsumer)
              .setOutput(dexOutputDir, OutputMode.DexIndexed)
              .setMode(CompilationMode.DEBUG)
              .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
              .addProguardConfiguration(proguardConfigurations, Origin.unknown());
      ToolHelper.getAppBuilder(command)
          .addDexProgramData(builder.compile(), EmbeddedOrigin.INSTANCE);
      ToolHelper.runR8WithFullResult(command.build(), optionsConsumer);
      return dexOutputDir.resolve("classes.dex");
    } catch (IOException
        | RecognitionException
        | ExecutionException
        | CompilationFailedException e) {
      throw new RuntimeException(e);
    }
  }

  protected DexClass getClass(DexApplication application, String className) {
    CodeInspector inspector = new CodeInspector(application);
    ClassSubject clazz = inspector.clazz(className);
    assertTrue(clazz.isPresent());
    return clazz.getDexClass();
  }

  protected DexClass getClass(DexApplication application, MethodSignature signature) {
    return getClass(application, signature.clazz);
  }

  protected DexClass getClass(Path appPath, String className) {
    try {
      CodeInspector inspector = new CodeInspector(appPath);
      ClassSubject clazz = inspector.clazz(className);
      assertTrue(clazz.isPresent());
      return clazz.getDexClass();
    } catch (IOException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  protected DexEncodedMethod getMethod(Path appPath, MethodSignature signature) {
    try {
      CodeInspector inspector = new CodeInspector(appPath);
      return getMethod(inspector, signature);
    } catch (IOException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  protected DexEncodedMethod getMethod(CodeInspector inspector, MethodSignature signature) {
    return getMethod(
        inspector, signature.clazz, signature.returnType, signature.name, signature.parameterTypes);
  }

  protected DexEncodedMethod getMethod(AndroidApp application, MethodSignature signature) {
    return getMethod(application,
        signature.clazz, signature.returnType, signature.name, signature.parameterTypes);
  }

  /**
   * Create an application with one method, and processed that application using R8.
   *
   * Returns the processed method for inspection.
   *
   * @param returnType the return type for the method
   * @param parameters the parameter types for the method
   * @param locals number of locals needed for the application
   * @param instructions instructions for the method
   * @return the processed method for inspection
   */
  public AndroidApp singleMethodApplication(String returnType, List<String> parameters,
      int locals, String... instructions) {
    // Build a one class method.
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);
    builder.addStaticMethod(returnType, DEFAULT_METHOD_NAME, parameters, locals, instructions);

    // Read the one class method as an application.
    return buildApplication(builder);
  }

  private int getNumberOfClassesForResources(Iterable<ProgramResource> resources) {
    int count = 0;
    for (ProgramResource resource : resources) {
      Collection<String> descriptors = resource.getClassDescriptors();
      if (descriptors == null) {
        throw new IllegalStateException("Cannot count classes in application without descriptors.");
      }
      count += descriptors.size();
    }
    return count;
  }

  protected int getNumberOfProgramClasses(AndroidApp application) {
    try {
      return getNumberOfClassesForResources(application.getClassProgramResourcesForTesting())
          + getNumberOfClassesForResources(application.getDexProgramResourcesForTesting());
    } catch (IOException e) {
      return -1;
    }
  }

  protected AppInfo getAppInfo(AndroidApp application) {
    try {
      DexApplication dexApplication = new ApplicationReader(application, new InternalOptions(),
          new Timing("SmaliTest.getAppInfo")).read();
      return new AppInfo(dexApplication);
    } catch (IOException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Create an application with one method, and processed that application using R8.
   *
   * Returns the processed method for inspection.
   *
   * @param returnType the return type for the method
   * @param parameters the parameter types for the method
   * @param locals number of locals needed for the application
   * @param instructions instructions for the method
   * @return the processed method for inspection
   */
  public DexEncodedMethod oneMethodApplication(String returnType, List<String> parameters,
      int locals, String... instructions) {
    InternalOptions options = new InternalOptions();

    // Build a one class application.
    AndroidApp application = singleMethodApplication(
        returnType, parameters, locals, instructions);

    // Process the application with R8.
    AndroidApp processdApplication = processApplication(application);
    assertEquals(1, getNumberOfProgramClasses(processdApplication));

    // Return the processed method for inspection.
    return getMethod(
        processdApplication, DEFAULT_CLASS_NAME, returnType, DEFAULT_METHOD_NAME, parameters);
  }

  public String runArt(AndroidApp application) {
    return runArt(application, DEFAULT_MAIN_CLASS_NAME);
  }

  public String runArt(AndroidApp application, String mainClass) {
    try {
      Path out = temp.getRoot().toPath().resolve("run-art-input.zip");
      // TODO(sgjesse): Pass in a unique temp directory for each run.
      application.writeToZip(out, OutputMode.DexIndexed);
      return ToolHelper.runArtNoVerificationErrors(out.toString(), mainClass);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String runArt(Path path, String mainClass) {
    try {
      return ToolHelper.runArtNoVerificationErrors(path.toString(), mainClass);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void runDex2Oat(AndroidApp application) {
    try {
      Path dexOut = temp.getRoot().toPath().resolve("run-dex2oat-input.zip");
      Path oatFile = temp.getRoot().toPath().resolve("oat-file");
      application.writeToZip(dexOut, OutputMode.DexIndexed);
      ToolHelper.runDex2Oat(dexOut, oatFile);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
