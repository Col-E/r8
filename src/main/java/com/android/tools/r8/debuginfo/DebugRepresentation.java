// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexDebugInfo.PcBasedDebugInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.LebUtils;
import com.android.tools.r8.utils.LineNumberOptimizer;
import com.android.tools.r8.utils.StringUtils;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterators;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class DebugRepresentation {

  public interface DebugRepresentationPredicate {

    boolean useDexPcEncoding(DexProgramClass holder, DexEncodedMethod method);
  }

  public static DebugRepresentationPredicate none(InternalOptions options) {
    assert !options.canUseDexPc2PcAsDebugInformation();
    return (holder, method) -> false;
  }

  public static DebugRepresentationPredicate fromFiles(
      List<VirtualFile> files, InternalOptions options) {
    if (!options.canUseDexPc2PcAsDebugInformation()) {
      return none(options);
    }
    if (options.canUseNativeDexPcInsteadOfDebugInfo() || options.testing.forcePcBasedEncoding) {
      return (holder, method) -> true;
    }
    // TODO(b/220999985): Avoid the need to maintain a class-to-file map.
    Map<DexProgramClass, VirtualFile> classMapping = new IdentityHashMap<>();
    for (VirtualFile file : files) {
      file.classes().forEach(c -> classMapping.put(c, file));
    }
    return (holder, method) -> {
      if (!isPcCandidate(method)) {
        return false;
      }
      VirtualFile file = classMapping.get(holder);
      DebugRepresentation cutoffs = file.getDebugRepresentation();
      return cutoffs.usesPcEncoding(method);
    };
  }

  private final Int2ReferenceMap<CostSummary> paramToInfo;

  private DebugRepresentation(Int2ReferenceMap<CostSummary> paramToInfo) {
    this.paramToInfo = paramToInfo;
  }

  public static void computeForFile(AppView<?> appView, VirtualFile file) {
    InternalOptions options = appView.options();
    if (!options.canUseDexPc2PcAsDebugInformation()
        || options.canUseNativeDexPcInsteadOfDebugInfo()
        || options.testing.forcePcBasedEncoding) {
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
        if (!isPcCandidate(definition)) {
          continue;
        }
        DexCode code = definition.getCode().asDexCode();
        DexDebugInfo debugInfo = code.getDebugInfo();
        DexInstruction lastInstruction = getLastExecutableInstruction(code);
        if (lastInstruction == null) {
          continue;
        }
        int lastPc = lastInstruction.getOffset();
        int debugInfoCost = estimatedDebugInfoSize(debugInfo);
        paramCountToCosts
            .computeIfAbsent(debugInfo.getParameterCount(), DebugRepresentation.CostSummary::new)
            .addCost(lastPc, debugInfoCost);
      }
    }
    // Second compute the cost of converting to a pc encoding.
    paramCountToCosts.forEach((ignored, summary) -> summary.computeConversionCosts());
    // The result is stored on the virtual files for thread safety.
    // TODO(b/220999985): Consider just passing this to the line number optimizer once fixed.
    file.setDebugRepresentation(new DebugRepresentation(paramCountToCosts));
  }

  private boolean usesPcEncoding(DexEncodedMethod method) {
    DexCode code = method.getCode().asDexCode();
    DexDebugInfo debugInfo = code.getDebugInfo();
    int paramCount = debugInfo.getParameterCount();
    CostSummary conversionInfo = paramToInfo.get(paramCount);
    if (conversionInfo.cutoff < 0) {
      return false;
    }
    DexInstruction lastInstruction = getLastExecutableInstruction(code);
    if (lastInstruction == null) {
      return false;
    }
    int maxPc = lastInstruction.getOffset();
    return maxPc <= conversionInfo.cutoff;
  }

  @Override
  public String toString() {
    List<CostSummary> sorted = new ArrayList<>(paramToInfo.values());
    sorted.sort(Comparator.comparing(i -> i.paramCount));
    return StringUtils.join("\n", sorted, CostSummary::toString);
  }

  private static boolean isPcCandidate(DexEncodedMethod method) {
    if (!method.hasCode() || !method.getCode().isDexCode()) {
      return false;
    }
    DexCode code = method.getCode().asDexCode();
    return LineNumberOptimizer.doesContainPositions(code);
  }

  /** The cost of representing normal debug info for all methods with this max pc value. */
  private static class PcNormalCost {

    final int pc;
    int cost;
    int methods;

    public PcNormalCost(int pc) {
      assert pc >= 0;
      this.pc = pc;
    }

    void add(int cost) {
      assert cost >= 0;
      methods++;
      this.cost += cost;
    }
  }

  /** The summary of normal costs for all debug info with a particular parameter size. */
  private static class CostSummary {

    private final int paramCount;

    // Values for the normal encoding costs per-pc.
    private final Int2ReferenceMap<PcNormalCost> pcToCost = new Int2ReferenceOpenHashMap<>();
    private int minPc = Integer.MAX_VALUE;
    private int maxPc = Integer.MIN_VALUE;

    // Values for the conversion costs. These are computed only after all per-pc costs are known.
    private int cutoff;
    private int normalPreCutoffCost;
    private int normalPostCutoffCost;

    private CostSummary(int paramCount) {
      assert paramCount >= 0;
      this.paramCount = paramCount;
    }

    private void addCost(int pc, int cost) {
      assert pc >= 0;
      pcToCost.computeIfAbsent(pc, PcNormalCost::new).add(cost);
      minPc = Math.min(minPc, pc);
      maxPc = Math.max(maxPc, pc);
    }

    private void computeConversionCosts() {
      assert !pcToCost.isEmpty();
      // Point at which it is estimated that conversion to PC-encoding is viable.
      int currentConvertedPc = -1;
      // The normal cost of the part that is viable for conversion (this is just for debugging).
      int normalConvertedCost = 0;
      // The normal cost of the part that is not yet part of the converted range.
      int normalOutstandingCost = 0;
      // Iterate in ascending order as the point conversion cost is the sum of the preceding costs.
      int[] sortedPcs = new int[pcToCost.size()];
      IntIterators.unwrap(pcToCost.keySet().iterator(), sortedPcs);
      Arrays.sort(sortedPcs);
      for (int currentPc : sortedPcs) {
        PcNormalCost pcSummary = pcToCost.get(currentPc);
        // The cost of the debug info unconverted is the sum of the unconverted up to this point.
        normalOutstandingCost += pcSummary.cost;
        // The cost of the conversion is the delta between the already converted and the current.
        // This does not account for the header overhead on converting the first point. However,
        // the few bytes overhead per param-count should not affect much.
        int costToConvert = currentPc - currentConvertedPc;
        // If the estimated cost is larger we convert. The order here could be either way as
        // both the normal cost and converted cost are estimates. Canonicalization could reduce
        // the former and compaction could reduce the latter.
        if (normalOutstandingCost > costToConvert) {
          normalConvertedCost += normalOutstandingCost;
          normalOutstandingCost = 0;
          currentConvertedPc = currentPc;
        }
      }
      cutoff = currentConvertedPc;
      normalPreCutoffCost = normalConvertedCost;
      normalPostCutoffCost = normalOutstandingCost;
      assert cutoff >= -1;
      assert normalPreCutoffCost >= 0;
      assert normalPostCutoffCost >= 0;
      assert preCutoffPcCost() >= 0;
      assert postCutoffPcCost() >= 0;
    }

    private int preCutoffPcCost() {
      return cutoff > 0 ? PcBasedDebugInfo.estimatedWriteSize(paramCount, cutoff) : 0;
    }

    private int postCutoffPcCost() {
      return cutoff < maxPc ? PcBasedDebugInfo.estimatedWriteSize(paramCount, maxPc - cutoff) : 0;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder
          .append("p:")
          .append(paramCount)
          .append(", c:")
          .append(cutoff)
          .append(", m:")
          .append(maxPc);
      if (cutoff > 0) {
        builder
            .append(", preCutNormal:")
            .append(normalPreCutoffCost)
            .append(", preCutPC:")
            .append(preCutoffPcCost());
      }
      if (cutoff < maxPc) {
        builder
            .append(", postCutNormal:")
            .append(normalPostCutoffCost)
            .append(", postCutPC:")
            .append(postCutoffPcCost());
      }
      return builder.toString();
    }
  }

  private static DexInstruction getLastExecutableInstruction(DexCode code) {
    DexInstruction lastInstruction = null;
    for (DexInstruction instruction : code.instructions) {
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
