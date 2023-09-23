package test.book;

import jdk.jfr.Label;
import mixin.Util;
import mixin.annotations.Annotations;
import mixin.annotations.Mixin;
import mixin.annotations.Shadow;
import mixin.annotations.method.ChangeReturn;
import mixin.annotations.method.InjectHead;
import mixin.annotations.method.InjectTail;
import mixin.annotations.method.Overwrite;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

@Mixin("test.book.Book")
public class BookMixin {
    public static Book book = new Book("bookMixin", 2000, 101.1);

    @Shadow static int publishedYear;
    @Shadow static double staticTestField;

    public static double injectHeadTestBool;
    public static double injectTailTestBool;

    public static double getStaticTestField(Book instance) {
        return staticTestField;
    }

    public static void setStaticTestField(Book instance, double value) {
        staticTestField = value;
    }

    public static void setPublishedYear(Book instance, int value) {
        publishedYear = value;
    }

    public static int getPublishedYear(Book instance) {
        return publishedYear;
    }

    @Annotations("name")
    private static Annotation[] getNameAnnotations() {
        Label label = Util.getAnnotation(Label.class, Map.ofEntries(Map.entry("value", "LabelNameAnnotationMixin")));
        return new Annotation[]{label};
    }

    @Annotations("get()Ltest/book/Book;")
    public static Annotation[] getgetNameAnnotations() {
        Label label = Util.getAnnotation(Label.class, Map.ofEntries(Map.entry("value", "LabelBookAnnotationMixin")));
        return new Annotation[]{label};
    }

    @Annotations("cost")
    public static Annotation[] getCostNameAnnotations() {
        Label label = Util.getAnnotation(Label.class, Map.ofEntries(Map.entry("value", "LabelCostAnnotation")));
        return new Annotation[]{label};
    }

    @InjectHead("setCost(D)V")
    public static void setHead(Book instance, double cost) {
        injectHeadTestBool = cost;
    }

    @InjectTail("setCost(D)V")
    public static void setTail(Book instance, double cost) {
        injectTailTestBool = cost;
    }

    @ChangeReturn("get()Ltest/book/Book;")
    public static Book get(Book instance, Book returnVal) {
        return (returnVal != null && returnVal == instance) ? book : returnVal;
    }
}