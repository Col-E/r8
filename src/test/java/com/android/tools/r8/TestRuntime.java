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
      return CfRuntime.fromCfVm(cfVm);
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

    public boolean hasModularRuntime() {
      return this != JDK8;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  // Values are the path in third_party/openjdk to the repository with bin
  private static ImmutableMap<CfVm, Path> CHECKED_IN_JDKS = initializeCheckedInJDKs();

  private static ImmutableMap<CfVm, Path> initializeCheckedInJDKs() {
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

  static boolean isCheckedInJDK(CfVm jdk) {
    return CHECKED_IN_JDKS.containsKey(jdk);
  }

  static Path getCheckedInJDKHome(CfVm jdk) {
    return Paths.get("third_party", "openjdk").resolve(CHECKED_IN_JDKS.get(jdk));
  }

  static Path getCheckedInJDKPathFor(CfVm jdk) {
    return getCheckedInJDKHome(jdk).resolve(Paths.get("bin", "java"));
  }

  public static TestRuntime getDefaultJavaRuntime() {
    // For compatibility with old tests not specifying a Java runtime
    return CfRuntime.fromCfVm(CfVm.JDK9);
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
    public static final CfRuntime JDK8 = new CfRuntime(CfVm.JDK8);
    public static final CfRuntime JDK9 = new CfRuntime(CfVm.JDK9);
    public static final CfRuntime JDK11 = new CfRuntime(CfVm.JDK11);

    private final boolean systemJdk;
    private final CfVm vm;
    private final Path home;

    private CfRuntime(CfVm vm, Path home, boolean systemJdk) {
      assert vm != null;
      this.systemJdk = systemJdk;
      this.vm = vm;
      this.home = home;
    }

    public CfRuntime(CfVm vm, Path home) {
      this(vm, home, false);
    }

    private CfRuntime(CfVm vm) {
      this(vm, getCheckedInJDKHome(vm), isThisSystemJdk(vm));
    }

    private static boolean isThisSystemJdk(CfVm vm) {
      String version = System.getProperty("java.version");
      switch (vm) {
        case JDK8:
          return version.startsWith("1.8.");
        case JDK9:
          return version.startsWith("9.");
        case JDK11:
          return version.startsWith("11.");
      }
      throw new Unreachable();
    }

    public Path getJavaExecutable() {
      return home.resolve("bin").resolve("java");
    }

    public static CfRuntime fromCfVm(CfVm vm) {
      switch (vm) {
        case JDK8:
          return JDK8;
        case JDK9:
          return JDK9;
        case JDK11:
          return JDK11;
      }
      throw new Unreachable();
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

    @Override
    public boolean equals(Object obj) {
      return obj instanceof CfRuntime && ((CfRuntime) obj).vm.equals(vm);
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
