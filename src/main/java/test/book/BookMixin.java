package test.book;

import mixin.annotations.Mixin;
import mixin.annotations.Overwrite;

@Mixin(Book.class)
public class BookMixin {
    public static Book book = new Book("bookMixin", 2000, 101.1);

    @Overwrite("get()Ltest/book/Book;")
    public static Book get(Book book1) {
        return book;
    }
}
