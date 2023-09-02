package test.books;

import mixin.annotations.Mixin;
import mixin.annotations.Method.OverwriteMethod;
import mixin.annotations.Method.ShadowMethod;
import mixin.annotations.field.OverwriteField;
import mixin.annotations.field.ShadowField;
import test.book.Book;

import java.util.ArrayList;
import java.util.List;

@Mixin(Books.class)
public class BooksMixin {

    @OverwriteField("books")
    private static void setBooks(Books books, List<Book> book) {

    }

    @ShadowField("books")
    private static List<Book> books(Books books) {
        return null;
    }

    @ShadowMethod
    private static List<Book> getBooks(Books books) {
        return null;
    }

    @OverwriteMethod("find(Ljava/lang/String;)Ljava/util/List;")
    public static List<Book> find(Books books, String name) {
        if(books(books) != getBooks(books)) throw new RuntimeException();

        List<Book> oldBookList = books(books);
        setBooks(books, new ArrayList<>(books(books)));

        if(oldBookList == books(books)) throw new RuntimeException();

        return getBooks(books).stream().filter(book -> book.name.contains(name)).toList();
    }
}
