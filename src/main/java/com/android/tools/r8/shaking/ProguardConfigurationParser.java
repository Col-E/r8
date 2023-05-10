// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.shaking.ProguardKeepAttributes.RUNTIME_INVISIBLE_ANNOTATIONS;
import static com.android.tools.r8.shaking.ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS;
import static com.android.tools.r8.utils.DescriptorUtils.javaTypeToDescriptor;

import com.android.tools.r8.InputDependencyGraphConsumer;
import com.android.tools.r8.Version;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.position.TextRange;
import com.android.tools.r8.shaking.ProguardConfiguration.Builder;
import com.android.tools.r8.shaking.ProguardTypeMatcher.ClassOrType;
import com.android.tools.r8.shaking.ProguardWildcard.BackReference;
import com.android.tools.r8.shaking.ProguardWildcard.Pattern;
import com.android.tools.r8.utils.IdentifierUtils;
import com.android.tools.r8.utils.InternalOptions.PackageObfuscationMode;
import com.android.tools.r8.utils.LongInterval;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingAction;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

public class ProguardConfigurationParser {

  private final Builder configurationBuilder;
  private final DexItemFactory dexItemFactory;
  private final ProguardConfigurationParserOptions options;
  private final Reporter reporter;
  private final InputDependencyGraphConsumer inputDependencyConsumer;

  public static final String FLATTEN_PACKAGE_HIERARCHY = "flattenpackagehierarchy";
  public static final String REPACKAGE_CLASSES = "repackageclasses";

  private static final List<String> IGNORED_SINGLE_ARG_OPTIONS = ImmutableList.of(
      "protomapping",
      "target",
      "maximuminlinedcodelength");

  private static final List<String> IGNORED_OPTIONAL_SINGLE_ARG_OPTIONS =
      ImmutableList.of("runtype", "laststageoutput");

  private static final List<String> IGNORED_FLAG_OPTIONS =
      ImmutableList.of(
          "forceprocessing",
          "dontpreverify",
          "experimentalshrinkunusedprotofields",
          "filterlibraryjarswithorginalprogramjars",
          "dontskipnonpubliclibraryclasses",
          "dontskipnonpubliclibraryclassmembers",
          "invokebasemethod",
          "overloadaggressively",
          "mergeinterfacesaggressively",
          "android",
          "allowruntypeandignoreoptimizationpasses",
          "dontshrinkduringoptimization",
          "convert_proto_enum_to_string",
          "adaptkotlinmetadata");

  private static final List<String> IGNORED_CLASS_DESCRIPTOR_OPTIONS =
      ImmutableList.of("isclassnamestring", "whyarenotsimple");

  private static final List<String> WARNED_SINGLE_ARG_OPTIONS = ImmutableList.of(
      // TODO(b/37137994): -outjars should be reported as errors, not just as warnings!
      "outjars");

  private static final List<String> WARNED_OPTIONAL_SINGLE_ARG_OPTIONS = ImmutableList.of(
      // TODO(b/121340442): we may support this later.
      "dump");

  private static final List<String> WARNED_FLAG_OPTIONS =
      ImmutableList.of("useuniqueclassmembernames");

  private static final List<String> WARNED_CLASS_DESCRIPTOR_OPTIONS = ImmutableList.of(
      // TODO(b/73708157): add support -assumenoexternalsideeffects <class_spec>
      "assumenoexternalsideeffects",
      // TODO(b/73707404): add support -assumenoescapingparameters <class_spec>
      "assumenoescapingparameters",
      // TODO(b/73708085): add support -assumenoexternalreturnvalues <class_spec>
      "assumenoexternalreturnvalues");

  // Those options are unsupported and are treated as compilation errors.
  // Just ignoring them would produce outputs incompatible with user expectations.
  private static final List<String> UNSUPPORTED_FLAG_OPTIONS =
      ImmutableList.of("skipnonpubliclibraryclasses");

  public static ImmutableList<ProguardConfigurationRule> parse(
      List<ProguardConfigurationSource> sources, DexItemFactory factory, Reporter reporter) {
    if (sources.isEmpty()) {
      return ImmutableList.of();
    }
    ProguardConfigurationParser parser = new ProguardConfigurationParser(factory, reporter);
    parser.parse(sources);
    return ImmutableList.copyOf(parser.getConfig().getRules());
  }

  public ProguardConfigurationParser(
      DexItemFactory dexItemFactory, Reporter reporter) {
    this(
        dexItemFactory,
        reporter,
        ProguardConfigurationParserOptions.builder()
            .setEnableExperimentalCheckEnumUnboxed(false)
            .setEnableExperimentalConvertCheckNotNull(false)
            .setEnableExperimentalWhyAreYouNotInlining(false)
            .setEnableTestingOptions(false)
            .build());
  }

  public ProguardConfigurationParser(
      DexItemFactory dexItemFactory,
      Reporter reporter,
      ProguardConfigurationParserOptions options) {
    this(dexItemFactory, reporter, options, null);
  }

  public ProguardConfigurationParser(
      DexItemFactory dexItemFactory,
      Reporter reporter,
      ProguardConfigurationParserOptions options,
      InputDependencyGraphConsumer inputDependencyConsumer) {
    this.configurationBuilder = ProguardConfiguration.builder(dexItemFactory, reporter);
    this.dexItemFactory = dexItemFactory;
    this.options = options;
    this.reporter = reporter;
    this.inputDependencyConsumer =
        inputDependencyConsumer != null
            ? inputDependencyConsumer
            : emptyInputDependencyGraphConsumer();
  }

  private static InputDependencyGraphConsumer emptyInputDependencyGraphConsumer() {
    return new InputDependencyGraphConsumer() {
      @Override
      public void accept(Origin dependent, Path dependency) {
        // ignored.
      }

      @Override
      public void finished() {
        // ignored.
      }
    };
  }

  public ProguardConfiguration.Builder getConfigurationBuilder() {
    return configurationBuilder;
  }

  private void validate() {
    if (configurationBuilder.isKeepParameterNames() && configurationBuilder.isObfuscating()) {
      // The flag -keepparameternames has only effect when minifying, so ignore it if we
      // are not.
      throw reporter.fatalError(new StringDiagnostic(
          "-keepparameternames is not supported",
          configurationBuilder.getKeepParameterNamesOptionOrigin(),
          configurationBuilder.getKeepParameterNamesOptionPosition()));
    }
    if (configurationBuilder.isConfigurationDebugging()) {
      String suffix = "due to the use of -addconfigurationdebugging";
      if (configurationBuilder.isObfuscating()) {
        reporter.info(
            new StringDiagnostic("Build is not being obfuscated " + suffix));
        configurationBuilder.disableObfuscation();
      }
      if (configurationBuilder.hasApplyMappingFile()) {
        reporter.info(
            new StringDiagnostic(
                "Applying the obfuscation map (-applymapping) is disabled " + suffix));
        configurationBuilder.setApplyMappingFile(null);
      }
    }
  }

  /**
   * Returns the Proguard configuration with default rules derived from empty rules added.
   */
  public ProguardConfiguration getConfig() {
    validate();
    return configurationBuilder.build();
  }

  /**
   * Returns the Proguard configuration from exactly the rules parsed, without any
   * defaults derived from empty rules.
   */
  public ProguardConfiguration getConfigRawForTesting() {
    validate();
    return configurationBuilder.buildRaw();
  }

  public void parse(Path path) {
    parse(ImmutableList.of(new ProguardConfigurationSourceFile(path)));
  }

  public void parse(ProguardConfigurationSource source) {
    parse(ImmutableList.of(source));
  }

  public void parse(List<ProguardConfigurationSource> sources) {
    for (ProguardConfigurationSource source : sources) {
      try {
        new ProguardConfigurationSourceParser(source).parse();
      } catch (IOException e) {
        reporter.error(new StringDiagnostic("Failed to read file: " + e.getMessage(),
            source.getOrigin()));
      } catch (ProguardRuleParserException e) {
        reporter.error(e);
      }
    }
    reporter.failIfPendingErrors();
  }

  private enum IdentifierType {
    PACKAGE_NAME,
    CLASS_NAME,
    ANY
  }

  private class ProguardConfigurationSourceParser {
    private final String name;
    private final String contents;
    private int position = 0;
    private int positionAfterInclude = 0;
    private int line = 1;
    private int lineStartPosition = 0;
    private Path baseDirectory;
    private final Origin origin;

    ProguardConfigurationSourceParser(ProguardConfigurationSource source) throws IOException {
      // Strip any leading BOM here so it is not included in the text position.
      contents = StringUtils.stripLeadingBOM(source.get());
      baseDirectory = source.getBaseDirectory();
      name = source.getName();
      this.origin = source.getOrigin();
    }

    public void parse() throws ProguardRuleParserException {
      do {
        skipWhitespace();
      } while (parseOption());
      // This may be unknown, but we want to always ensure that we don't attribute lines to the
      // wrong configuration.
      configurationBuilder.addParsedConfiguration(
          "# The proguard configuration file for the following section is " + origin.toString());

      // Collect the parsed configuration.
      configurationBuilder.addParsedConfiguration(contents.substring(positionAfterInclude));
      configurationBuilder.addParsedConfiguration("# End of content from " + origin.toString());
    }

