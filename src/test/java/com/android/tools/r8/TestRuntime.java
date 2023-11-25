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
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

// Base class for the runtime structure in the test parameters.
public abstract class TestRuntime {

  // Enum describing the possible/supported CF runtimes.
  public enum CfVm implements Ordered<CfVm> {
    JDK8("jdk8", 52),
    JDK9("jdk9", 53),
    JDK10("jdk10", 54),
    JDK11("jdk11", 55),
    JDK12("jdk12", 56),
    JDK13("jdk13", 57),
    JDK14("jdk14", 58),
    JDK15("jdk15", 59),
    JDK16("jdk16", 60),
    JDK17("jdk17", 61),
    JDK18("jdk18", 62),
    JDK20("jdk20", 64),
    JDK21("jdk21", 65);

    /** This should generally be the latest checked in CF runtime we fully support. */
    private static final CfVm DEFAULT = JDK11;

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
      return JDK17;
    }

    public boolean lessThan(CfVm other) {
      return isLessThan(other);
    }

    public boolean lessThanOrEqual(CfVm other) {
      return isLessThanOrEqualTo(other);
    }

    @Override
    public String toString() {
      return name;
    }

    public static CfVm getMinimumSystemVersion() {
      return JDK11;
    }

    // Records was experimental from JDK-15 (requiring turning on experimental feaures), and GA
    // in JDK-17.
    public boolean hasRecordsSupport() {
      return isGreaterThanOrEqualTo(JDK17);
    }
  }

  private static final Path JDK8_PATH = Paths.get(ToolHelper.THIRD_PARTY_DIR, "openjdk", "jdk8");
  private static final Path JDK9_PATH =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "openjdk", "openjdk-9.0.4");
  private static final Path JDK11_PATH = Paths.get(ToolHelper.THIRD_PARTY_DIR, "openjdk", "jdk-11");
  private static final Path JDK17_PATH = Paths.get(ToolHelper.THIRD_PARTY_DIR, "openjdk", "jdk-17");
  private static final Path JDK21_PATH = Paths.get(ToolHelper.THIRD_PARTY_DIR, "openjdk", "jdk-21");
  private static final Map<CfVm, Path> jdkPaths =
      ImmutableMap.of(
          CfVm.JDK8, JDK8_PATH,
          CfVm.JDK9, JDK9_PATH,
          CfVm.JDK11, JDK11_PATH,
          CfVm.JDK17, JDK17_PATH,
          CfVm.JDK21, JDK21_PATH);

  public static CfRuntime getCheckedInJdk(CfVm vm) {
    if (vm == CfVm.JDK8) {
      return getCheckedInJdk8();
    }
    return new CfRuntime(vm, getCheckedInJdkHome(vm));
  }

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

  private static Path getCheckedInJdkHome(CfVm vm) {
    Path path = jdkPaths.get(vm);
    assert path != null : "No JDK path defined for " + vm;
    if (ToolHelper.isLinux()) {
      return path.resolve("linux");
    } else if (ToolHelper.isMac()) {
      return vm.lessThanOrEqual(CfVm.JDK9)
          ? path.resolve("osx")
          : path.resolve("osx/Contents/Home");
    } else {
      assert ToolHelper.isWindows();
      return path.resolve("windows");
    }
  }

  public static CfRuntime getCheckedInJdk9() {
    return new CfRuntime(CfVm.JDK9, getCheckedInJdkHome(CfVm.JDK9));
  }

  public static CfRuntime getCheckedInJdk11() {
    return new CfRuntime(CfVm.JDK11, getCheckedInJdkHome(CfVm.JDK11));
  }

  public static CfRuntime getCheckedInJdk17() {
    return new CfRuntime(CfVm.JDK17, getCheckedInJdkHome(CfVm.JDK17));
  }

  public static CfRuntime getCheckedInJdk21() {
    return new CfRuntime(CfVm.JDK21, getCheckedInJdkHome(CfVm.JDK21));
  }

  public static List<CfRuntime> getCheckedInCfRuntimes() {
    CfRuntime[] jdks =
        new CfRuntime[] {
          getCheckedInJdk8(),
          getCheckedInJdk9(),
          getCheckedInJdk11(),
          getCheckedInJdk17(),
          getCheckedInJdk21()
        };
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

  public static CfRuntime getDefaultCfRuntime() {
    return TestRuntime.getCheckedInJdk(CfVm.DEFAULT);
  }

  public static DexRuntime getDefaultDexRuntime() {
    return new DexRuntime(DexVm.Version.NEW_DEFAULT);
  }

  public static List<TestRuntime> getCheckedInRuntimes() {
    return ImmutableList.<TestRuntime>builder()
        .addAll(getCheckedInCfRuntimes())
        .addAll(getCheckedInDexRuntimes())
        .build();
  }

  public static CfRuntime getSystemRuntime() {
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
    if (version.equals("11") || version.startsWith("11.")) {
      return new CfRuntime(CfVm.JDK11, Paths.get(home));
    }
    if (version.equals("17") || version.startsWith("17.")) {
      return new CfRuntime(CfVm.JDK17, Paths.get(home));
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
    public AndroidApiLevel maxSupportedApiLevel() {
      // The "none" runtime trivally supports all api levels as nothing is run.
      return AndroidApiLevel.LATEST;
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

    public DexVm.Version getVersion() {
      return vm.getVersion();
    }

    @Override
    public AndroidApiLevel maxSupportedApiLevel() {
      return getMinApiLevel();
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

    public boolean hasRecordsSupport() {
      return getVersion().hasRecordsSupport();
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
      return StringUtils.toLowerCase(vm.name());
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
    public AndroidApiLevel maxSupportedApiLevel() {
      // TODO: define the mapping from android API levels back to JDKs.
      return AndroidApiLevel.LATEST;
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

    public boolean isOlderThan(CfVm version) {
      return vm.lessThan(version);
    }

    public boolean isNewerThan(CfVm version) {
      return !vm.lessThanOrEqual(version);
    }

    public boolean isNewerThanOrEqual(CfVm version) {
      return vm == version || !vm.lessThanOrEqual(version);
    }

    public boolean hasRecordsSupport() {
      return getVm().hasRecordsSupport();
    }
  }

  public <T> T match(Function<CfRuntime, T> onCf, BiFunction<DexRuntime, DexVm.Version, T> onDex) {
    if (isCf()) {
      return onCf.apply(asCf());
    }
    if (isDex()) {
      return onDex.apply(asDex(), asDex().getVersion());
    }
    throw new Unreachable();
  }

  public void match(Consumer<CfRuntime> onCf, BiConsumer<DexRuntime, DexVm.Version> onDex) {
    if (isCf()) {
      onCf.accept(asCf());
    } else if (isDex()) {
      onDex.accept(asDex(), asDex().getVersion());
    } else {
      throw new Unreachable();
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

  public abstract AndroidApiLevel maxSupportedApiLevel();

  @Override
  public abstract boolean equals(Object other);

  @Override
  public abstract int hashCode();

  public abstract String name();
}
