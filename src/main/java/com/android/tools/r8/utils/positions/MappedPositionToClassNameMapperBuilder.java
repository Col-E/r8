// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.positions;

import static com.android.tools.r8.naming.mappinginformation.ResidualSignatureMappingInformation.ResidualFieldSignatureMappingInformation.fromDexField;
import static com.android.tools.r8.utils.positions.PositionUtils.mustHaveResidualDebugInfo;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.OutlineCallerPosition;
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
import com.android.tools.r8.naming.mappinginformation.ResidualSignatureMappingInformation;
import com.android.tools.r8.naming.mappinginformation.ResidualSignatureMappingInformation.ResidualMethodSignatureMappingInformation;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation.RemoveInnerFramesAction;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation.ThrowsCondition;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.OneShotCollectionConsumer;
import com.android.tools.r8.utils.OriginalSourceFiles;
import com.android.tools.r8.utils.Pair;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Function;

public class MappedPositionToClassNameMapperBuilder {

  private static final int MAX_LINE_NUMBER = 65535;
  private static final String PRUNED_INLINED_CLASS_OBFUSCATED_PREFIX = "R8$$REMOVED$$CLASS$$";

  private final OriginalSourceFiles originalSourceFiles;
  private final AppView<?> appView;

  private final ClassNameMapper.Builder classNameMapperBuilder;
  private final Map<DexMethod, OutlineFixupBuilder> outlinesToFix = new IdentityHashMap<>();
  private final Map<DexType, String> prunedInlinedClasses = new IdentityHashMap<>();

  private final CardinalPositionRangeAllocator cardinalRangeCache =
      PositionRangeAllocator.createCardinalPositionRangeAllocator();
  private final NonCardinalPositionRangeAllocator nonCardinalRangeCache =
      PositionRangeAllocator.createNonCardinalPositionRangeAllocator();
  private final int maxGap;

  private MappedPositionToClassNameMapperBuilder(
      AppView<?> appView, OriginalSourceFiles originalSourceFiles) {
    this.appView = appView;
    this.originalSourceFiles = originalSourceFiles;
    classNameMapperBuilder = ClassNameMapper.builder();
    classNameMapperBuilder.setCurrentMapVersion(
        appView.options().getMapFileVersion().toMapVersionMappingInformation());
    maxGap = appView.options().lineNumberOptimization.isOn() ? 1000 : 0;
  }

  public static String getPrunedInlinedClassObfuscatedPrefix() {
    return PRUNED_INLINED_CLASS_OBFUSCATED_PREFIX;
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
    IntBox counter = new IntBox();
    prunedEntries.sort(Entry.comparingByKey());
    prunedEntries.forEach(
        entry -> {
          DexType holder = entry.getKey();
          assert appView.appInfo().definitionForWithoutExistenceAssert(holder) == null
              || !appView.appInfo().definitionForWithoutExistenceAssert(holder).isProgramClass();
          String typeName = holder.toSourceString();
          String sourceFile = entry.getValue();
          // We have to pick a right-hand side destination that does not overlap with an existing
          // mapping.
          String renamedName;
          do {
            renamedName = PRUNED_INLINED_CLASS_OBFUSCATED_PREFIX + counter.getAndIncrement();
          } while (classNameMapperBuilder.hasMapping(renamedName));
          classNameMapperBuilder
              .classNamingBuilder(
                  renamedName, typeName, com.android.tools.r8.position.Position.UNKNOWN)
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
                CompilerSynthesizedMappingInformation.getInstance(), Unreachable::raise);
      }
      return this;
    }

    @SuppressWarnings("ReferenceEquality")
    private MappedPositionToClassNamingBuilder addFields() {
      MapVersion mapFileVersion = appView.options().getMapFileVersion();
      clazz.forEachField(
          dexEncodedField -> {
            DexField dexField = dexEncodedField.getReference();
            DexField originalField = appView.graphLens().getOriginalFieldSignature(dexField);
            DexField residualField =
                appView.getNamingLens().lookupField(dexField, appView.dexItemFactory());
            if (residualField.name != originalField.name
                || residualField.getType() != originalField.getType()
                || originalField.holder != originalType) {
              FieldSignature originalSignature =
                  FieldSignature.fromDexField(originalField, originalField.holder != originalType);
              FieldSignature residualSignature = FieldSignature.fromDexField(residualField);
              MemberNaming memberNaming = new MemberNaming(originalSignature, residualSignature);
              if (ResidualSignatureMappingInformation.isSupported(mapFileVersion)
                  && !originalSignature.type.equals(residualSignature.type)) {
                memberNaming.addMappingInformation(fromDexField(residualField), Unreachable::raise);
              }
              if (dexEncodedField.isD8R8Synthesized()) {
                memberNaming.addMappingInformation(
                    CompilerSynthesizedMappingInformation.getInstance(), Unreachable::raise);
              }
              getBuilder().addMemberEntry(memberNaming);
            }
          });
      return this;
    }