    private boolean parseOption() throws ProguardRuleParserException {
      if (eof()) {
        return false;
      }
      if (acceptArobaseInclude()) {
        return true;
      }
      TextPosition optionStart = getPosition();
      expectChar('-');
      if (parseIgnoredOption(optionStart)
          || parseIgnoredOptionAndWarn(optionStart)
          || parseExperimentalOption(optionStart)
          || parseTestingOption(optionStart)
          || parseUnsupportedOptionAndErr(optionStart)) {
        // Intentionally left empty.
      } else if (acceptString("keepkotlinmetadata")) {
        String source = "-keepkotlinmetadata";
        ProguardKeepRule keepKotlinMetadata =
            ProguardKeepRuleUtils.keepClassAndMembersRule(
                origin, optionStart, dexItemFactory.kotlinMetadataType, source);
        ProguardKeepRule keepKotlinJvmNameAnnotation =
            ProguardKeepRuleUtils.keepClassAndMembersRule(
                origin, optionStart, dexItemFactory.kotlinJvmNameType, source);
        // Mark the rules as used to ensure we do not report any information messages if the class
        // is not present.
        keepKotlinMetadata.markAsUsed();
        keepKotlinJvmNameAnnotation.markAsUsed();
        configurationBuilder.addRule(keepKotlinMetadata);
        configurationBuilder.addRule(keepKotlinJvmNameAnnotation);
        configurationBuilder.addKeepAttributePatterns(
            Collections.singletonList(RUNTIME_VISIBLE_ANNOTATIONS));
        configurationBuilder.addKeepAttributePatterns(
            Collections.singletonList(RUNTIME_INVISIBLE_ANNOTATIONS));
      } else if (acceptString("renamesourcefileattribute")) {
        skipWhitespace();
        if (isOptionalArgumentGiven()) {
          configurationBuilder.setRenameSourceFileAttribute(acceptQuotedOrUnquotedString());
        } else {
          configurationBuilder.setRenameSourceFileAttribute("");
        }
      } else if (acceptString("keepattributes")) {
        parseKeepAttributes();
      } else if (acceptString("keeppackagenames")) {
        parseClassFilter(configurationBuilder::addKeepPackageNamesPattern);
      } else if (acceptString("keepparameternames")) {
        configurationBuilder.setKeepParameterNames(true, origin, getPosition(optionStart));
      } else if (acceptString("checkdiscard")) {
        ProguardCheckDiscardRule rule = parseCheckDiscardRule(optionStart);
        configurationBuilder.addRule(rule);
      } else if (acceptString("checkenumstringsdiscarded")) {
        // Not supported, ignore.
        parseCheckDiscardRule(optionStart);
      } else if (acceptString("keepdirectories")) {
        configurationBuilder.enableKeepDirectories();
        parsePathFilter(configurationBuilder::addKeepDirectories);
      } else if (acceptString("keep")) {
        ProguardKeepRule rule = parseKeepRule(optionStart);
        configurationBuilder.addRule(rule);
      } else if (acceptString("whyareyoukeeping")) {
        ProguardWhyAreYouKeepingRule rule = parseWhyAreYouKeepingRule(optionStart);
        configurationBuilder.addRule(rule);
      } else if (acceptString("dontoptimize")) {
        configurationBuilder.disableOptimization();
      } else if (acceptString("optimizationpasses")) {
        skipWhitespace();
        Integer expectedOptimizationPasses = acceptInteger();
        if (expectedOptimizationPasses == null) {
          throw reporter.fatalError(new StringDiagnostic(
              "Missing n of \"-optimizationpasses n\"", origin, getPosition(optionStart)));
        }
        infoIgnoringOptions("optimizationpasses", optionStart);
      } else if (acceptString("dontobfuscate")) {
        configurationBuilder.disableObfuscation();
      } else if (acceptString("dontshrink")) {
        configurationBuilder.disableShrinking();
      } else if (acceptString("printusage")) {
        configurationBuilder.setPrintUsage(true);
        skipWhitespace();
        if (isOptionalArgumentGiven()) {
          configurationBuilder.setPrintUsageFile(parseFileName(false));
        }
      } else if (acceptString("shrinkunusedprotofields")) {
        configurationBuilder.enableProtoShrinking();
      } else if (acceptString("verbose")) {
        configurationBuilder.setVerbose(true);
      } else if (acceptString("ignorewarnings")) {
        configurationBuilder.setIgnoreWarnings(true);
      } else if (acceptString("dontwarn")) {
        parseClassFilter(configurationBuilder::addDontWarnPattern);
      } else if (acceptString("dontnote")) {
        parseClassFilter(configurationBuilder::addDontNotePattern);
      } else if (acceptString(REPACKAGE_CLASSES)) {
        if (configurationBuilder.getPackageObfuscationMode() == PackageObfuscationMode.FLATTEN) {
          warnOverridingOptions(REPACKAGE_CLASSES, FLATTEN_PACKAGE_HIERARCHY, optionStart);
        }
        skipWhitespace();
        char quote = acceptQuoteIfPresent();
        if (isQuote(quote)) {
          configurationBuilder.setPackagePrefix(parsePackageNameOrEmptyString());
          expectClosingQuote(quote);
        } else {
          if (hasNextChar('-')) {
            configurationBuilder.setPackagePrefix("");
          } else {
            configurationBuilder.setPackagePrefix(parsePackageNameOrEmptyString());
          }
        }
      } else if (acceptString(FLATTEN_PACKAGE_HIERARCHY)) {
        if (configurationBuilder.getPackageObfuscationMode() == PackageObfuscationMode.REPACKAGE) {
          warnOverridingOptions(REPACKAGE_CLASSES, FLATTEN_PACKAGE_HIERARCHY, optionStart);
          skipWhitespace();
          if (isOptionalArgumentGiven()) {
            skipSingleArgument();
          }
        } else {
          skipWhitespace();
          char quote = acceptQuoteIfPresent();
          if (isQuote(quote)) {
            configurationBuilder.setFlattenPackagePrefix(parsePackageNameOrEmptyString());
            expectClosingQuote(quote);
          } else {
            if (hasNextChar('-')) {
              configurationBuilder.setFlattenPackagePrefix("");
            } else {
              configurationBuilder.setFlattenPackagePrefix(parsePackageNameOrEmptyString());
            }
          }
        }
      } else if (acceptString("allowaccessmodification")) {
        configurationBuilder.setAllowAccessModification(true);
      } else if (acceptString("printconfiguration")) {
        configurationBuilder.setPrintConfiguration(true);
        skipWhitespace();
        if (isOptionalArgumentGiven()) {
          configurationBuilder.setPrintConfigurationFile(parseFileName(false));
        }
      } else if (acceptString("printmapping")) {
        configurationBuilder.setPrintMapping(true);
        skipWhitespace();
        if (isOptionalArgumentGiven()) {
          configurationBuilder.setPrintMappingFile(parseFileName(false));
        }
      } else if (acceptString("applymapping")) {
        configurationBuilder.setApplyMappingFile(
            parseFileInputDependency(inputDependencyConsumer::acceptProguardApplyMapping));
      } else if (acceptString("assumenosideeffects")) {
        ProguardAssumeNoSideEffectRule rule = parseAssumeNoSideEffectsRule(optionStart);
        configurationBuilder.addRule(rule);
      } else if (acceptString("assumevalues")) {
        ProguardAssumeValuesRule rule = parseAssumeValuesRule(optionStart);
        configurationBuilder.addRule(rule);
      } else if (acceptString("include")) {
        // Collect the parsed configuration until the include.
        configurationBuilder.addParsedConfiguration(
            contents.substring(positionAfterInclude, position - ("include".length() + 1)));
        skipWhitespace();
        parseInclude();
        positionAfterInclude = position;
      } else if (acceptString("basedirectory")) {
        skipWhitespace();
        baseDirectory = parseFileName(false);
      } else if (acceptString("injars")) {
        configurationBuilder.addInjars(
            parseClassPath(inputDependencyConsumer::acceptProguardInJars));
      } else if (acceptString("libraryjars")) {
        configurationBuilder.addLibraryJars(
            parseClassPath(inputDependencyConsumer::acceptProguardLibraryJars));
      } else if (acceptString("printseeds")) {
        configurationBuilder.setPrintSeeds(true);
        skipWhitespace();
        if (isOptionalArgumentGiven()) {
          configurationBuilder.setSeedFile(parseFileName(false));
        }
      } else if (acceptString("obfuscationdictionary")) {
        configurationBuilder.setObfuscationDictionary(
            parseFileInputDependency(inputDependencyConsumer::acceptProguardObfuscationDictionary));
      } else if (acceptString("classobfuscationdictionary")) {
        configurationBuilder.setClassObfuscationDictionary(
            parseFileInputDependency(
                inputDependencyConsumer::acceptProguardClassObfuscationDictionary));
      } else if (acceptString("packageobfuscationdictionary")) {
        configurationBuilder.setPackageObfuscationDictionary(
            parseFileInputDependency(
                inputDependencyConsumer::acceptProguardPackageObfuscationDictionary));
      } else if (acceptString("alwaysinline")) {
        InlineRule rule = parseInlineRule(InlineRule.Type.ALWAYS, optionStart);
        configurationBuilder.addRule(rule);
      } else if (acceptString("adaptclassstrings")) {
        parseClassFilter(configurationBuilder::addAdaptClassStringsPattern);
      } else if (acceptString("adaptresourcefilenames")) {
        parsePathFilter(configurationBuilder::addAdaptResourceFilenames);
      } else if (acceptString("adaptresourcefilecontents")) {
        parsePathFilter(configurationBuilder::addAdaptResourceFileContents);
      } else if (acceptString("identifiernamestring")) {
        configurationBuilder.addRule(parseIdentifierNameStringRule(optionStart));
      } else if (acceptString("if")) {
        configurationBuilder.addRule(parseIfRule(optionStart));
      } else if (acceptString("addconfigurationdebugging")) {
        configurationBuilder.setConfigurationDebugging(true);
      } else if (acceptString("dontusemixedcaseclassnames")) {
        configurationBuilder.setDontUseMixedCaseClassnames(true);
      } else if (parseMaximumRemovedAndroidLogLevelRule(optionStart)) {
        return true;
      } else {
        String unknownOption = acceptString();
        String devMessage = "";
        if (Version.isDevelopmentVersion()
            && unknownOption != null
            && unknownOption.equals("neverinline")) {
          devMessage = ", this option needs to be turned on explicitly if used for tests.";
        }
        throw unknownOption(unknownOption, optionStart, devMessage);
      }
      return true;
    }

    private boolean parseExperimentalOption(TextPosition optionStart)
        throws ProguardRuleParserException {
      if (acceptString(CheckEnumUnboxedRule.RULE_NAME)) {
        CheckEnumUnboxedRule checkEnumUnboxedRule = parseCheckEnumUnboxedRule(optionStart);
        if (options.isExperimentalCheckEnumUnboxedEnabled()) {
          configurationBuilder.addRule(checkEnumUnboxedRule);
        }
        return true;
      }
      if (acceptString(ConvertCheckNotNullRule.RULE_NAME)) {
        ConvertCheckNotNullRule convertCheckNotNullRule = parseConvertCheckNotNullRule(optionStart);
        if (options.isExperimentalConvertCheckNotNullEnabled()) {
          configurationBuilder.addRule(convertCheckNotNullRule);
        }
        return true;
      }
      if (options.isExperimentalWhyAreYouNotInliningEnabled()) {
        if (acceptString(WhyAreYouNotInliningRule.RULE_NAME)) {
          configurationBuilder.addRule(parseWhyAreYouNotInliningRule(optionStart));
          return true;
        }
      }
      return false;
    }

