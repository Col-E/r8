package backport;

public final class ShortBackportJava9Main {
  private static final byte MIN_UNSIGNED_VALUE = (short) 0;
  private static final byte MAX_UNSIGNED_VALUE = (short) -1;

  public static void main(String[] args) {
    testCompareUnsigned();
  }

  private static void testCompareUnsigned() {
    assertTrue(Short.compareUnsigned(MIN_UNSIGNED_VALUE, MIN_UNSIGNED_VALUE) == 0);
    assertTrue(Short.compareUnsigned(MIN_UNSIGNED_VALUE, Short.MAX_VALUE) < 0);
    assertTrue(Short.compareUnsigned(MIN_UNSIGNED_VALUE, Short.MIN_VALUE) < 0);
    assertTrue(Short.compareUnsigned(MIN_UNSIGNED_VALUE, MAX_UNSIGNED_VALUE) < 0);

    assertTrue(Short.compareUnsigned(Short.MAX_VALUE, MIN_UNSIGNED_VALUE) > 0);
    assertTrue(Short.compareUnsigned(Short.MAX_VALUE, Short.MAX_VALUE) == 0);
    assertTrue(Short.compareUnsigned(Short.MAX_VALUE, Short.MIN_VALUE) < 0);
    assertTrue(Short.compareUnsigned(Short.MAX_VALUE, MAX_UNSIGNED_VALUE) < 0);

    assertTrue(Short.compareUnsigned(Short.MIN_VALUE, MIN_UNSIGNED_VALUE) > 0);
    assertTrue(Short.compareUnsigned(Short.MIN_VALUE, Short.MAX_VALUE) > 0);
    assertTrue(Short.compareUnsigned(Short.MIN_VALUE, Short.MIN_VALUE) == 0);
    assertTrue(Short.compareUnsigned(Short.MIN_VALUE, MAX_UNSIGNED_VALUE) < 0);

    assertTrue(Short.compareUnsigned(MAX_UNSIGNED_VALUE, MIN_UNSIGNED_VALUE) > 0);
    assertTrue(Short.compareUnsigned(MAX_UNSIGNED_VALUE, Short.MAX_VALUE) > 0);
    assertTrue(Short.compareUnsigned(MAX_UNSIGNED_VALUE, Short.MIN_VALUE) > 0);
    assertTrue(Short.compareUnsigned(MAX_UNSIGNED_VALUE, MAX_UNSIGNED_VALUE) == 0);
  }

  private static void assertTrue(boolean value) {
    if (!value) {
      throw new AssertionError("Expected <true> but was <false>");
    }
  }
}
