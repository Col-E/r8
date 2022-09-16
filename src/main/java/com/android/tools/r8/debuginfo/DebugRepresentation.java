// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.CollectionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.LebUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.positions.LineNumberOptimizer;
import com.android.tools.r8.utils.positions.PositionUtils;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.ints.IntIterators;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class DebugRepresentation {

  public static final int NO_PC_ENCODING = -1;
  public static final int ALWAYS_PC_ENCODING = Integer.MAX_VALUE;

  public interface DebugRepresentationPredicate {

    int getDexPcEncodingCutoff(ProgramMethod method);
  }

  public static DebugRepresentationPredicate none(InternalOptions options) {
    assert !options.canUseDexPc2PcAsDebugInformation();
    return method -> NO_PC_ENCODING;
  }

  public static DebugRepresentationPredicate fromFiles(
      List<VirtualFile> files, InternalOptions options) {
    if (!options.canUseDexPc2PcAsDebugInformation()) {
      return none(options);
    }
    if (options.canUseNativeDexPcInsteadOfDebugInfo()) {
      return method -> ALWAYS_PC_ENCODING;
    }
    // TODO(b/220999985): Avoid the need to maintain a class-to-file map.
    Map<DexProgramClass, VirtualFile> classMapping = new IdentityHashMap<>();
    for (VirtualFile file : files) {
      if (options.testing.debugRepresentationCallback != null) {
        options.testing.debugRepresentationCallback.accept(file.getDebugRepresentation());
      }
      file.classes().forEach(c -> classMapping.put(c, file));
    }
    return method -> {
      if (!isPcCandidate(method.getDefinition(), options)) {
        return NO_PC_ENCODING;
      }
      VirtualFile file = classMapping.get(method.getHolder());
      DebugRepresentation cutoffs = file.getDebugRepresentation();
      int maxPc = cutoffs.getDexPcEncodingCutoff(method);
      assert maxPc == NO_PC_ENCODING
          || verifyLastExecutableInstructionWithinBound(
              method.getDefinition().getCode().asDexCode(), maxPc);
      return maxPc;
    };
  }

  private final Int2ReferenceMap<ConversionInfo> paramToInfo;

  private DebugRepresentation(Int2ReferenceMap<ConversionInfo> paramToInfo) {
    this.paramToInfo = paramToInfo;
  }

  public static void computeForFile(AppView<?> appView, VirtualFile file) {
    InternalOptions options = appView.options();
    if (!options.canUseDexPc2PcAsDebugInformation()
        || options.canUseNativeDexPcInsteadOfDebugInfo()) {
      return;
    }
    // First collect all of the per-pc costs
    // (the sum of the normal debug info for all methods sharing the same max pc and param count.)
    Int2ReferenceMap<CostSummary> paramCountToCosts = new Int2ReferenceOpenHashMap<>();
    for (DexProgramClass clazz : file.classes()) {
      IdentityHashMap<DexString, List<ProgramMethod>> overloads =
          LineNumberOptimizer.groupMethodsByRenamedName(appView, clazz);
      for (List<ProgramMethod> methods : overloads.values()) {
        if (methods.size() != 1) {
          // Never use PC info for overloaded methods. They need distinct lines to disambiguate.
          continue;
        }
        ProgramMethod method = methods.get(0);
        DexEncodedMethod definition = method.getDefinition();
        if (!isPcCandidate(definition, options)) {
          continue;
        }
        DexCode code = definition.getCode().asDexCode();
        DexDebugInfo debugInfo = code.getDebugInfo();
        if (debugInfo == null) {
          // If debug info is "null" then the cost of representing it as normal events will be a
          // single default event to ensure its source file content is active.
          debugInfo =
              DexDebugInfo.createEventBasedInfoForMethodWithoutDebugInfo(
                  definition, options.dexItemFactory());
        }
        assert debugInfo.getParameterCount() == method.getParameters().size();
        DexInstruction lastInstruction = getLastExecutableInstruction(code);
        if (lastInstruction == null) {
          continue;
        }
        int lastPc = lastInstruction.getOffset();
        int debugInfoCost = estimatedDebugInfoSize(debugInfo);
        paramCountToCosts
            .computeIfAbsent(debugInfo.getParameterCount(), CostSummary::new)
            .addCost(lastPc, debugInfoCost);
      }
    }
    // Second compute the cost of converting to a pc encoding.
    Int2ReferenceMap<ConversionInfo> conversions =
        new Int2ReferenceOpenHashMap<>(paramCountToCosts.size());
    paramCountToCosts.forEach(
        (param, summary) -> conversions.put(param, summary.computeConversionCosts(appView)));
    // The result is stored on the virtual files for thread safety.
    // TODO(b/220999985): Consider just passing this to the line number optimizer once fixed.
    file.setDebugRepresentation(new DebugRepresentation(conversions));
  }

  private int getDexPcEncodingCutoff(ProgramMethod method) {
    if (paramToInfo.isEmpty()) {
      // This should only be the case if the method has overloads and thus *cannot* use pc encoding.
      assert verifyMethodHasOverloads(method);
      return NO_PC_ENCODING;
    }
    DexCode code = method.getDefinition().getCode().asDexCode();
    int paramCount = method.getParameters().size();
    assert code.getDebugInfo() == null || code.getDebugInfo().getParameterCount() == paramCount;
    ConversionInfo conversionInfo = paramToInfo.get(paramCount);
    if (conversionInfo == null || !conversionInfo.hasConversions()) {
      // We expect all methods calling this to have computed conversion info.
      assert conversionInfo != null;
      return NO_PC_ENCODING;
    }
    DexInstruction lastInstruction = getLastExecutableInstruction(code);
    if (lastInstruction == null) {
      return NO_PC_ENCODING;
    }
    int maxPc = lastInstruction.getOffset();
    return conversionInfo.getConversionPointFor(maxPc);
  }

  private boolean verifyMethodHasOverloads(ProgramMethod method) {
    assert 1
        < IterableUtils.size(method.getHolder().methods(m -> m.getName().equals(method.getName())));
    return true;
  }

  @Override
  public String toString() {
    return toString(false);
  }

  public String toString(boolean printCostSummary) {
    List<ConversionInfo> sorted = new ArrayList<>(paramToInfo.values());
    sorted.sort(Comparator.comparing(i -> i.paramCount));
    return StringUtils.join("\n", sorted, c -> c.toString(printCostSummary));
  }

  private static boolean isPcCandidate(DexEncodedMethod method, InternalOptions options) {
    if (!method.hasCode() || !method.getCode().isDexCode()) {
      return false;
    }
    return PositionUtils.mustHaveResidualDebugInfo(options, method);
  }

  /** Cost information for debug info at a given PC. */
  private static class PcCostInfo {
    // PC point for which the information pertains to.
    final int pc;

    // Normal debug-info encoding cost.
    int cost = 0;

    // Number of methods this information pertains to.
    int methods = 0;

    @Override
    public String toString() {
      return "pc="
          + pc
          + ", cost="
          + cost
          + ", methods="
          + methods
          + ", saved="
          + (cost - pc)
          + ", overhead="
          + getExpansionOverhead(pc, methods, cost);
    }

    public PcCostInfo(int pc) {
      assert pc >= 0;
      this.pc = pc;
    }

    void add(int cost) {
      assert cost >= 0;
      methods++;
      this.cost += cost;
    }
  }

  /** Conversion judgment up to a given PC bound (the lower bound is context dependent). */
  private static class PcConversionInfo {
    static final PcConversionInfo NO_CONVERSION = new PcConversionInfo(-1, false, 0, 0);

    // PC bound for which the information pertains to.
    final int pc;

    // Judgment of whether the methods in this grouping should be converted to pc2pc encoding.
    final boolean converted;

    // Info for debugging.
    private final int methods;
    private final int normalCost;

    public PcConversionInfo(int pc, boolean converted, int methods, int normalCost) {
      this.pc = pc;
      this.converted = converted;
      this.methods = methods;
      this.normalCost = normalCost;
    }

    @Override
    public String toString() {
      return "pc="
          + pc
          + ", converted="
          + converted
          + ", cost="
          + normalCost
          + ", methods="
          + methods
          + ", saved="
          + (normalCost - pc)
          + ", overhead="
          + getExpansionOverhead(pc, methods, normalCost);
    }
  }

  // A pc2pc stream is approximately one event more than the pc.
  private static int pcEventCount(int pc) {
    return pc + 1;
  }

  /**
   * Figure for the overhead that the pc2pc encoding can result in.
   *
   * <p>This overhead is not in the encoding size but rather if the encoding is expanded at each
   * method referencing the shared PC encoding.
   */
  private static int getExpansionOverhead(int currentPc, int methodCount, int normalCost) {
    long expansion = ((long) pcEventCount(currentPc)) * methodCount;
    long cost = expansion - normalCost;
    return cost > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cost;
  }

  private static boolean isWithinExpansionThreshold(
      int threshold, int currentPc, int methodCount, int normalCost) {
    // A negative threshold denotes unbounded.
    if (threshold < 0) {
      return true;
    }
    return getExpansionOverhead(currentPc, methodCount, normalCost) <= threshold;
  }

  /** The summary of normal costs for all debug info with a particular parameter size. */
  private static class CostSummary {
    private final int paramCount;

    // Values for the normal encoding costs per-pc.
    private Int2ReferenceMap<PcCostInfo> pcToCost = new Int2ReferenceOpenHashMap<>();

    private CostSummary(int paramCount) {
      assert paramCount >= 0;
      this.paramCount = paramCount;
    }

    private void addCost(int pc, int cost) {
      assert pc >= 0;
      pcToCost.computeIfAbsent(pc, PcCostInfo::new).add(cost);
    }

    private static class ConversionState {
      Int2ReferenceSortedMap<PcConversionInfo> groups = new Int2ReferenceAVLTreeMap<>();
      PcConversionInfo converted = PcConversionInfo.NO_CONVERSION;
      int flushedPc = 0;
      int unconvertedPc = 0;
      int normalCost = 0;
      int methods = 0;

      void reset() {
        converted = PcConversionInfo.NO_CONVERSION;
        unconvertedPc = 0;
        normalCost = 0;
        methods = 0;
      }

      void add(PcCostInfo costInfo) {
        methods += costInfo.methods;
        normalCost += costInfo.cost;
      }

      void flush() {
        if (flushedPc < converted.pc) {
          groups.put(converted.pc, converted);
          flushedPc = converted.pc;
        }
        if (flushedPc < unconvertedPc) {
          if (0 < flushedPc && !groups.get(flushedPc).converted) {
            groups.remove(flushedPc);
          }
          PcConversionInfo unconverted =
              new PcConversionInfo(unconvertedPc, false, methods, normalCost);
          groups.put(unconvertedPc, unconverted);
          flushedPc = unconvertedPc;
        }
        reset();
      }

      void update(int currentPc, boolean convertToPc) {
        if (convertToPc) {
          converted = new PcConversionInfo(currentPc, true, methods, normalCost);
          unconvertedPc = 0;
        } else {
          unconvertedPc = currentPc;
        }
      }

      public Int2ReferenceSortedMap<PcConversionInfo> getFinalConversions() {
        // If there is only a single group check it is actually a converted range.
        if (groups.size() > 1
            || (groups.size() == 1 && groups.values().iterator().next().converted)) {
          return groups;
        }
        return null;
      }
    }

    private ConversionInfo computeConversionCosts(AppView<?> appView) {
      int threshold = appView.options().testing.pcBasedDebugEncodingOverheadThreshold;
      boolean forcePcBasedEncoding = appView.options().testing.forcePcBasedEncoding;
      assert !pcToCost.isEmpty();
      // Iterate in ascending order as the point conversion cost is the sum of the preceding costs.
      int[] sortedPcs = new int[pcToCost.size()];
      IntIterators.unwrap(pcToCost.keySet().iterator(), sortedPcs);
      Arrays.sort(sortedPcs);
      ConversionState state = new ConversionState();
      for (int currentPc : sortedPcs) {
        PcCostInfo current = pcToCost.get(currentPc);
        assert currentPc == current.pc;
        // Don't extend the conversion group as it could potentially become too large if expanded.
        // Any pending conversion can be flushed now.
        if (!isWithinExpansionThreshold(
            threshold,
            currentPc,
            state.methods + current.methods,
            state.normalCost + current.cost)) {
          state.flush();
        }
        state.add(current);
        int costToConvert = pcEventCount(currentPc);
        boolean canExpand =
            isWithinExpansionThreshold(threshold, currentPc, state.methods, state.normalCost);
        state.update(
            currentPc, canExpand && (forcePcBasedEncoding || state.normalCost > costToConvert));
      }
      state.flush();
      return new ConversionInfo(
          paramCount,
          state.getFinalConversions(),
          appView.options().testing.debugRepresentationCallback != null ? this : null);
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("params:").append(paramCount).append('\n');
      Collection<Integer> keys = CollectionUtils.sort(pcToCost.keySet(), Integer::compareTo);
      for (int key : keys) {
        builder.append(pcToCost.get(key)).append('\n');
      }
      return builder.toString();
    }
  }

  /** The computed conversion points all debug info with a particular parameter size. */
  private static class ConversionInfo {
    private final int paramCount;
    private final Int2ReferenceSortedMap<PcConversionInfo> conversions;

    // For debugging purposes.
    private final CostSummary costSummary;

    private ConversionInfo(
        int paramCount,
        Int2ReferenceSortedMap<PcConversionInfo> conversions,
        CostSummary costSummary) {
      assert paramCount >= 0;
      this.paramCount = paramCount;
      this.conversions = conversions;
      this.costSummary = costSummary;
    }

    boolean hasConversions() {
      return conversions != null;
    }

    int getConversionPointFor(int pc) {
      Int2ReferenceSortedMap<PcConversionInfo> tailMap = conversions.tailMap(pc);
      if (tailMap.isEmpty()) {
        return -1;
      }
      int pcGroupBound = tailMap.firstIntKey();
      PcConversionInfo entryUpToIncludingMax = conversions.get(pcGroupBound);
      if (entryUpToIncludingMax.converted) {
        assert pcGroupBound == entryUpToIncludingMax.pc;
        return pcGroupBound;
      }
      return -1;
    }

    @Override
    public String toString() {
      return toString(false);
    }

    public String toString(boolean printCostSummaries) {
      StringBuilder builder = new StringBuilder();
      builder.append("params:").append(paramCount).append('\n');
      if (conversions != null) {
        for (PcConversionInfo group : conversions.values()) {
          builder.append(group).append('\n');
        }
      } else {
        builder.append(" no conversions").append('\n');
      }
      if (printCostSummaries && costSummary != null) {
        builder.append("Cost summaries:\n");
        builder.append(costSummary);
      }
      return builder.toString();
    }
  }

  public static boolean verifyLastExecutableInstructionWithinBound(DexCode code, int maxPc) {
    DexInstruction lastExecutableInstruction = getLastExecutableInstruction(code);
    int offset = lastExecutableInstruction.getOffset();
    assert offset <= maxPc;
    return true;
  }

  public static DexInstruction getLastExecutableInstruction(DexCode code) {
    return getLastExecutableInstruction(code.instructions);
  }

  public static DexInstruction getLastExecutableInstruction(DexInstruction[] instructions) {
    DexInstruction lastInstruction = null;
    for (DexInstruction instruction : instructions) {
      if (!instruction.isPayload()) {
        lastInstruction = instruction;
      }
    }
    return lastInstruction;
  }

  private static int estimatedDebugInfoSize(DexDebugInfo info) {
    if (info.isPcBasedInfo()) {
      return info.asPcBasedInfo().estimatedWriteSize();
    }
    // Each event is a single byte so we take the event length as the cost of the info.
    // Note that the line number optimizer could reduce the line diffs such that deltas are
    // smaller, but this is likely a very good estimate of the actual cost.
    int parameterCount = info.getParameterCount();
    int eventCount = info.asEventBasedInfo().events.length;
    // Size: startline(0) + paramCount + null-array[paramCount] + eventCount + 1(end-event)
    return LebUtils.sizeAsUleb128(0)
        + LebUtils.sizeAsUleb128(parameterCount)
        + LebUtils.sizeAsUleb128(0) * parameterCount
        + eventCount
        + 1;
  }
}