    private boolean parseTestingOption(TextPosition optionStart)
        throws ProguardRuleParserException {
      if (options.isTestingOptionsEnabled()) {
        if (acceptString("assumemayhavesideeffects")) {
          ProguardAssumeMayHaveSideEffectsRule rule =
              parseAssumeMayHaveSideEffectsRule(optionStart);
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString(KeepConstantArgumentRule.RULE_NAME)) {
          KeepConstantArgumentRule rule =
              parseNoOptimizationRule(optionStart, KeepConstantArgumentRule.builder());
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString(KeepUnusedArgumentRule.RULE_NAME)) {
          KeepUnusedArgumentRule rule =
              parseNoOptimizationRule(optionStart, KeepUnusedArgumentRule.builder());
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString(KeepUnusedReturnValueRule.RULE_NAME)) {
          KeepUnusedReturnValueRule rule =
              parseNoOptimizationRule(optionStart, KeepUnusedReturnValueRule.builder());
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString("alwaysclassinline")) {
          ClassInlineRule rule = parseClassInlineRule(ClassInlineRule.Type.ALWAYS, optionStart);
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString("neverclassinline")) {
          ClassInlineRule rule = parseClassInlineRule(ClassInlineRule.Type.NEVER, optionStart);
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString("neverinline")) {
          InlineRule rule = parseInlineRule(InlineRule.Type.NEVER, optionStart);
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString("neversinglecallerinline")) {
          InlineRule rule = parseInlineRule(InlineRule.Type.NEVER_SINGLE_CALLER, optionStart);
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString(NoFieldTypeStrengtheningRule.RULE_NAME)) {
          ProguardConfigurationRule rule = parseNoFieldTypeStrengtheningRule(optionStart);
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString(NoUnusedInterfaceRemovalRule.RULE_NAME)) {
          ProguardConfigurationRule rule = parseNoUnusedInterfaceRemovalRule(optionStart);
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString(NoVerticalClassMergingRule.RULE_NAME)) {
          ProguardConfigurationRule rule = parseNoVerticalClassMergingRule(optionStart);
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString(NoHorizontalClassMergingRule.RULE_NAME)) {
          ProguardConfigurationRule rule = parseNoHorizontalClassMergingRule(optionStart);
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString(NoMethodStaticizingRule.RULE_NAME)) {
          ProguardConfigurationRule rule =
              parseNoOptimizationRule(optionStart, NoMethodStaticizingRule.builder());
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString(NoParameterReorderingRule.RULE_NAME)) {
          ProguardConfigurationRule rule =
              parseNoOptimizationRule(optionStart, NoParameterReorderingRule.builder());
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString(NoParameterTypeStrengtheningRule.RULE_NAME)) {
          ProguardConfigurationRule rule =
              parseNoOptimizationRule(optionStart, NoParameterTypeStrengtheningRule.builder());
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString(NoRedundantFieldLoadEliminationRule.RULE_NAME)) {
          ProguardConfigurationRule rule =
              parseNoOptimizationRule(optionStart, NoRedundantFieldLoadEliminationRule.builder());
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString(NoReturnTypeStrengtheningRule.RULE_NAME)) {
          ProguardConfigurationRule rule =
              parseNoOptimizationRule(optionStart, NoReturnTypeStrengtheningRule.builder());
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString("neverpropagatevalue")) {
          MemberValuePropagationRule rule =
              parseMemberValuePropagationRule(MemberValuePropagationRule.Type.NEVER, optionStart);
          configurationBuilder.addRule(rule);
          return true;
        }
        if (acceptString("neverreprocessclassinitializer")) {
          configurationBuilder.addRule(
              parseReprocessClassInitializerRule(
                  ReprocessClassInitializerRule.Type.NEVER, optionStart));
          return true;
        }
        if (acceptString("neverreprocessmethod")) {
          configurationBuilder.addRule(
              parseReprocessMethodRule(ReprocessMethodRule.Type.NEVER, optionStart));
          return true;
        }
        if (acceptString("reprocessclassinitializer")) {
          configurationBuilder.addRule(
              parseReprocessClassInitializerRule(
                  ReprocessClassInitializerRule.Type.ALWAYS, optionStart));
          return true;
        }
        if (acceptString("reprocessmethod")) {
          configurationBuilder.addRule(
              parseReprocessMethodRule(ReprocessMethodRule.Type.ALWAYS, optionStart));
          return true;
        }
      }
      return false;
    }

    private RuntimeException unknownOption(String unknownOption, TextPosition optionStart) {
      throw unknownOption(unknownOption, optionStart, "");
    }

    private RuntimeException unknownOption(
        String unknownOption, TextPosition optionStart, String additionalMessage) {
      throw reporter.fatalError((new StringDiagnostic(
          "Unknown option \"-" + unknownOption + "\"" + additionalMessage,
          origin, getPosition(optionStart))));
    }

    private boolean parseUnsupportedOptionAndErr(TextPosition optionStart) {
      String option = Iterables.find(UNSUPPORTED_FLAG_OPTIONS, this::skipFlag, null);
      if (option != null) {
        reporter.error(new StringDiagnostic(
            "Unsupported option: -" + option, origin, getPosition(optionStart)));
        return true;
      }
      return false;
    }

    private boolean parseIgnoredOptionAndWarn(TextPosition optionStart) {
      String option =
          Iterables.find(WARNED_CLASS_DESCRIPTOR_OPTIONS, this::skipOptionWithClassSpec, null);
      if (option == null) {
        option = Iterables.find(WARNED_FLAG_OPTIONS, this::skipFlag, null);
        if (option == null) {
          option = Iterables.find(WARNED_SINGLE_ARG_OPTIONS, this::skipOptionWithSingleArg, null);
          if (option == null) {
            option = Iterables.find(
                WARNED_OPTIONAL_SINGLE_ARG_OPTIONS, this::skipOptionWithOptionalSingleArg, null);
            if (option == null) {
              return false;
            }
          }
        }
      }
      warnIgnoringOptions(option, optionStart);
      return true;
    }

    private boolean parseIgnoredOption(TextPosition optionStart)
        throws ProguardRuleParserException {
      return Iterables.any(IGNORED_SINGLE_ARG_OPTIONS, this::skipOptionWithSingleArg)
          || Iterables.any(
              IGNORED_OPTIONAL_SINGLE_ARG_OPTIONS, this::skipOptionWithOptionalSingleArg)
          || Iterables.any(IGNORED_FLAG_OPTIONS, this::skipFlag)
          || Iterables.any(IGNORED_CLASS_DESCRIPTOR_OPTIONS, this::skipOptionWithClassSpec)
          || parseOptimizationOption(optionStart);
    }

    private void parseInclude() throws ProguardRuleParserException {
      TextPosition start = getPosition();
      Path included = parseFileInputDependency(inputDependencyConsumer::acceptProguardInclude);
      try {
        new ProguardConfigurationSourceParser(new ProguardConfigurationSourceFile(included))
            .parse();
      } catch (FileNotFoundException | NoSuchFileException e) {
        throw parseError("Included file '" + included.toString() + "' not found",
            start, e);
      } catch (IOException e) {
        throw parseError("Failed to read included file '" + included.toString() + "'",
            start, e);
      }
    }

    private boolean acceptArobaseInclude() throws ProguardRuleParserException {
      if (remainingChars() < 2) {
        return false;
      }
      if (!acceptChar('@')) {
        return false;
      }
      parseInclude();
      return true;
    }

    private void parseKeepAttributes() throws ProguardRuleParserException {
      List<String> attributesPatterns = acceptPatternList();
      if (attributesPatterns.isEmpty()) {
        throw parseError("Expected attribute pattern list");
      }
      configurationBuilder.addKeepAttributePatterns(attributesPatterns);
    }

    private boolean skipFlag(String name) {
      if (acceptString(name)) {
        return true;
      }
      return false;
    }

    private boolean skipOptionWithSingleArg(String name) {
      if (acceptString(name)) {
        skipSingleArgument();
        return true;
      }
      return false;
    }

    private boolean skipOptionWithOptionalSingleArg(String name) {
      if (acceptString(name)) {
        skipWhitespace();
        if (isOptionalArgumentGiven()) {
          skipSingleArgument();
        }
        return true;
      }
      return false;
    }

    private boolean skipOptionWithClassSpec(String name) {
      if (acceptString(name)) {
        try {
          ProguardKeepRule.Builder keepRuleBuilder = ProguardKeepRule.builder();
          parseClassSpec(keepRuleBuilder, true);
          return true;
        } catch (ProguardRuleParserException e) {
          throw reporter.fatalError(e);
        }
      }
      return false;
    }

    private boolean parseOptimizationOption(TextPosition optionStart)
        throws ProguardRuleParserException {
      if (!acceptString("optimizations")) {
        return false;
      }
      infoIgnoringOptions("optimizations", optionStart);
      do {
        skipWhitespace();
        skipOptimizationName();
        skipWhitespace();
      } while (acceptChar(','));
      return true;
    }

    private void skipOptimizationName() throws ProguardRuleParserException {
      char quote = acceptQuoteIfPresent();
      if (isQuote(quote)) {
        skipWhitespace();
      }
      if (acceptChar('!')) {
        skipWhitespace();
      }
      acceptString(next -> Character.isAlphabetic(next) || next == '/' || next == '*');
      if (isQuote(quote)) {
        skipWhitespace();
        expectClosingQuote(quote);
      }
    }

    private void skipSingleArgument() {
      skipWhitespace();
      while (!eof() && !Character.isWhitespace(peekChar())) {
        readChar();
      }
    }

    private ProguardKeepRule parseKeepRule(Position start)
        throws ProguardRuleParserException {
      ProguardKeepRule.Builder keepRuleBuilder = ProguardKeepRule.builder()
          .setOrigin(origin)
          .setStart(start);
      parseRuleTypeAndModifiers(keepRuleBuilder);
      parseClassSpec(keepRuleBuilder);
      if (keepRuleBuilder.getMemberRules().isEmpty()) {
        // If there are no member rules, a default rule for the parameterless constructor
        // applies. So we add that here.
        ProguardMemberRule.Builder defaultRuleBuilder = ProguardMemberRule.builder();
        defaultRuleBuilder.setName(
            IdentifierPatternWithWildcards.withoutWildcards(Constants.INSTANCE_INITIALIZER_NAME));
        defaultRuleBuilder.setRuleType(ProguardMemberType.INIT);
        defaultRuleBuilder.setArguments(Collections.emptyList());
        keepRuleBuilder.getMemberRules().add(defaultRuleBuilder.build());
      }
      Position end = getPosition();
      keepRuleBuilder.setSource(getSourceSnippet(contents, start, end));
      keepRuleBuilder.setEnd(end);
      return keepRuleBuilder.build();
    }

    private ProguardWhyAreYouKeepingRule parseWhyAreYouKeepingRule(Position start)
        throws ProguardRuleParserException {
      ProguardWhyAreYouKeepingRule.Builder keepRuleBuilder = ProguardWhyAreYouKeepingRule.builder()
          .setOrigin(origin)
          .setStart(start);
      parseClassSpec(keepRuleBuilder);
      Position end = getPosition();
      keepRuleBuilder.setSource(getSourceSnippet(contents, start, end));
      keepRuleBuilder.setEnd(end);
      return keepRuleBuilder.build();
    }

    private ProguardCheckDiscardRule parseCheckDiscardRule(Position start)
        throws ProguardRuleParserException {
      ProguardCheckDiscardRule.Builder keepRuleBuilder = ProguardCheckDiscardRule.builder()
          .setOrigin(origin)
          .setStart(start);
      parseClassSpec(keepRuleBuilder);
      Position end = getPosition();
      keepRuleBuilder.setSource(getSourceSnippet(contents, start, end));
      keepRuleBuilder.setEnd(end);
      return keepRuleBuilder.build();
    }

    private ClassInlineRule parseClassInlineRule(ClassInlineRule.Type type, Position start)
        throws ProguardRuleParserException {
      ClassInlineRule.Builder keepRuleBuilder =
          ClassInlineRule.builder().setOrigin(origin).setStart(start).setType(type);
      parseClassSpec(keepRuleBuilder);
      Position end = getPosition();
      keepRuleBuilder.setSource(getSourceSnippet(contents, start, end));
      keepRuleBuilder.setEnd(end);
      return keepRuleBuilder.build();
    }

