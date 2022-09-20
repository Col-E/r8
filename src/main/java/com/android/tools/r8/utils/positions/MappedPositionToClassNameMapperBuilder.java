// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.positions;

import static com.android.tools.r8.utils.positions.PositionUtils.mustHaveResidualDebugInfo;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexDebugInfoForSingleLineMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ClassNaming;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.naming.PositionRangeAllocator;
import com.android.tools.r8.naming.PositionRangeAllocator.CardinalPositionRangeAllocator;
import com.android.tools.r8.naming.PositionRangeAllocator.NonCardinalPositionRangeAllocator;
import com.android.tools.r8.naming.Range;
import com.android.tools.r8.naming.mappinginformation.CompilerSynthesizedMappingInformation;
import com.android.tools.r8.naming.mappinginformation.FileNameInformation;
import com.android.tools.r8.naming.mappinginformation.MappingInformation;
import com.android.tools.r8.naming.mappinginformation.OutlineCallsiteMappingInformation;
import com.android.tools.r8.naming.mappinginformation.OutlineMappingInformation;
import com.android.tools.r8.naming.mappinginformation.ResidualSignatureMappingInformation.ResidualMethodSignatureMappingInformation;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation.RemoveInnerFramesAction;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation.ThrowsCondition;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.OriginalSourceFiles;
import com.android.tools.r8.utils.Pair;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Function;

public class MappedPositionToClassNameMapperBuilder {

  private static final int MAX_LINE_NUMBER = 65535;

  private final OriginalSourceFiles originalSourceFiles;
  private final AppView<?> appView;

  private final ClassNameMapper.Builder classNameMapperBuilder;
  private final Map<DexMethod, OutlineFixupBuilder> outlinesToFix = new IdentityHashMap<>();
  private final Map<DexType, String> prunedInlinedClasses = new IdentityHashMap<>();

  private final CardinalPositionRangeAllocator cardinalRangeCache =
      PositionRangeAllocator.createCardinalPositionRangeAllocator();
  private final NonCardinalPositionRangeAllocator nonCardinalRangeCache =
      PositionRangeAllocator.createNonCardinalPositionRangeAllocator();

  private MappedPositionToClassNameMapperBuilder(
      AppView<?> appView, OriginalSourceFiles originalSourceFiles) {
    this.appView = appView;
    this.originalSourceFiles = originalSourceFiles;
    classNameMapperBuilder = ClassNameMapper.builder();
    classNameMapperBuilder.setCurrentMapVersion(
        appView.options().getMapFileVersion().toMapVersionMappingInformation());
  }

  public static int getMaxLineNumber() {
    return MAX_LINE_NUMBER;
  }

  public static MappedPositionToClassNameMapperBuilder builder(
      AppView<?> appView, OriginalSourceFiles originalSourceFiles) {
    return new MappedPositionToClassNameMapperBuilder(appView, originalSourceFiles);
  }

  public ClassNameMapper build() {
    // Fixup all outline positions
    outlinesToFix.values().forEach(OutlineFixupBuilder::fixup);
    addSourceFileLinesForPrunedClasses();
    return classNameMapperBuilder.build();
  }

  private void addSourceFileLinesForPrunedClasses() {
    // Add all pruned inline classes to the mapping to recover source files.
    List<Entry<DexType, String>> prunedEntries = new ArrayList<>(prunedInlinedClasses.entrySet());
    prunedEntries.sort(Entry.comparingByKey());
    prunedEntries.forEach(
        entry -> {
          DexType holder = entry.getKey();
          assert appView.appInfo().definitionForWithoutExistenceAssert(holder) == null;
          String typeName = holder.toSourceString();
          String sourceFile = entry.getValue();
          classNameMapperBuilder
              .classNamingBuilder(
                  typeName, typeName, com.android.tools.r8.position.Position.UNKNOWN)
              .addMappingInformation(FileNameInformation.build(sourceFile), Unreachable::raise);
        });
  }

  public MappedPositionToClassNamingBuilder addClassNaming(DexProgramClass clazz) {
    DexType originalType = appView.graphLens().getOriginalType(clazz.type);
    DexString renamedDescriptor = appView.getNamingLens().lookupDescriptor(clazz.getType());
    return new MappedPositionToClassNamingBuilder(
            clazz, originalType, DescriptorUtils.descriptorToJavaType(renamedDescriptor.toString()))
        .addSourceFile(originalSourceFiles)
        .addSynthetic(appView.getSyntheticItems())
        .addFields();
  }

  public class MappedPositionToClassNamingBuilder {

