// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.google.common.base.Predicates.alwaysFalse;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.debuginfo.DebugRepresentation;
import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.experimental.startup.StartupOrder;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ThrowNullCode;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMaps;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Predicate;

public class VirtualFile {

  public static final int MAX_ENTRIES = Constants.U16BIT_MAX + 1;

  private final int id;
  private final VirtualFileIndexedItemCollection indexedItems;
  private final IndexedItemTransaction transaction;
  private final FeatureSplit featureSplit;

  private final DexString primaryClassDescriptor;
  private DebugRepresentation debugRepresentation;

  VirtualFile(int id, AppView<?> appView) {
    this(id, appView, null, null);
  }

  VirtualFile(
      int id,
      AppView<?> appView,
      FeatureSplit featureSplit) {
    this(id, appView, null, featureSplit);
  }

  private VirtualFile(
      int id,
      AppView<?> appView,
      DexProgramClass primaryClass) {
    this(id, appView, primaryClass, null);
  }

  private VirtualFile(
      int id,
      AppView<?> appView,
      DexProgramClass primaryClass,
      FeatureSplit featureSplit) {
    this.id = id;
    this.indexedItems = new VirtualFileIndexedItemCollection(appView);
    this.transaction = new IndexedItemTransaction(indexedItems, appView);
    this.primaryClassDescriptor =
        primaryClass == null
            ? null
            : appView.getNamingLens().lookupClassDescriptor(primaryClass.type);
    this.featureSplit = featureSplit;
  }

  public int getId() {
    return id;
  }

  public Set<String> getClassDescriptors() {
    Set<String> classDescriptors = new HashSet<>();
    for (DexProgramClass clazz : indexedItems.classes) {
      boolean added = classDescriptors.add(clazz.type.descriptor.toString());
      assert added;
    }
    return classDescriptors;
  }

  public FeatureSplit getFeatureSplit() {
    return featureSplit;
  }

  public String getPrimaryClassDescriptor() {
    return primaryClassDescriptor == null ? null : primaryClassDescriptor.toString();
  }

  public void setDebugRepresentation(DebugRepresentation debugRepresentation) {
    assert debugRepresentation != null;
    assert this.debugRepresentation == null;
    this.debugRepresentation = debugRepresentation;
  }

  public DebugRepresentation getDebugRepresentation() {
    assert debugRepresentation != null;
    return debugRepresentation;
  }

  public static String deriveCommonPrefixAndSanityCheck(List<String> fileNames) {
    Iterator<String> nameIterator = fileNames.iterator();
    String first = nameIterator.next();
    if (!first.toLowerCase().endsWith(FileUtils.DEX_EXTENSION)) {
      throw new RuntimeException("Illegal suffix for dex file: `" + first + "`.");
    }
    String prefix = first.substring(0, first.length() - FileUtils.DEX_EXTENSION.length());
    int index = 2;
    while (nameIterator.hasNext()) {
      String next = nameIterator.next();
      if (!next.toLowerCase().endsWith(FileUtils.DEX_EXTENSION)) {
        throw new RuntimeException("Illegal suffix for dex file: `" + first + "`.");
      }
      if (!next.startsWith(prefix)) {
        throw new RuntimeException("Input filenames lack common prefix.");
      }
      String numberPart =
          next.substring(prefix.length(), next.length() - FileUtils.DEX_EXTENSION.length());
      if (Integer.parseInt(numberPart) != index++) {
        throw new RuntimeException("DEX files are not numbered consecutively.");
      }
    }
    return prefix;
  }

  public void injectString(DexString string) {
    transaction.addString(string);
    commitTransaction();
  }

  private static Map<DexProgramClass, String> computeOriginalNameMapping(
      Collection<DexProgramClass> classes, GraphLens graphLens, ClassNameMapper proguardMap) {
    Map<DexProgramClass, String> originalNames = new IdentityHashMap<>(classes.size());
    classes.forEach(
        clazz -> {
          DexType originalType = graphLens.getOriginalType(clazz.getType());
          originalNames.put(
              clazz,
              DescriptorUtils.descriptorToJavaType(originalType.toDescriptorString(), proguardMap));
        });
    return originalNames;
  }

  private ObjectToOffsetMapping objectMapping = null;

  public ObjectToOffsetMapping getObjectMapping() {
    assert objectMapping != null;
    return objectMapping;
  }

  public void computeMapping(
      AppView<?> appView,
      int lazyDexStringsCount,
      Timing timing) {
    assert transaction.isEmpty();
    assert objectMapping == null;
    objectMapping =
        new ObjectToOffsetMapping(
            appView,
            transaction.rewriter,
            indexedItems.classes,
            indexedItems.protos,
            indexedItems.types,
            indexedItems.methods,
            indexedItems.fields,
            indexedItems.strings,
            indexedItems.callSites,
            indexedItems.methodHandles,
            lazyDexStringsCount,
            timing);
  }

  void addClass(DexProgramClass clazz) {
    transaction.addClassAndDependencies(clazz);
  }

