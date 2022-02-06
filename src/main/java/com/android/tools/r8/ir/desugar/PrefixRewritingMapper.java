// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public abstract class PrefixRewritingMapper {

  public static PrefixRewritingMapper empty() {
    return new EmptyPrefixRewritingMapper();
  }

  public abstract void rewriteType(DexType type, DexType rewrittenType);

  public abstract DexType rewrittenType(DexType type, AppView<?> appView);

  public abstract DexType rewrittenContextType(DexType type);

  public boolean hasRewrittenType(DexType type, AppView<?> appView) {
    return rewrittenType(type, appView) != null;
  }

  public boolean hasRewrittenTypeInSignature(DexProto proto, AppView<?> appView) {
    if (hasRewrittenType(proto.returnType, appView)) {
      return true;
    }
    for (DexType paramType : proto.parameters.values) {
      if (hasRewrittenType(paramType, appView)) {
        return true;
      }
    }
    return false;
  }

  public abstract boolean isRewriting();

  public abstract void forAllRewrittenTypes(Consumer<DexType> consumer);

  public static class MachineDesugarPrefixRewritingMapper extends PrefixRewritingMapper {

    private final Map<DexType, DexType> rewriteType;
    private final Map<DexType, DexType> rewriteDerivedTypeOnly;

    public MachineDesugarPrefixRewritingMapper(MachineDesugaredLibrarySpecification specification) {
      this.rewriteType = new ConcurrentHashMap<>(specification.getRewriteType());
      rewriteDerivedTypeOnly = specification.getRewriteDerivedTypeOnly();
    }

    @Override
    public DexType rewrittenType(DexType type, AppView<?> appView) {
      if (type.isArrayType()) {
        DexType rewrittenBaseType =
            rewrittenType(type.toBaseType(appView.dexItemFactory()), appView);
        if (rewrittenBaseType == null) {
          return null;
        }
        return appView
            .dexItemFactory()
            .createArrayType(type.getNumberOfLeadingSquareBrackets(), rewrittenBaseType);
      }
      return rewriteType.get(type);
    }

    @Override
    public DexType rewrittenContextType(DexType context) {
      assert !context.isArrayType();
      if (rewriteType.containsKey(context)) {
        return rewriteType.get(context);
      }
      return rewriteDerivedTypeOnly.get(context);
    }

    @Override
    public void rewriteType(DexType type, DexType rewrittenType) {
      rewriteType.compute(
          type,
          (t, val) -> {
            assert val == null || val == rewrittenType;
            return rewrittenType;
          });
    }

    @Override
    public boolean isRewriting() {
      return true;
    }

    @Override
    public void forAllRewrittenTypes(Consumer<DexType> consumer) {
      rewriteType.keySet().forEach(consumer);
    }
  }

  public static class EmptyPrefixRewritingMapper extends PrefixRewritingMapper {

    @Override
    public DexType rewrittenType(DexType type, AppView<?> appView) {
      return null;
    }

    @Override
    public DexType rewrittenContextType(DexType type) {
      return null;
    }

    @Override
    public void rewriteType(DexType type, DexType rewrittenType) {}

    @Override
    public boolean isRewriting() {
      return false;
    }

    @Override
    public void forAllRewrittenTypes(Consumer<DexType> consumer) {}
  }
}