    private NoFieldTypeStrengtheningRule parseNoFieldTypeStrengtheningRule(Position start)
        throws ProguardRuleParserException {
      NoFieldTypeStrengtheningRule.Builder keepRuleBuilder =
          NoFieldTypeStrengtheningRule.builder().setOrigin(origin).setStart(start);
      parseClassSpec(keepRuleBuilder);
      Position end = getPosition();
      keepRuleBuilder.setSource(getSourceSnippet(contents, start, end));
      keepRuleBuilder.setEnd(end);
      return keepRuleBuilder.build();
    }

    private NoUnusedInterfaceRemovalRule parseNoUnusedInterfaceRemovalRule(Position start)
        throws ProguardRuleParserException {
      NoUnusedInterfaceRemovalRule.Builder keepRuleBuilder =
          NoUnusedInterfaceRemovalRule.builder().setOrigin(origin).setStart(start);
      parseClassSpec(keepRuleBuilder);
      Position end = getPosition();
      keepRuleBuilder.setSource(getSourceSnippet(contents, start, end));
      keepRuleBuilder.setEnd(end);
      return keepRuleBuilder.build();
    }

    private NoVerticalClassMergingRule parseNoVerticalClassMergingRule(Position start)
        throws ProguardRuleParserException {
      NoVerticalClassMergingRule.Builder keepRuleBuilder =
          NoVerticalClassMergingRule.builder().setOrigin(origin).setStart(start);
      parseClassSpec(keepRuleBuilder);
      Position end = getPosition();
      keepRuleBuilder.setSource(getSourceSnippet(contents, start, end));
      keepRuleBuilder.setEnd(end);
      return keepRuleBuilder.build();
    }

    private NoHorizontalClassMergingRule parseNoHorizontalClassMergingRule(Position start)
        throws ProguardRuleParserException {
      NoHorizontalClassMergingRule.Builder keepRuleBuilder =
          NoHorizontalClassMergingRule.builder().setOrigin(origin).setStart(start);
      parseClassSpec(keepRuleBuilder);
      Position end = getPosition();
      keepRuleBuilder.setSource(getSourceSnippet(contents, start, end));
      keepRuleBuilder.setEnd(end);
      return keepRuleBuilder.build();
    }

    private <R extends NoOptimizationBaseRule<R>, B extends NoOptimizationBaseRule.Builder<R, B>>
        R parseNoOptimizationRule(Position start, B builder) throws ProguardRuleParserException {
      builder.setOrigin(origin).setStart(start);
      parseClassSpec(builder);
      Position end = getPosition();
      builder.setSource(getSourceSnippet(contents, start, end));
      builder.setEnd(end);
      return builder.build();
    }

    private MemberValuePropagationRule parseMemberValuePropagationRule(
        MemberValuePropagationRule.Type type, Position start)
        throws ProguardRuleParserException {
      MemberValuePropagationRule .Builder keepRuleBuilder =
          MemberValuePropagationRule.builder().setOrigin(origin).setStart(start).setType(type);
      parseClassSpec(keepRuleBuilder);
      Position end = getPosition();
      keepRuleBuilder.setSource(getSourceSnippet(contents, start, end));
      keepRuleBuilder.setEnd(end);
      return keepRuleBuilder.build();
    }

    private InlineRule parseInlineRule(InlineRule.Type type, Position start)
        throws ProguardRuleParserException {
      InlineRule.Builder keepRuleBuilder = InlineRule.builder()
          .setOrigin(origin)
          .setStart(start)
          .setType(type);
      parseClassSpec(keepRuleBuilder);
      Position end = getPosition();
      keepRuleBuilder.setSource(getSourceSnippet(contents, start, end));
      keepRuleBuilder.setEnd(end);
      return keepRuleBuilder.build();
    }

    private ProguardIdentifierNameStringRule parseIdentifierNameStringRule(Position start)
        throws ProguardRuleParserException {
      ProguardIdentifierNameStringRule.Builder keepRuleBuilder =
          ProguardIdentifierNameStringRule.builder()
              .setOrigin(origin)
              .setStart(start);
      parseClassSpec(keepRuleBuilder);
      Position end = getPosition();
      keepRuleBuilder.setSource(getSourceSnippet(contents, start, end));
      keepRuleBuilder.setEnd(end);
      return keepRuleBuilder.build();
    }

    private ProguardIfRule parseIfRule(TextPosition optionStart)
        throws ProguardRuleParserException {
      ProguardIfRule.Builder ifRuleBuilder = ProguardIfRule.builder()
          .setOrigin(origin)
          .setStart(optionStart);
      parseClassSpec(ifRuleBuilder);

      // Required a subsequent keep rule.
      skipWhitespace();
      Position keepStart = getPosition();
      if (acceptString("-keep")) {
        ProguardKeepRule subsequentRule = parseKeepRule(keepStart);
        ifRuleBuilder.setSubsequentRule(subsequentRule);
        Position end = getPosition();
        ifRuleBuilder.setSource(getSourceSnippet(contents, optionStart, end));
        ifRuleBuilder.setEnd(end);
        ProguardIfRule ifRule = ifRuleBuilder.build();
        verifyAndLinkBackReferences(ifRule.getWildcards());
        return ifRule;
      }
      throw reporter.fatalError(new StringDiagnostic(
          "Expecting '-keep' option after '-if' option.", origin, getPosition(optionStart)));
    }

    private boolean parseMaximumRemovedAndroidLogLevelRule(Position start)
        throws ProguardRuleParserException {
      if (acceptString("maximumremovedandroidloglevel")) {
        skipWhitespace();
        // First parse the mandatory log level int.
        Integer maxRemovedAndroidLogLevel = acceptInteger();
        if (maxRemovedAndroidLogLevel == null
            || maxRemovedAndroidLogLevel < MaximumRemovedAndroidLogLevelRule.NONE) {
          throw parseError("Expected integer greater than or equal to 1", getPosition());
        }
        MaximumRemovedAndroidLogLevelRule.Builder builder =
            MaximumRemovedAndroidLogLevelRule.builder()
                .setMaxRemovedAndroidLogLevel(maxRemovedAndroidLogLevel)
                .setOrigin(origin)
                .setStart(start);
        // Check if we can parse any class annotations or flag.
        if (parseClassAnnotationsAndFlags(builder)) {
          // Parse the remainder of the class specification.
          parseClassSpecFromClassTypeInclusive(builder, false);
        } else {
          // Otherwise check if we can parse a class name.
          parseClassType(
              builder,
              // Parse the remainder of the class specification.
              () -> parseClassSpecFromClassNameInclusive(builder, false),
              // In case of an error, move position back to the place we expected an (optional)
              // class type.
              expectedClassTypeStart -> position = expectedClassTypeStart.getOffsetAsInt());
        }
        if (builder.hasClassType()) {
          Position end = getPosition();
          configurationBuilder.addRule(
              builder.setEnd(end).setSource(getSourceSnippet(contents, start, end)).build());
        } else {
          configurationBuilder.joinMaxRemovedAndroidLogLevel(maxRemovedAndroidLogLevel);
        }
        return true;
      }
      return false;
    }

    private ReprocessClassInitializerRule parseReprocessClassInitializerRule(
        ReprocessClassInitializerRule.Type type, Position start)
        throws ProguardRuleParserException {
      ReprocessClassInitializerRule.Builder builder =
          ReprocessClassInitializerRule.builder().setOrigin(origin).setStart(start).setType(type);
      parseClassSpec(builder);
      Position end = getPosition();
      builder.setSource(getSourceSnippet(contents, start, end));
      builder.setEnd(end);
      return builder.build();
    }

    private ReprocessMethodRule parseReprocessMethodRule(
        ReprocessMethodRule.Type type, Position start) throws ProguardRuleParserException {
      ReprocessMethodRule.Builder builder =
          ReprocessMethodRule.builder().setOrigin(origin).setStart(start).setType(type);
      parseClassSpec(builder);
      Position end = getPosition();
      builder.setSource(getSourceSnippet(contents, start, end));
      builder.setEnd(end);
      return builder.build();
    }

    private WhyAreYouNotInliningRule parseWhyAreYouNotInliningRule(Position start)
        throws ProguardRuleParserException {
      WhyAreYouNotInliningRule.Builder keepRuleBuilder =
          WhyAreYouNotInliningRule.builder().setOrigin(origin).setStart(start);
      parseClassSpec(keepRuleBuilder);
      Position end = getPosition();
      keepRuleBuilder.setSource(getSourceSnippet(contents, start, end));
      keepRuleBuilder.setEnd(end);
      return keepRuleBuilder.build();
    }

    void verifyAndLinkBackReferences(Iterable<ProguardWildcard> wildcards) {
      List<Pattern> patterns = new ArrayList<>();
      boolean backReferenceStarted = false;
      for (ProguardWildcard wildcard : wildcards) {
        if (wildcard.isBackReference()) {
          backReferenceStarted = true;
          BackReference backReference = wildcard.asBackReference();
          if (patterns.size() < backReference.referenceIndex) {
            throw reporter.fatalError(new StringDiagnostic(
                "Wildcard <" + backReference.referenceIndex + "> is invalid "
                    + "(only seen " + patterns.size() + " at this point).",
                origin, getPosition()));
          }
          backReference.setReference(patterns.get(backReference.referenceIndex - 1));
        } else {
          assert wildcard.isPattern();
          if (!backReferenceStarted) {
            patterns.add(wildcard.asPattern());
          }
        }
      }
    }

    private <
            C extends ProguardClassSpecification,
            B extends ProguardClassSpecification.Builder<C, B>>
        void parseClassSpec(ProguardClassSpecification.Builder<C, B> builder)
            throws ProguardRuleParserException {
      parseClassSpec(builder, false);
    }

    private
    <C extends ProguardClassSpecification, B extends ProguardClassSpecification.Builder<C, B>>
    void parseClassSpec(
        ProguardClassSpecification.Builder<C, B> builder,
        boolean allowValueSpecification)
        throws ProguardRuleParserException {
      parseClassAnnotationsAndFlags(builder);
      parseClassSpecFromClassTypeInclusive(builder, allowValueSpecification);
    }

    private <
            C extends ProguardClassSpecification,
            B extends ProguardClassSpecification.Builder<C, B>>
        void parseClassSpecFromClassTypeInclusive(
            ProguardClassSpecification.Builder<C, B> builder, boolean allowValueSpecification)
            throws ProguardRuleParserException {
      parseClassType(
          builder,
          () -> parseClassSpecFromClassNameInclusive(builder, allowValueSpecification),
          this::parseClassTypeErrorHandler);
    }

    private <
            C extends ProguardClassSpecification,
            B extends ProguardClassSpecification.Builder<C, B>>
        void parseClassSpecFromClassNameInclusive(
            ProguardClassSpecification.Builder<C, B> builder, boolean allowValueSpecification)
            throws ProguardRuleParserException {
      builder.setClassNames(parseClassNames());
      parseInheritance(builder);
      parseMemberRules(builder, allowValueSpecification);
    }