  public boolean isFull(int maxEntries) {
    return (transaction.getNumberOfMethods() > maxEntries)
        || (transaction.getNumberOfFields() > maxEntries);
  }

  boolean isFull() {
    return isFull(MAX_ENTRIES);
  }

  public int getNumberOfMethods() {
    return transaction.getNumberOfMethods();
  }

  public int getNumberOfFields() {
    return transaction.getNumberOfFields();
  }

  public int getNumberOfClasses() {
    return transaction.getNumberOfClasses();
  }

  void throwIfFull(boolean hasMainDexList, Reporter reporter) {
    if (!isFull()) {
      return;
    }
    throw reporter.fatalError(
        new DexFileOverflowDiagnostic(
            hasMainDexList, transaction.getNumberOfMethods(), transaction.getNumberOfFields()));
  }

  private boolean isFilledEnough() {
    return isFull(MAX_ENTRIES);
  }

  public void abortTransaction() {
    transaction.abort();
  }

  public void commitTransaction() {
    transaction.commit();
  }

  public boolean containsString(DexString string) {
    return indexedItems.strings.contains(string);
  }

  public boolean containsType(DexType type) {
    return indexedItems.types.contains(type);
  }

  public boolean isEmpty() {
    return indexedItems.classes.isEmpty();
  }

  public Collection<DexProgramClass> classes() {
    return indexedItems.classes;
  }

  public abstract static class Distributor {
    protected final AppView<?> appView;
    protected final ApplicationWriter writer;
    protected final List<VirtualFile> virtualFiles = new ArrayList<>();

    Distributor(ApplicationWriter writer) {
      this.appView = writer.appView;
      this.writer = writer;
    }

    public abstract List<VirtualFile> run() throws ExecutionException, IOException;

  }

  /**
   * Distribute each type to its individual virtual except for types synthesized during this
   * compilation. Synthesized classes are emitted in the individual virtual files
   * of the input classes they were generated from. Shared synthetic classes
   * may then be distributed in several individual virtual files.
   */
  public static class FilePerInputClassDistributor extends Distributor {
    private final Collection<DexProgramClass> classes;
    private final boolean combineSyntheticClassesWithPrimaryClass;

    FilePerInputClassDistributor(
        ApplicationWriter writer,
        Collection<DexProgramClass> classes,
        boolean combineSyntheticClassesWithPrimaryClass) {
      super(writer);
      this.classes = classes;
      this.combineSyntheticClassesWithPrimaryClass = combineSyntheticClassesWithPrimaryClass;
    }

    @Override
    public List<VirtualFile> run() {
      HashMap<DexProgramClass, VirtualFile> files = new HashMap<>();
      Collection<DexProgramClass> synthetics = new ArrayList<>();
      // Assign dedicated virtual files for all program classes.
      for (DexProgramClass clazz : classes) {
        // TODO(b/181636450): Simplify this making use of the assumption that synthetics are never
        //  duplicated.
        if (!combineSyntheticClassesWithPrimaryClass
            || !appView.getSyntheticItems().isSyntheticClass(clazz)) {
          VirtualFile file = new VirtualFile(virtualFiles.size(), appView, clazz);
          virtualFiles.add(file);
          file.addClass(clazz);
          files.put(clazz, file);
          // Commit this early, so that we do not keep the transaction state around longer than
          // needed and clear the underlying sets.
          file.commitTransaction();
        } else {
          synthetics.add(clazz);
        }
      }
      for (DexProgramClass synthetic : synthetics) {
        Collection<DexType> synthesizingContexts =
            appView.getSyntheticItems().getSynthesizingContextTypes(synthetic.getType());
        assert synthesizingContexts.size() == 1;
        DexProgramClass inputType =
            appView.definitionForProgramType(synthesizingContexts.iterator().next());
        VirtualFile file = files.get(inputType);
        file.addClass(synthetic);
        file.commitTransaction();
      }
      return virtualFiles;
    }
  }

  public abstract static class DistributorBase extends Distributor {
    protected Set<DexProgramClass> classes;
    protected Map<DexProgramClass, String> originalNames;
    protected final VirtualFile mainDexFile;
    protected final InternalOptions options;

    DistributorBase(
        ApplicationWriter writer, Collection<DexProgramClass> classes, InternalOptions options) {
      super(writer);
      this.options = options;
      this.classes = SetUtils.newIdentityHashSet(classes);

      // Create the primary dex file. The distribution will add more if needed.
      mainDexFile = new VirtualFile(0, appView);
      assert virtualFiles.isEmpty();
      virtualFiles.add(mainDexFile);
      addMarkers(mainDexFile);

      originalNames =
          computeOriginalNameMapping(
              classes, appView.graphLens(), appView.appInfo().app().getProguardMap());
    }

    private void addMarkers(VirtualFile virtualFile) {
      if (writer.markerStrings != null && !writer.markerStrings.isEmpty()) {
        for (DexString markerString : writer.markerStrings) {
          virtualFile.transaction.addString(markerString);
        }
        virtualFile.commitTransaction();
      }
    }

