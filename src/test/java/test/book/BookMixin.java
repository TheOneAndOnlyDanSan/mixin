package test.book;

import jdk.jfr.Label;
import mixin.Util;
import mixin.annotations.Annotations;
import mixin.annotations.Mixin;
import mixin.annotations.Shadow;
import mixin.annotations.method.Overwrite;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

@Mixin(Book.class)
public class BookMixin {
    public static Book book = new Book("bookMixin", 2000, 101.1);

    @Shadow static int publishedYear;
    @Shadow static double staticTestField;

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

    @Overwrite("get()Ltest/book/Book;")
    public static Book get(Book book1) {
        return book;
    }
}