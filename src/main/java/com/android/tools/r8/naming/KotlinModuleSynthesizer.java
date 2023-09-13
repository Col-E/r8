// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.FunctionUtils.ignoreArgument;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.kotlin.KotlinClassLevelInfo;
import com.android.tools.r8.kotlin.KotlinMultiFileClassPartInfo;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kotlinx.metadata.jvm.KotlinModuleMetadata.Writer;

/**
 * The kotlin module synthesizer will scan through all file facades and multiclass files to figure
 * out the residual package destination of these and bucket them into their original module names.
 */
public class KotlinModuleSynthesizer {

  private final AppView<?> appView;

  public KotlinModuleSynthesizer(AppView<?> appView) {
    this.appView = appView;
  }

  public boolean isKotlinModuleFile(DataEntryResource file) {
    return file.getName().endsWith(".kotlin_module");
  }

  @SuppressWarnings("MixedMutabilityReturnType")
  public List<DataEntryResource> synthesizeKotlinModuleFiles() {
    Map<String, KotlinModuleInfoBuilder> kotlinModuleBuilders = new HashMap<>();
    // We cannot obtain the module name for multi class file facades. But, we can for a multi class
    // part obtain both the module name and the multi class facade. We therefore iterate over all
    // classes to find a multi class facade -> module name mapping, and then iterate over all
    // classes to assign multi class facades to modules.
    Map<String, String> moduleNamesForParts = new HashMap<>();
    for (DexProgramClass clazz : appView.app().classesWithDeterministicOrder()) {
      KotlinClassLevelInfo kotlinInfo = clazz.getKotlinInfo();
      if (kotlinInfo.isFileFacade()) {
        kotlinModuleBuilders
            .computeIfAbsent(
                kotlinInfo.asFileFacade().getModuleName(),
                moduleName -> new KotlinModuleInfoBuilder(moduleName, appView))
            .add(clazz);
      } else if (kotlinInfo.isMultiFileClassPart()) {
        KotlinMultiFileClassPartInfo kotlinMultiFileClassPartInfo =
            kotlinInfo.asMultiFileClassPart();
        moduleNamesForParts.computeIfAbsent(
            kotlinMultiFileClassPartInfo.getFacadeClassName(),
            ignored -> kotlinMultiFileClassPartInfo.getModuleName());
        kotlinModuleBuilders
            .computeIfAbsent(
                kotlinMultiFileClassPartInfo.getModuleName(),
                moduleName -> new KotlinModuleInfoBuilder(moduleName, appView))
            .add(clazz);
      }
    }
    for (DexProgramClass clazz : appView.app().classesWithDeterministicOrder()) {
      KotlinClassLevelInfo kotlinInfo = clazz.getKotlinInfo();
      if (kotlinInfo.isMultiFileFacade()) {
        DexType originalType = appView.graphLens().getOriginalType(clazz.getType());
        if (originalType != null) {
          String moduleNameForPart = moduleNamesForParts.get(originalType.toBinaryName());
          // If module name is null then we did not find any multi class file parts and therefore
          // do not have to do anything for the facade.
          if (moduleNameForPart != null) {
            KotlinModuleInfoBuilder kotlinModuleInfoBuilder =
                kotlinModuleBuilders.get(moduleNameForPart);
            assert kotlinModuleInfoBuilder != null;
            kotlinModuleInfoBuilder.add(clazz);
          }
        }
      }
    }
    if (kotlinModuleBuilders.isEmpty()) {
      return Collections.emptyList();
    }
    List<DataEntryResource> newResources = new ArrayList<>();
    kotlinModuleBuilders.values().forEach(builder -> builder.build().ifPresent(newResources::add));
    return newResources;
  }

  private static class KotlinModuleInfoBuilder {

    private final String moduleName;
    private final GraphLens graphLens;
    private final NamingLens namingLens;
    private final DexItemFactory factory;