    protected void fillForMainDexList(Set<DexProgramClass> classes) {
      MainDexInfo mainDexInfo = appView.appInfo().getMainDexInfo();
      if (mainDexInfo.isEmpty()) {
        return;
      }
      VirtualFile mainDexFile = virtualFiles.get(0);
      mainDexInfo.forEach(
          type -> {
            DexProgramClass clazz = asProgramClassOrNull(appView.appInfo().definitionFor(type));
            if (clazz != null) {
              mainDexFile.addClass(clazz);
              classes.remove(clazz);
            }
            mainDexFile.commitTransaction();
          });
      if (Log.ENABLED) {
        Log.info(
            VirtualFile.class, "Main dex classes: " + mainDexFile.transaction.getNumberOfClasses());
        Log.info(
            VirtualFile.class, "Main dex methods: " + mainDexFile.transaction.getNumberOfMethods());
        Log.info(
            VirtualFile.class, "Main dex fields: " + mainDexFile.transaction.getNumberOfFields());
      }
      mainDexFile.throwIfFull(true, options.reporter);
    }

    protected Map<FeatureSplit, Set<DexProgramClass>> removeFeatureSplitClassesGetMapping() {
      assert appView.appInfo().hasClassHierarchy() == appView.enableWholeProgramOptimizations();
      if (!appView.appInfo().hasClassHierarchy()) {
        return ImmutableMap.of();
      }

      AppView<? extends AppInfoWithClassHierarchy> appViewWithClassHierarchy =
          appView.withClassHierarchy();
      ClassToFeatureSplitMap classToFeatureSplitMap =
          appViewWithClassHierarchy.appInfo().getClassToFeatureSplitMap();
      if (classToFeatureSplitMap.isEmpty()) {
        return ImmutableMap.of();
      }

      // Pull out the classes that should go into feature splits.
      Map<FeatureSplit, Set<DexProgramClass>> featureSplitClasses =
          classToFeatureSplitMap.getFeatureSplitClasses(classes, appViewWithClassHierarchy);
      if (featureSplitClasses.size() > 0) {
        for (Set<DexProgramClass> featureClasses : featureSplitClasses.values()) {
          classes.removeAll(featureClasses);
        }
      }
      return featureSplitClasses;
    }

    protected void addFeatureSplitFiles(
        Map<FeatureSplit, Set<DexProgramClass>> featureSplitClasses) {
      if (featureSplitClasses.isEmpty()) {
        return;
      }
      for (Map.Entry<FeatureSplit, Set<DexProgramClass>> featureSplitSetEntry :
          featureSplitClasses.entrySet()) {
        // Add a new virtual file, start from index 0 again
        IntBox nextFileId = new IntBox();
        VirtualFile featureFile =
            new VirtualFile(
                nextFileId.getAndIncrement(),
                appView,
                featureSplitSetEntry.getKey());
        virtualFiles.add(featureFile);
        addMarkers(featureFile);
        List<VirtualFile> files = virtualFiles;
        List<VirtualFile> filesForDistribution = ImmutableList.of(featureFile);
        new PackageSplitPopulator(
                files,
                filesForDistribution,
                appView,
                featureSplitSetEntry.getValue(),
                originalNames,
                nextFileId)
            .run();
      }
    }
  }

  public static class FillFilesDistributor extends DistributorBase {
    private final ExecutorService executorService;

    FillFilesDistributor(
        ApplicationWriter writer,
        Collection<DexProgramClass> classes,
        InternalOptions options,
        ExecutorService executorService) {
      super(writer, classes, options);
      this.executorService = executorService;
    }

    @Override
    public List<VirtualFile> run() throws IOException {
      assert virtualFiles.size() == 1;
      assert virtualFiles.get(0).isEmpty();

      int totalClassNumber = classes.size();
      // First fill required classes into the main dex file.
      fillForMainDexList(classes);
      if (classes.isEmpty()) {
        // All classes ended up in the main dex file, no more to do.
        return virtualFiles;
      }

      List<VirtualFile> filesForDistribution = virtualFiles;
      boolean multidexLegacy = !mainDexFile.isEmpty();
      if (options.minimalMainDex && multidexLegacy) {
        assert virtualFiles.size() == 1;
        assert !virtualFiles.get(0).isEmpty();
        // Don't consider the main dex for distribution.
        filesForDistribution = Collections.emptyList();
      }

      Map<FeatureSplit, Set<DexProgramClass>> featureSplitClasses =
          removeFeatureSplitClassesGetMapping();

      IntBox nextFileId = new IntBox(1);
      if (multidexLegacy && options.enableInheritanceClassInDexDistributor) {
        new InheritanceClassInDexDistributor(
                mainDexFile,
                virtualFiles,
                filesForDistribution,
                classes,
                nextFileId,
                appView,
                executorService)
            .distribute();
      } else {
        new PackageSplitPopulator(
                virtualFiles, filesForDistribution, appView, classes, originalNames, nextFileId)
            .run();
      }
      addFeatureSplitFiles(featureSplitClasses);

      assert totalClassNumber == virtualFiles.stream().mapToInt(dex -> dex.classes().size()).sum();
      return virtualFiles;
    }
  }

