package test.book;

import mixin.ClassInjector;

public class BookTest {

    public static void test() {
        getWithoutMixin();

        ClassInjector.addClasses(BookMixin.class);

        getWithMixin();
    }

    public static void getWithoutMixin() {
        Book book = new Book("x", 1999, 100.1);

        if(book.get() != book) throw new RuntimeException();
    }

    public static void getWithMixin() {
        Book book = new Book("x", 1999, 100.1);

        if(book.get() != BookMixin.book) throw new RuntimeException();
    }
}
