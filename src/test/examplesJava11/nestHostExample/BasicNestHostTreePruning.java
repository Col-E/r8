package nestHostExample;

public class BasicNestHostTreePruning {

  private String field = "NotPruned";

  public static class NotPruned extends BasicNestHostTreePruning {

    public String getFields() {
      return ((BasicNestHostTreePruning) this).field;
    }
  }

  public static class Pruned {

    public static void main(String[] args) {
      System.out.println("NotPruned");
    }
  }

  public static void main(String[] args) {
    System.out.println(new NotPruned().getFields());
  }
}