  public static class MonoDexDistributor extends DistributorBase {
    MonoDexDistributor(
        ApplicationWriter writer, Collection<DexProgramClass> classes, InternalOptions options) {
      super(writer, classes, options);
    }

    @Override
    public List<VirtualFile> run() throws ExecutionException, IOException {
      Map<FeatureSplit, Set<DexProgramClass>> featureSplitClasses =
          removeFeatureSplitClassesGetMapping();
      // Add all classes to the main dex file.
      for (DexProgramClass programClass : classes) {
        mainDexFile.addClass(programClass);
      }
      mainDexFile.commitTransaction();
      mainDexFile.throwIfFull(false, options.reporter);
      if (options.featureSplitConfiguration != null) {
        if (!featureSplitClasses.isEmpty()) {
          // TODO(141334414): Figure out if we allow multidex in features even when mono-dexing
          addFeatureSplitFiles(featureSplitClasses);
        }
      }
      return virtualFiles;
    }
  }

  private static class VirtualFileIndexedItemCollection implements IndexedItemCollection {

    private final GraphLens graphLens;
    private final InitClassLens initClassLens;
    private final NamingLens namingLens;

    private final Set<DexProgramClass> classes = Sets.newIdentityHashSet();
    private final Set<DexProto> protos = Sets.newIdentityHashSet();
    private final Set<DexType> types = Sets.newIdentityHashSet();
    private final Set<DexMethod> methods = Sets.newIdentityHashSet();
    private final Set<DexField> fields = Sets.newIdentityHashSet();
    private final Set<DexString> strings = Sets.newIdentityHashSet();
    private final Set<DexCallSite> callSites = Sets.newIdentityHashSet();
    private final Set<DexMethodHandle> methodHandles = Sets.newIdentityHashSet();

    public VirtualFileIndexedItemCollection(AppView<?> appView) {
      this.graphLens = appView.graphLens();
      this.initClassLens = appView.initClassLens();
      this.namingLens = appView.getNamingLens();
    }

    @Override
    public boolean addClass(DexProgramClass clazz) {
      return classes.add(clazz);
    }

    @Override
    public boolean addField(DexField field) {
      return fields.add(field);
    }

    @Override
    public boolean addMethod(DexMethod method) {
      return methods.add(method);
    }

    @Override
    public boolean addString(DexString string) {
      return strings.add(string);
    }

    @Override
    public boolean addProto(DexProto proto) {
      return protos.add(proto);
    }

    @Override
    public boolean addType(DexType type) {
      assert SyntheticNaming.verifyNotInternalSynthetic(type);
      return types.add(type);
    }

    @Override
    public boolean addCallSite(DexCallSite callSite) {
      return callSites.add(callSite);
    }

    @Override
    public boolean addMethodHandle(DexMethodHandle methodHandle) {
      return methodHandles.add(methodHandle);
    }

    int getNumberOfMethods() {
      return methods.size();
    }

    int getNumberOfFields() {
      return fields.size();
    }
  }

  public static class IndexedItemTransaction implements IndexedItemCollection {

    private final AppView<?> appView;
    private final VirtualFileIndexedItemCollection base;
    private final LensCodeRewriterUtils rewriter;

    private final Set<DexProgramClass> classes = new LinkedHashSet<>();
    private final Set<DexField> fields = new LinkedHashSet<>();
    private final Set<DexMethod> methods = new LinkedHashSet<>();
    private final Set<DexType> types = new LinkedHashSet<>();
    private final Set<DexProto> protos = new LinkedHashSet<>();
    private final Set<DexString> strings = new LinkedHashSet<>();
    private final Set<DexCallSite> callSites = new LinkedHashSet<>();
    private final Set<DexMethodHandle> methodHandles = new LinkedHashSet<>();

    private IndexedItemTransaction(VirtualFileIndexedItemCollection base, AppView<?> appView) {
      this.appView = appView;
      this.base = base;
      this.rewriter = new LensCodeRewriterUtils(appView, true);
    }

    private NamingLens getNamingLens() {
      return appView.getNamingLens();
    }

    private <T extends DexItem> boolean maybeInsert(T item, Set<T> set, Set<T> baseSet) {
      if (baseSet.contains(item) || set.contains(item)) {
        return false;
      }
      set.add(item);
      return true;
    }

    void addClassAndDependencies(DexProgramClass clazz) {
      clazz.collectIndexedItems(appView, this, rewriter);
    }

    @Override
    public boolean addClass(DexProgramClass dexProgramClass) {
      return maybeInsert(dexProgramClass, classes, base.classes);
    }

    @Override
    public boolean addField(DexField field) {
      return maybeInsert(field, fields, base.fields);
    }