    private void parseRuleTypeAndModifiers(ProguardKeepRule.Builder builder) {
      if (acceptString("names")) {
        builder.setType(ProguardKeepRuleType.KEEP);
        builder.getModifiersBuilder().setAllowsShrinking(true);
      } else if (acceptString("class")) {
        if (acceptString("members")) {
          builder.setType(ProguardKeepRuleType.KEEP_CLASS_MEMBERS);
        } else if (acceptString("eswithmembers")) {
          builder.setType(ProguardKeepRuleType.KEEP_CLASSES_WITH_MEMBERS);
        } else if (acceptString("membernames")) {
          builder.setType(ProguardKeepRuleType.KEEP_CLASS_MEMBERS);
          builder.getModifiersBuilder().setAllowsShrinking(true);
        } else if (acceptString("eswithmembernames")) {
          builder.setType(ProguardKeepRuleType.KEEP_CLASSES_WITH_MEMBERS);
          builder.getModifiersBuilder().setAllowsShrinking(true);
        } else {
          // The only path to here is through "-keep" followed by "class".
          unacceptString("-keepclass");
          TextPosition start = getPosition();
          acceptString("-");
          String unknownOption = acceptString();
          throw unknownOption(unknownOption, start);
        }
      } else {
        builder.setType(ProguardKeepRuleType.KEEP);
      }
      if (!eof() && !Character.isWhitespace(peekChar()) && peekChar() != ',') {
        // The only path to here is through "-keep" with an unsupported suffix.
        unacceptString("-keep");
        TextPosition start = getPosition();
        acceptString("-");
        String unknownOption = acceptString();
        throw unknownOption(unknownOption, start);
      }
      parseRuleModifiers(builder);
    }

    private void parseRuleModifiers(ProguardKeepRule.Builder builder) {
      skipWhitespace();
      while (acceptChar(',')) {
        skipWhitespace();
        TextPosition start = getPosition();
        if (acceptString("allow")) {
          if (acceptString("shrinking")) {
            builder.getModifiersBuilder().setAllowsShrinking(true);
          } else if (acceptString("optimization")) {
            builder.getModifiersBuilder().setAllowsOptimization(true);
          } else if (acceptString("obfuscation")) {
            builder.getModifiersBuilder().setAllowsObfuscation(true);
          } else if (acceptString("accessmodification")) {
            builder.getModifiersBuilder().setAllowsAccessModification(true);
          } else if (acceptString("repackage")) {
            builder.getModifiersBuilder().setAllowsRepackaging(true);
          } else if (options.isTestingOptionsEnabled()) {
            if (acceptString("annotationremoval")) {
              builder.getModifiersBuilder().setAllowsAnnotationRemoval(true);
            }
          }
        } else if (acceptString("includedescriptorclasses")) {
          builder.getModifiersBuilder().setIncludeDescriptorClasses(true);
        } else if (acceptString("includecode")) {
          infoIgnoringModifier("includecode", start);
        }
        skipWhitespace();
      }
    }

    private List<ProguardTypeMatcher> parseAnnotationList() throws ProguardRuleParserException {
      List<ProguardTypeMatcher> annotations = null;
      ProguardTypeMatcher current;
      while ((current = parseAnnotation()) != null) {
        if (annotations == null) {
          annotations = new ArrayList<>(2);
        }
        annotations.add(current);
      }
      return annotations != null ? annotations : Collections.emptyList();
    }

    private ProguardTypeMatcher parseAnnotation() throws ProguardRuleParserException {
      skipWhitespace();
      int startPosition = position;
      if (acceptChar('@')) {
        IdentifierPatternWithWildcards identifierPatternWithWildcards = parseClassName();
        String className = identifierPatternWithWildcards.pattern;
        if (className.equals("interface")) {
          // Not an annotation after all but a class type. Move position back to start
          // so this can be dealt with as a class type instead.
          position = startPosition;
          return null;
        }
        return ProguardTypeMatcher.create(
            identifierPatternWithWildcards, ClassOrType.CLASS, dexItemFactory);
      }
      return null;
    }

    private boolean parseNegation() {
      skipWhitespace();
      return acceptChar('!');
    }

    /** Returns true if any class annotations or flags were parsed. */
    private boolean parseClassAnnotationsAndFlags(ProguardClassSpecification.Builder<?, ?> builder)
        throws ProguardRuleParserException {
      // We allow interleaving the class annotations and class flags for compatibility with
      // Proguard, although this should not be possible according to the grammar.
      boolean changed = false;
      while (true) {
        ProguardTypeMatcher annotation = parseAnnotation();
        if (annotation != null) {
          builder.addClassAnnotation(annotation);
          changed = true;
        } else {
          int start = position;
          ProguardAccessFlags flags =
              parseNegation()
                  ? builder.getNegatedClassAccessFlags()
                  : builder.getClassAccessFlags();
          skipWhitespace();
          if (acceptString("public")) {
            flags.setPublic();
            changed = true;
          } else if (acceptString("final")) {
            flags.setFinal();
            changed = true;
          } else if (acceptString("abstract")) {
            flags.setAbstract();
            changed = true;
          } else {
            // Undo reading the ! in case there is no modifier following.
            position = start;
            break;
          }
        }
      }
      return changed;
    }

    private StringDiagnostic parseClassTypeUnexpected(Origin origin, TextPosition start) {
      return new StringDiagnostic(
          "Expected [!]interface|@interface|class|enum", origin, getPosition(start));
    }

    private <E extends Throwable> void parseClassType(
        ProguardClassSpecification.Builder<?, ?> builder,
        ThrowingAction<E> continuation,
        Consumer<TextPosition> errorHandler)
        throws E {
      skipWhitespace();
      TextPosition start = getPosition();
      if (acceptChar('!')) {
        builder.setClassTypeNegated(true);
      }
      if (acceptChar('@')) {
        skipWhitespace();
        if (acceptString("interface")) {
          builder.setClassType(ProguardClassType.ANNOTATION_INTERFACE);
        } else {
          errorHandler.accept(start);
          return;
        }
      } else if (acceptString("interface")) {
        builder.setClassType(ProguardClassType.INTERFACE);
      } else if (acceptString("class")) {
        builder.setClassType(ProguardClassType.CLASS);
      } else if (acceptString("enum")) {
        builder.setClassType(ProguardClassType.ENUM);
      } else {
        errorHandler.accept(start);
        return;
      }
      continuation.execute();
    }

    private void parseClassTypeErrorHandler(TextPosition start) {
      throw reporter.fatalError(parseClassTypeUnexpected(origin, start));
    }

    private void parseInheritance(
        ProguardClassSpecification.Builder<?, ?> classSpecificationBuilder)
        throws ProguardRuleParserException {
      skipWhitespace();
      if (acceptString("implements")) {
        classSpecificationBuilder.setInheritanceIsExtends(false);
      } else if (acceptString("extends")) {
        classSpecificationBuilder.setInheritanceIsExtends(true);
      } else {
        return;
      }
      classSpecificationBuilder.addInheritanceAnnotations(parseAnnotationList());
      classSpecificationBuilder.setInheritanceClassName(ProguardTypeMatcher.create(parseClassName(),
          ClassOrType.CLASS, dexItemFactory));
    }

    private
    <C extends ProguardClassSpecification, B extends ProguardClassSpecification.Builder<C, B>>
    void parseMemberRules(
        ProguardClassSpecification.Builder<C, B> classSpecificationBuilder,
        boolean allowValueSpecification)
        throws ProguardRuleParserException {
      skipWhitespace();
      if (!eof() && acceptChar('{')) {
        ProguardMemberRule rule;
        while ((rule = parseMemberRule(allowValueSpecification)) != null) {
          classSpecificationBuilder.getMemberRules().add(rule);
        }
        skipWhitespace();
        expectChar('}');
      }
    }

    private ProguardMemberRule parseMemberRule(boolean allowValueSpecification)
        throws ProguardRuleParserException {
      ProguardMemberRule.Builder ruleBuilder = ProguardMemberRule.builder();
      ruleBuilder.setAnnotations(parseAnnotationList());
      parseMemberAccessFlags(ruleBuilder);
      parseMemberPattern(ruleBuilder, allowValueSpecification);
      return ruleBuilder.isValid() ? ruleBuilder.build() : null;
    }

    private void parseMemberAccessFlags(ProguardMemberRule.Builder ruleBuilder) {
      boolean found = true;
      while (found && !eof()) {
        found = false;
        boolean negated = parseNegation();
        ProguardAccessFlags flags =
            negated ? ruleBuilder.getNegatedAccessFlags() : ruleBuilder.getAccessFlags();
        skipWhitespace();
        switch (peekChar()) {
          case 'a':
            if ((found = acceptString("abstract"))) {
              flags.setAbstract();
            }
            break;
          case 'b':
            if ((found = acceptString("bridge"))) {
              flags.setBridge();
            }
            break;
          case 'c':
            if ((found = acceptString("constructor"))) {
              flags.setConstructor();
            }
            break;
          case 'f':
            if ((found = acceptString("final"))) {
              flags.setFinal();
            }
            break;
          case 'n':
            if ((found = acceptString("native"))) {
              flags.setNative();
            }
            break;
          case 'p':
            if ((found = acceptString("public"))) {
              flags.setPublic();
            } else if ((found = acceptString("private"))) {
              flags.setPrivate();
            } else if ((found = acceptString("protected"))) {
              flags.setProtected();
            }
            break;
          case 's':
            if ((found = acceptString("synchronized"))) {
              flags.setSynchronized();
            } else if ((found = acceptString("static"))) {
              flags.setStatic();
            } else if ((found = acceptString("strictfp"))) {
              flags.setStrict();
            } else if ((found = acceptString("synthetic"))) {
              flags.setSynthetic();
            }
            break;
          case 't':
            if ((found = acceptString("transient"))) {
              flags.setTransient();
            }
            break;
          case 'v':
            if ((found = acceptString("volatile"))) {
              flags.setVolatile();
            }
            break;
          default:
            // Intentionally left empty.
        }

        // Ensure that we do not consume a negation character '!' when the subsequent identifier
        // does not match a valid access flag name (e.g., "private !int x").
        if (!found && negated) {
          unacceptString("!");
        }
      }
    }

