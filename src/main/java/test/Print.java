package test;

public class Print {

    private final String a;
    private static final String b;

    static {
        b = "privateStatic";
    }

    public Print() {
        a = "private";
    }


    public void print() {
        System.out.printf("%d, %d, %d, %d%n", getPrintInt(), getPrintInteger(), getPrintIntArray()[0], getPrintIntegerArray()[0]);
        print2();
    }

    public void print2() {
        System.out.printf("%d, %d, %d, %d%n", getPrintInt(), getPrintInteger(), getPrintIntArray()[0], getPrintIntegerArray()[0]);
    }

    private String getA() {
        return a;
    }

    private static String getB() {
        return b;
    }

    private int getPrintInt() {
        return 0;
    }

    private Integer getPrintInteger() {
        return 0;
    }

    private static int[] getPrintIntArray() {
        return new int[] {0};
    }

    private static Integer[] getPrintIntegerArray() {
        return new Integer[] {0};
    }
}