    @Override
    public boolean addMethod(DexMethod method) {
      return maybeInsert(method, methods, base.methods);
    }

    @Override
    public boolean addString(DexString string) {
      return maybeInsert(string, strings, base.strings);
    }

    @Override
    public boolean addProto(DexProto proto) {
      return maybeInsert(proto, protos, base.protos);
    }

    @Override
    public boolean addType(DexType type) {
      assert SyntheticNaming.verifyNotInternalSynthetic(type);
      return maybeInsert(type, types, base.types);
    }

    @Override
    public boolean addCallSite(DexCallSite callSite) {
      return maybeInsert(callSite, callSites, base.callSites);
    }

    @Override
    public boolean addMethodHandle(DexMethodHandle methodHandle) {
      return maybeInsert(methodHandle, methodHandles, base.methodHandles);
    }

    int getNumberOfMethods() {
      return methods.size() + base.getNumberOfMethods();
    }

    int getNumberOfClasses() {
      return classes.size() + base.classes.size();
    }

    int getNumberOfFields() {
      return fields.size() + base.getNumberOfFields();
    }

    private <T extends DexItem> void commitItemsIn(Set<T> set, Function<T, Boolean> hook) {
      set.forEach((item) -> {
        boolean newlyAdded = hook.apply(item);
        assert newlyAdded;
      });
      set.clear();
    }

    void commit() {
      commitItemsIn(classes, base::addClass);
      commitItemsIn(fields, base::addField);
      commitItemsIn(methods, base::addMethod);
      commitItemsIn(protos, base::addProto);
      commitItemsIn(types, base::addType);
      commitItemsIn(strings, base::addString);
      commitItemsIn(callSites, base::addCallSite);
      commitItemsIn(methodHandles, base::addMethodHandle);
    }

    void abort() {
      classes.clear();
      fields.clear();
      methods.clear();
      protos.clear();
      types.clear();
      strings.clear();
    }

    public boolean isEmpty() {
      return classes.isEmpty() && fields.isEmpty() && methods.isEmpty() && protos.isEmpty()
          && types.isEmpty() && strings.isEmpty();
    }

  }

  /**
   * Helper class to cycle through the set of virtual files.
   *
   * Iteration starts at the first file and iterates through all files.
   *
   * When {@link VirtualFileCycler#restart()} is called iteration of all files is restarted at the
   * current file.
   *
   * If the fill strategy indicate that the main dex file should be minimal, then the main dex file
   * will not be part of the iteration.
   */
  static class VirtualFileCycler {

    private final List<VirtualFile> files;
    private final List<VirtualFile> filesForDistribution;
    private final AppView<?> appView;

    private final IntBox nextFileId;
    private Iterator<VirtualFile> allFilesCyclic;
    private Iterator<VirtualFile> activeFiles;
    private FeatureSplit featureSplit;

    VirtualFileCycler(
        List<VirtualFile> files,
        List<VirtualFile> filesForDistribution,
        AppView<?> appView,
        IntBox nextFileId) {
      this.files = files;
      this.filesForDistribution = new ArrayList<>(filesForDistribution);
      this.appView = appView;
      this.nextFileId = nextFileId;

      if (filesForDistribution.size() > 0) {
        featureSplit = filesForDistribution.get(0).getFeatureSplit();
      }

      reset();
    }

    void clearFilesForDistribution() {
      filesForDistribution.clear();
      reset();
    }

    void reset() {
      allFilesCyclic = Iterators.cycle(filesForDistribution);
      restart();
    }

    boolean hasNext() {
      return activeFiles.hasNext();
    }

    VirtualFile next() {
      return activeFiles.next();
    }

    /**
     * Get next {@link VirtualFile} and create a new empty one if there is no next available.
     */
    VirtualFile nextOrCreate() {
      if (hasNext()) {
        return next();
      } else {
        VirtualFile newFile = internalAddFile();
        allFilesCyclic = Iterators.cycle(filesForDistribution);
        return newFile;
      }
    }

    /**
     * Get next {@link VirtualFile} accepted by the given filter and create a new empty one if there
     * is no next available.
     * @param filter allows to to reject some of the available {@link VirtualFile}. Rejecting empt
     * {@link VirtualFile} is not authorized since it would sometimes prevent to find a result.
     */
    VirtualFile nextOrCreate(Predicate<? super VirtualFile> filter) {
      while (true) {
        VirtualFile dex = nextOrCreate();
        if (dex.isEmpty()) {
          assert filter.test(dex);
          return dex;
        } else if (filter.test(dex)) {
          return dex;
        }
      }
    }

    // Start a new iteration over all files, starting at the current one.
    void restart() {
      activeFiles = Iterators.limit(allFilesCyclic, filesForDistribution.size());
    }

    VirtualFile addFile() {
      VirtualFile newFile = internalAddFile();
      reset();
      return newFile;
    }

