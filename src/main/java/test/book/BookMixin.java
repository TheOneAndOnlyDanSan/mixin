package test.book;

import mixin.annotations.Method.MethodAnnotations;
import mixin.annotations.Mixin;
import mixin.annotations.Overwrite;

import java.lang.annotation.Annotation;
import java.util.Map;

import static mixin.Util.getAnnotation;

@Mixin(Book.class)
public class BookMixin {
    public static Book book = new Book("bookMixin", 2000, 101.1);

    @MethodAnnotations("get()Ltest/book/Book;")
    public static Annotation[] get() {
        return new Annotation[]{getAnnotation(Deprecated.class), getAnnotation(Deprecated.class, Map.ofEntries(Map.entry("since", "1")))};
    }

    @Overwrite("get()Ltest/book/Book;")
    public static Book get(Book book1) {
        return book;
    }
}
