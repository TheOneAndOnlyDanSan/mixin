package test.book;

import jdk.jfr.Label;
import mixin.ClassInjector;
import org.junit.jupiter.api.Test;
import reflection.FieldReflection;
import reflection.MethodReflection;

import static org.junit.jupiter.api.Assertions.*;

public class BookTest {

    @Test
    public void test() {
        fieldAnnotationsWithout();
        getWithoutMixin();
        methodAnnotationsWithout();

        ClassInjector.addClasses(BookMixin.class);

        fieldAnnotationsWith();
        getWithMixin();
        methodAnnotationsWith();
        fieldSameAnnotationsWith();
    }

    public void fieldSameAnnotationsWith() {
        assertEquals(FieldReflection.getField(Book.class, "cost").getDeclaredAnnotation(Label.class).value(), "LabelCostAnnotation");
        assertEquals(FieldReflection.getField(Book.class, "cost").getDeclaredAnnotations().length, 1);
    }

    public void methodAnnotationsWithout() {
        assertEquals(MethodReflection.getMethod(Book.class, "get").getDeclaredAnnotation(Label.class).value(), "LabelBookAnnotation");
    }

    public void methodAnnotationsWith() {
        assertEquals(MethodReflection.getMethod(Book.class, "get").getDeclaredAnnotation(Label.class).value(), "LabelBookAnnotationMixin");
        assertEquals(MethodReflection.getMethod(Book.class, "get").getDeclaredAnnotations().length, 1);
    }

    public void fieldAnnotationsWithout() {
        assertEquals(FieldReflection.getField(Book.class, "name").getDeclaredAnnotation(Label.class).value(), "LabelNameAnnotation");
    }

    public void fieldAnnotationsWith() {
        assertEquals(FieldReflection.getField(Book.class, "name").getDeclaredAnnotation(Label.class).value(), "LabelNameAnnotationMixin");
        assertEquals(FieldReflection.getField(Book.class, "name").getDeclaredAnnotations().length, 1);
    }

    public void fieldWithoutAnnotationsWith() {
        assertEquals(FieldReflection.getField(Book.class, "publishedYear").getDeclaredAnnotations().length, 0);
    }

    public void getWithoutMixin() {
        Book book = new Book("x", 1999, 100.1);

        assertEquals(book.get(), book);
    }

    public void getWithMixin() {
        Book book = new Book("x", 1999, 100.1);

        assertEquals(book.get(), BookMixin.book);
    }
}