    private VirtualFile internalAddFile() {
      VirtualFile newFile = new VirtualFile(nextFileId.getAndIncrement(), appView, featureSplit);
      files.add(newFile);
      filesForDistribution.add(newFile);
      return newFile;
    }

    VirtualFileCycler ensureFile() {
      if (filesForDistribution.isEmpty()) {
        addFile();
      }
      return this;
    }
  }

  /**
   * Distributes the given classes over the files in package order.
   *
   * <p>The populator avoids package splits. Big packages are split into subpackages if their size
   * exceeds 20% of the dex file. This populator also avoids filling files completely to cater for
   * future growth.
   *
   * <p>The populator cycles through the files until all classes have been successfully placed and
   * adds new files to the passed in map if it can't fit in the existing files.
   */
  private static class PackageSplitPopulator {

    static class PackageSplitClassPartioning {

      // The set of startup classes, sorted by original names so that classes in the same package
      // are adjacent. This is empty if no startup configuration is given.
      private final List<DexProgramClass> startupClasses;

      // The remaining set of classes that must be written, sorted by original names so that classes
      // in the same package are adjacent.
      private final List<DexProgramClass> nonStartupClasses;

      private PackageSplitClassPartioning(
          List<DexProgramClass> startupClasses, List<DexProgramClass> nonStartupClasses) {
        this.startupClasses = startupClasses;
        this.nonStartupClasses = nonStartupClasses;
      }

      public static PackageSplitClassPartioning create(
          Collection<DexProgramClass> classes,
          AppView<?> appView,
          Map<DexProgramClass, String> originalNames) {
        return create(
            classes,
            getClassesByPackageComparator(originalNames),
            getStartupClassPredicate(appView));
      }

      private static PackageSplitClassPartioning create(
          Collection<DexProgramClass> classes,
          Comparator<DexProgramClass> comparator,
          Predicate<DexProgramClass> startupClassPredicate) {
        List<DexProgramClass> startupClasses = new ArrayList<>();
        List<DexProgramClass> nonStartupClasses = new ArrayList<>(classes.size());
        for (DexProgramClass clazz : classes) {
          if (startupClassPredicate.test(clazz)) {
            startupClasses.add(clazz);
          } else {
            nonStartupClasses.add(clazz);
          }
        }
        startupClasses.sort(comparator);
        nonStartupClasses.sort(comparator);
        return new PackageSplitClassPartioning(startupClasses, nonStartupClasses);
      }

      private static Comparator<DexProgramClass> getClassesByPackageComparator(
          Map<DexProgramClass, String> originalNames) {
        return (a, b) -> {
          String originalA = originalNames.get(a);
          String originalB = originalNames.get(b);
          int indexA = originalA.lastIndexOf('.');
          int indexB = originalB.lastIndexOf('.');
          if (indexA == -1 && indexB == -1) {
            // Empty package, compare the class names.
            return originalA.compareTo(originalB);
          }
          if (indexA == -1) {
            // Empty package name comes first.
            return -1;
          }
          if (indexB == -1) {
            // Empty package name comes first.
            return 1;
          }
          String prefixA = originalA.substring(0, indexA);
          String prefixB = originalB.substring(0, indexB);
          int result = prefixA.compareTo(prefixB);
          if (result != 0) {
            return result;
          }
          return originalA.compareTo(originalB);
        };
      }

      private static Predicate<DexProgramClass> getStartupClassPredicate(AppView<?> appView) {
        if (!appView.hasClassHierarchy()) {
          return alwaysFalse();
        }
        StartupOrder startupOrder = appView.appInfoWithClassHierarchy().getStartupOrder();
        SyntheticItems syntheticItems = appView.getSyntheticItems();
        return clazz -> startupOrder.contains(clazz.getType(), syntheticItems);
      }

      public List<DexProgramClass> getStartupClasses() {
        return startupClasses;
      }

      public List<DexProgramClass> getNonStartupClasses() {
        return nonStartupClasses;
      }
    }

    /**
     * Android suggests com.company.product for package names, so the components will be at level 4
     */
    private static final int MINIMUM_PREFIX_LENGTH = 4;
    private static final int MAXIMUM_PREFIX_LENGTH = 7;
    /**
     * We allow 1/MIN_FILL_FACTOR of a file to remain empty when moving to the next file, i.e., a
     * rollback with less than 1/MAX_FILL_FACTOR of the total classes in a file will move to the
     * next file.
     */
    private static final int MIN_FILL_FACTOR = 5;

    private final PackageSplitClassPartioning classPartioning;
    private final Map<DexProgramClass, String> originalNames;
    private final DexItemFactory dexItemFactory;
    private final InternalOptions options;
    private final VirtualFileCycler cycler;

    PackageSplitPopulator(
        List<VirtualFile> files,
        List<VirtualFile> filesForDistribution,
        AppView<?> appView,
        Collection<DexProgramClass> classes,
        Map<DexProgramClass, String> originalNames,
        IntBox nextFileId) {
      this.classPartioning = PackageSplitClassPartioning.create(classes, appView, originalNames);
      this.originalNames = originalNames;
      this.dexItemFactory = appView.dexItemFactory();
      this.options = appView.options();
      this.cycler = new VirtualFileCycler(files, filesForDistribution, appView, nextFileId);
    }