    private final DexProgramClass clazz;
    private final DexType originalType;
    private final String renamedName;

    private ClassNaming.Builder builder;

    private MappedPositionToClassNamingBuilder(
        DexProgramClass clazz, DexType originalType, String renamedName) {
      this.clazz = clazz;
      this.originalType = originalType;
      this.renamedName = renamedName;
      // If the class is renamed trigger an entry in the builder.
      if (!originalType.toSourceString().equals(renamedName)) {
        getBuilder();
      }
    }

    public MappedPositionToClassNamingBuilder addSourceFile(
        OriginalSourceFiles originalSourceFiles) {
      // Check if source file should be added to the map
      DexString originalSourceFile = originalSourceFiles.getOriginalSourceFile(clazz);
      if (originalSourceFile != null) {
        getBuilder()
            .addMappingInformation(
                FileNameInformation.build(originalSourceFile.toSourceString()), Unreachable::raise);
      }
      return this;
    }

    public MappedPositionToClassNamingBuilder addSynthetic(SyntheticItems syntheticItems) {
      if (syntheticItems.isSyntheticClass(clazz)) {
        getBuilder()
            .addMappingInformation(
                CompilerSynthesizedMappingInformation.builder().build(), Unreachable::raise);
      }
      return this;
    }

    private MappedPositionToClassNamingBuilder addFields() {
      clazz.forEachField(
          dexEncodedField -> {
            DexField dexField = dexEncodedField.getReference();
            DexField originalField = appView.graphLens().getOriginalFieldSignature(dexField);
            DexField residualField =
                appView.getNamingLens().lookupField(dexField, appView.dexItemFactory());
            if (residualField.name != originalField.name || originalField.holder != originalType) {
              FieldSignature originalSignature =
                  FieldSignature.fromDexField(originalField, originalField.holder != originalType);
              FieldSignature residualSignature = FieldSignature.fromDexField(residualField);
              MemberNaming memberNaming = new MemberNaming(originalSignature, residualSignature);
              getBuilder().addMemberEntry(memberNaming, residualSignature);
            }
          });
      return this;
    }

