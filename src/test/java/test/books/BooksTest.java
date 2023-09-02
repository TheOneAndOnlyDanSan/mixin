package test.books;

import mixin.ClassInjector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BooksTest {

    @Test
    public void test() {
        //findWithoutMixin();

        ClassInjector.addClasses(BooksMixin.class);

        findWithMixin();
    }

    public static void findWithoutMixin() {
        Books books = new Books();

        assertEquals(books.find("myBook").size(), 2);
        assertEquals(books.find("myBook2").size(), 1);
        assertEquals(books.find("myBook3").size(), 0);
    }

    public static void findWithMixin() {
        Books books = new Books();

        assertEquals(books.find("myBook").size(), 3);
    }
}
