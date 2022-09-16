// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.positions;

import com.android.tools.r8.debuginfo.DebugRepresentation;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexDebugInfoForSingleLineMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public interface PositionToMappedRangeMapper {

  List<MappedPosition> getMappedPositions(
      ProgramMethod method,
      PositionRemapper positionRemapper,
      boolean hasOverloads,
      boolean canUseDexPc,
      int pcEncodingCutoff);

  void updateDebugInfoInCodeObjects();

  static PositionToMappedRangeMapper create(AppView<?> appView) {
    return appView.options().isGeneratingClassFiles()
        ? new ClassFilePositionToMappedRangeMapper(appView)
        : new DexPositionToMappedRangeMapper(appView);
  }

  class DexPositionToMappedRangeMapper implements PositionToMappedRangeMapper {

    private final DexPositionToNoPcMappedRangeMapper noPcMapper;
    private final DexPositionToPcMappedRangeMapper pcMapper;

    private final PcBasedDebugInfoRecorder pcBasedDebugInfoRecorder;

    private DexPositionToMappedRangeMapper(AppView<?> appView) {
      pcBasedDebugInfoRecorder =
          appView.options().canUseNativeDexPcInsteadOfDebugInfo()
              ? new NativePcSupport()
              : new Pc2PcMappingSupport(appView.options().allowDiscardingResidualDebugInfo());
      noPcMapper = new DexPositionToNoPcMappedRangeMapper(appView);
      pcMapper = new DexPositionToPcMappedRangeMapper(appView, pcBasedDebugInfoRecorder);
    }

    @Override
    public List<MappedPosition> getMappedPositions(
        ProgramMethod method,
        PositionRemapper positionRemapper,
        boolean hasOverloads,
        boolean canUseDexPc,
        int pcEncodingCutoff) {
      List<MappedPosition> mappedPositions =
          canUseDexPc
              ? pcMapper.optimizeDexCodePositionsForPc(method, positionRemapper, pcEncodingCutoff)
              : noPcMapper.optimizeDexCodePositions(method, positionRemapper, hasOverloads);
      DexEncodedMethod definition = method.getDefinition();
      if (definition.getCode().isDexCode()
          && definition.getCode().asDexCode().getDebugInfo()
              == DexDebugInfoForSingleLineMethod.getInstance()) {
        pcBasedDebugInfoRecorder.recordSingleLineFor(method, pcEncodingCutoff);
      }
      return mappedPositions;
    }

    @Override
    public void updateDebugInfoInCodeObjects() {
      pcBasedDebugInfoRecorder.updateDebugInfoInCodeObjects();
    }
  }

  interface PcBasedDebugInfoRecorder {
    /** Callback to record a code object with a given max instruction PC and parameter count. */
    void recordPcMappingFor(ProgramMethod method, int maxEncodingPc);

    /** Callback to record a code object with only a single "line". */
    void recordSingleLineFor(ProgramMethod method, int maxEncodingPc);

    /**
     * Install the correct debug info objects.
     *
     * <p>Must be called after all recordings have been given to allow computing the debug info
     * items to be installed.
     */
    void updateDebugInfoInCodeObjects();

    int getPcEncoding(int pc);
  }

  class Pc2PcMappingSupport implements PcBasedDebugInfoRecorder {

    private static class UpdateInfo {
      final DexCode code;
      final int paramCount;
      final int maxEncodingPc;

      public UpdateInfo(DexCode code, int paramCount, int maxEncodingPc) {
        this.code = code;
        this.paramCount = paramCount;
        this.maxEncodingPc = maxEncodingPc;
      }

      // Used as key when building the shared debug info map.
      // Only param and max-pc are part of the key.

      @Override
      public boolean equals(Object o) {
        UpdateInfo that = (UpdateInfo) o;
        return paramCount == that.paramCount && maxEncodingPc == that.maxEncodingPc;
      }

      @Override
      public int hashCode() {
        return Objects.hash(paramCount, maxEncodingPc);
      }
    }

    private final List<UpdateInfo> codesToUpdate = new ArrayList<>();

    // We can only drop single-line debug info if it is OK to lose the source-file info.
    // This list is null if we must retain single-line entries.
    private final List<DexCode> singleLineCodesToClear;

    public Pc2PcMappingSupport(boolean allowDiscardingSourceFile) {
      singleLineCodesToClear = allowDiscardingSourceFile ? new ArrayList<>() : null;
    }

    @Override
    public int getPcEncoding(int pc) {
      assert pc >= 0;
      return pc + 1;
    }

    private boolean cantAddToClearSet(ProgramMethod method) {
      assert method.getDefinition().getCode().isDexCode();
      if (singleLineCodesToClear == null) {
        return true;
      }
      singleLineCodesToClear.add(method.getDefinition().getCode().asDexCode());
      return false;
    }

    @Override
    public void recordPcMappingFor(ProgramMethod method, int maxEncodingPc) {
      assert method.getDefinition().getCode().isDexCode();
      int parameterCount = method.getParameters().size();
      DexCode code = method.getDefinition().getCode().asDexCode();
      assert DebugRepresentation.verifyLastExecutableInstructionWithinBound(code, maxEncodingPc);
      codesToUpdate.add(new UpdateInfo(code, parameterCount, maxEncodingPc));
    }

    @Override
    public void recordSingleLineFor(ProgramMethod method, int maxEncodingPc) {
      if (cantAddToClearSet(method)) {
        recordPcMappingFor(method, maxEncodingPc);
      }
    }

    @Override
    public void updateDebugInfoInCodeObjects() {
      Map<UpdateInfo, DexDebugInfo> debugInfos = new HashMap<>();
      codesToUpdate.forEach(
          entry -> {
            assert DebugRepresentation.verifyLastExecutableInstructionWithinBound(
                entry.code, entry.maxEncodingPc);
            DexDebugInfo debugInfo =
                debugInfos.computeIfAbsent(entry, Pc2PcMappingSupport::buildPc2PcDebugInfo);
            assert debugInfo.asPcBasedInfo().getMaxPc() == entry.maxEncodingPc;
            entry.code.setDebugInfo(debugInfo);
          });
      if (singleLineCodesToClear != null) {
        singleLineCodesToClear.forEach(c -> c.setDebugInfo(null));
      }
    }

    private static DexDebugInfo buildPc2PcDebugInfo(UpdateInfo info) {
      return new DexDebugInfo.PcBasedDebugInfo(info.paramCount, info.maxEncodingPc);
    }
  }

  class NativePcSupport implements PcBasedDebugInfoRecorder {

    @Override
    public int getPcEncoding(int pc) {
      assert pc >= 0;
      return pc;
    }

    private void clearDebugInfo(ProgramMethod method) {
      // Always strip the info in full as the runtime will emit the PC directly.
      method.getDefinition().getCode().asDexCode().setDebugInfo(null);
    }

    @Override
    public void recordPcMappingFor(ProgramMethod method, int maxEncodingPc) {
      clearDebugInfo(method);
    }

    @Override
    public void recordSingleLineFor(ProgramMethod method, int maxEncodingPc) {
      clearDebugInfo(method);
    }

    @Override
    public void updateDebugInfoInCodeObjects() {
      // Already null out the info so nothing to do.
    }
  }
}