    public MappedPositionToClassNamingBuilder addMappedPositions(
        ProgramMethod method,
        List<MappedPosition> mappedPositions,
        PositionRemapper positionRemapper,
        boolean canUseDexPc) {
      DexEncodedMethod definition = method.getDefinition();
      DexMethod originalMethod =
          appView.graphLens().getOriginalMethodSignature(method.getReference());
      MethodSignature originalSignature =
          MethodSignature.fromDexMethod(originalMethod, originalMethod.holder != originalType);

      List<MappingInformation> methodMappingInfo = new ArrayList<>();
      if (method.getDefinition().isD8R8Synthesized()) {
        methodMappingInfo.add(CompilerSynthesizedMappingInformation.builder().build());
      }

      DexMethod residualMethod =
          appView.getNamingLens().lookupMethod(method.getReference(), appView.dexItemFactory());

      MapVersion mapFileVersion = appView.options().getMapFileVersion();
      if (isIdentityMapping(
          mapFileVersion,
          mappedPositions,
          methodMappingInfo,
          method,
          residualMethod.getName(),
          originalMethod,
          originalType)) {
        assert appView.options().lineNumberOptimization == LineNumberOptimization.OFF
            || hasAtMostOnePosition(appView, definition)
            || appView.isCfByteCodePassThrough(definition);
        return this;
      }
      if (mapFileVersion.isGreaterThan(MapVersion.MAP_VERSION_2_1)
          && originalMethod != method.getReference()
          && !appView.graphLens().isSimpleRenaming(residualMethod)) {
        methodMappingInfo.add(
            ResidualMethodSignatureMappingInformation.fromDexMethod(residualMethod));
      }
      MethodSignature residualSignature = MethodSignature.fromDexMethod(residualMethod);

      MemberNaming memberNaming = new MemberNaming(originalSignature, residualSignature);
      getBuilder().addMemberEntry(memberNaming, residualSignature);

      // Add simple "a() -> b" mapping if we won't have any other with concrete line numbers
      if (mappedPositions.isEmpty()) {
        MappedRange range =
            getBuilder().addMappedRange(null, originalSignature, null, residualSignature.getName());
        methodMappingInfo.forEach(info -> range.addMappingInformation(info, Unreachable::raise));
        return this;
      }

      Map<DexMethod, MethodSignature> signatures = new IdentityHashMap<>();
      signatures.put(originalMethod, originalSignature);
      Function<DexMethod, MethodSignature> getOriginalMethodSignature =
          m ->
              signatures.computeIfAbsent(
                  m, key -> MethodSignature.fromDexMethod(m, m.holder != clazz.getType()));

      // Check if mapped position is an outline
      DexMethod outlineMethod = getOutlineMethod(mappedPositions.get(0));
      if (outlineMethod != null) {
        outlinesToFix
            .computeIfAbsent(
                outlineMethod,
                outline -> new OutlineFixupBuilder(computeMappedMethod(outline, appView)))
            .setMappedPositionsOutline(mappedPositions);
        methodMappingInfo.add(OutlineMappingInformation.builder().build());
      }

      // Update memberNaming with the collected positions, merging multiple positions into a
      // single region whenever possible.
      for (int i = 0; i < mappedPositions.size(); /* updated in body */ ) {
        MappedPosition firstPosition = mappedPositions.get(i);
        int j = i + 1;
        MappedPosition lastPosition = firstPosition;
        MappedPositionRange mappedPositionRange = MappedPositionRange.SINGLE_LINE;
        for (; j < mappedPositions.size(); j++) {
          // Break if this position cannot be merged with lastPosition.
          MappedPosition currentPosition = mappedPositions.get(j);
          mappedPositionRange =
              mappedPositionRange.canAddNextMappingToRange(lastPosition, currentPosition);
          // Note that currentPosition.caller and lastPosition.class must be deep-compared since
          // multiple inlining passes lose the canonical property of the positions.
          if (currentPosition.getMethod() != lastPosition.getMethod()
              || mappedPositionRange.isOutOfRange()
              || !Objects.equals(currentPosition.getCaller(), lastPosition.getCaller())
              // Break when we see a mapped outline
              || currentPosition.getOutlineCallee() != null
              // Ensure that we break when we start iterating with an outline caller again.
              || firstPosition.getOutlineCallee() != null) {
            break;
          }
          // The mapped positions are not guaranteed to be in order, so maintain first and last
          // position.
          if (firstPosition.getObfuscatedLine() > currentPosition.getObfuscatedLine()) {
            firstPosition = currentPosition;
          }
          if (lastPosition.getObfuscatedLine() < currentPosition.getObfuscatedLine()) {
            lastPosition = currentPosition;
          }
        }
        Range obfuscatedRange;
        if (definition.getCode().isDexCode()
            && definition.getCode().asDexCode().getDebugInfo()
                == DexDebugInfoForSingleLineMethod.getInstance()) {
          assert firstPosition.getOriginalLine() == lastPosition.getOriginalLine();
          obfuscatedRange = nonCardinalRangeCache.get(0, MAX_LINE_NUMBER);
        } else {
          obfuscatedRange =
              nonCardinalRangeCache.get(
                  firstPosition.getObfuscatedLine(), lastPosition.getObfuscatedLine());
        }
        MappedRange lastMappedRange =
            getMappedRangesForPosition(
                appView,
                getOriginalMethodSignature,
                getBuilder(),
                firstPosition.getMethod(),
                residualSignature,
                obfuscatedRange,
                nonCardinalRangeCache.get(
                    firstPosition.getOriginalLine(), lastPosition.getOriginalLine()),
                firstPosition.getCaller(),
                prunedInlinedClasses,
                cardinalRangeCache);
        for (MappingInformation info : methodMappingInfo) {
          lastMappedRange.addMappingInformation(info, Unreachable::raise);
        }
        // firstPosition will contain a potential outline caller.
        if (firstPosition.getOutlineCallee() != null) {
          Int2IntMap positionMap = new Int2IntArrayMap();
          int maxPc = ListUtils.last(mappedPositions).getObfuscatedLine();
          firstPosition
              .getOutlinePositions()
              .forEach(
                  (line, position) -> {
                    int placeHolderLineToBeFixed;
                    if (canUseDexPc) {
                      placeHolderLineToBeFixed = maxPc + line + 1;
                    } else {
                      placeHolderLineToBeFixed =
                          positionRemapper.createRemappedPosition(position).getSecond().getLine();
                    }
                    positionMap.put((int) line, placeHolderLineToBeFixed);
                    getMappedRangesForPosition(
                        appView,
                        getOriginalMethodSignature,
                        getBuilder(),
                        position.getMethod(),
                        residualSignature,
                        nonCardinalRangeCache.get(
                            placeHolderLineToBeFixed, placeHolderLineToBeFixed),
                        nonCardinalRangeCache.get(position.getLine(), position.getLine()),
                        position.getCallerPosition(),
                        prunedInlinedClasses,
                        cardinalRangeCache);
                  });
          outlinesToFix
              .computeIfAbsent(
                  firstPosition.getOutlineCallee(),
                  outline -> new OutlineFixupBuilder(computeMappedMethod(outline, appView)))
              .addMappedRangeForOutlineCallee(lastMappedRange, positionMap);
        }
        i = j;
      }
      return this;
    }

