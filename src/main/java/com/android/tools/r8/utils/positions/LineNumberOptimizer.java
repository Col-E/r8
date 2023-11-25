// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.positions;

import static com.android.tools.r8.utils.positions.PositionUtils.mustHaveResidualDebugInfo;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.debuginfo.DebugRepresentation.DebugRepresentationPredicate;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.MappingComposeException;
import com.android.tools.r8.naming.MappingComposer;
import com.android.tools.r8.naming.ProguardMapSupplier;
import com.android.tools.r8.naming.ProguardMapSupplier.ProguardMapId;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import com.android.tools.r8.naming.mappinginformation.ResidualSignatureMappingInformation;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.CfLineToMethodMapper;
import com.android.tools.r8.utils.OriginalSourceFiles;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.positions.MappedPositionToClassNameMapperBuilder.MappedPositionToClassNamingBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

public class LineNumberOptimizer {

  public static ProguardMapId runAndWriteMap(
      AndroidApp inputApp,
      AppView<?> appView,
      Timing timing,
      OriginalSourceFiles originalSourceFiles,
      DebugRepresentationPredicate representation) {
    assert appView.options().mapConsumer != null;
    if (shouldEmitOriginalMappingFile(appView)) {
      appView.options().reporter.warning(new NotSupportedMapVersionForMappingComposeDiagnostic());
      timing.begin("Write proguard map");
      ProguardMapId proguardMapId =
          ProguardMapSupplier.create(appView.appInfo().app().getProguardMap(), appView.options())
              .writeProguardMap();
      timing.end();
      return proguardMapId;
    }
    // When line number optimization is turned off the identity mapping for line numbers is
    // used. We still run the line number optimizer to collect line numbers and inline frame
    // information for the mapping file.
    timing.begin("Line number remapping");
    ClassNameMapper mapper = run(appView, inputApp, originalSourceFiles, representation);
    timing.end();
    if (appView.options().mappingComposeOptions().generatedClassNameMapperConsumer != null) {
      appView.options().mappingComposeOptions().generatedClassNameMapperConsumer.accept(mapper);
    }
    if (appView.options().mappingComposeOptions().enableExperimentalMappingComposition
        && appView.appInfo().app().getProguardMap() != null) {
      timing.begin("Proguard map composition");
      try {
        mapper =
            ClassNameMapper.mapperFromStringWithPreamble(
                MappingComposer.compose(
                    appView.options(), appView.appInfo().app().getProguardMap(), mapper));
      } catch (IOException | MappingComposeException e) {
        throw new CompilationError(e.getMessage(), e);
      }
      timing.end();
    }
    timing.begin("Write proguard map");
    ProguardMapId mapId = ProguardMapSupplier.create(mapper, appView.options()).writeProguardMap();
    timing.end();
    return mapId;
  }

  private static boolean shouldEmitOriginalMappingFile(AppView<?> appView) {
    if (!appView.options().mappingComposeOptions().enableExperimentalMappingComposition
        || appView.appInfo().app().getProguardMap() == null) {
      return false;
    }
    MapVersionMappingInformation mapVersionInfo =
        appView.appInfo().app().getProguardMap().getFirstMapVersionInformation();
    if (mapVersionInfo == null) {
      return true;
    }
    MapVersion newMapVersion = mapVersionInfo.getMapVersion();
    return !ResidualSignatureMappingInformation.isSupported(newMapVersion)
        || newMapVersion.isUnknown();
  }

  @SuppressWarnings("ReferenceEquality")
  public static ClassNameMapper run(
      AppView<?> appView,
      AndroidApp inputApp,
      OriginalSourceFiles originalSourceFiles,
      DebugRepresentationPredicate representation) {
    // For finding methods in kotlin files based on SourceDebugExtensions, we use a line method map.
    // We create it here to ensure it is only reading class files once.
    // TODO(b/220999985): Make this threaded per virtual file. Possibly pull the kotlin line mapping
    //  onto main thread before threading.
    CfLineToMethodMapper cfLineToMethodMapper = new CfLineToMethodMapper(inputApp);

    PositionToMappedRangeMapper positionToMappedRangeMapper =
        PositionToMappedRangeMapper.create(appView);

    MappedPositionToClassNameMapperBuilder builder =
        MappedPositionToClassNameMapperBuilder.builder(appView, originalSourceFiles);

    // Collect which files contain which classes that need to have their line numbers optimized.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      IdentityHashMap<DexString, List<ProgramMethod>> methodsByRenamedName =
          groupMethodsByRenamedName(appView, clazz);

      MappedPositionToClassNamingBuilder classNamingBuilder = builder.addClassNaming(clazz);

      // Process methods ordered by renamed name.
      List<DexString> renamedMethodNames = new ArrayList<>(methodsByRenamedName.keySet());
      renamedMethodNames.sort(DexString::compareTo);
      for (DexString methodName : renamedMethodNames) {
        List<ProgramMethod> methods = methodsByRenamedName.get(methodName);
        if (methods.size() > 1) {
          // If there are multiple methods with the same name (overloaded) then sort them for
          // deterministic behaviour: the algorithm will assign new line numbers in this order.
          // Methods with different names can share the same line numbers, that's why they don't
          // need to be sorted.
          // If we are compiling to DEX we will try to not generate overloaded names. This saves
          // space by allowing more debug-information to be canonicalized. If we have overloaded
          // methods, we either did not rename them, we renamed them according to a supplied map or
          // they may be bridges for interface methods with covariant return types.
          sortMethods(methods);
          assert verifyMethodsAreKeptDirectlyOrIndirectly(appView, methods);
        }

        PositionRemapper positionRemapper =
            PositionRemapper.getPositionRemapper(appView, cfLineToMethodMapper);

        for (ProgramMethod method : methods) {
          DexEncodedMethod definition = method.getDefinition();
          if (methodName == method.getName()
              && !mustHaveResidualDebugInfo(appView.options(), definition)
              && !definition.isD8R8Synthesized()
              && methods.size() <= 1) {
            continue;
          }
          positionRemapper.setCurrentMethod(definition);
          List<MappedPosition> mappedPositions;
          int pcEncodingCutoff =
              methods.size() == 1 ? representation.getDexPcEncodingCutoff(method) : -1;
          boolean canUseDexPc = pcEncodingCutoff > 0;
          if (definition.getCode() != null
              && (definition.getCode().isCfCode() || definition.getCode().isDexCode())
              && !appView.isCfByteCodePassThrough(definition)) {
            mappedPositions =
                positionToMappedRangeMapper.getMappedPositions(
                    method, positionRemapper, methods.size() > 1, canUseDexPc, pcEncodingCutoff);
          } else {
            mappedPositions = new ArrayList<>();
          }

          classNamingBuilder.addMappedPositions(
              method, mappedPositions, positionRemapper, canUseDexPc);
        } // for each method of the group
      } // for each method group, grouped by name
    } // for each class

