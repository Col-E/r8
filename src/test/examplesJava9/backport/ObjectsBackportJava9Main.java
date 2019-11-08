package backport;

import java.util.Objects;

public final class ObjectsBackportJava9Main {
  public static void main(String[] args) {
    boolean isAndroid = "Dalvik".equals(System.getProperty("java.vm.name"));
    String majorVersion = System.getProperty("java.vm.version").split("\\.", -1)[0];

    testRequireNonNullElse();
    if (!isAndroid || Integer.parseInt(majorVersion) >= 7) {
      // TODO desugaring desugaredlibrary is blocked by
      // https://issuetracker.google.com/issues/114481425
      testRequireNonNullElseGet();
    }
    testCheckIndex();
    testCheckFromToIndex();
    testCheckFromIndexSize();
  }

  private static void testRequireNonNullElse() {
    Object one = new Object();
    Object two = new Object();

    assertSame(one, Objects.requireNonNullElse(one, two));
    assertSame(two, Objects.requireNonNullElse(null, two));

    try {
      throw new AssertionError(Objects.requireNonNullElse(null, null));
    } catch (NullPointerException expected) {
    }
  }

  private static void testRequireNonNullElseGet() {
    Object one = new Object();
    Object two = new Object();

    assertSame(one, Objects.requireNonNullElseGet(one, () -> two));
    assertSame(two, Objects.requireNonNullElseGet(null, () -> two));

    try {
      throw new AssertionError(Objects.requireNonNullElseGet(null, null));
    } catch (NullPointerException expected) {
    }
    try {
      throw new AssertionError(Objects.requireNonNullElseGet(null, () -> null));
    } catch (NullPointerException expected) {
    }
  }

  private static void testCheckIndex() {
    for (int i = 0; i < 10; i++) {
      assertEquals(i, Objects.checkIndex(i, 10));
    }

    try {
      throw new AssertionError(Objects.checkIndex(-1, 10));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkIndex(10, 0));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkIndex(0, 0));
    } catch (IndexOutOfBoundsException expected) {
    }
  }

  private static void testCheckFromToIndex() {
    for (int i = 0; i <= 10; i++) {
      for (int j = i; j <= 10; j++) {
        assertEquals(i, Objects.checkFromToIndex(i, j, 10));
      }
    }
    assertEquals(0, Objects.checkFromToIndex(0, 0, 0));

    try {
      throw new AssertionError(Objects.checkFromToIndex(4, 2, 10));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromToIndex(-1, 5, 10));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromToIndex(0, -1, 10));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromToIndex(11, 11, 10));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromToIndex(0, 1, 0));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromToIndex(1, 1, 0));
    } catch (IndexOutOfBoundsException expected) {
    }
  }

  private static void testCheckFromIndexSize() {
    for (int i = 0; i <= 10; i++) {
      for (int j = 10 - i; j >= 0; j--) {
        assertEquals(i, Objects.checkFromIndexSize(i, j, 10));
      }
    }
    assertEquals(0, Objects.checkFromIndexSize(0, 0, 0));

    try {
      throw new AssertionError(Objects.checkFromIndexSize(8, 4, 10));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromIndexSize(-1, 5, 10));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromIndexSize(11, 0, 10));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromIndexSize(0, 1, 0));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromIndexSize(1, 1, 0));
    } catch (IndexOutOfBoundsException expected) {
    }

    // Check for cases where overflow might occur producing incorrect results.
    try {
      throw new AssertionError(Objects.checkFromIndexSize(Integer.MAX_VALUE, 1, Integer.MAX_VALUE));
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      throw new AssertionError(Objects.checkFromIndexSize(0, 1, Integer.MIN_VALUE));
    } catch (IndexOutOfBoundsException expected) {
    }
  }

  private static void assertEquals(int expected, int actual) {
    if (expected != actual) {
      throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
    }
  }

  private static void assertSame(Object expected, Object actual) {
    if (expected != actual) {
      throw new AssertionError(
          "Expected <" + expected + "> to be same instance as <" + actual + '>');
    }
  }
}
