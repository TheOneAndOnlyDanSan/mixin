package test.books;

import mixin.ClassInjector;

public class BooksTest {

    public static void test() {
        //findWithoutMixin();

        ClassInjector.addClasses(BooksMixin.class);

        findWithMixin();
    }

    public static void findWithoutMixin() {
        Books books = new Books();

        if(books.find("myBook").size() != 2) throw new RuntimeException();
        if(books.find("myBook2").size() != 1) throw new RuntimeException();
        if(books.find("myBook3").size() != 0) throw new RuntimeException();
    }

    public static void findWithMixin() {
        Books books = new Books();

        if(books.find("myBook").size() != 3) throw new RuntimeException();
    }
}
