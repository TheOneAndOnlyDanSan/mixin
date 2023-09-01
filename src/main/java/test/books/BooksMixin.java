package test.books;

import mixin.annotations.Mixin;
import mixin.annotations.Overwrite;
import mixin.annotations.Shadow.ShadowMethod;
import test.book.Book;

import java.util.List;

@Mixin(Books.class)
public class BooksMixin {

    @Overwrite("find(Ljava/lang/String;)Ljava/util/List;")
    public static List<Book> find(Books books, String name) {
        return books.books.stream().filter(book -> book.name.contains(name)).toList();
    }
}
