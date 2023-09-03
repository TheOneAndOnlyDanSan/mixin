package test.book;

import mixin.ClassInjector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BookTest {

    @Test
    public void test() {
        getWithoutMixin();

        ClassInjector.addClasses(BookMixin.class);

        getWithMixin();
    }

    public static void getWithoutMixin() {
        Book book = new Book("x", 1999, 100.1);

        assertEquals(book.get(), book);
    }

    public static void getWithMixin() {
        Book book = new Book("x", 1999, 100.1);

        System.out.println(book.equals(book));

        assertSame(book.get(), BookMixin.book);
    }
}