    private final Map<String, List<String>> newFacades = new HashMap<>();
    private final Map<String, List<Pair<String, String>>> multiClassFacadeOriginalToRenamed =
        new LinkedHashMap<>();
    private final Map<String, List<String>> multiClassPartToOriginal = new HashMap<>();
    private final Box<int[]> metadataVersion = new Box<>();

    private KotlinModuleInfoBuilder(String moduleName, AppView<?> appView) {
      this.moduleName = moduleName;
      this.graphLens = appView.graphLens();
      this.namingLens = appView.getNamingLens();
      this.factory = appView.dexItemFactory();
    }

    private void add(DexProgramClass clazz) {
      DexType classType = clazz.getType();
      KotlinClassLevelInfo classKotlinInfo = clazz.getKotlinInfo();
      DexType renamedType = namingLens.lookupType(classType, factory);
      if (classKotlinInfo.isFileFacade()) {
        metadataVersion.computeIfAbsent(classKotlinInfo::getMetadataVersion);
        newFacades
            .computeIfAbsent(renamedType.getPackageName(), ignoreArgument(ArrayList::new))
            .add(renamedType.toBinaryName());
      } else if (classKotlinInfo.isMultiFileFacade()) {
        metadataVersion.computeIfAbsent(classKotlinInfo::getMetadataVersion);
        DexType originalType = graphLens.getOriginalType(classType);
        multiClassFacadeOriginalToRenamed
            .computeIfAbsent(renamedType.getPackageName(), ignoreArgument(ArrayList::new))
            .add(Pair.create(originalType.toBinaryName(), renamedType.toBinaryName()));
      } else {
        assert classKotlinInfo.isMultiFileClassPart();
        metadataVersion.computeIfAbsent(classKotlinInfo::getMetadataVersion);
        KotlinMultiFileClassPartInfo classPart = classKotlinInfo.asMultiFileClassPart();
        multiClassPartToOriginal
            .computeIfAbsent(classPart.getFacadeClassName(), ignoreArgument(ArrayList::new))
            .add(renamedType.toBinaryName());
      }
    }

    private Optional<DataEntryResource> build() {
      // If multiClassParts are non empty but multiFileFacade is, then we have no place to put
      // the parts anyway, so we can just return empty.
      if (newFacades.isEmpty() && multiClassFacadeOriginalToRenamed.isEmpty()) {
        return Optional.empty();
      }
      assert metadataVersion.isSet();
      List<String> packagesSorted = new ArrayList<>(newFacades.keySet());
      for (String newPackage : multiClassFacadeOriginalToRenamed.keySet()) {
        if (!newFacades.containsKey(newPackage)) {
          packagesSorted.add(newPackage);
        }
      }
      Collections.sort(packagesSorted);
      Writer writer = new Writer();
      for (String newPackage : packagesSorted) {
        // Calling other visitors than visitPackageParts are currently not supported.
        // https://github.com/JetBrains/kotlin/blob/master/libraries/kotlinx-metadata/
        //  jvm/src/kotlinx/metadata/jvm/KotlinModuleMetadata.kt#L70
        Map<String, String> newMultiFiles = new LinkedHashMap<>();
        multiClassFacadeOriginalToRenamed
            .getOrDefault(newPackage, Collections.emptyList())
            .forEach(
                pair -> {
                  String originalName = pair.getFirst();
                  String rewrittenName = pair.getSecond();
                  multiClassPartToOriginal
                      .getOrDefault(originalName, Collections.emptyList())
                      .forEach(
                          classPart -> {
                            newMultiFiles.put(classPart, rewrittenName);
                          });
                });
        writer.visitPackageParts(
            newPackage,
            newFacades.getOrDefault(newPackage, Collections.emptyList()),
            newMultiFiles);
      }
      return Optional.of(
          DataEntryResource.fromBytes(
              writer.write(metadataVersion.get()),
              "META-INF/" + moduleName + ".kotlin_module",
              Origin.unknown()));
    }
  }
}