    @SuppressWarnings("ReferenceEquality")
    public MappedPositionToClassNamingBuilder addMappedPositions(
        ProgramMethod method,
        List<MappedPosition> mappedPositions,
        PositionRemapper positionRemapper,
        boolean canUseDexPc) {
      DexEncodedMethod definition = method.getDefinition();
      DexMethod originalMethod =
          appView.graphLens().getOriginalMethodSignatureForMapping(method.getReference());
      MethodSignature originalSignature =
          MethodSignature.fromDexMethod(originalMethod, originalMethod.holder != originalType);

      OneShotCollectionConsumer<MappingInformation> methodSpecificMappingInformation =
          OneShotCollectionConsumer.wrap(new ArrayList<>());
      // We only do global synthetic classes when using names from the library. For such classes it
      // is important that we do not filter out stack frames since they could appear from concrete
      // classes in the library. Additionally, this is one place where it is helpful for developers
      // to also get reported synthesized frames since stubbing can change control-flow and
      // exceptions.
      if (isD8R8Synthesized(method, mappedPositions)
          && !appView.getSyntheticItems().isGlobalSyntheticClass(method.getHolder())) {
        methodSpecificMappingInformation.add(CompilerSynthesizedMappingInformation.getInstance());
      }

      DexMethod residualMethod =
          appView.getNamingLens().lookupMethod(method.getReference(), appView.dexItemFactory());

      MapVersion mapFileVersion = appView.options().getMapFileVersion();
      if (isIdentityMapping(
          mapFileVersion,
          mappedPositions,
          methodSpecificMappingInformation,
          residualMethod,
          originalMethod,
          originalType)) {
        assert appView.options().lineNumberOptimization.isOff()
            || hasAtMostOnePosition(appView, definition)
            || appView.isCfByteCodePassThrough(definition);
        return this;
      }
      MethodSignature residualSignature = MethodSignature.fromDexMethod(residualMethod);

      if (ResidualSignatureMappingInformation.isSupported(mapFileVersion)
          && (!originalSignature.type.equals(residualSignature.type)
              || !Arrays.equals(originalSignature.parameters, residualSignature.parameters))) {
        methodSpecificMappingInformation.add(
            ResidualMethodSignatureMappingInformation.fromDexMethod(residualMethod));
      }

      MemberNaming memberNaming = new MemberNaming(originalSignature, residualSignature);
      getBuilder().addMemberEntry(memberNaming);

      // Add simple "a() -> b" mapping if we won't have any other with concrete line numbers
      if (mappedPositions.isEmpty()) {
        MappedRange range =
            getBuilder().addMappedRange(null, originalSignature, null, residualSignature.getName());
        methodSpecificMappingInformation.consume(
            info -> range.addMappingInformation(info, Unreachable::raise));
        return this;
      }

      Map<DexMethod, MethodSignature> signatures = new IdentityHashMap<>();
      signatures.put(originalMethod, originalSignature);
      Function<DexMethod, MethodSignature> getOriginalMethodSignature =
          m ->
              signatures.computeIfAbsent(
                  m,
                  key -> {
                    DexType holder = key.holder;
                    boolean withQualifiedName =
                        !holder.isIdenticalTo(clazz.getType())
                            && !holder.isIdenticalTo(originalType);
                    return MethodSignature.fromDexMethod(m, withQualifiedName);
                  });

      // Check if mapped position is an outline
      DexMethod outlineMethod = getOutlineMethod(mappedPositions.get(0).getPosition());
      if (outlineMethod != null) {
        outlinesToFix
            .computeIfAbsent(
                outlineMethod,
                outline -> new OutlineFixupBuilder(computeMappedMethod(outline, appView)))
            .setMappedPositionsOutline(mappedPositions);
        methodSpecificMappingInformation.add(OutlineMappingInformation.builder().build());
      }

      mappedPositions.sort(Comparator.comparing(MappedPosition::getObfuscatedLine));

      Map<OutlineCallerPosition, MappedRange> outlineCallerPositions = new LinkedHashMap<>();

      // Update memberNaming with the collected positions, merging multiple positions into a
      // single region whenever possible.
      for (int i = 0; i < mappedPositions.size(); /* updated in body */ ) {
        MappedPosition firstMappedPosition = mappedPositions.get(i);
        int j = i + 1;
        MappedPosition lastMappedPosition = firstMappedPosition;
        MappedPositionRange mappedPositionRange = MappedPositionRange.SINGLE_LINE;
        for (; j < mappedPositions.size(); j++) {
          // Break if this position cannot be merged with lastPosition.
          MappedPosition currentMappedPosition = mappedPositions.get(j);
          mappedPositionRange =
              mappedPositionRange.canAddNextMappingToRange(
                  lastMappedPosition, currentMappedPosition, maxGap);
          // Note that currentPosition.caller and lastPosition.class must be deep-compared since
          // multiple inlining passes lose the canonical property of the positions.
          Position currentPosition = currentMappedPosition.getPosition();
          Position lastPosition = lastMappedPosition.getPosition();
          if (mappedPositionRange.isOutOfRange()
              // Check if inline positions has changed
              || currentPosition.getMethod() != lastPosition.getMethod()
              || !Objects.equals(
                  currentPosition.getCallerPosition(), lastPosition.getCallerPosition())
              // Check if outline positions has changed
              || !Objects.equals(
                  currentPosition.getOutlineCallee(), lastPosition.getOutlineCallee())
              || !Objects.equals(
                  currentPosition.getOutlinePositions(), lastPosition.getOutlinePositions())) {
            break;
          }
          lastMappedPosition = currentMappedPosition;
        }
        Range obfuscatedRange =
            nonCardinalRangeCache.get(
                firstMappedPosition.getObfuscatedLine(), lastMappedPosition.getObfuscatedLine());

        Position firstPosition = firstMappedPosition.getPosition();
        Position lastPosition = lastMappedPosition.getPosition();

        Range originalRange =
            nonCardinalRangeCache.get(firstPosition.getLine(), lastPosition.getLine());

        MappedRange lastMappedRange =
            getMappedRangesForPosition(
                appView,
                getOriginalMethodSignature,
                getBuilder(),
                firstPosition,
                residualSignature,
                obfuscatedRange,
                originalRange,
                prunedInlinedClasses,
                cardinalRangeCache);
        // firstPosition will contain a potential outline caller.
        if (firstPosition.isOutlineCaller()) {
          outlineCallerPositions.putIfAbsent(firstPosition.asOutlineCaller(), lastMappedRange);
        }
        methodSpecificMappingInformation.consume(
            info -> lastMappedRange.addMappingInformation(info, Unreachable::raise));
        i = j;
      }
      IntBox maxPc = new IntBox(ListUtils.last(mappedPositions).getObfuscatedLine());
      for (Map.Entry<OutlineCallerPosition, MappedRange> outlinePositionEntry :
          outlineCallerPositions.entrySet()) {
        Int2IntMap positionMap = new Int2IntArrayMap();
        outlinePositionEntry
            .getKey()
            .getOutlinePositions()
            .forEach(
                (line, position) -> {
                  int placeHolderLineToBeFixed;
                  if (canUseDexPc) {
                    placeHolderLineToBeFixed = maxPc.get() + line + 1;
                  } else {
                    placeHolderLineToBeFixed =
                        positionRemapper.createRemappedPosition(position).getSecond().getLine();
                  }
                  positionMap.put((int) line, placeHolderLineToBeFixed);
                  MappedRange lastRange =
                      getMappedRangesForPosition(
                          appView,
                          getOriginalMethodSignature,
                          getBuilder(),
                          position,
                          residualSignature,
                          nonCardinalRangeCache.get(
                              placeHolderLineToBeFixed, placeHolderLineToBeFixed),
                          nonCardinalRangeCache.get(position.getLine(), position.getLine()),
                          prunedInlinedClasses,
                          cardinalRangeCache);
                  maxPc.set(lastRange.minifiedRange.to);
                });
        outlinesToFix
            .computeIfAbsent(
                outlinePositionEntry.getKey().getOutlineCallee(),
                outline -> new OutlineFixupBuilder(computeMappedMethod(outline, appView)))
            .addMappedRangeForOutlineCallee(outlinePositionEntry.getValue(), positionMap);
      }
      assert mappedPositions.size() <= 1
          || getBuilder().hasNoOverlappingRangesForSignature(residualSignature);
      return this;
    }