    static boolean coveredByPrefix(String originalName, String currentPrefix) {
      if (currentPrefix == null) {
        return false;
      }
      if (currentPrefix.endsWith(".*")) {
        return originalName.startsWith(currentPrefix.substring(0, currentPrefix.length() - 2));
      } else {
        return originalName.startsWith(currentPrefix)
            && originalName.lastIndexOf('.') == currentPrefix.length();
      }
    }

    private String getOriginalName(DexProgramClass clazz) {
      return originalNames.get(clazz);
    }

    public void run() {
      addStartupClasses();
      enableStartupCompletenessCheckForTesting();
      List<DexProgramClass> nonPackageClasses = addNonStartupClasses();
      addNonPackageClasses(cycler, nonPackageClasses);
    }

    private void addStartupClasses() {
      List<DexProgramClass> startupClasses = classPartioning.getStartupClasses();
      if (startupClasses.isEmpty()) {
        return;
      }

      assert options.getStartupOptions().hasStartupConfiguration();

      // In practice, all startup classes should fit in a single dex file, so optimistically try to
      // commit the startup classes using a single transaction.
      VirtualFile virtualFile = cycler.next();
      for (DexProgramClass startupClass : classPartioning.getStartupClasses()) {
        virtualFile.addClass(startupClass);
      }

      if (hasSpaceForTransaction(virtualFile, options)) {
        virtualFile.commitTransaction();
      } else {
        virtualFile.abortTransaction();

        // If the above failed, then add the startup classes one by one.
        for (DexProgramClass startupClass : classPartioning.getStartupClasses()) {
          virtualFile.addClass(startupClass);
          if (hasSpaceForTransaction(virtualFile, options)) {
            virtualFile.commitTransaction();
          } else {
            virtualFile.abortTransaction();
            virtualFile = cycler.addFile();
            virtualFile.addClass(startupClass);
            assert hasSpaceForTransaction(virtualFile, options);
            virtualFile.commitTransaction();
          }
        }
      }

      if (options.getStartupOptions().isMinimalStartupDexEnabled()) {
        cycler.clearFilesForDistribution();
      } else {
        cycler.restart();
      }
    }

    /**
     * Replaces the code of each method of a non-startup class by {@code throw null}. If the
     * application fails on launch with this enabled this points to the startup configuration being
     * incomplete.
     */
    private void enableStartupCompletenessCheckForTesting() {
      if (!options.getStartupOptions().isStartupCompletenessCheckForTesting()) {
        return;
      }
      for (DexProgramClass clazz : classPartioning.getNonStartupClasses()) {
        clazz.forEachProgramMethodMatching(
            DexEncodedMethod::hasCode,
            method ->
                method.getDefinition().setCode(ThrowNullCode.get(), Int2ReferenceMaps.emptyMap()));
        if (!clazz.hasClassInitializer()) {
          clazz.addDirectMethod(
              DexEncodedMethod.syntheticBuilder()
                  .setAccessFlags(MethodAccessFlags.createForClassInitializer())
                  .setCode(ThrowNullCode.get())
                  .setMethod(dexItemFactory.createClassInitializer(clazz.getType()))
                  .build());
        }
      }
    }

