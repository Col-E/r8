// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class CompileDumpBase {

  static void setEnableExperimentalMissingLibraryApiModeling(
      Object builder, boolean enableMissingLibraryApiModeling) {
    getReflectiveBuilderMethod(
            builder, "setEnableExperimentalMissingLibraryApiModeling", boolean.class)
        .accept(new Object[] {enableMissingLibraryApiModeling});
  }

  static void setAndroidPlatformBuild(Object builder, boolean androidPlatformBuild) {
    getReflectiveBuilderMethod(builder, "setAndroidPlatformBuild", boolean.class)
        .accept(new Object[] {androidPlatformBuild});
  }

  static void addArtProfilesForRewriting(Object builder, Map<Path, Path> artProfileFiles) {
    try {
      Class<?> artProfileProviderClass =
          Class.forName("com.android.tools.r8.profile.art.ArtProfileProvider");
      Class<?> artProfileConsumerClass =
          Class.forName("com.android.tools.r8.profile.art.ArtProfileConsumer");
      artProfileFiles.forEach(
          (artProfile, residualArtProfile) ->
              getReflectiveBuilderMethod(
                      builder,
                      "addArtProfileForRewriting",
                      artProfileProviderClass,
                      artProfileConsumerClass)
                  .accept(
                      new Object[] {
                        createArtProfileProvider(artProfile),
                        createResidualArtProfileConsumer(residualArtProfile)
                      }));
    } catch (ClassNotFoundException e) {
      // Ignore.
    }
  }

  static void addStartupProfileProviders(Object builder, List<Path> startupProfileFiles) {
    getReflectiveBuilderMethod(builder, "addStartupProfileProviders", Collection.class)
        .accept(new Object[] {createStartupProfileProviders(startupProfileFiles)});
  }

  static Object createArtProfileProvider(Path artProfile) {
    Object[] artProfileProvider = new Object[1];
    boolean found =
        callReflectiveUtilsMethod(
            "createArtProfileProviderFromDumpFile",
            new Class<?>[] {Path.class},
            fn -> artProfileProvider[0] = fn.apply(new Object[] {artProfile}));
    if (!found) {
      System.out.println(
          "Unable to add art profiles as input. "
              + "Method createArtProfileProviderFromDumpFile() was not found.");
      return null;
    }
    System.out.println(artProfileProvider[0]);
    return artProfileProvider[0];
  }

  static Object createResidualArtProfileConsumer(Path residualArtProfile) {
    Object[] residualArtProfileConsumer = new Object[1];
    boolean found =
        callReflectiveUtilsMethod(
            "createResidualArtProfileConsumerFromDumpFile",
            new Class<?>[] {Path.class},
            fn -> residualArtProfileConsumer[0] = fn.apply(new Object[] {residualArtProfile}));
    if (!found) {
      System.out.println(
          "Unable to add art profiles as input. "
              + "Method createResidualArtProfileConsumerFromDumpFile() was not found.");
      return null;
    }
    System.out.println(residualArtProfileConsumer[0]);
    return residualArtProfileConsumer[0];
  }

  static Collection<Object> createStartupProfileProviders(List<Path> startupProfileFiles) {
    List<Object> startupProfileProviders = new ArrayList<>();
    for (Path startupProfileFile : startupProfileFiles) {
      boolean found =
          callReflectiveUtilsMethod(
              "createStartupProfileProviderFromDumpFile",
              new Class<?>[] {Path.class},
              fn -> startupProfileProviders.add(fn.apply(new Object[] {startupProfileFile})));
      if (!found) {
        System.out.println(
            "Unable to add startup profiles as input. "
                + "Method createStartupProfileProviderFromDumpFile() was not found.");
        break;
      }
    }
    return startupProfileProviders;
  }

  static Consumer<Object[]> getReflectiveBuilderMethod(
      Object builder, String setter, Class<?>... parameters) {
    try {
      Method declaredMethod = builder.getClass().getMethod(setter, parameters);
      return args -> {
        try {
          declaredMethod.invoke(builder, args);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      };
    } catch (NoSuchMethodException e) {
      System.out.println(setter + " is not available on the compiledump version.");
      // The option is not available so we just return an empty consumer
      return args -> {};
    }
  }

  static boolean callReflectiveUtilsMethod(
      String methodName, Class<?>[] parameters, Consumer<Function<Object[], Object>> fnConsumer) {
    Class<?> utilsClass;
    try {
      utilsClass = Class.forName("com.android.tools.r8.utils.CompileDumpUtils");
    } catch (ClassNotFoundException e) {
      return false;
    }

    Method declaredMethod;
    try {
      declaredMethod = utilsClass.getDeclaredMethod(methodName, parameters);
    } catch (NoSuchMethodException e) {
      return false;
    }

    fnConsumer.accept(
        args -> {
          try {
            return declaredMethod.invoke(null, args);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    return true;
  }

  @SuppressWarnings({"CatchAndPrintStackTrace", "DefaultCharset"})
  // We cannot use StringResource since this class is added to the class path and has access only
  // to the public APIs.
  static String readAllBytesJava7(Path filePath) {
    String content = "";

    try {
      content = new String(Files.readAllBytes(filePath));
    } catch (IOException e) {
      e.printStackTrace();
    }

    return content;
  }
}
