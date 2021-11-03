// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexWritableCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class MethodToCodeObjectMapping {

  public abstract DexWritableCode getCode(DexEncodedMethod method);

  public abstract void clearCode(DexEncodedMethod method);

  public abstract boolean verifyCodeObjects(Collection<DexEncodedMethod> codes);

  public static MethodToCodeObjectMapping fromMethodBacking() {
    return MethodBacking.INSTANCE;
  }

  public static MethodToCodeObjectMapping fromMapBacking(
      Map<DexEncodedMethod, DexWritableCode> map) {
    return new MapBacking(map);
  }

  private static class MethodBacking extends MethodToCodeObjectMapping {

    private static final MethodBacking INSTANCE = new MethodBacking();

    @Override
    public DexWritableCode getCode(DexEncodedMethod method) {
      Code code = method.getCode();
      assert code == null || code.isDexWritableCode();
      return code == null ? null : code.asDexWritableCode();
    }

    @Override
    public void clearCode(DexEncodedMethod method) {
      method.unsetCode();
    }

    @Override
    public boolean verifyCodeObjects(Collection<DexEncodedMethod> codes) {
      return true;
    }
  }

  private static class MapBacking extends MethodToCodeObjectMapping {

    private final Map<DexEncodedMethod, DexWritableCode> codes;

    public MapBacking(Map<DexEncodedMethod, DexWritableCode> codes) {
      this.codes = codes;
    }

    @Override
    public DexWritableCode getCode(DexEncodedMethod method) {
      return codes.get(method);
    }

    @Override
    public void clearCode(DexEncodedMethod method) {
      // We can safely clear the thread local pointer to even shared methods.
      codes.put(method, null);
    }

    @Override
    public boolean verifyCodeObjects(Collection<DexEncodedMethod> methods) {
      // TODO(b/204056443): Convert to a Set<DexWritableCode> when DexCode#hashCode() works.
      List<DexWritableCode> codes =
          methods.stream().map(this::getCode).collect(Collectors.toList());
      assert this.codes.values().containsAll(codes);
      return true;
    }
  }
}
