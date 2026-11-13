package dev.jreact.examples.library.server;

import dev.jreact.examples.library.shared.BookStatus;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

/** Fills the in-memory database on start, so there is something to search and page through. */
@Configuration
class SampleData {

    @Bean
    ApplicationRunner seed(BookRepository books) {
        return args -> {
            if (books.count() > 0) return;
            Object[][] rows = {
                    {"The Pragmatic Programmer", "Andrew Hunt", 1999, BookStatus.AVAILABLE, "978-0201616224"},
                    {"Clean Code", "Robert C. Martin", 2008, BookStatus.BORROWED, "978-0132350884"},
                    {"Refactoring", "Martin Fowler", 1999, BookStatus.AVAILABLE, "978-0201485677"},
                    {"Design Patterns", "Erich Gamma", 1994, BookStatus.AVAILABLE, "978-0201633610"},
                    {"Effective Java", "Joshua Bloch", 2001, BookStatus.BORROWED, "978-0134685991"},
                    {"Java Concurrency in Practice", "Brian Goetz", 2006, BookStatus.AVAILABLE, "978-0321349606"},
                    {"The Mythical Man-Month", "Frederick P. Brooks", 1975, BookStatus.LOST, null},
                    {"Code Complete", "Steve McConnell", 1993, BookStatus.AVAILABLE, "978-0735619678"},
                    {"Domain-Driven Design", "Eric Evans", 2003, BookStatus.AVAILABLE, "978-0321125217"},
                    {"Working Effectively with Legacy Code", "Michael Feathers", 2004, BookStatus.BORROWED, null},
                    {"Structure and Interpretation of Computer Programs", "Harold Abelson", 1985, BookStatus.AVAILABLE, "978-0262510875"},
                    {"The Art of Computer Programming", "Donald Knuth", 1968, BookStatus.LOST, null},
                    {"Introduction to Algorithms", "Thomas H. Cormen", 1990, BookStatus.AVAILABLE, "978-0262033848"},
                    {"A Philosophy of Software Design", "John Ousterhout", 2018, BookStatus.AVAILABLE, "978-1732102200"},
                    {"Designing Data-Intensive Applications", "Martin Kleppmann", 2017, BookStatus.BORROWED, "978-1449373320"},
                    {"Release It!", "Michael T. Nygard", 2007, BookStatus.AVAILABLE, null},
                    {"Patterns of Enterprise Application Architecture", "Martin Fowler", 2002, BookStatus.AVAILABLE, "978-0321127426"},
                    {"The Clean Coder", "Robert C. Martin", 2011, BookStatus.AVAILABLE, "978-0137081073"},
                    {"Programming Pearls", "Jon Bentley", 1986, BookStatus.BORROWED, "978-0201657883"},
                    {"Test Driven Development: By Example", "Kent Beck", 2002, BookStatus.AVAILABLE, "978-0321146533"},
                    {"Continuous Delivery", "Jez Humble", 2010, BookStatus.AVAILABLE, "978-0321601919"},
                    {"The Phoenix Project", "Gene Kim", 2013, BookStatus.LOST, null},
                    {"Accelerate", "Nicole Forsgren", 2018, BookStatus.AVAILABLE, "978-1942788331"},
            };
            for (Object[] r : rows) {
                books.save(new BookEntity((String) r[0], (String) r[1], (Integer) r[2], (BookStatus) r[3], (String) r[4], LocalDate.now().minusDays(rows.length - books.count())));
            }
        };
    }
}