    // Update all the debug-info objects.
    positionToMappedRangeMapper.updateDebugInfoInCodeObjects();

    return builder.build();
  }

  @SuppressWarnings("ComplexBooleanConstant")
  private static boolean verifyMethodsAreKeptDirectlyOrIndirectly(
      AppView<?> appView, List<ProgramMethod> methods) {
    if (appView.options().isGeneratingClassFiles() || !appView.appInfo().hasClassHierarchy()) {
      return true;
    }
    AppInfoWithClassHierarchy appInfo = appView.appInfo().withClassHierarchy();
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    boolean allSeenAreInstanceInitializers = true;
    DexString originalName = null;
    for (ProgramMethod method : methods) {
      // We cannot rename instance initializers.
      if (method.getDefinition().isInstanceInitializer()) {
        assert allSeenAreInstanceInitializers;
        continue;
      }
      allSeenAreInstanceInitializers = false;
      // If the method is pinned, we cannot minify it.
      if (!keepInfo.isMinificationAllowed(method, appView.options())) {
        continue;
      }
      // With desugared library, call-backs names are reserved here.
      if (method.getDefinition().isLibraryMethodOverride().isTrue()) {
        continue;
      }
      // We use the same name for interface names even if it has different types.
      DexProgramClass clazz = appView.definitionForProgramType(method.getHolderType());
      DexClassAndMethod lookupResult =
          appInfo.lookupMaximallySpecificMethod(clazz, method.getReference());
      if (lookupResult == null) {
        // We cannot rename methods we cannot look up.
        continue;
      }
      String errorString = method.getReference().qualifiedName() + " is not kept but is overloaded";
      assert lookupResult.getHolder().isInterface() : errorString;
      // TODO(b/159113601): Reenable assert.
      assert true || originalName == null || originalName.equals(method.getReference().name)
          : errorString;
      originalName = method.getReference().name;
    }
    return true;
  }

  private static int getMethodStartLine(ProgramMethod method) {
    Code code = method.getDefinition().getCode();
    if (code == null) {
      return 0;
    }
    if (code.isDexCode()) {
      DexDebugInfo dexDebugInfo = code.asDexCode().getDebugInfo();
      return dexDebugInfo == null ? 0 : dexDebugInfo.getStartLine();
    } else if (code.isCfCode()) {
      List<CfInstruction> instructions = code.asCfCode().getInstructions();
      for (CfInstruction instruction : instructions) {
        if (!(instruction instanceof CfPosition)) {
          continue;
        }
        return ((CfPosition) instruction).getPosition().getLine();
      }
    }
    return 0;
  }

  // Sort by startline, then DexEncodedMethod.slowCompare.
  // Use startLine = 0 if no debuginfo.
  private static void sortMethods(List<ProgramMethod> methods) {
    methods.sort(
        (lhs, rhs) -> {
          int lhsStartLine = getMethodStartLine(lhs);
          int rhsStartLine = getMethodStartLine(rhs);
          int startLineDiff = lhsStartLine - rhsStartLine;
          if (startLineDiff != 0) return startLineDiff;
          return DexEncodedMethod.slowCompare(lhs.getDefinition(), rhs.getDefinition());
        });
  }

  @SuppressWarnings("UnusedVariable")
  public static IdentityHashMap<DexString, List<ProgramMethod>> groupMethodsByRenamedName(
      AppView<?> appView, DexProgramClass clazz) {
    IdentityHashMap<DexString, List<ProgramMethod>> methodsByRenamedName =
        new IdentityHashMap<>(clazz.getMethodCollection().size());
    for (ProgramMethod programMethod : clazz.programMethods()) {
      // Add method only if renamed, moved, or if it has debug info to map.
      DexEncodedMethod definition = programMethod.getDefinition();
      DexMethod method = programMethod.getReference();
      DexString renamedName = appView.getNamingLens().lookupName(method);
      methodsByRenamedName
          .computeIfAbsent(renamedName, key -> new ArrayList<>())
          .add(programMethod);
    }
    return methodsByRenamedName;
  }
}