    private void parseMemberPattern(
        ProguardMemberRule.Builder ruleBuilder, boolean allowValueSpecification)
        throws ProguardRuleParserException {
      skipWhitespace();
      if (!eof() && peekChar() == '!') {
        throw parseError(
            "Unexpected character '!': "
                + "The negation character can only be used to negate access flags");
      }
      if (acceptString("<methods>")) {
        ruleBuilder.setRuleType(ProguardMemberType.ALL_METHODS);
      } else if (acceptString("<fields>")) {
        ruleBuilder.setRuleType(ProguardMemberType.ALL_FIELDS);
      } else if (acceptString("<init>")) {
        ruleBuilder.setRuleType(ProguardMemberType.INIT);
        ruleBuilder.setName(IdentifierPatternWithWildcards.withoutWildcards("<init>"));
        ruleBuilder.setArguments(parseArgumentList());
      } else if (acceptString("<clinit>")) {
        ruleBuilder.setRuleType(ProguardMemberType.CLINIT);
        ruleBuilder.setName(IdentifierPatternWithWildcards.withoutWildcards("<clinit>"));
        ruleBuilder.setArguments(parseArgumentList());
      } else {
        TextPosition firstStart = getPosition();
        IdentifierPatternWithWildcards first =
            acceptIdentifierWithBackreference(IdentifierType.ANY);
        if (first != null) {
          skipWhitespace();
          if (first.pattern.equals("*") && hasNextChar(';')) {
            ruleBuilder.setRuleType(ProguardMemberType.ALL);
          } else {
            // No return type present, only method name, most likely constructors.
            if (hasNextChar('(')) {
              // "<init>" and "<clinit>" are explicitly checked, so angular brackets can't appear.
              checkConstructorPattern(first, firstStart);
              ruleBuilder.setRuleType(ProguardMemberType.CONSTRUCTOR);
              ruleBuilder.setName(first);
              ruleBuilder.setArguments(parseArgumentList());
            } else {
              if (acceptString("<init>")) {
                ProguardTypeMatcher typeMatcher =
                    ProguardTypeMatcher.create(first, ClassOrType.TYPE, dexItemFactory);
                if (!typeMatcher.matchesSpecificType()
                    || !typeMatcher.getSpecificType().isVoidType()) {
                  throw parseError("Expected [access-flag]* void <init>");
                }
                ruleBuilder.setRuleType(ProguardMemberType.INIT);
                ruleBuilder.setName(IdentifierPatternWithWildcards.withoutWildcards("<init>"));
                ruleBuilder.setTypeMatcher(typeMatcher);
                ruleBuilder.setArguments(parseArgumentList());
              } else if (acceptString("<clinit>")) {
                ProguardTypeMatcher typeMatcher =
                    ProguardTypeMatcher.create(first, ClassOrType.TYPE, dexItemFactory);
                if (!typeMatcher.matchesSpecificType()
                    || !typeMatcher.getSpecificType().isVoidType()) {
                  throw parseError("Expected [access-flag]* void <clinit>");
                }
                ruleBuilder.setRuleType(ProguardMemberType.CLINIT);
                ruleBuilder.setName(IdentifierPatternWithWildcards.withoutWildcards("<clinit>"));
                ruleBuilder.setTypeMatcher(typeMatcher);
                ruleBuilder.setArguments(parseArgumentList());
              } else {
                TextPosition secondStart = getPosition();
                IdentifierPatternWithWildcards second =
                    acceptIdentifierWithBackreference(IdentifierType.ANY);
                if (second != null) {
                  skipWhitespace();
                  if (hasNextChar('(')) {
                    // Parsing legitimate constructor patters is already done, so angular brackets
                    // can't appear, except for legitimate back references.
                    if (!second.hasBackreference() || second.hasUnusualCharacters()) {
                      checkConstructorPattern(second, secondStart);
                    }
                    ruleBuilder.setRuleType(ProguardMemberType.METHOD);
                    ruleBuilder.setName(second);
                    ruleBuilder
                        .setTypeMatcher(
                            ProguardTypeMatcher.create(first, ClassOrType.TYPE, dexItemFactory));
                    ruleBuilder.setArguments(parseArgumentList());
                  } else {
                    if (first.hasUnusualCharacters()) {
                      warnUnusualCharacters("type", first.pattern, "field", firstStart);
                    }
                    if (second.hasUnusualCharacters()) {
                      warnUnusualCharacters("field name", second.pattern, "field", secondStart);
                    }
                    ruleBuilder.setRuleType(ProguardMemberType.FIELD);
                    ruleBuilder.setName(second);
                    ruleBuilder
                        .setTypeMatcher(
                            ProguardTypeMatcher.create(first, ClassOrType.TYPE, dexItemFactory));
                  }
                  skipWhitespace();
                  // Parse "return ..." if present.
                  TextPosition returnStart = getPosition();
                  if (acceptString("return")) {
                    if (!allowValueSpecification) {
                      throw parseError("Unexpected value specification", returnStart);
                    }
                    skipWhitespace();
                    if (acceptString("true")) {
                      ruleBuilder.setReturnValue(new ProguardMemberRuleReturnValue(true));
                    } else if (acceptString("false")) {
                      ruleBuilder.setReturnValue(new ProguardMemberRuleReturnValue(false));
                    } else if (acceptString("null")) {
                      ruleBuilder.setReturnValue(
                          new ProguardMemberRuleReturnValue(Nullability.definitelyNull()));
                    } else {
                      Integer integer = acceptInteger();
                      if (integer != null) {
                        Integer min = integer;
                        Integer max = min;
                        skipWhitespace();
                        if (acceptString("..")) {
                          skipWhitespace();
                          max = acceptInteger();
                          if (max == null) {
                            throw parseError("Expected integer value");
                          }
                        }
                        ruleBuilder.setReturnValue(
                            new ProguardMemberRuleReturnValue(new LongInterval(min, max)));
                      } else {
                        Nullability nullability = Nullability.maybeNull();
                        if (acceptString("_NONNULL_")) {
                          nullability = Nullability.definitelyNotNull();
                          skipWhitespace();
                          if (acceptChar(';')) {
                            ruleBuilder.setReturnValue(
                                new ProguardMemberRuleReturnValue(nullability));
                            return;
                          }
                        }
                        String qualifiedFieldName = acceptQualifiedFieldName();
                        if (qualifiedFieldName != null) {
                          int lastDotIndex = qualifiedFieldName.lastIndexOf(".");
                          DexType fieldHolder =
                              dexItemFactory.createType(
                                  javaTypeToDescriptor(
                                      qualifiedFieldName.substring(0, lastDotIndex)));
                          DexString fieldName =
                              dexItemFactory.createString(
                                  qualifiedFieldName.substring(lastDotIndex + 1));
                          ruleBuilder.setReturnValue(
                              new ProguardMemberRuleReturnValue(
                                  fieldHolder, fieldName, nullability));
                        } else {
                          throw parseError("Expected qualified field");
                        }
                      }
                    }
                  }
                } else {
                  throw parseError("Expected field or method name");
                }
              }
            }
          }
        }
      }
      // If we found a member pattern eat the terminating ';'.
      if (ruleBuilder.isValid()) {
        skipWhitespace();
        expectChar(';');
      }
    }

    private void checkConstructorPattern(
        IdentifierPatternWithWildcards pattern, TextPosition position)
        throws ProguardRuleParserException {
      if (pattern.pattern.equals("<clinit>")) {
        reporter.warning(
            new StringDiagnostic("Member rule for <clinit> has no effect.", origin, position));
        return;
      }
      if (pattern.pattern.contains("<")) {
        throw parseError("Unexpected character '<' in method name. "
            + "The character '<' is only allowed in the method name '<init>'.", position);
      } else if (pattern.pattern.contains(">")) {
        throw parseError("Unexpected character '>' in method name. "
            + "The character '>' is only allowed in the method name '<init>'.", position);
      }
    }

    private List<ProguardTypeMatcher> parseArgumentList() throws ProguardRuleParserException {
      List<ProguardTypeMatcher> arguments = new ArrayList<>();
      skipWhitespace();
      expectChar('(');
      skipWhitespace();
      if (acceptChar(')')) {
        return arguments;
      }
      if (acceptString("...")) {
        arguments.add(ProguardTypeMatcher.create(
            IdentifierPatternWithWildcards.withoutWildcards("..."),
            ClassOrType.TYPE,
            dexItemFactory));
      } else {
        for (IdentifierPatternWithWildcards identifierPatternWithWildcards = parseClassName();
            identifierPatternWithWildcards != null;
            identifierPatternWithWildcards = acceptChar(',') ? parseClassName() : null) {
          arguments.add(ProguardTypeMatcher.create(
              identifierPatternWithWildcards, ClassOrType.TYPE, dexItemFactory));
          skipWhitespace();
        }
      }
      skipWhitespace();
      expectChar(')');
      return arguments;
    }

    private String replaceSystemPropertyReferences(String fileName)
        throws ProguardRuleParserException{
      StringBuilder result = new StringBuilder();
      int copied = 0;  // Last endIndex for substring.
      int start = -1;
      for (int i = 0; i < fileName.length(); i++) {
        if (fileName.charAt(i) == '<') {
          if (copied < i) {
            result.append(fileName, copied, i);
            copied = i;
          }
          start = i;
        } else if (fileName.charAt(i) == '>') {
          if (start != -1 && start < i) {
            String systemProperty = fileName.substring(start + 1, i);
            String v = null;
            if (systemProperty.length() > 0) {
              v = System.getProperty(systemProperty);
            }
            if (v == null) {
              throw parseError("Value of system property '" + systemProperty + "' not found");
            }
            result.append(v);
            start = -1;
            copied = i + 1;
          }
        }
      }

      if (copied == 0) return fileName;

      result.append(fileName.substring(copied, fileName.length()));
      return result.toString();
    }

    private Path parseFileInputDependency(BiConsumer<Origin, Path> dependencyConsumer)
        throws ProguardRuleParserException {
      Path file = parseFileName(false);
      dependencyConsumer.accept(origin, file);
      return file;
    }

    private Path parseFileName(boolean stopAfterPathSeparator) throws ProguardRuleParserException {
      TextPosition start = getPosition();
      skipWhitespace();

      if (baseDirectory == null) {
        throw parseError("Options with file names are not supported", start);
      }

      final char quote = acceptQuoteIfPresent();
      final boolean quoted = isQuote(quote);
      String fileName = acceptString(character ->
          (!quoted || character != quote)
              && (quoted || character != File.pathSeparatorChar || !stopAfterPathSeparator)
              && (quoted || !Character.isWhitespace(character))
              && (quoted ||  character != '('));
      if (fileName == null) {
        throw parseError("File name expected", start);
      }
      if (quoted) {
        if (eof()) {
          throw parseError("No closing " + quote + " quote", start);
        }
        acceptChar(quote);
      }

      fileName = replaceSystemPropertyReferences(fileName);

      return baseDirectory.resolve(fileName);
    }

    private List<FilteredClassPath> parseClassPath(BiConsumer<Origin, Path> dependencyCallback)
        throws ProguardRuleParserException {
      List<FilteredClassPath> classPath = new ArrayList<>();
      skipWhitespace();
      TextPosition position = getPosition();
      Path file = parseFileName(true);
      dependencyCallback.accept(origin, file);
      ImmutableList<String> filters = parseClassPathFilters();
      classPath.add(new FilteredClassPath(file, filters, origin, position));
      while (acceptChar(File.pathSeparatorChar)) {
        file = parseFileName(true);
        dependencyCallback.accept(origin, file);
        filters = parseClassPathFilters();
        classPath.add(new FilteredClassPath(file, filters, origin, position));
      }
      return classPath;
    }

    private ImmutableList<String> parseClassPathFilters() throws ProguardRuleParserException {
      skipWhitespace();
      if (acceptChar('(')) {
        ImmutableList.Builder<String> filters = new ImmutableList.Builder<>();
        filters.add(parseFileFilter());
        skipWhitespace();
        while (acceptChar(',')) {
          filters.add(parseFileFilter());
          skipWhitespace();
        }
        if (peekChar() == ';') {
          throw parseError("Only class file filters are supported in classpath");
        }
        expectChar(')');
        return filters.build();
      } else {
        return ImmutableList.of();
      }
    }

    private String parseFileFilter() throws ProguardRuleParserException {
      TextPosition start = getPosition();
      skipWhitespace();
      String fileFilter = acceptString(character ->
          character != ',' && character != ';' && character != ')'
              && !Character.isWhitespace(character));
      if (fileFilter == null) {
        throw parseError("file filter expected", start);
      }
      return fileFilter;
    }

