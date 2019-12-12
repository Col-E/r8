// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;


import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

// Base class for the runtime structure in the test parameters.
public abstract class TestRuntime {

  // Enum describing the possible/supported CF runtimes.
  public enum CfVm {
    JDK8("jdk8", 52),
    JDK9("jdk9", 53),
    JDK10("jdk10", 54),
    JDK11("jdk11", 55),
    ;

    private final String name;
    private final int classfileVersion;

    CfVm(String name, int classfileVersion) {
      this.name = name;
      this.classfileVersion = classfileVersion;
    }

    public int getClassfileVersion() {
      return classfileVersion;
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

  private static final Path JDK8_PATH = Paths.get(ToolHelper.THIRD_PARTY_DIR, "openjdk", "jdk8");
  private static final Path JDK9_PATH =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "openjdk", "openjdk-9.0.4");
  private static final Path JDK11_PATH = Paths.get(ToolHelper.THIRD_PARTY_DIR, "openjdk", "jdk-11");

  public static CfRuntime getCheckedInJdk8() {
    Path home;
    if (ToolHelper.isLinux()) {
      home = JDK8_PATH.resolve("linux-x86");
    } else if (ToolHelper.isMac()) {
      home = JDK8_PATH.resolve("darwin-x86");
    } else {
      assert ToolHelper.isWindows();
      return null;
    }
    return new CfRuntime(CfVm.JDK8, home);
  }

  public static CfRuntime getCheckedInJdk9() {
    Path home;
    if (ToolHelper.isLinux()) {
      home = JDK9_PATH.resolve("linux");
    } else if (ToolHelper.isMac()) {
      home = JDK9_PATH.resolve("osx");
    } else {
      assert ToolHelper.isWindows();
      home = JDK9_PATH.resolve("windows");
    }
    return new CfRuntime(CfVm.JDK9, home);
  }

  public static CfRuntime getCheckedInJdk11() {
    Path home;
    if (ToolHelper.isLinux()) {
      home = JDK11_PATH.resolve("Linux");
    } else if (ToolHelper.isMac()) {
      home = Paths.get(JDK11_PATH.toString(), "Mac", "Contents", "Home");
    } else {
      assert ToolHelper.isWindows();
      home = JDK11_PATH.resolve("Windows");
    }
    return new CfRuntime(CfVm.JDK11, home);
  }

  public static List<CfRuntime> getCheckedInCfRuntimes() {
    CfRuntime[] jdks =
        new CfRuntime[] {getCheckedInJdk8(), getCheckedInJdk9(), getCheckedInJdk11()};
    Builder<CfRuntime> builder = ImmutableList.builder();
    for (CfRuntime jdk : jdks) {
      if (jdk != null) {
        builder.add(jdk);
      }
    }
    return builder.build();
  }

  private static List<DexRuntime> getCheckedInDexRuntimes() {
    if (ToolHelper.isLinux()) {
      return ListUtils.map(Arrays.asList(DexVm.Version.values()), DexRuntime::new);
    }
    assert ToolHelper.isMac() || ToolHelper.isWindows();
    return ImmutableList.of();
  }

  // For compatibility with old tests not specifying a Java runtime
  @Deprecated
  public static TestRuntime getDefaultJavaRuntime() {
    return getCheckedInJdk9();
  }

  public static List<TestRuntime> getCheckedInRuntimes() {
    return ImmutableList.<TestRuntime>builder()
        .addAll(getCheckedInCfRuntimes())
        .addAll(getCheckedInDexRuntimes())
        .build();
  }

  public static TestRuntime getSystemRuntime() {
    String version = System.getProperty("java.version");
    String home = System.getProperty("java.home");
    if (version == null || version.isEmpty() || home == null || home.isEmpty()) {
      throw new Unimplemented("Unable to create a system runtime");
    }
    if (version.startsWith("1.8.")) {
      return new CfRuntime(CfVm.JDK8, Paths.get(home));
    }
    if (version.startsWith("9.")) {
      return new CfRuntime(CfVm.JDK9, Paths.get(home));
    }
    if (version.startsWith("11.")) {
      return new CfRuntime(CfVm.JDK11, Paths.get(home));
    }
    throw new Unimplemented("No support for JDK version: " + version);
  }

  public static class NoneRuntime extends TestRuntime {

    private static final String NAME = "none";
    private static final NoneRuntime INSTANCE = new NoneRuntime();

    private NoneRuntime() {}

    public static NoneRuntime getInstance() {
      return INSTANCE;
    }

    @Override
    public String name() {
      return NAME;
    }

    @Override
    public String toString() {
      return NAME;
    }

    @Override
    public boolean equals(Object other) {
      return this == other;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
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
    public String name() {
      return "dex-" + vm.getVersion().toString();
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
    public boolean equals(Object other) {
      if (!(other instanceof DexRuntime)) {
        return false;
      }
      DexRuntime dexRuntime = (DexRuntime) other;
      return vm == dexRuntime.vm;
    }

    @Override
    public int hashCode() {
      return vm.hashCode();
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
    private final Path home;

    public CfRuntime(CfVm vm, Path home) {
      assert vm != null;
      this.vm = vm;
      this.home = home.toAbsolutePath();
    }

    @Override
    public String name() {
      return vm.name().toLowerCase();
    }

    public Path getJavaHome() {
      return home;
    }

    public Path getJavaExecutable() {
      return home.resolve("bin").resolve("java");
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
      if (!(obj instanceof CfRuntime)) {
        return false;
      }
      CfRuntime cfRuntime = (CfRuntime) obj;
      return vm == cfRuntime.vm && home.equals(cfRuntime.home);
    }

    @Override
    public int hashCode() {
      return Objects.hash(vm, home);
    }

    public boolean isNewerThan(CfVm version) {
      return !vm.lessThanOrEqual(version);
    }

    public boolean isNewerThanOrEqual(CfVm version) {
      return vm == version || !vm.lessThanOrEqual(version);
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

  @Override
  public abstract boolean equals(Object other);

  @Override
  public abstract int hashCode();

  public abstract String name();
}
