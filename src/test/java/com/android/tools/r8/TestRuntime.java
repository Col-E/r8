// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.AndroidApiLevel;

// Base class for the runtime structure in the test parameters.
public class TestRuntime {

  // Enum describing the possible/supported CF runtimes.
  public enum CfVm {
    JDK8("jdk8"),
    JDK9("jdk9");

    private final String name;

    public static CfVm fromName(String v) {
      for (CfVm value : CfVm.values()) {
        if (value.name.equals(v)) {
          return value;
        }
      }
      throw new Unreachable("Unexpected CfVm name: " + v);
    }

    CfVm(String name) {
      this.name = name;
    }

    public static CfVm first() {
      return JDK8;
    }

    public static CfVm last() {
      return JDK9;
    }

    public boolean lessThan(CfVm other) {
      return this.ordinal() < other.ordinal();
    }

    public boolean lessThanOrEqual(CfVm other) {
      return this.ordinal() <= other.ordinal();
    }

    @Override
    public String toString() {
      return name;
    }
  }

  // Wrapper for the DEX runtimes.
  public static class DexRuntime extends TestRuntime {
    private final DexVm vm;

    public DexRuntime(DexVm vm) {
      assert vm != null;
      this.vm = vm;
    }

    @Override
    public boolean isDex() {
      return true;
    }

    @Override
    public DexRuntime asDex() {
      return this;
    }

    public DexVm getVm() {
      return vm;
    }

    @Override
    public String toString() {
      return "dex-" + vm.getVersion().toString();
    }

    public AndroidApiLevel getMinApiLevel() {
      return ToolHelper.getMinApiLevelForDexVm(vm);
    }
  }

  // Wrapper for the CF runtimes.
  public static class CfRuntime extends TestRuntime {
    private final CfVm vm;

    public CfRuntime(CfVm vm) {
      assert vm != null;
      this.vm = vm;
    }

    @Override
    public boolean isCf() {
      return true;
    }

    @Override
    public CfRuntime asCf() {
      return this;
    }

    public CfVm getVm() {
      return vm;
    }

    @Override
    public String toString() {
      return vm.toString();
    }
  }

  public boolean isDex() {
    return false;
  }

  public boolean isCf() {
    return false;
  }

  public DexRuntime asDex() {
    return null;
  }

  public CfRuntime asCf() {
    return null;
  }

  public Backend getBackend() {
    if (isDex()) {
      return Backend.DEX;
    }
    if (isCf()) {
      return Backend.CF;
    }
    throw new Unreachable("Unexpected runtime without backend: " + this);
  }
}