    private ProguardAssumeNoSideEffectRule parseAssumeNoSideEffectsRule(Position start)
        throws ProguardRuleParserException {
      ProguardAssumeNoSideEffectRule.Builder builder = ProguardAssumeNoSideEffectRule.builder()
          .setOrigin(origin)
          .setStart(start);
      parseClassSpec(builder, true);
      Position end = getPosition();
      builder.setSource(getSourceSnippet(contents, start, end));
      builder.setEnd(end);
      return builder.build();
    }

    private ProguardAssumeMayHaveSideEffectsRule parseAssumeMayHaveSideEffectsRule(Position start)
        throws ProguardRuleParserException {
      ProguardAssumeMayHaveSideEffectsRule.Builder builder =
          ProguardAssumeMayHaveSideEffectsRule.builder().setOrigin(origin).setStart(start);
      parseClassSpec(builder);
      Position end = getPosition();
      builder.setSource(getSourceSnippet(contents, start, end));
      builder.setEnd(end);
      return builder.build();
    }

    private ProguardAssumeValuesRule parseAssumeValuesRule(Position start)
        throws ProguardRuleParserException {
      ProguardAssumeValuesRule.Builder builder = ProguardAssumeValuesRule.builder()
          .setOrigin(origin)
          .setStart(start);
      parseClassSpec(builder, true);
      Position end = getPosition();
      builder.setSource(getSourceSnippet(contents, start, end));
      builder.setEnd(end);
      return builder.build();
    }

    private CheckEnumUnboxedRule parseCheckEnumUnboxedRule(Position start)
        throws ProguardRuleParserException {
      CheckEnumUnboxedRule.Builder builder =
          CheckEnumUnboxedRule.builder().setOrigin(origin).setStart(start);
      parseClassSpec(builder);
      Position end = getPosition();
      builder.setSource(getSourceSnippet(contents, start, end));
      builder.setEnd(end);
      return builder.build();
    }

    private ConvertCheckNotNullRule parseConvertCheckNotNullRule(Position start)
        throws ProguardRuleParserException {
      ConvertCheckNotNullRule.Builder builder =
          ConvertCheckNotNullRule.builder().setOrigin(origin).setStart(start);
      parseClassSpec(builder);
      Position end = getPosition();
      builder.setSource(getSourceSnippet(contents, start, end));
      builder.setEnd(end);
      return builder.build();
    }

    private void skipWhitespace() {
      while (!eof() && StringUtils.isWhitespace(peekChar())) {
        if (peekChar() == '\n') {
          line++;
          lineStartPosition = position + 1;
        }
        position++;
      }
      skipComment();
    }

    private void skipComment() {
      if (eof()) {
        return;
      }
      if (peekChar() == '#') {
        while (!eof() && peekChar() != '\n') {
          position++;
        }
        skipWhitespace();
      }
    }

    private boolean eof() {
      return position == contents.length();
    }

    private boolean eof(int position) {
      return position == contents.length();
    }

    private boolean hasNextChar(char c) {
      if (eof()) {
        return false;
      }
      return peekChar() == c;
    }

    private boolean hasNextChar(Predicate<Character> predicate) {
      if (eof()) {
        return false;
      }
      return predicate.test(peekChar());
    }

    private boolean isOptionalArgumentGiven() {
      return !eof() && !hasNextChar('-') && !hasNextChar('@');
    }

    private boolean acceptChar(char c) {
      if (hasNextChar(c)) {
        position++;
        return true;
      }
      return false;
    }

    private char acceptQuoteIfPresent() {
      final char NO_QUOTE = '\0';
      return hasNextChar(this::isQuote) ? readChar() : NO_QUOTE;
    }

    private void expectClosingQuote(char quote) throws ProguardRuleParserException {
      assert isQuote(quote);
      if (!hasNextChar(quote)) {
        throw parseError("Missing closing quote");
      }
      acceptChar(quote);
    }

    private boolean isQuote(char c) {
      return c == '\'' || c == '"';
    }

    private char peekChar() {
      return contents.charAt(position);
    }

    private char peekCharAt(int position) {
      assert !eof(position);
      return contents.charAt(position);
    }

    private char readChar() {
      return contents.charAt(position++);
    }

    private int remainingChars() {
      return contents.length() - position;
    }

    private void expectChar(char c) throws ProguardRuleParserException {
      if (!acceptChar(c)) {
        throw parseError("Expected char '" + c + "'");
      }
    }

    private boolean acceptString(String expected) {
      if (remainingChars() < expected.length()) {
        return false;
      }
      for (int i = 0; i < expected.length(); i++) {
        if (expected.charAt(i) != contents.charAt(position + i)) {
          return false;
        }
      }
      position += expected.length();
      return true;
    }

    private String acceptString() {
      return acceptString(c -> !Character.isWhitespace(c));
    }

    private String acceptQuotedOrUnquotedString() throws ProguardRuleParserException {
      final char quote = acceptQuoteIfPresent();
      String result = acceptString(c -> !Character.isWhitespace(c) && c != quote);
      if (isQuote(quote)) {
        expectClosingQuote(quote);
      }
      return result == null ? "" : result;
    }

    private Integer acceptInteger() {
      String s = acceptString(Character::isDigit);
      if (s == null) {
        return null;
      }
      return Integer.parseInt(s);
    }

    private boolean isClassName(int codePoint) {
      return IdentifierUtils.isDexIdentifierPart(codePoint)
          || codePoint == '.'
          || codePoint == '*'
          || codePoint == '?'
          || codePoint == '%'
          || codePoint == '['
          || codePoint == ']';
    }

    private boolean isPackageName(int codePoint) {
      return IdentifierUtils.isDexIdentifierPart(codePoint)
          || codePoint == '.'
          || codePoint == '*'
          || codePoint == '?';
    }

    private String acceptClassName() {
      return acceptString(this::isClassName);
    }

    private IdentifierPatternWithWildcards acceptIdentifierWithBackreference(IdentifierType kind) {
      IdentifierPatternWithWildcardsAndNegation pattern =
          acceptIdentifierWithBackreference(kind, false);
      if (pattern == null) {
        return null;
      }
      assert !pattern.negated;
      return pattern.patternWithWildcards;
    }

    private IdentifierPatternWithWildcardsAndNegation acceptIdentifierWithBackreference(
        IdentifierType kind, boolean allowNegation) {
      ImmutableList.Builder<ProguardWildcard> wildcardsCollector = ImmutableList.builder();
      StringBuilder currentAsterisks = null;
      int asteriskCount = 0;
      StringBuilder currentBackreference = null;
      skipWhitespace();

      final char quote = acceptQuoteIfPresent();
      final boolean quoted = isQuote(quote);
      final boolean negated = allowNegation ? acceptChar('!') : false;

      int start = position;
      int end = position;
      while (!eof(end)) {
        int current = contents.codePointAt(end);
        // Should not be both in asterisk collecting state and back reference collecting state.
        assert currentAsterisks == null || currentBackreference == null;
        if (currentBackreference != null) {
          if (current == '>') {
            try {
              int backreference = Integer.parseUnsignedInt(currentBackreference.toString());
              if (backreference <= 0) {
                throw reporter.fatalError(new StringDiagnostic(
                    "Wildcard <" + backreference + "> is invalid.", origin, getPosition()));
              }
              wildcardsCollector.add(new BackReference(backreference));
              currentBackreference = null;
              end += Character.charCount(current);
              continue;
            } catch (NumberFormatException e) {
              throw reporter.fatalError(new StringDiagnostic(
                  "Wildcard <" + currentBackreference.toString() + "> is invalid.",
                  origin, getPosition()));
            }
          } else if (('0' <= current && current <= '9')
              // Only collect integer literal for the back reference.
              || (current == '-' && currentBackreference.length() == 0)) {
            currentBackreference.append((char) current);
            end += Character.charCount(current);
            continue;
          } else if (kind == IdentifierType.CLASS_NAME) {
            throw reporter.fatalError(new StringDiagnostic(
                "Use of generics not allowed for java type.", origin, getPosition()));
          } else {
            // If not parsing a class name allow identifiers including <'s by canceling the
            // collection of the back reference.
            currentBackreference = null;
          }
        } else if (currentAsterisks != null) {
          if (current == '*') {
            // only '*', '**', and '***' are allowed.
            // E.g., '****' should be regarded as two separate wildcards (e.g., '***' and '*')
            if (asteriskCount >= 3) {
              wildcardsCollector.add(new ProguardWildcard.Pattern(currentAsterisks.toString()));
              currentAsterisks = new StringBuilder();
              asteriskCount = 0;
            }
            currentAsterisks.append((char) current);
            asteriskCount++;
            end += Character.charCount(current);
            continue;
          } else {
            wildcardsCollector.add(new ProguardWildcard.Pattern(currentAsterisks.toString()));
            currentAsterisks = null;
            asteriskCount = 0;
          }
        }
        // From now on, neither in asterisk collecting state nor back reference collecting state.
        assert currentAsterisks == null && currentBackreference == null;
        if (current == '*') {
          if (kind == IdentifierType.CLASS_NAME) {
            // '**' and '***' are only allowed in type name.
            currentAsterisks = new StringBuilder();
            currentAsterisks.append((char) current);
            asteriskCount = 1;
          } else {
            // For member names, regard '**' or '***' as separate single-asterisk wildcards.
            wildcardsCollector.add(new ProguardWildcard.Pattern(String.valueOf((char) current)));
          }
          end += Character.charCount(current);
        } else if (current == '?' || current == '%') {
          wildcardsCollector.add(new ProguardWildcard.Pattern(String.valueOf((char) current)));
          end += Character.charCount(current);
        } else if (kind == IdentifierType.PACKAGE_NAME
            ? isPackageName(current)
            : (isClassName(current) || current == '>')) {
          end += Character.charCount(current);
        } else if (kind != IdentifierType.PACKAGE_NAME && current == '<') {
          currentBackreference = new StringBuilder();
          end += Character.charCount(current);
        } else {
          if (quoted && quote != current) {
            throw reporter.fatalError(
                new StringDiagnostic(
                    "Invalid character '" + (char) current + "', expected end-quote.",
                    origin,
                    getPosition()));
          }
          break;
        }
      }
      position = quoted ? end + 1 : end;
      if (currentAsterisks != null) {
        wildcardsCollector.add(new ProguardWildcard.Pattern(currentAsterisks.toString()));
      }
      if (kind == IdentifierType.CLASS_NAME && currentBackreference != null) {
        // Proguard 6 reports this error message, so try to be compatible.
        throw reporter.fatalError(
            new StringDiagnostic("Missing closing angular bracket", origin, getPosition()));
      }
      if (start == end) {
        return null;
      }
      return new IdentifierPatternWithWildcardsAndNegation(
          contents.substring(start, end), wildcardsCollector.build(), negated);
    }

