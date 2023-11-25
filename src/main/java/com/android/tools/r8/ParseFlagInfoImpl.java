// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.BaseCompilerCommandParser.ART_PROFILE_FLAG;
import static com.android.tools.r8.BaseCompilerCommandParser.MAP_DIAGNOSTICS;
import static com.android.tools.r8.BaseCompilerCommandParser.MIN_API_FLAG;
import static com.android.tools.r8.BaseCompilerCommandParser.THREAD_COUNT_FLAG;
import static com.android.tools.r8.D8CommandParser.STARTUP_PROFILE_FLAG;

import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ParseFlagInfoImpl implements ParseFlagInfo {

  private static String defaultSuffix(boolean isDefault) {
    return isDefault ? " (default)" : "";
  }

  public static ParseFlagInfoImpl getRelease(boolean isDefault) {
    return flag0(
        "--release", "Compile without debugging information" + defaultSuffix(isDefault) + ".");
  }

  public static ParseFlagInfoImpl getDebug(boolean isDefault) {
    return flag0("--debug", "Compile with debugging information" + defaultSuffix(isDefault) + ".");
  }

  public static ParseFlagInfoImpl getDex(boolean isDefault) {
    return flag0("--dex", "Compile program to DEX file format" + defaultSuffix(isDefault) + ".");
  }

  public static ParseFlagInfoImpl getClassfile() {
    return flag0("--classfile", "Compile program to Java classfile format.");
  }

  public static ParseFlagInfoImpl getOutput() {
    return flag1(
        "--output",
        "<file>",
        "Output result in <file>.",
        "<file> must be an existing directory or a zip file.");
  }

  public static ParseFlagInfoImpl getLib() {
    return flag1("--lib", "<file|jdk-home>", "Add <file|jdk-home> as a library resource.");
  }

  public static ParseFlagInfoImpl getClasspath() {
    return flag1("--classpath", "<file>", "Add <file> as a classpath resource.");
  }

  public static ParseFlagInfoImpl getMinApi() {
    return flag1(
        MIN_API_FLAG,
        "<number>",
        "Minimum Android API level compatibility (default: "
            + AndroidApiLevel.getDefault().getLevel()
            + ").");
  }

  public static ParseFlagInfoImpl getDesugaredLib() {
    return flag1(
        "--desugared-lib",
        "<file>",
        "Specify desugared library configuration.",
        "<file> is a desugared library configuration (json).");
  }

  public static ParseFlagInfoImpl getMainDexRules() {
    return flag1(
        "--main-dex-rules",
        "<file>",
        "Proguard keep rules for classes to place in the",
        "primary dex file.");
  }

  public static ParseFlagInfoImpl getMainDexList() {
    return flag1("--main-dex-list", "<file>", "List of classes to place in the primary dex file.");
  }

  public static ParseFlagInfoImpl getMainDexListOutput() {
    return flag1("--main-dex-list-output", "<file>", "Output resulting main dex list in <file>.");
  }

  public static ParseFlagInfoImpl getVersion(String tool) {
    return flag0("--version", "Print the version of " + tool + ".");
  }

  public static ParseFlagInfoImpl getHelp() {
    return flag0("--help", "Print this message.");
  }

  public static ParseFlagInfoImpl getPgConf() {
    return ParseFlagInfoImpl.flag1("--pg-conf", "<file>", "Proguard configuration <file>.");
  }

  public static ParseFlagInfoImpl getPgMapOutput() {
    return ParseFlagInfoImpl.flag1(
        "--pg-map-output", "<file>", "Output the resulting name and line mapping to <file>.");
  }

  public static ParseFlagInfoImpl getPartitionMapOutput() {
    return ParseFlagInfoImpl.flag1(
        "--partition-map-output", "<file>", "Output the resulting mapping to <file>.");
  }

  public static List<ParseFlagInfoImpl> getAssertionsFlags() {
    return ImmutableList.of(
        flag0a1(
            "--force-enable-assertions[:[<class name>|<package name>...]]",
            "--force-ea[:[<class name>|<package name>...]]",
            "Forcefully enable javac generated assertion code."),
        flag0a1(
            "--force-disable-assertions[:[<class name>|<package name>...]]",
            "--force-da[:[<class name>|<package name>...]]",
            "Forcefully disable javac generated assertion code.",
            "This is the default handling of javac assertion code",
            "when generating DEX file format."),
        flag0a1(
            "--force-passthrough-assertions[:[<class name>|<package name>...]]",
            "--force-pa[:[<class name>|<package name>...]]",
            "Don't change javac generated assertion code. This",
            "is the default handling of javac assertion code when",
            "generating class file format."),
        flag0a1(
            "--force-assertions-handler:<handler method>[:[<class name>|<package name>...]]",
            "--force-ah:<handler method>[:[<class name>|<package name>...]]",
            "Change javac and kotlinc generated assertion code",
            "to invoke the method <handler method> with each",
            "assertion error instead of throwing it.",
            "The <handler method> is specified as a class name",
            "followed by a dot and the method name.",
            "The handler method must take a single argument of",
            "type java.lang.Throwable and have return type void."));
  }

  public static ParseFlagInfoImpl getThreadCount() {
    return flag1(
        THREAD_COUNT_FLAG,
        "<number>",
        "Use <number> of threads for compilation.",
        "If not specified the number will be based on",
        "heuristics taking the number of cores into account.");
  }

  public static ParseFlagInfoImpl getMapDiagnostics() {
    return flag2(
        MAP_DIAGNOSTICS + "[:<type>]",
        "<from-level>",
        "<to-level>",
        "Map diagnostics of <type> (default any) reported as",
        "<from-level> to <to-level> where <from-level> and",
        "<to-level> are one of 'info', 'warning', or 'error'",
        "and the optional <type> is either the simple or",
        "fully qualified Java type name of a diagnostic.",
        "If <type> is unspecified, all diagnostics at ",
        "<from-level> will be mapped.",
        "Note that fatal compiler errors cannot be mapped.");
  }

  public static ParseFlagInfoImpl getAndroidPlatformBuild() {
    return flag0(
        "--android-platform-build",
        "Compile as a platform build where the runtime/bootclasspath",
        "is assumed to be the version specified by --min-api.");
  }

  public static ParseFlagInfoImpl getArtProfile() {
    return flag2(
        ART_PROFILE_FLAG,
        "<input>",
        "<output>",
        "Rewrite human readable ART profile read from <input> and write to <output>.");
  }

  public static ParseFlagInfoImpl getStartupProfile() {
    return flag1(STARTUP_PROFILE_FLAG, "<file>", "Startup profile <file> to use for dex layout.");
  }

  public static ParseFlagInfoImpl flag0(String flag, String... help) {
    return flag(flag, Collections.emptyList(), Arrays.asList(help));
  }

  public static ParseFlagInfoImpl flag1(String flag, String arg, String... help) {
    return flag(flag, Collections.singletonList(arg), Arrays.asList(help));
  }

  public static ParseFlagInfoImpl flag2(String flag, String arg1, String arg2, String... help) {
    return flag(flag, ImmutableList.of(arg1, arg2), Arrays.asList(help));
  }

  private static String fmt(String flag, List<String> args) {
    StringBuilder builder = new StringBuilder(flag);
    for (String arg : args) {
      builder.append(" ").append(arg);
    }
    return builder.toString();
  }

  @SuppressWarnings("UnusedVariable")
  public static ParseFlagInfoImpl flag(String flag, List<String> args, List<String> help) {
    return new ParseFlagInfoImpl(flag, fmt(flag, args), Collections.emptyList(), help);
  }

  public static ParseFlagInfoImpl flag0a1(String flag, String alternative, String... help) {
    return new ParseFlagInfoImpl(
        null, flag, Collections.singletonList(alternative), Arrays.asList(help));
  }

  // Note that the raw flag may be non-representable as in the case of the family of flags for
  // assertions.
  @SuppressWarnings("UnusedVariable")
  private final String rawFlag;

  private final String flagWithArgs;
  private final List<String> alternatives;
  private final List<String> flagHelp;

  public ParseFlagInfoImpl(
      String rawFlag, String flagWithArgs, List<String> alternatives, List<String> flagHelp) {
    // Raw flag may be null if it does not have a unique definition.
    assert flagWithArgs != null;
    assert alternatives != null;
    assert flagHelp != null;
    this.rawFlag = rawFlag;
    this.flagWithArgs = flagWithArgs;
    this.alternatives = alternatives;
    this.flagHelp = flagHelp;
  }

  @Override
  public String getFlagFormat() {
    return flagWithArgs;
  }

  @Override
  public List<String> getFlagFormatAlternatives() {
    return alternatives;
  }

  @Override
  public List<String> getFlagHelp() {
    return flagHelp;
  }
}
