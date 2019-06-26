// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;


import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.nio.file.Paths;

// Base class for the runtime structure in the test parameters.
public class TestRuntime {

  public static TestRuntime fromName(String name) {
    if (NoneRuntime.NAME.equals(name)) {
      return NoneRuntime.getInstance();
    }
    CfVm cfVm = CfVm.fromName(name);
    if (cfVm != null) {
      return new CfRuntime(cfVm);
    }
    if (name.startsWith("dex-")) {
      DexVm dexVm = DexVm.fromShortName(name.substring(4) + "_host");
      if (dexVm != null) {
        return new DexRuntime(dexVm);
      }
    }
    return null;
  }

  // Enum describing the possible/supported CF runtimes.
  public enum CfVm {
    JDK8("jdk8"),
    JDK9("jdk9"),
    JDK11("jdk11");

    private final String name;

    public static CfVm fromName(String v) {
      for (CfVm value : CfVm.values()) {
        if (value.name.equals(v)) {
          return value;
        }
      }
      return null;
    }

    CfVm(String name) {
      this.name = name;
    }

    public static CfVm first() {
      return JDK8;
    }

    public static CfVm last() {
      return JDK11;
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

  // Values are the path in third_party/openjdk to the repository with bin
  public static ImmutableMap<CfVm, Path> CHECKED_IN_JDKS = initializeCheckedInJDKs();

  public static ImmutableMap<CfVm, Path> initializeCheckedInJDKs() {
    if (ToolHelper.isLinux()) {
      return ImmutableMap.of(
          CfVm.JDK8,
          Paths.get("jdk8", "linux-x86"),
          CfVm.JDK9,
          Paths.get("openjdk-9.0.4", "linux"),
          CfVm.JDK11,
          Paths.get("jdk-11", "Linux"));
    }
    if (ToolHelper.isMac()) {
      return ImmutableMap.of(
          CfVm.JDK8,
          Paths.get("jdk8", "darwin-x86"),
          CfVm.JDK9,
          Paths.get("openjdk-9.0.4", "osx"),
          CfVm.JDK11,
          Paths.get("jdk-11", "Mac", "Contents", "Home"));
    }
    assert ToolHelper.isWindows();
    return ImmutableMap.of(
        CfVm.JDK9,
        Paths.get("openjdk-9.0.4", "windows"),
        CfVm.JDK11,
        Paths.get("jdk-11", "Windows"));
  }

  public static boolean isCheckedInJDK(CfVm jdk) {
    return CHECKED_IN_JDKS.containsKey(jdk);
  }

  public static Path getCheckInJDKPathFor(CfVm jdk) {
    return Paths.get("third_party", "openjdk")
        .resolve(CHECKED_IN_JDKS.get(jdk))
        .resolve(Paths.get("bin", "java"));
  }

  public static TestRuntime getDefaultJavaRuntime() {
    // For compatibility with old tests not specifying a Java runtime
    return new CfRuntime(CfVm.JDK9);
  }

  public static class NoneRuntime extends TestRuntime {

    private static final String NAME = "none";
    private static final NoneRuntime INSTANCE = new NoneRuntime();

    private NoneRuntime() {}

    public static NoneRuntime getInstance() {
      return INSTANCE;
    }

    @Override
    public String toString() {
      return NAME;
    }
  }

  // Wrapper for the DEX runtimes.
  public static class DexRuntime extends TestRuntime {

    private final DexVm vm;

    public DexRuntime(DexVm.Version version) {
      this(DexVm.fromVersion(version));
    }

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