    private String acceptQualifiedFieldName() {
      skipWhitespace();
      int start = position;
      // A qualified field name must be non empty.
      if (eof(start)) {
        return null;
      }
      // The first character of a qualified field name is an identifier part.
      int firstCodePoint = contents.codePointAt(start);
      if (!IdentifierUtils.isDexIdentifierStart(contents.codePointAt(start))) {
        return null;
      }
      int end = start + Character.charCount(firstCodePoint);
      while (!eof(end)) {
        int currentCodePoint = contents.codePointAt(end);
        if (currentCodePoint == '.') {
          end += 1;
          // Each dot in a qualified field name must be followed by an identifier part.
          if (!eof(end) && IdentifierUtils.isDexIdentifierPart(peekCharAt(end))) {
            end += Character.charCount(contents.codePointAt(end));
          } else {
            break;
          }
        } else if (IdentifierUtils.isDexIdentifierPart(currentCodePoint)) {
          end += Character.charCount(currentCodePoint);
        } else {
          break;
        }
      }
      position = end;
      return contents.substring(start, end);
    }

    private List<String> acceptPatternList() throws ProguardRuleParserException {
      List<String> patterns = new ArrayList<>();
      skipWhitespace();
      char quote = acceptQuoteIfPresent();
      String pattern = acceptPattern();
      if (isQuote(quote)) {
        expectClosingQuote(quote);
      }
      while (pattern != null) {
        patterns.add(pattern);
        skipWhitespace();
        if (acceptChar(',')) {
          skipWhitespace();
          TextPosition start = getPosition();
          quote = acceptQuoteIfPresent();
          pattern = acceptPattern();
          if (isQuote(quote)) {
            expectClosingQuote(quote);
          }
          if (pattern == null) {
            throw parseError("Expected list element", start);
          }
        } else {
          pattern = null;
        }
      }
      skipWhitespace();
      if (!eof() && !hasNextChar('-') && !hasNextChar('@')) {
        throw parseError("Unexpected attribute");
      }
      return patterns;
    }

    private String acceptPattern() {
      return acceptString(
          codePoint ->
              IdentifierUtils.isDexIdentifierPart(codePoint)
                  || codePoint == '!'
                  || codePoint == '*'
                  || codePoint == '.');
    }

    private String acceptString(IntPredicate codepointAcceptor) {
      int start = position;
      int end = position;
      while (!eof(end)) {
        int current = contents.codePointAt(end);
        if (codepointAcceptor.test(current)) {
          end += Character.charCount(current);
        } else {
          break;
        }
      }
      if (start == end) {
        return null;
      }
      position = end;
      return contents.substring(start, end);
    }

    private void unacceptString(String expected) {
      assert position >= expected.length();
      position -= expected.length();
      for (int i = 0; i < expected.length(); i++) {
        assert expected.charAt(i) == contents.charAt(position + i);
      }
    }

    private void parseClassFilter(Consumer<ProguardClassNameList> consumer)
        throws ProguardRuleParserException {
      skipWhitespace();
      if (isOptionalArgumentGiven()) {
        consumer.accept(parseClassNames());
      } else {
        consumer.accept(
            ProguardClassNameList.singletonList(ProguardTypeMatcher.defaultAllMatcher()));
      }
    }

    private void parseClassNameAddToBuilder(ProguardClassNameList.Builder builder)
        throws ProguardRuleParserException {
      IdentifierPatternWithWildcardsAndNegation name = parseClassName(true);
      builder.addClassName(
          name.negated,
          ProguardTypeMatcher.create(name.patternWithWildcards, ClassOrType.CLASS, dexItemFactory));
      skipWhitespace();
    }

    private ProguardClassNameList parseClassNames() throws ProguardRuleParserException {
      ProguardClassNameList.Builder builder = ProguardClassNameList.builder();
      do {
        parseClassNameAddToBuilder(builder);
      } while (acceptChar(','));
      return builder.build();
    }

    private String parsePackageNameOrEmptyString() {
      String name = acceptClassName();
      return name == null ? "" : name;
    }

    private IdentifierPatternWithWildcards parseClassName() throws ProguardRuleParserException {
      IdentifierPatternWithWildcardsAndNegation name = parseClassName(false);
      assert !name.negated;
      return name.patternWithWildcards;
    }

    private IdentifierPatternWithWildcardsAndNegation parseClassName(boolean allowNegation)
        throws ProguardRuleParserException {
      IdentifierPatternWithWildcardsAndNegation name =
          acceptIdentifierWithBackreference(IdentifierType.CLASS_NAME, allowNegation);
      if (name == null) {
        throw parseError("Class name expected");
      }
      return name;
    }

    private boolean pathFilterMatcher(Integer character) {
      return character != ',' && !Character.isWhitespace(character);
    }

    private void parsePathFilter(Consumer<ProguardPathList> consumer)
        throws ProguardRuleParserException {
      skipWhitespace();
      if (isOptionalArgumentGiven()) {
        consumer.accept(parsePathFilter());
      } else {
        consumer.accept(ProguardPathList.emptyList());
      }
    }

    private ProguardPathList parsePathFilter() throws ProguardRuleParserException {
      ProguardPathList.Builder builder = ProguardPathList.builder();
      skipWhitespace();
      boolean negated = acceptChar('!');
      skipWhitespace();
      String fileFilter = acceptString(this::pathFilterMatcher);
      if (fileFilter == null) {
        throw parseError("Path filter expected");
      }
      builder.addFileName(fileFilter, negated);
      skipWhitespace();
      while (acceptChar(',')) {
        skipWhitespace();
        negated = acceptChar('!');
        skipWhitespace();
        fileFilter = acceptString(this::pathFilterMatcher);
        if (fileFilter == null) {
          throw parseError("Path filter expected");
        }
        builder.addFileName(fileFilter, negated);
        skipWhitespace();
      }
      return builder.build();
    }

    private String snippetForPosition() {
      // TODO(ager): really should deal with \r as well to get column right.
      String[] lines = contents.split("\n", -1);  // -1 to get trailing empty lines represented.
      int remaining = position;
      for (int lineNumber = 0; lineNumber < lines.length; lineNumber++) {
        String line = lines[lineNumber];
        if (remaining <= line.length() || lineNumber == lines.length - 1) {
          String arrow = CharBuffer.allocate(remaining).toString().replace('\0', ' ') + '^';
          return name + ":" + (lineNumber + 1) + ":" + (remaining + 1) + "\n" + line
              + '\n' + arrow;
        }
        remaining -= (line.length() + 1); // Include newline.
      }
      return name;
    }

    private String snippetForPosition(TextPosition start) {
      // TODO(ager): really should deal with \r as well to get column right.
      String[] lines = contents.split("\n", -1);  // -1 to get trailing empty lines represented.
      String line = lines[start.getLine() - 1];
      String arrow = CharBuffer.allocate(start.getColumn() - 1).toString().replace('\0', ' ') + '^';
      return name + ":" + (start.getLine() + 1) + ":" + start.getColumn() + "\n" + line
          + '\n' + arrow;
    }

    private ProguardRuleParserException parseError(String message) {
      return new ProguardRuleParserException(message, snippetForPosition(), origin, getPosition());
    }

    private ProguardRuleParserException parseError(String message, TextPosition start,
        Throwable cause) {
      return new ProguardRuleParserException(message, snippetForPosition(start),
          origin, getPosition(start), cause);
    }

    private ProguardRuleParserException parseError(String message, TextPosition start) {
      return new ProguardRuleParserException(message, snippetForPosition(start),
          origin, getPosition(start));
    }

    private void infoIgnoringOptions(String optionName, TextPosition start) {
      reporter.info(new StringDiagnostic(
          "Ignoring option: -" + optionName, origin, getPosition(start)));
    }

    private void warnIgnoringOptions(String optionName, TextPosition start) {
      reporter.warning(new StringDiagnostic(
          "Ignoring option: -" + optionName, origin, getPosition(start)));
    }

    private void warnOverridingOptions(String optionName, String victim, TextPosition start) {
      reporter.warning(new StringDiagnostic(
          "Option -" + optionName + " overrides -" + victim, origin, getPosition(start)));
    }

    private void warnUnusualCharacters(
        String kind, String pattern, String ruleType, TextPosition start) {
      reporter.warning(new StringDiagnostic(
          "The " + kind + " \"" + pattern + "\" is used in a " + ruleType + " rule. The "
              + "characters in this " + kind + " are legal for the JVM, "
              + "but unlikely to originate from a source language. "
              + "Maybe this is not the rule you are looking for.",
          origin, getPosition(start)));
    }

    private void infoIgnoringModifier(String modifier, TextPosition start) {
      reporter.info(new StringDiagnostic(
          "Ignoring modifier: " + modifier, origin, getPosition(start)));
    }

    private Position getPosition(TextPosition start) {
      if (start.getOffset() == position) {
        return start;
      } else {
        return new TextRange(start, getPosition());
      }
    }

    private TextPosition getPosition() {
      return new TextPosition(position, line, getColumn());
    }

    private int getColumn() {
      return position - lineStartPosition + 1 /* column starts at 1 */;
    }

    private String getSourceSnippet(String source, Position start, Position end) {
      return start instanceof TextPosition && end instanceof TextPosition
          ? getTextSourceSnippet(source, (TextPosition) start, (TextPosition) end)
          : null;
    }
  }

  private String getTextSourceSnippet(String source, TextPosition start, TextPosition end) {
    long length = end.getOffset() - start.getOffset();
    if (start.getOffset() < 0 || end.getOffset() < 0
        || start.getOffset() >= source.length() || end.getOffset() > source.length()
        || length <= 0) {
      return null;
    } else {
      return source.substring(start.getOffsetAsInt(), end.getOffsetAsInt());
    }
  }

  static class IdentifierPatternWithWildcards {
    final String pattern;
    final List<ProguardWildcard> wildcards;

    IdentifierPatternWithWildcards(String pattern, List<ProguardWildcard> wildcards) {
      this.pattern = pattern;
      this.wildcards = wildcards;
    }

    static IdentifierPatternWithWildcards withoutWildcards(String pattern) {
      return new IdentifierPatternWithWildcards(pattern, ImmutableList.of());
    }

    boolean isMatchAllNames() {
      return pattern.equals("*");
    }

    boolean hasBackreference() {
      return !wildcards.isEmpty()
          && wildcards.stream().anyMatch(ProguardWildcard::isBackReference);
    }

    boolean hasUnusualCharacters() {
      if (pattern.contains("<") || pattern.contains(">")) {
        int angleStartCount = 0;
        int angleEndCount = 0;
        for (int i = 0; i < pattern.length(); i++) {
          char c = pattern.charAt(i);
          if (c == '<') {
            angleStartCount++;
          }
          if (c == '>') {
            angleEndCount++;
          }
        }
        // Check that start/end angles are matched, and *only* used for well-formed wildcard
        // backreferences (e.g. '<1>', but not '<<1>>', '<<*>>' or '>1<').
        return !(angleStartCount == angleEndCount && angleStartCount == wildcards.size());
      }
      return false;
    }
  }

  static class IdentifierPatternWithWildcardsAndNegation {
    final IdentifierPatternWithWildcards patternWithWildcards;
    final boolean negated;

    IdentifierPatternWithWildcardsAndNegation(
        String pattern, List<ProguardWildcard> wildcards, boolean negated) {
      patternWithWildcards = new IdentifierPatternWithWildcards(pattern, wildcards);
      this.negated = negated;
    }
  }
}