// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.StringUtils.EMPTY_CHAR_ARRAY;
import static com.android.tools.r8.utils.SymbolGenerationUtils.RESERVED_NAMES;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.naming.ClassNameMinifier.ClassNamingStrategy;
import com.android.tools.r8.naming.ClassNameMinifier.ClassRenaming;
import com.android.tools.r8.naming.FieldNameMinifier.FieldRenaming;
import com.android.tools.r8.naming.MethodNameMinifier.MethodRenaming;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.SymbolGenerationUtils;
import com.android.tools.r8.utils.SymbolGenerationUtils.MixedCasing;
import com.android.tools.r8.utils.Timing;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class Minifier {

  private final AppView<AppInfoWithLiveness> appView;

  public Minifier(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public NamingLens run(ExecutorService executorService, Timing timing) throws ExecutionException {
    assert appView.options().isMinifying();
    SubtypingInfo subtypingInfo = MinifierUtils.createSubtypingInfo(appView);
    timing.begin("ComputeInterfaces");
    List<DexClass> interfaces = subtypingInfo.computeReachableInterfacesWithDeterministicOrder();
    timing.end();
    timing.begin("MinifyClasses");
    ClassNameMinifier classNameMinifier =
        new ClassNameMinifier(
            appView,
            appView.options().synthesizedClassPrefix.isEmpty()
                ? new MinificationClassNamingStrategy(appView)
                : new L8MinificationClassNamingStrategy(appView),
            // Use deterministic class order to make sure renaming is deterministic.
            appView.appInfo().classesWithDeterministicOrder());
    ClassRenaming classRenaming = classNameMinifier.computeRenaming(timing);
    timing.end();

    assert new MinifiedRenaming(
            appView, classRenaming, MethodRenaming.empty(), FieldRenaming.empty())
        .verifyNoCollisions(appView.appInfo().classes(), appView.dexItemFactory());

    MemberNamingStrategy minifyMembers = new MinifierMemberNamingStrategy(appView);
    timing.begin("MinifyMethods");
    MethodRenaming methodRenaming =
        new MethodNameMinifier(appView, minifyMembers)
            .computeRenaming(interfaces, subtypingInfo, executorService, timing);
    timing.end();

    assert new MinifiedRenaming(appView, classRenaming, methodRenaming, FieldRenaming.empty())
        .verifyNoCollisions(appView.appInfo().classes(), appView.dexItemFactory());

    timing.begin("MinifyFields");
    FieldRenaming fieldRenaming =
        new FieldNameMinifier(appView, subtypingInfo, minifyMembers)
            .computeRenaming(interfaces, timing);
    timing.end();

    NamingLens lens = new MinifiedRenaming(appView, classRenaming, methodRenaming, fieldRenaming);
    assert lens.verifyNoCollisions(appView.appInfo().classes(), appView.dexItemFactory());

    timing.begin("MinifyIdentifiers");
    new IdentifierMinifier(appView, lens).run(executorService);
    timing.end();

    timing.begin("RecordInvokeDynamicRewrite");
    new RecordInvokeDynamicInvokeCustomRewriter(appView, lens).run(executorService);
    timing.end();

    appView.notifyOptimizationFinishedForTesting();
    return lens;
  }

  abstract static class BaseMinificationNamingStrategy {

    // We have to ensure that the names proposed by the minifier is not used in the obfuscation
    // dictionary. We use a list for direct indexing based on a number and a set for looking up.
    private final List<String> obfuscationDictionary;
    private final Set<String> obfuscationDictionaryForLookup;
    private final MixedCasing mixedCasing;

    BaseMinificationNamingStrategy(List<String> obfuscationDictionary, boolean dontUseMixedCasing) {
      assert obfuscationDictionary != null;
      this.obfuscationDictionary = obfuscationDictionary;
      this.obfuscationDictionaryForLookup = new HashSet<>(obfuscationDictionary);
      this.mixedCasing =
          dontUseMixedCasing ? MixedCasing.DONT_USE_MIXED_CASE : MixedCasing.USE_MIXED_CASE;
    }

    /** TODO(b/182992598): using char[] could give problems with unicode */
    String nextName(char[] packagePrefix, InternalNamingState state) {
      StringBuilder nextName = new StringBuilder();
      nextName.append(packagePrefix);
      nextName.append(nextString(packagePrefix, state));
      return nextName.toString();
    }

    String nextString(char[] packagePrefix, InternalNamingState state) {
      String nextString;
      do {
        if (state.getDictionaryIndex() < obfuscationDictionary.size()) {
          nextString = obfuscationDictionary.get(state.incrementDictionaryIndex());
        } else {
          do {
            nextString =
                SymbolGenerationUtils.numberToIdentifier(state.incrementNameIndex(), mixedCasing);
          } while (obfuscationDictionaryForLookup.contains(nextString));
        }
      } while (RESERVED_NAMES.contains(nextString));
      return nextString;
    }
  }

  static class L8MinificationClassNamingStrategy extends MinificationClassNamingStrategy {

    private final String prefix;

    L8MinificationClassNamingStrategy(AppView<AppInfoWithLiveness> appView) {
      super(appView);
      String synthesizedClassPrefix = appView.options().synthesizedClassPrefix;
      prefix = synthesizedClassPrefix.substring(0, synthesizedClassPrefix.length() - 1);
    }

    private boolean startsWithPrefix(char[] packagePrefix) {
      if (packagePrefix.length < prefix.length() + 1) {
        return false;
      }
      for (int i = 0; i < prefix.length(); i++) {
        if (prefix.charAt(i) != packagePrefix[i + 1]) {
          return false;
        }
      }
      return true;
    }

    @Override
    String nextString(char[] packagePrefix, InternalNamingState state) {
      String nextString = super.nextString(packagePrefix, state);
      return startsWithPrefix(packagePrefix) ? nextString : prefix + nextString;
    }
  }

  static class MinificationClassNamingStrategy extends BaseMinificationNamingStrategy
      implements ClassNamingStrategy {

    final AppView<AppInfoWithLiveness> appView;
    final DexItemFactory factory;

    MinificationClassNamingStrategy(AppView<AppInfoWithLiveness> appView) {
      super(
          appView.options().getProguardConfiguration().getClassObfuscationDictionary(),
          appView.options().getProguardConfiguration().hasDontUseMixedCaseClassnames());
      this.appView = appView;
      this.factory = appView.dexItemFactory();
    }

    @Override
    public DexString next(
        DexType type, char[] packagePrefix, InternalNamingState state, Predicate<String> isUsed) {
      String candidate = null;
      String lastName = null;
      do {
        String newName = nextName(packagePrefix, state) + ";";
        if (newName.equals(lastName)) {
          throw new CompilationError(
              "Generating same name '"
                  + newName
                  + "' when given a new minified name to '"
                  + type.toString()
                  + "'.");
        }
        lastName = newName;
        // R.class in Android, which contains constant IDs to assets, can be bundled at any time.
        // Insert `R` immediately so that the class name minifier can skip that name by default.
        if (newName.endsWith("LR;") || newName.endsWith("/R;")) {
          continue;
        }
        candidate = newName;
      } while (candidate == null || isUsed.test(candidate));
      return factory.createString(candidate);
    }

    @Override
    public DexString reservedDescriptor(DexType type) {
      if (!appView.appInfo().isMinificationAllowed(type)) {
        return type.descriptor;
      }
      return null;
    }

    @Override
    public boolean isRenamedByApplyMapping(DexType type) {
      return false;
    }
  }

  public static class MinificationPackageNamingStrategy extends BaseMinificationNamingStrategy {

    private final InternalNamingState namingState =
        new InternalNamingState() {

          private int dictionaryIndex = 0;
          private int nameIndex = 1;

          @Override
          public int getDictionaryIndex() {
            return dictionaryIndex;
          }

          @Override
          public int incrementDictionaryIndex() {
            return dictionaryIndex++;
          }

          @Override
          public int incrementNameIndex() {
            return nameIndex++;
          }
        };

    public MinificationPackageNamingStrategy(AppView<?> appView) {
      super(
          appView.options().getProguardConfiguration().getPackageObfuscationDictionary(),
          appView.options().getProguardConfiguration().hasDontUseMixedCaseClassnames());
    }

    public String next(String packagePrefix, Predicate<String> isUsed) {
      // Note that the differences between this method and the other variant for class renaming are
      // 1) this one uses the different dictionary and counter,
      // 2) this one does not append ';' at the end, and
      // 3) this one assumes no 'L' at the beginning to make the return value a binary form.
      String nextPackageName;
      do {
        nextPackageName = nextName(packagePrefix.toCharArray(), namingState);
      } while (isUsed.test(nextPackageName));
      return nextPackageName;
    }
  }

  static class MinifierMemberNamingStrategy extends BaseMinificationNamingStrategy
      implements MemberNamingStrategy {

    final AppView<AppInfoWithLiveness> appView;
    private final DexItemFactory factory;
    private final boolean desugaredLibraryRenaming;

    public MinifierMemberNamingStrategy(AppView<AppInfoWithLiveness> appView) {
      super(appView.options().getProguardConfiguration().getObfuscationDictionary(), false);
      this.appView = appView;
      this.factory = appView.dexItemFactory();
      this.desugaredLibraryRenaming = appView.typeRewriter.isRewriting();
    }

    @Override
    public DexString next(
        DexEncodedMethod method,
        InternalNamingState internalState,
        BiPredicate<DexString, DexMethod> isAvailable) {
      assert checkAllowMemberRenaming(method.getHolderType());
      DexString candidate;
      do {
        candidate = getNextName(internalState);
      } while (!isAvailable.test(candidate, method.getReference()));
      return candidate;
    }

    @Override
    public DexString next(
        ProgramField field,
        InternalNamingState internalState,
        BiPredicate<DexString, ProgramField> isAvailable) {
      assert checkAllowMemberRenaming(field.getHolderType());
      DexString candidate;
      do {
        candidate = getNextName(internalState);
      } while (!isAvailable.test(candidate, field));
      return candidate;
    }

    private DexString getNextName(InternalNamingState internalState) {
      return factory.createString(nextName(EMPTY_CHAR_ARRAY, internalState));
    }

    @Override
    public DexString getReservedName(DexEncodedMethod method, DexClass holder) {
      if (!allowMemberRenaming(holder)
          || holder.accessFlags.isAnnotation()
          || method.accessFlags.isConstructor()
          || !appView.appInfo().isMinificationAllowed(method)) {
        return method.getReference().name;
      }
      if (desugaredLibraryRenaming
          && method.isLibraryMethodOverride().isTrue()
          && appView.typeRewriter.hasRewrittenTypeInSignature(
              method.getReference().proto, appView)) {
        // With desugared library, call-backs names are reserved here.
        return method.getReference().name;
      }
      return null;
    }

    @Override
    public DexString getReservedName(DexEncodedField field, DexClass holder) {
      if (holder.isLibraryClass() || !appView.appInfo().isMinificationAllowed(field)) {
        return field.getReference().name;
      }
      return null;
    }

    @Override
    public boolean allowMemberRenaming(DexClass holder) {
      return holder.isProgramClass();
    }

    public boolean checkAllowMemberRenaming(DexType holder) {
      DexClass clazz = appView.definitionFor(holder);
      assert clazz != null && allowMemberRenaming(clazz);
      return true;
    }
  }
}