    private boolean isD8R8Synthesized(ProgramMethod method, List<MappedPosition> mappedPositions) {
      return method.getDefinition().isD8R8Synthesized()
          || (!mappedPositions.isEmpty()
              && mappedPositions.get(0).getPosition().isD8R8Synthesized());
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
        Position position,
        MethodSignature residualSignature,
        Range obfuscatedRange,
        Range originalLine,
        Map<DexType, String> prunedInlineHolder,
        CardinalPositionRangeAllocator cardinalRangeCache) {
      MappedRange lastMappedRange = null;
      int inlineFramesCount = -1;
      do {
        if (position.isD8R8Synthesized() && position.hasCallerPosition()) {
          position = position.getCallerPosition();
          continue;
        }
        inlineFramesCount += 1;
        DexType holderType = position.getMethod().getHolderType();
        String prunedClassSourceFileInfo = appView.getPrunedClassSourceFileInfo(holderType);
        if (prunedClassSourceFileInfo != null) {
          String originalValue = prunedInlineHolder.put(holderType, prunedClassSourceFileInfo);
          assert originalValue == null || originalValue.equals(prunedClassSourceFileInfo);
        }
        lastMappedRange =
            classNamingBuilder.addMappedRange(
                obfuscatedRange,
                getOriginalMethodSignature.apply(position.getMethod()),
                inlineFramesCount == 0
                    ? originalLine
                    : cardinalRangeCache.get(
                        Math.max(position.getLine(), 0)), // Prevent against "no-position".
                residualSignature.getName());
        if (position.isRemoveInnerFramesIfThrowingNpe()) {
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
        position = position.getCallerPosition();
      } while (position != null);
      assert lastMappedRange != null;
      return lastMappedRange;
    }

    private DexMethod getOutlineMethod(Position mappedPosition) {
      if (mappedPosition.isOutline()) {
        return mappedPosition.getMethod();
      }
      Position caller = mappedPosition.getCallerPosition();
      if (caller == null) {
        return null;
      }
      Position outermostCaller = caller.getOutermostCaller();
      return outermostCaller.isOutline() ? outermostCaller.getMethod() : null;
    }

    @SuppressWarnings("ReferenceEquality")
    private boolean isIdentityMapping(
        MapVersion mapFileVersion,
        List<MappedPosition> mappedPositions,
        OneShotCollectionConsumer<MappingInformation> methodMappingInfo,
        DexMethod residualMethod,
        DexMethod originalMethod,
        DexType originalType) {
      if (ResidualSignatureMappingInformation.isSupported(mapFileVersion)) {
        // Don't emit pure identity mappings.
        return mappedPositions.isEmpty()
            && methodMappingInfo.isEmpty()
            && originalMethod == residualMethod;
      } else {
        // Don't emit pure identity mappings.
        return mappedPositions.isEmpty()
            && methodMappingInfo.isEmpty()
            && residualMethod.getName() == originalMethod.name
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
        MappedPosition lastPosition, MappedPosition currentPosition, int maxGap) {
      if (isOutOfRange()) {
        return this;
      }
      // We allow for ranges being mapped to the same line but not to other ranges:
      //   1:10:void foo():42:42 -> a
      // is OK since retrace(a(:7)) = 42, however, the following is not OK:
      //   1:10:void foo():42:43 -> a
      // since retrace(a(:7)) = 49, which is not correct.
      int currentOriginalLine = currentPosition.getPosition().getLine();
      int lastOriginalLine = lastPosition.getPosition().getLine();
      boolean hasSameRightHandSide = lastOriginalLine == currentOriginalLine;
      if (hasSameRightHandSide) {
        boolean hasSameLeftHandSide =
            lastPosition.getObfuscatedLine() == currentPosition.getObfuscatedLine();
        if (isSameDelta()) {
          return hasSameLeftHandSide ? SAME_DELTA : OUT_OF_RANGE;
        }
        return (hasSameLeftHandSide && isSingleLine()) ? SINGLE_LINE : RANGE_TO_SINGLE;
      }
      if (isRangeToSingle()) {
        // We cannot recover a delta encoding if we have had range to single encoding.
        return OUT_OF_RANGE;
      }
      int gap = currentPosition.getObfuscatedLine() - lastPosition.getObfuscatedLine();
      boolean gapLessThanMaxGap = gap >= 0 && gap <= maxGap;
      boolean sameDelta =
          currentOriginalLine - lastOriginalLine
              == currentPosition.getObfuscatedLine() - lastPosition.getObfuscatedLine();
      return (gapLessThanMaxGap && sameDelta) ? SAME_DELTA : OUT_OF_RANGE;
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
        // TODO(b/296195931): Reenable assert.
        // assert false : "Mapped outline positions is empty";
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
        if (mappedPosition.getPosition().getLine() == originalPosition) {
          return mappedPosition.getObfuscatedLine();
        }
      }
      return MINIFIED_POSITION_REMOVED;
    }
  }
}