    private MethodReference computeMappedMethod(DexMethod current, AppView<?> appView) {
      NamingLens namingLens = appView.getNamingLens();
      DexMethod renamedMethodSignature =
          namingLens.lookupMethod(
              appView.graphLens().getRenamedMethodSignature(current), appView.dexItemFactory());
      return renamedMethodSignature.asMethodReference();
    }

    private MappedRange getMappedRangesForPosition(
        AppView<?> appView,
        Function<DexMethod, MethodSignature> getOriginalMethodSignature,
        ClassNaming.Builder classNamingBuilder,
        DexMethod method,
        MethodSignature residualSignature,
        Range obfuscatedRange,
        Range originalLine,
        Position caller,
        Map<DexType, String> prunedInlineHolder,
        CardinalPositionRangeAllocator cardinalRangeCache) {
      MappedRange lastMappedRange =
          classNamingBuilder.addMappedRange(
              obfuscatedRange,
              getOriginalMethodSignature.apply(method),
              originalLine,
              residualSignature.getName());
      int inlineFramesCount = 0;
      while (caller != null) {
        inlineFramesCount += 1;
        String prunedClassSourceFileInfo =
            appView.getPrunedClassSourceFileInfo(method.getHolderType());
        if (prunedClassSourceFileInfo != null) {
          String originalValue =
              prunedInlineHolder.put(method.getHolderType(), prunedClassSourceFileInfo);
          assert originalValue == null || originalValue.equals(prunedClassSourceFileInfo);
        }
        lastMappedRange =
            classNamingBuilder.addMappedRange(
                obfuscatedRange,
                getOriginalMethodSignature.apply(caller.getMethod()),
                cardinalRangeCache.get(
                    Math.max(caller.getLine(), 0)), // Prevent against "no-position".
                residualSignature.getName());
        if (caller.isRemoveInnerFramesIfThrowingNpe()) {
          lastMappedRange.addMappingInformation(
              RewriteFrameMappingInformation.builder()
                  .addCondition(
                      ThrowsCondition.create(
                          Reference.classFromDescriptor(
                              appView.dexItemFactory().npeDescriptor.toString())))
                  .addRewriteAction(RemoveInnerFramesAction.create(inlineFramesCount))
                  .build(),
              Unreachable::raise);
        }
        caller = caller.getCallerPosition();
      }
      return lastMappedRange;
    }

    private DexMethod getOutlineMethod(MappedPosition mappedPosition) {
      if (mappedPosition.isOutline()) {
        return mappedPosition.getMethod();
      }
      Position caller = mappedPosition.getCaller();
      if (caller == null) {
        return null;
      }
      Position outermostCaller = caller.getOutermostCaller();
      return outermostCaller.isOutline() ? outermostCaller.getMethod() : null;
    }

    private boolean isIdentityMapping(
        MapVersion mapFileVersion,
        List<MappedPosition> mappedPositions,
        List<MappingInformation> methodMappingInfo,
        ProgramMethod method,
        DexString obfuscatedNameDexString,
        DexMethod originalMethod,
        DexType originalType) {
      if (mapFileVersion.isGreaterThan(MapVersion.MAP_VERSION_2_1)) {
        // Don't emit pure identity mappings.
        return mappedPositions.isEmpty()
            && methodMappingInfo.isEmpty()
            && originalMethod == method.getReference();
      } else {
        // Don't emit pure identity mappings.
        return mappedPositions.isEmpty()
            && methodMappingInfo.isEmpty()
            && obfuscatedNameDexString == originalMethod.name
            && originalMethod.holder == originalType;
      }
    }

    private boolean hasAtMostOnePosition(AppView<?> appView, DexEncodedMethod definition) {
      if (!mustHaveResidualDebugInfo(appView.options(), definition)) {
        return true;
      }
      Code code = definition.getCode();
      // If the dex code is a single PC code then that also qualifies as having at most one
      // position.
      return code.isDexCode() && code.asDexCode().instructions.length == 1;
    }

    private ClassNaming.Builder getBuilder() {
      if (builder == null) {
        builder =
            classNameMapperBuilder.classNamingBuilder(
                renamedName,
                originalType.toSourceString(),
                com.android.tools.r8.position.Position.UNKNOWN);
      }
      return builder;
    }
  }

