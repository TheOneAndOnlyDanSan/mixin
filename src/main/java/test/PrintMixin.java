package test;

import mixin.annotations.Mixin;
import mixin.annotations.Overwrite;
import mixin.annotations.Shadow.ShadowMethod;

@Mixin(Print.class)
public class PrintMixin {

    @ShadowMethod
    static String getA(Print print) {
        return null;
    }

    @ShadowMethod
    static String getB(Print print) {
        return null;
    }

    @Overwrite("print2()V")
    public static void print(Print print) {
        System.out.println(getA(print) + ", " + getB(null));
    }

    @Overwrite("getPrintInt()I")
    private static int getPrintInt(Print print) {
        return 1;
    }

    @Overwrite("getPrintInteger()Ljava/lang/Integer;")
    private static Integer getPrintInteger(Print print) {
        return 2;
    }

    @Overwrite("getPrintIntArray()[I")
    private static int[] getPrintIntArray(Print print) {
        return new int[] {3};
    }

    @Overwrite("getPrintIntegerArray()[Ljava/lang/Integer;")
    private static Integer[] getPrintIntegerArray(Print print) {
        return new Integer[] {4};
    }
}
