// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.InitClassLens;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.CompareToVisitorWithStringTable;
import com.android.tools.r8.utils.structural.CompareToVisitorWithTypeTable;
import com.android.tools.r8.utils.structural.StructuralItem;
import it.unimi.dsi.fastutil.objects.Reference2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ObjectToOffsetMapping {

  private final static int NOT_FOUND = -1;

  private final int lazyDexStringsCount;
  private final AppView<?> appView;
  private final GraphLens graphLens;
  private final NamingLens namingLens;
  private final InitClassLens initClassLens;
  private final LensCodeRewriterUtils lensCodeRewriter;

  // Sorted collection of objects mapped to their offsets.
  private final DexProgramClass[] classes;
  private final Reference2IntLinkedOpenHashMap<DexProto> protos;
  private final Reference2IntLinkedOpenHashMap<DexType> types;
  private final Reference2IntLinkedOpenHashMap<DexMethod> methods;
  private final Reference2IntLinkedOpenHashMap<DexField> fields;
  // Non-final to support (order consistent) reindexing in case of lazy computed strings.
  private Reference2IntLinkedOpenHashMap<DexString> strings;
  private final Reference2IntLinkedOpenHashMap<DexCallSite> callSites;
  private final Reference2IntLinkedOpenHashMap<DexMethodHandle> methodHandles;
  // Indirection table of protos to their shorty strings.
  private final Map<DexProto, DexString> protoToShorty;

  private DexString firstJumboString;

  private final CompareToVisitor compareToVisitor;

  public ObjectToOffsetMapping(
      AppView<?> appView,
      ObjectToOffsetMapping sharedMapping,
      LensCodeRewriterUtils lensCodeRewriter,
      Collection<DexProgramClass> classes,
      Map<DexProto, DexString> protos,
      Collection<DexType> types,
      Collection<DexMethod> methods,
      Collection<DexField> fields,
      Collection<DexString> strings,
      Collection<DexCallSite> callSites,
      Collection<DexMethodHandle> methodHandles,
      int lazyDexStringsCount,
      Timing timing) {
    assert appView != null;
    assert classes != null;
    assert protos != null;
    assert types != null;
    assert methods != null;
    assert fields != null;
    assert strings != null;
    assert callSites != null;
    assert methodHandles != null;
    this.lazyDexStringsCount = lazyDexStringsCount;
    this.appView = appView;
    this.graphLens = appView.graphLens();
    this.namingLens = appView.getNamingLens();
    this.initClassLens = appView.initClassLens();
    this.lensCodeRewriter = lensCodeRewriter;
    this.protoToShorty = protos;
    timing.begin("Sort strings");
    if (sharedMapping == null) {
      this.strings =
          createSortedMap(
              strings, DexString::compareTo, this::setFirstJumboString, lazyDexStringsCount);
    } else {
      this.strings = sharedMapping.strings;
      this.firstJumboString = sharedMapping.firstJumboString;
    }
    CompareToVisitor visitor =
        new CompareToVisitorWithStringTable(namingLens, this.strings::getInt);
    timing.end();
    timing.begin("Sort types");
    this.types = createSortedMap(types, compare(visitor), this::failOnOverflow);
    visitor =
        new CompareToVisitorWithTypeTable(namingLens, this.strings::getInt, this.types::getInt);
    timing.end();
    timing.begin("Sort classes");
    this.classes = sortClasses(classes, visitor);
    timing.end();
    timing.begin("Sort protos");
    this.protos = createSortedMap(protos.keySet(), compare(visitor), this::failOnOverflow);
    timing.end();
    timing.begin("Sort methods");
    this.methods = createSortedMap(methods, compare(visitor), this::failOnOverflow);
    timing.end();
    timing.begin("Sort fields");
    this.fields = createSortedMap(fields, compare(visitor), this::failOnOverflow);
    timing.end();
    timing.begin("Sort call-sites");
    this.callSites = createSortedMap(callSites, compare(visitor), this::failOnOverflow);
    timing.end();
    timing.begin("Sort method handles");
    this.methodHandles = createSortedMap(methodHandles, compare(visitor), this::failOnOverflow);
    timing.end();

    ObjectToOffsetMapping mapping = this;
    compareToVisitor =
        new CompareToVisitorWithTypeTable(namingLens, this.strings::getInt, this.types::getInt) {

          @Override
          public int visitDexField(DexField field1, DexField field2) {
            return Integer.compare(mapping.fields.getInt(field1), mapping.fields.getInt(field2));
          }

          @Override
          public int visitDexMethod(DexMethod method1, DexMethod method2) {
            return Integer.compare(
                mapping.methods.getInt(method1), mapping.methods.getInt(method2));
          }
        };
  }

  public void computeAndReindexForLazyDexStrings(List<DexString> forcedStrings) {
    assert lazyDexStringsCount == forcedStrings.size();
    if (forcedStrings.isEmpty()) {
      return;
    }
    // If there are any lazy strings we need to recompute the offsets, even if the strings
    // are already in the set as we have shifted the initial offset by the size of lazy strings.
    for (DexString forcedString : forcedStrings) {
      // Amend the string table to ensure all strings are now present.
      if (forcedString != null) {
        strings.put(forcedString, -1);
      }
    }
    Box<DexString> newJumboString = new Box<>();
    strings = createSortedMap(strings.keySet(), DexString::compareTo, newJumboString::set);
    // After reindexing it must hold that the new jumbo start is on the same or a larger string.
    // The new jumbo string is not set as the first determined string is still the cut-off point
    // where JumboString instructions are used.
    assert !hasJumboStrings() || newJumboString.get().isGreaterThanOrEqualTo(getFirstJumboString());
  }

  public CompareToVisitor getCompareToVisitor() {
    return compareToVisitor;
  }

  private <T extends StructuralItem<T>> Comparator<T> compare(CompareToVisitor visitor) {
    return (a, b) -> a.acceptCompareTo(b, visitor);
  }

  private void setFirstJumboString(DexString string) {
    assert firstJumboString == null;
    firstJumboString = string;
  }

  private void failOnOverflow(DexItem item) {
    throw new CompilationError("Index overflow for " + item.getClass());
  }

  private <T> Reference2IntLinkedOpenHashMap<T> createSortedMap(
      Collection<T> items, Comparator<T> comparator, Consumer<T> onUInt16Overflow) {
    return createSortedMap(items, comparator, onUInt16Overflow, 0);
  }

  private <T> Reference2IntLinkedOpenHashMap<T> createSortedMap(
      Collection<T> items,
      Comparator<T> comparator,
      Consumer<T> onUInt16Overflow,
      int reservedIndicesBeforeOverflow) {
    if (items.isEmpty()) {
      return new Reference2IntLinkedOpenHashMap<>();
    }
    // Sort items and compute the offset mapping for each in sorted order.
    ArrayList<T> sorted = new ArrayList<>(items);
    sorted.sort(comparator);
    Reference2IntLinkedOpenHashMap<T> map = new Reference2IntLinkedOpenHashMap<>(items.size());
    map.defaultReturnValue(NOT_FOUND);
    int index = 0;
    for (T item : sorted) {
      if (index + reservedIndicesBeforeOverflow == Constants.U16BIT_MAX + 1) {
        onUInt16Overflow.accept(item);
      }
      map.put(item, index++);
    }
    return map;
  }

  /**
   * Here, 'depth' of a program class is an integer one bigger then the maximum depth of its
   * superclass and implemented interfaces. The depth of classes without any or without known
   * superclasses and interfaces is 1.
   */
  private static class ProgramClassDepthsMemoized {

    private final static int UNKNOWN_DEPTH = -1;

    private final AppInfo appInfo;
    private final Reference2IntMap<DexProgramClass> depthOfClasses = new Reference2IntOpenHashMap<>();

    ProgramClassDepthsMemoized(AppInfo appInfo) {
      this.appInfo = appInfo;
      depthOfClasses.defaultReturnValue(UNKNOWN_DEPTH);
    }

    int getDepth(DexProgramClass programClass) {
      int depth = depthOfClasses.getInt(programClass);

      // TODO(b/65536002): use "computeIntIfAbsent" after upgrading to fastutils 8.x.
      if (depth == UNKNOWN_DEPTH) {
        // Emulating the algorithm of com.android.dx.merge.SortableType.tryAssignDepth().
        DexType superType = programClass.superType;
        int maxDepth;
        if (superType == null) {
          maxDepth = 0;
        } else {
          maxDepth = 1;
          DexProgramClass superClass = appInfo.programDefinitionFor(superType, programClass);
          if (superClass != null) {
            maxDepth = getDepth(superClass);
          }
        }
        for (DexType inf : programClass.interfaces.values) {
          DexProgramClass infClass = appInfo.programDefinitionFor(inf, programClass);
          maxDepth = Math.max(maxDepth, infClass == null ? 1 : getDepth(infClass));
        }
        depth = maxDepth + 1;
        depthOfClasses.put(programClass, depth);
      }

      return depth;
    }
  }

  private DexProgramClass[] sortClasses(
      Collection<DexProgramClass> classes, CompareToVisitor visitor) {
    // Collect classes in subtyping order, based on a sorted list of classes to start with.
    ProgramClassDepthsMemoized classDepths = new ProgramClassDepthsMemoized(appView.appInfo());
    DexProgramClass[] sortedClasses = classes.toArray(DexProgramClass.EMPTY_ARRAY);
    Arrays.sort(
        sortedClasses,
        (x, y) -> {
          int dx = classDepths.getDepth(x);
          int dy = classDepths.getDepth(y);
          return dx != dy ? dx - dy : visitor.visitDexType(x.type, y.type);
        });
    return sortedClasses;
  }

  private static <T> Collection<T> keysOrEmpty(Reference2IntLinkedOpenHashMap<T> map) {
    // The key-set is deterministic (linked) and inserted in sorted order.
    return map == null ? Collections.emptyList() : map.keySet();
  }

  public DexItemFactory dexItemFactory() {
    return appView.dexItemFactory();
  }

  public GraphLens getGraphLens() {
    return graphLens;
  }

  public NamingLens getNamingLens() {
    return namingLens;
  }

  public LensCodeRewriterUtils getLensCodeRewriter() {
    return lensCodeRewriter;
  }

  public Collection<DexMethod> getMethods() {
    return keysOrEmpty(methods);
  }

  public DexProgramClass[] getClasses() {
    return classes;
  }

  public Collection<DexType> getTypes() {
    return keysOrEmpty(types);
  }

  public Collection<DexProto> getProtos() {
    return keysOrEmpty(protos);
  }

  public Collection<DexField> getFields() {
    return keysOrEmpty(fields);
  }

  public Collection<DexString> getStrings() {
    return keysOrEmpty(strings);
  }

  public Collection<DexCallSite> getCallSites() {
    return keysOrEmpty(callSites);
  }

  public Collection<DexMethodHandle> getMethodHandles() {
    return keysOrEmpty(methodHandles);
  }

  public DexString getShorty(DexProto proto) {
    return protoToShorty.get(proto);
  }

  public boolean hasJumboStrings() {
    return firstJumboString != null;
  }

  public DexString getFirstJumboString() {
    return firstJumboString;
  }

  public DexString getFirstString() {
    for (Entry<DexString> dexStringEntry : strings.reference2IntEntrySet()) {
      if (dexStringEntry.getIntValue() == 0) {
        return dexStringEntry.getKey();
      }
    }
    return null;
  }

  private <T extends IndexedDexItem> int getOffsetFor(T item, Reference2IntMap<T> map) {
    int index = map.getInt(item);
    assert index != NOT_FOUND : "Missing dependency: " + item;
    return index;
  }

  public int getOffsetFor(DexProto proto) {
    return getOffsetFor(proto, protos);
  }

  public int getOffsetFor(DexField field) {
    return getOffsetFor(field, fields);
  }

  public int getOffsetFor(DexMethod method) {
    return getOffsetFor(method, methods);
  }

  public int getOffsetFor(DexString string) {
    return getOffsetFor(string, strings);
  }

  public int getOffsetFor(DexType type) {
    return getOffsetFor(type, types);
  }

  public int getOffsetFor(DexCallSite callSite) {
    return getOffsetFor(callSite, callSites);
  }

  public int getOffsetFor(DexMethodHandle methodHandle) {
    return getOffsetFor(methodHandle, methodHandles);
  }

  public DexField getClinitField(DexType type) {
    return initClassLens.getInitClassField(type);
  }
}
