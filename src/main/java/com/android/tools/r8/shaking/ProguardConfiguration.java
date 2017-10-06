// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.naming.DictionaryReader;
import com.android.tools.r8.utils.InternalOptions.KeepAttributeOptions;
import com.android.tools.r8.utils.InternalOptions.PackageObfuscationMode;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ProguardConfiguration {

  public static class Builder {

    private final List<FilteredClassPath> injars = new ArrayList<>();
    private final List<FilteredClassPath> libraryjars = new ArrayList<>();
    private PackageObfuscationMode packageObfuscationMode = PackageObfuscationMode.NONE;
    private String packagePrefix = "";
    private boolean allowAccessModification = false;
    private boolean ignoreWarnings = false;
    private boolean optimizing = true;
    private boolean obfuscating = true;
    private boolean shrinking = true;
    private boolean printUsage = false;
    private Path printUsageFile;
    private boolean printMapping;
    private Path printMappingFile;
    private Path applyMappingFile = null;
    private boolean verbose = false;
    private String renameSourceFileAttribute = null;
    private final List<String> keepAttributePatterns = new ArrayList<>();
    private ProguardClassNameList dontWarnPatterns = ProguardClassNameList.emptyList();
    protected final List<ProguardConfigurationRule> rules = new ArrayList<>();
    private final DexItemFactory dexItemFactory;
    private boolean printSeeds;
    private Path seedFile;
    private Path obfuscationDictionary;
    private Path classObfuscationDictionary;
    private Path packageObfuscationDictionary;
    private boolean useUniqueClassMemberNames;
    private boolean keepParameterNames;

    private Builder(DexItemFactory dexItemFactory) {
      this.dexItemFactory = dexItemFactory;
    }

    public void addInjars(List<FilteredClassPath> injars) {
      this.injars.addAll(injars);
    }

    public void addLibraryJars(List<FilteredClassPath> libraryJars) {
      this.libraryjars.addAll(libraryJars);
    }

    public PackageObfuscationMode getPackageObfuscationMode() {
      return packageObfuscationMode;
    }

    public void setPackagePrefix(String packagePrefix) {
      packageObfuscationMode = PackageObfuscationMode.REPACKAGE;
      this.packagePrefix = packagePrefix;
    }

    public void setFlattenPackagePrefix(String packagePrefix) {
      packageObfuscationMode = PackageObfuscationMode.FLATTEN;
      this.packagePrefix = packagePrefix;
    }

    public void setAllowAccessModification(boolean allowAccessModification) {
      this.allowAccessModification = allowAccessModification;
    }

    public void setIgnoreWarnings(boolean ignoreWarnings) {
      this.ignoreWarnings = ignoreWarnings;
    }

    public void setOptimizing(boolean optimizing) {
      this.optimizing = optimizing;
    }

    public void setObfuscating(boolean obfuscate) {
      this.obfuscating = obfuscate;
    }

    boolean isObfuscating() {
      return obfuscating;
    }

    public void setShrinking(boolean shrinking) {
      this.shrinking = shrinking;
    }

    public void setPrintUsage(boolean printUsage) {
      this.printUsage = printUsage;
    }

    public void setPrintUsageFile(Path printUsageFile) {
      this.printUsageFile = printUsageFile;
    }

    public void setPrintMapping(boolean printMapping) {
      this.printMapping = printMapping;
    }

    public void setPrintMappingFile(Path file) {
      this.printMappingFile = file;
    }

    public void setApplyMappingFile(Path file) {
      this.applyMappingFile = file;
    }

    public void setVerbose(boolean verbose) {
      this.verbose = verbose;
    }

    public void setRenameSourceFileAttribute(String renameSourceFileAttribute) {
      this.renameSourceFileAttribute = renameSourceFileAttribute;
    }

    public void addKeepAttributePatterns(List<String> keepAttributePatterns) {
      this.keepAttributePatterns.addAll(keepAttributePatterns);
    }

    public void addRule(ProguardConfigurationRule rule) {
      this.rules.add(rule);
    }

    public void setDontWarnPatterns(ProguardClassNameList patterns) {
      dontWarnPatterns = patterns;
    }

    public void setSeedFile(Path seedFile) {
      this.seedFile = seedFile;
    }

    public void setPrintSeeds(boolean printSeeds) {
      this.printSeeds = printSeeds;
    }

    public void setObfuscationDictionary(Path obfuscationDictionary) {
      this.obfuscationDictionary = obfuscationDictionary;
    }

    public void setClassObfuscationDictionary(Path classObfuscationDictionary) {
      this.classObfuscationDictionary = classObfuscationDictionary;
    }

    public void setPackageObfuscationDictionary(Path packageObfuscationDictionary) {
      this.packageObfuscationDictionary = packageObfuscationDictionary;
    }

    public void setUseUniqueClassMemberNames(boolean useUniqueClassMemberNames) {
      this.useUniqueClassMemberNames = useUniqueClassMemberNames;
    }

    boolean isUseUniqueClassMemberNames() {
      return useUniqueClassMemberNames;
    }

    public void setKeepParameterNames(boolean keepParameterNames) {
      this.keepParameterNames = keepParameterNames;
    }

    boolean isKeepParameterNames() {
      return keepParameterNames;
    }

    public ProguardConfiguration build() throws CompilationException {
      return new ProguardConfiguration(
          dexItemFactory,
          injars,
          libraryjars,
          packageObfuscationMode,
          packagePrefix,
          allowAccessModification,
          ignoreWarnings,
          optimizing,
          obfuscating,
          shrinking,
          printUsage,
          printUsageFile,
          printMapping,
          printMappingFile,
          applyMappingFile,
          verbose,
          renameSourceFileAttribute,
          keepAttributePatterns,
          dontWarnPatterns,
          rules,
          printSeeds,
          seedFile,
          DictionaryReader.readAllNames(obfuscationDictionary),
          DictionaryReader.readAllNames(classObfuscationDictionary),
          DictionaryReader.readAllNames(packageObfuscationDictionary),
          useUniqueClassMemberNames,
          keepParameterNames);
    }
  }

  private final DexItemFactory dexItemFactory;
  private final ImmutableList<FilteredClassPath> injars;
  private final ImmutableList<FilteredClassPath> libraryjars;
  private final PackageObfuscationMode packageObfuscationMode;
  private final String packagePrefix;
  private final boolean allowAccessModification;
  private final boolean ignoreWarnings;
  private final boolean optimizing;
  private final boolean obfuscating;
  private final boolean shrinking;
  private final boolean printUsage;
  private final Path printUsageFile;
  private final boolean printMapping;
  private final Path printMappingFile;
  private final Path applyMappingFile;
  private final boolean verbose;
  private final String renameSourceFileAttribute;
  private final ImmutableList<String> keepAttributesPatterns;
  private final ProguardClassNameList dontWarnPatterns;
  protected final ImmutableList<ProguardConfigurationRule> rules;
  private final boolean printSeeds;
  private final Path seedFile;
  private final ImmutableList<String> obfuscationDictionary;
  private final ImmutableList<String> classObfuscationDictionary;
  private final ImmutableList<String> packageObfuscationDictionary;
  private boolean useUniqueClassMemberNames;
  private boolean keepParameterNames;

  private ProguardConfiguration(
      DexItemFactory factory,
      List<FilteredClassPath> injars,
      List<FilteredClassPath> libraryjars,
      PackageObfuscationMode packageObfuscationMode,
      String packagePrefix,
      boolean allowAccessModification,
      boolean ignoreWarnings,
      boolean optimizing,
      boolean obfuscating,
      boolean shrinking,
      boolean printUsage,
      Path printUsageFile,
      boolean printMapping,
      Path printMappingFile,
      Path applyMappingFile,
      boolean verbose,
      String renameSourceFileAttribute,
      List<String> keepAttributesPatterns,
      ProguardClassNameList dontWarnPatterns,
      List<ProguardConfigurationRule> rules,
      boolean printSeeds,
      Path seedFile,
      ImmutableList<String> obfuscationDictionary,
      ImmutableList<String> classObfuscationDictionary,
      ImmutableList<String> packageObfuscationDictionary,
      boolean useUniqueClassMemberNames,
      boolean keepParameterNames) {
    this.dexItemFactory = factory;
    this.injars = ImmutableList.copyOf(injars);
    this.libraryjars = ImmutableList.copyOf(libraryjars);
    this.packageObfuscationMode = packageObfuscationMode;
    this.packagePrefix = packagePrefix;
    this.allowAccessModification = allowAccessModification;
    this.ignoreWarnings = ignoreWarnings;
    this.optimizing = optimizing;
    this.obfuscating = obfuscating;
    this.shrinking = shrinking;
    this.printUsage = printUsage;
    this.printUsageFile = printUsageFile;
    this.printMapping = printMapping;
    this.printMappingFile = printMappingFile;
    this.applyMappingFile = applyMappingFile;
    this.verbose = verbose;
    this.renameSourceFileAttribute = renameSourceFileAttribute;
    this.keepAttributesPatterns = ImmutableList.copyOf(keepAttributesPatterns);
    this.dontWarnPatterns = dontWarnPatterns;
    this.rules = ImmutableList.copyOf(rules);
    this.printSeeds = printSeeds;
    this.seedFile = seedFile;
    this.obfuscationDictionary = obfuscationDictionary;
    this.classObfuscationDictionary = classObfuscationDictionary;
    this.packageObfuscationDictionary = packageObfuscationDictionary;
    this.useUniqueClassMemberNames = useUniqueClassMemberNames;
    this.keepParameterNames = keepParameterNames;
  }

  /**
   * Create a new empty builder.
   */
  public static Builder builder(DexItemFactory dexItemFactory) {
    return new Builder(dexItemFactory);
  }

  public DexItemFactory getDexItemFactory() {
    return dexItemFactory;
  }

  public boolean isDefaultConfiguration() {
    return false;
  }

  public ImmutableList<FilteredClassPath> getInjars() {
    return injars;
  }

  public ImmutableList<FilteredClassPath> getLibraryjars() {
    return libraryjars;
  }

  public PackageObfuscationMode getPackageObfuscationMode() {
    return packageObfuscationMode;
  }

  public String getPackagePrefix() {
    return packagePrefix;
  }

  public boolean isAccessModificationAllowed() {
    return allowAccessModification;
  }

  public boolean isPrintMapping() {
    return printMapping;
  }

  public Path getPrintMappingFile() {
    return printMappingFile;
  }

  public boolean hasApplyMappingFile() {
    return applyMappingFile != null;
  }

  public Path getApplyMappingFile() {
    return applyMappingFile;
  }

  public boolean isIgnoreWarnings() {
    return ignoreWarnings;
  }

  public boolean isOptimizing() {
    return optimizing;
  }

  public boolean isObfuscating() {
    return obfuscating;
  }

  public boolean isShrinking() {
    return shrinking;
  }

  public boolean isPrintUsage() {
    return printUsage;
  }

  public Path getPrintUsageFile() {
    return printUsageFile;
  }

  public boolean isVerbose() {
    return verbose;
  }

  public String getRenameSourceFileAttribute() {
    return renameSourceFileAttribute;
  }

  public ImmutableList<String> getKeepAttributesPatterns() {
    return keepAttributesPatterns;
  }

  public ProguardClassNameList getDontWarnPatterns() {
    return dontWarnPatterns;
  }

  public ImmutableList<ProguardConfigurationRule> getRules() {
    return rules;
  }

  public ImmutableList<String> getObfuscationDictionary() {
    return obfuscationDictionary;
  }

  public ImmutableList<String> getClassObfuscationDictionary() {
    return classObfuscationDictionary;
  }

  public ImmutableList<String> getPackageObfuscationDictionary() {
    return packageObfuscationDictionary;
  }

  public boolean isUseUniqueClassMemberNames() {
    return useUniqueClassMemberNames;
  }

  public boolean isKeepParameterNames() {
    return keepParameterNames;
  }

  public static ProguardConfiguration defaultConfiguration(DexItemFactory dexItemFactory) {
    return new DefaultProguardConfiguration(dexItemFactory);
  }

  public static class DefaultProguardConfiguration extends ProguardConfiguration {

    public DefaultProguardConfiguration(DexItemFactory factory) {
      super(factory,
          ImmutableList.of()    /* injars */,
          ImmutableList.of()    /* libraryjars */,
          PackageObfuscationMode.NONE,
          ""                    /* package prefix */,
          false                 /* allowAccessModification */,
          false                 /* ignoreWarnings */,
          true                  /* optimizing */,
          false                 /* obfuscating */,
          false                 /* shrinking */,
          false                 /* printUsage */,
          null                  /* printUsageFile */,
          false                 /* printMapping */,
          null                  /* printMappingFile */,
          null                  /* applyMapping */,
          false                 /* verbose */,
          null                  /* renameSourceFileAttribute */,
          KeepAttributeOptions.KEEP_ALL,
          ProguardClassNameList.emptyList(),
          ImmutableList.of(ProguardKeepRule.defaultKeepAllRule()),
          false                 /* printSeeds */,
          null                  /* seedFile */,
          ImmutableList.of()    /* obfuscationDictionary */,
          ImmutableList.of()    /* classObfuscationDictionary */,
          ImmutableList.of()    /* packageObfuscationDictionary */,
          false                 /* useUniqueClassMemberNames*/,
          false                 /* keepParameterNames */);
    }

    @Override
    public boolean isDefaultConfiguration() {
      return true;
    }
  }

  public boolean isPrintSeeds() {
    return printSeeds;
  }

  public Path getSeedFile() {
    return seedFile;
  }
}