    private List<DexProgramClass> addNonStartupClasses() {
      int prefixLength = MINIMUM_PREFIX_LENGTH;
      int transactionStartIndex = 0;
      String currentPrefix = null;
      Object2IntMap<String> packageAssignments = new Object2IntOpenHashMap<>();
      VirtualFile current = cycler.ensureFile().next();
      List<DexProgramClass> classes = classPartioning.getNonStartupClasses();
      List<DexProgramClass> nonPackageClasses = new ArrayList<>();
      for (int classIndex = 0; classIndex < classes.size(); classIndex++) {
        DexProgramClass clazz = classes.get(classIndex);
        String originalName = getOriginalName(clazz);
        if (!coveredByPrefix(originalName, currentPrefix)) {
          if (currentPrefix != null) {
            current.commitTransaction();
            assert verifyPackageToVirtualFileAssignment(packageAssignments, currentPrefix, current);
            // Reset the cycler to again iterate over all files, starting with the current one.
            cycler.restart();
            // Try to reduce the prefix length if possible. Only do this on a successful commit.
            prefixLength = MINIMUM_PREFIX_LENGTH - 1;
          }
          String newPrefix;
          // Also, we need to avoid new prefixes that are a prefix of previously used prefixes, as
          // otherwise we might generate an overlap that will trigger problems when reusing the
          // package mapping generated here. For example, if an existing map contained
          //   com.android.foo.*
          // but we now try to place some new subpackage
          //   com.android.bar.*,
          // we locally could use
          //   com.android.*.
          // However, when writing out the final package map, we get overlapping patterns
          // com.android.* and com.android.foo.*.
          do {
            newPrefix = extractPrefixToken(++prefixLength, originalName, false);
          } while (currentPrefix != null && currentPrefix.startsWith(newPrefix));
          // Don't set the current prefix if we did not extract one.
          if (!newPrefix.equals("")) {
            currentPrefix = extractPrefixToken(prefixLength, originalName, true);
          }
          transactionStartIndex = classIndex;
        }

        if (currentPrefix == null) {
          assert clazz.superType != null;
          // We don't have a package, add this to a list of classes that we will add last.
          assert current.transaction.isEmpty();
          nonPackageClasses.add(clazz);
          continue;
        }

        assert clazz.superType != null || clazz.type == dexItemFactory.objectType;
        current.addClass(clazz);

        if (hasSpaceForTransaction(current, options)) {
          continue;
        }

        int numberOfClassesInTransaction = classIndex - transactionStartIndex + 1;
        int numberOfClassesInVirtualFileWithTransaction = current.getNumberOfClasses();

        current.abortTransaction();

        // We allow for a final rollback that has at most 20% of classes in it.
        // This is a somewhat random number that was empirically chosen.
        if (numberOfClassesInTransaction
                > numberOfClassesInVirtualFileWithTransaction / MIN_FILL_FACTOR
            && prefixLength < MAXIMUM_PREFIX_LENGTH) {
          // Go back to previous start index.
          classIndex = transactionStartIndex - 1;
          currentPrefix = null;
          prefixLength++;
          continue;
        }

        // Reset the state to after the last commit and cycle through files.
        // The idea is that we do not increase the number of files, so it has to fit somewhere.
        if (!cycler.hasNext()) {
          // Special case where we simply will never be able to fit the current package into
          // one dex file. This is currently the case for Strings in jumbo tests, see b/33227518.
          if (current.isEmpty()) {
            for (int j = transactionStartIndex; j <= classIndex; j++) {
              nonPackageClasses.add(classes.get(j));
            }
            transactionStartIndex = classIndex + 1;
          }
          // All files are filled up to the 20% mark.
          cycler.addFile();
        }

        // Go back to previous start index.
        classIndex = transactionStartIndex - 1;
        current = cycler.next();
        currentPrefix = null;
        prefixLength = MINIMUM_PREFIX_LENGTH;
      }

      current.commitTransaction();
      assert currentPrefix == null
          || verifyPackageToVirtualFileAssignment(packageAssignments, currentPrefix, current);

      return nonPackageClasses;
    }

    private static String extractPrefixToken(int prefixLength, String className, boolean addStar) {
      int index = 0;
      int lastIndex = 0;
      int segmentCount = 0;
      while (lastIndex != -1 && segmentCount++ < prefixLength) {
        index = lastIndex;
        lastIndex = className.indexOf('.', index + 1);
      }
      String prefix = className.substring(0, index);
      if (addStar && segmentCount >= prefixLength) {
        // Full match, add a * to also match sub-packages.
        prefix += ".*";
      }
      return prefix;
    }

    private boolean verifyPackageToVirtualFileAssignment(
        Object2IntMap<String> packageAssignments, String packageName, VirtualFile virtualFile) {
      assert !packageAssignments.containsKey(packageName);
      packageAssignments.put(packageName, virtualFile.getId());
      return true;
    }

    private boolean hasSpaceForTransaction(VirtualFile current, InternalOptions options) {
      return !isFullEnough(current, options);
    }

    private boolean isFullEnough(VirtualFile current, InternalOptions options) {
      if (options.testing.limitNumberOfClassesPerDex > 0
          && current.getNumberOfClasses() > options.testing.limitNumberOfClassesPerDex) {
        return true;
      }
      return current.isFull();
    }

    private void addNonPackageClasses(
        VirtualFileCycler cycler, List<DexProgramClass> nonPackageClasses) {
      if (nonPackageClasses.isEmpty()) {
        return;
      }
      cycler.restart();
      VirtualFile current;
      current = cycler.next();
      for (DexProgramClass clazz : nonPackageClasses) {
        if (current.isFull()) {
          current = getVirtualFile(cycler);
        }
        current.addClass(clazz);
        while (current.isFull()) {
          // This only happens if we have a huge class, that takes up more than 20% of a dex file.
          current.abortTransaction();
          current = getVirtualFile(cycler);
          boolean wasEmpty = current.isEmpty();
          current.addClass(clazz);
          if (wasEmpty && current.isFull()) {
            throw new InternalCompilerError(
                "Class " + clazz.toString() + " does not fit into a single dex file.");
          }
        }
        current.commitTransaction();
      }
    }

    private VirtualFile getVirtualFile(VirtualFileCycler cycler) {
      VirtualFile current = null;
      while (cycler.hasNext() && isFullEnough(current = cycler.next(), options)) {}
      if (current == null || isFullEnough(current, options)) {
        current = cycler.addFile();
      }
      return current;
    }
  }

}