  private enum MappedPositionRange {
    // Single line represent a mapping on the form X:X:<method>:Y:Y.
    SINGLE_LINE,
    // Range to single line allows for a range on the left hand side, X:X':<method>:Y:Y
    RANGE_TO_SINGLE,
    // Same delta is when we have a range on both sides and the delta (line mapping between them)
    // is the same: X:X':<method>:Y:Y' and delta(X,X') = delta(Y,Y')
    SAME_DELTA,
    // Out of range encodes a mapping range that cannot be encoded.
    OUT_OF_RANGE;

    private boolean isSingleLine() {
      return this == SINGLE_LINE;
    }

    private boolean isRangeToSingle() {
      return this == RANGE_TO_SINGLE;
    }

    private boolean isOutOfRange() {
      return this == OUT_OF_RANGE;
    }

    private boolean isSameDelta() {
      return this == SAME_DELTA;
    }

    public MappedPositionRange canAddNextMappingToRange(
        MappedPosition lastPosition, MappedPosition currentPosition) {
      if (isOutOfRange()) {
        return this;
      }
      // We allow for ranges being mapped to the same line but not to other ranges:
      //   1:10:void foo():42:42 -> a
      // is OK since retrace(a(:7)) = 42, however, the following is not OK:
      //   1:10:void foo():42:43 -> a
      // since retrace(a(:7)) = 49, which is not correct.
      boolean hasSameRightHandSide =
          lastPosition.getOriginalLine() == currentPosition.getOriginalLine();
      if (hasSameRightHandSide) {
        if (isSameDelta()) {
          return OUT_OF_RANGE;
        }
        boolean hasSameLeftHandSide =
            lastPosition.getObfuscatedLine() == currentPosition.getObfuscatedLine();
        return (hasSameLeftHandSide && isSingleLine()) ? SINGLE_LINE : RANGE_TO_SINGLE;
      }
      if (isRangeToSingle()) {
        // We cannot recover a delta encoding if we have had range to single encoding.
        return OUT_OF_RANGE;
      }
      boolean sameDelta =
          currentPosition.getOriginalLine() - lastPosition.getOriginalLine()
              == currentPosition.getObfuscatedLine() - lastPosition.getObfuscatedLine();
      return sameDelta ? SAME_DELTA : OUT_OF_RANGE;
    }
  }

  private static class OutlineFixupBuilder {

    private static final int MINIFIED_POSITION_REMOVED = -1;

    private final MethodReference outlineMethod;
    private List<MappedPosition> mappedOutlinePositions = null;
    private final List<Pair<MappedRange, Int2IntMap>> mappedOutlineCalleePositions =
        new ArrayList<>();

    private OutlineFixupBuilder(MethodReference outlineMethod) {
      this.outlineMethod = outlineMethod;
    }

    public void setMappedPositionsOutline(List<MappedPosition> mappedPositionsOutline) {
      this.mappedOutlinePositions = mappedPositionsOutline;
    }

    public void addMappedRangeForOutlineCallee(
        MappedRange mappedRangeForOutline, Int2IntMap calleePositions) {
      mappedOutlineCalleePositions.add(Pair.create(mappedRangeForOutline, calleePositions));
    }

    public void fixup() {
      if (mappedOutlinePositions == null || mappedOutlineCalleePositions.isEmpty()) {
        assert mappedOutlinePositions != null : "Mapped outline positions is null";
        assert false : "Mapped outline positions is empty";
        return;
      }
      for (Pair<MappedRange, Int2IntMap> mappingInfo : mappedOutlineCalleePositions) {
        MappedRange mappedRange = mappingInfo.getFirst();
        Int2IntMap positions = mappingInfo.getSecond();
        Int2IntSortedMap map = new Int2IntLinkedOpenHashMap();
        positions.forEach(
            (outlinePosition, calleePosition) -> {
              int minifiedLinePosition =
                  getMinifiedLinePosition(outlinePosition, mappedOutlinePositions);
              if (minifiedLinePosition != MINIFIED_POSITION_REMOVED) {
                map.put(minifiedLinePosition, (int) calleePosition);
              }
            });
        mappedRange.addMappingInformation(
            OutlineCallsiteMappingInformation.create(map, outlineMethod), Unreachable::raise);
      }
    }

    private int getMinifiedLinePosition(
        int originalPosition, List<MappedPosition> mappedPositions) {
      for (MappedPosition mappedPosition : mappedPositions) {
        if (mappedPosition.getOriginalLine() == originalPosition) {
          return mappedPosition.getObfuscatedLine();
        }
      }
      return MINIFIED_POSITION_REMOVED;
    }
  }
}
